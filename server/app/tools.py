"""Conversion implementations. Each takes (inputs, options, workdir) and returns the output path."""
from __future__ import annotations

import os
import re
import shutil
import subprocess
from pathlib import Path
from typing import Callable


class BadInput(Exception):
    """The file or options can't be processed (→ HTTP 422, not retried by the app)."""


def safe_name(name: str) -> str:
    name = os.path.basename(name).strip().replace("\x00", "")
    name = re.sub(r'[\\/:*?"<>|\r\n\t]', " ", name)
    return name[:120] or "file"


def base(p: Path) -> str:
    # inputs are stored as "<index>_<original name>"
    stem = p.stem.split("_", 1)[1] if "_" in p.stem and p.stem.split("_", 1)[0].isdigit() else p.stem
    return stem or "Unidoc"


def run(cmd: list[str], timeout: int = 480, env: dict | None = None) -> subprocess.CompletedProcess:
    try:
        proc = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout, env=env)
    except subprocess.TimeoutExpired:
        raise BadInput("Conversion timed out")
    if proc.returncode != 0:
        tail = (proc.stderr or proc.stdout or "").strip().splitlines()[-3:]
        raise RuntimeError(f"{cmd[0]} failed: {' | '.join(tail)}")
    return proc


def _require_pdf(p: Path) -> None:
    with p.open("rb") as f:
        if b"%PDF" not in f.read(1024):
            raise BadInput("Not a PDF file")


# ----------------------------------------------------------------------------- Office → PDF

OFFICE_EXT = {".doc", ".docx", ".odt", ".rtf", ".txt", ".ppt", ".pptx", ".odp", ".pps", ".ppsx",
              ".xls", ".xlsx", ".ods", ".csv"}


def office(inputs: list[Path], opts: dict, work: Path) -> Path:
    src = inputs[0]
    ext = src.suffix.lower()
    if ext not in OFFICE_EXT:
        raise BadInput(f"Unsupported Office format '{ext}'")
    clean = work / f"{base(src)}{ext}"
    shutil.copyfile(src, clean)
    out_dir = work / "out"
    out_dir.mkdir()
    profile = work / "lo-profile"
    run([
        "soffice", f"-env:UserInstallation=file://{profile}", "--headless", "--norestore", "--nolockcheck",
        "--convert-to", "pdf", "--outdir", str(out_dir), str(clean),
    ], timeout=300, env={**os.environ, "HOME": str(work)})
    result = out_dir / f"{clean.stem}.pdf"
    if not result.exists():
        raise BadInput("LibreOffice couldn't open this file (damaged or password-protected?)")
    return result


# ----------------------------------------------------------------------------- HTML → PDF

def html(inputs: list[Path], opts: dict, work: Path) -> Path:
    src = inputs[0]
    out = work / f"{base(src)}.pdf"
    run([
        "chromium", "--headless=new", "--no-sandbox", "--disable-gpu", "--disable-dev-shm-usage",
        "--no-pdf-header-footer", f"--print-to-pdf={out}", src.resolve().as_uri(),
    ], timeout=120)
    return out


# ----------------------------------------------------------------------------- PDF → Office

def pdf2docx(inputs: list[Path], opts: dict, work: Path) -> Path:
    from pdf2docx import Converter

    src = inputs[0]
    _require_pdf(src)
    if opts.get("ocr", "true") == "true" and _needs_ocr(src):
        src = _ocr_file(src, work, "eng", force=False)
    out = work / f"{base(inputs[0])}.docx"
    cv = Converter(str(src))
    try:
        cv.convert(str(out), multi_processing=False)
    finally:
        cv.close()
    return out


def pdf2xlsx(inputs: list[Path], opts: dict, work: Path) -> Path:
    import pdfplumber
    from openpyxl import Workbook
    from openpyxl.styles import Font

    src = inputs[0]
    _require_pdf(src)
    if opts.get("ocr", "true") == "true" and _needs_ocr(src):
        src = _ocr_file(src, work, "eng", force=False)
    wb = Workbook()
    wb.remove(wb.active)
    with pdfplumber.open(str(src)) as pdf:
        for n, page in enumerate(pdf.pages, start=1):
            tables = page.extract_tables() or []
            if not tables:
                rows = _word_rows(page)
                if not rows:
                    continue
                tables = [rows]
            for t, table in enumerate(tables, start=1):
                ws = wb.create_sheet(title=f"Page {n}" + (f" ({t})" if len(tables) > 1 else ""))
                for r, row in enumerate(table, start=1):
                    for c, value in enumerate(row, start=1):
                        text = (value or "").strip()
                        num = _number(text)
                        cell = ws.cell(row=r, column=c, value=num if num is not None else text)
                        if r == 1 and len(table) > 1:
                            cell.font = Font(bold=True)
                for col in ws.columns:
                    width = max((len(str(c.value or "")) for c in col), default=8)
                    ws.column_dimensions[col[0].column_letter].width = min(max(width + 2, 8), 60)
    if not wb.sheetnames:
        raise BadInput("No tables or text found in this PDF")
    out = work / f"{base(inputs[0])}.xlsx"
    wb.save(out)
    return out


def _word_rows(page) -> list[list[str]]:
    """Rebuilds rows/columns from word positions: same line = similar top; a gap wider than ~2
    characters starts a new cell."""
    words = page.extract_words(keep_blank_chars=False, use_text_flow=False)
    lines: list[list[dict]] = []
    for w in sorted(words, key=lambda w: (round(w["top"]), w["x0"])):
        if lines and abs(lines[-1][0]["top"] - w["top"]) < 3:
            lines[-1].append(w)
        else:
            lines.append([w])
    rows = []
    for line in lines:
        line.sort(key=lambda w: w["x0"])
        cells, current, prev = [], [], None
        for w in line:
            char_w = (w["x1"] - w["x0"]) / max(len(w["text"]), 1)
            if prev is not None and w["x0"] - prev["x1"] > char_w * 2:
                cells.append(" ".join(current))
                current = []
            current.append(w["text"])
            prev = w
        cells.append(" ".join(current))
        rows.append(cells)
    return rows


def _number(text: str):
    t = text.replace(",", "").replace("₹", "").replace("$", "").strip()
    if re.fullmatch(r"-?\d+(\.\d+)?", t):
        return float(t) if "." in t else int(t)
    return None


def pdf2pptx(inputs: list[Path], opts: dict, work: Path) -> Path:
    import fitz  # PyMuPDF
    from pptx import Presentation
    from pptx.util import Emu

    src = inputs[0]
    _require_pdf(src)
    doc = fitz.open(str(src))
    if doc.needs_pass:
        raise BadInput("This PDF is password-protected")
    prs = Presentation()
    first = doc[0].rect
    prs.slide_width = Emu(int(9144000 if first.width >= first.height else 9144000 * first.width / first.height))
    prs.slide_height = Emu(int(9144000 * first.height / first.width if first.width >= first.height else 9144000))
    blank = prs.slide_layouts[6]
    for i, page in enumerate(doc):
        pix = page.get_pixmap(dpi=150)
        img = work / f"slide{i}.png"
        pix.save(str(img))
        slide = prs.slides.add_slide(blank)
        slide.shapes.add_picture(str(img), 0, 0, width=prs.slide_width, height=prs.slide_height)
        # Speaker notes carry the page text so content stays searchable/editable.
        text = page.get_text().strip()
        if text:
            slide.notes_slide.notes_text_frame.text = text[:5000]
    out = work / f"{base(src)}.pptx"
    prs.save(out)
    return out


# ----------------------------------------------------------------------------- OCR

TESS_LANG = {
    "en": "eng", "hi": "hin", "mr": "mar", "ne": "nep", "sa": "san", "bn": "ben", "as": "asm", "ta": "tam",
    "te": "tel", "kn": "kan", "ml": "mal", "gu": "guj", "pa": "pan", "or": "ori", "ur": "urd",
}


def _needs_ocr(pdf: Path) -> bool:
    import fitz

    with fitz.open(str(pdf)) as doc:
        sample = list(doc)[:5]
        return sum(len(p.get_text().strip()) for p in sample) < 30 * max(len(sample), 1)


def _ocr_file(src: Path, work: Path, lang: str, force: bool) -> Path:
    out = work / f"{base(src)}.ocr.pdf"
    cmd = ["ocrmypdf", "--language", lang, "--output-type", "pdf", "--optimize", "1", "--jobs", "2", "--rotate-pages", "--deskew"]
    cmd += ["--force-ocr"] if force else ["--skip-text"]
    try:
        run(cmd + [str(src), str(out)], timeout=480)
    except RuntimeError as e:
        if "EncryptedPdfError" in str(e):
            raise BadInput("This PDF is password-protected")
        raise
    return out


def ocr(inputs: list[Path], opts: dict, work: Path) -> Path:
    src = inputs[0]
    _require_pdf(src)
    lang = TESS_LANG.get(opts.get("lang", "en"), "eng")
    langs = lang if lang == "eng" else f"{lang}+eng"
    result = _ocr_file(src, work, langs, force=opts.get("skip_text", "true") != "true")
    final = work / f"{base(src)} (searchable).pdf"
    result.rename(final)
    return final


# ----------------------------------------------------------------------------- Ghostscript tools

def compress(inputs: list[Path], opts: dict, work: Path) -> Path:
    src = inputs[0]
    _require_pdf(src)
    level = opts.get("level", "balanced")
    dpi = {"high": 200, "small": 96}.get(level, 144)
    preset = {"high": "/printer", "small": "/screen"}.get(level, "/ebook")
    out = work / f"{base(src)} (compressed).pdf"
    run([
        "gs", "-sDEVICE=pdfwrite", "-dCompatibilityLevel=1.6", f"-dPDFSETTINGS={preset}", "-dNOPAUSE", "-dBATCH", "-dQUIET",
        "-dDetectDuplicateImages=true", "-dCompressFonts=true", "-dSubsetFonts=true",
        "-dDownsampleColorImages=true", f"-dColorImageResolution={dpi}",
        "-dDownsampleGrayImages=true", f"-dGrayImageResolution={dpi}",
        "-dDownsampleMonoImages=true", f"-dMonoImageResolution={max(dpi * 2, 300)}",
        f"-sOutputFile={out}", str(src),
    ])
    if out.stat().st_size >= src.stat().st_size:
        shutil.copyfile(src, out)
    return out


def pdfa(inputs: list[Path], opts: dict, work: Path) -> Path:
    src = inputs[0]
    _require_pdf(src)
    level = opts.get("level", "2b")
    part = {"1b": "1", "3b": "3"}.get(level, "2")
    out = work / f"{base(src)} (PDF-A).pdf"
    run([
        "gs", f"-dPDFA={part}", "-dBATCH", "-dNOPAUSE", "-dQUIET", "-dNOOUTERSAVE", "-sColorConversionStrategy=RGB",
        "-sDEVICE=pdfwrite", "-dPDFACompatibilityPolicy=1", "-dEmbedAllFonts=true", "-dSubsetFonts=true",
        f"-sOutputFile={out}", str(src),
    ])
    return out


def repair(inputs: list[Path], opts: dict, work: Path) -> Path:
    src = inputs[0]
    fixed = work / f"{base(src)} (repaired).pdf"
    proc = subprocess.run(["qpdf", "--warning-exit-0", str(src), str(fixed)], capture_output=True, text=True, timeout=240)
    if proc.returncode not in (0, 3) or not fixed.exists():
        # Ghostscript re-interprets the whole file; recovers many files qpdf can't.
        run(["gs", "-sDEVICE=pdfwrite", "-dNOPAUSE", "-dBATCH", "-dQUIET", f"-sOutputFile={fixed}", str(src)])
    return fixed


TOOLS: dict[str, Callable[[list[Path], dict, Path], Path]] = {
    "office": office,
    "html": html,
    "pdf2docx": pdf2docx,
    "pdf2xlsx": pdf2xlsx,
    "pdf2pptx": pdf2pptx,
    "ocr": ocr,
    "compress": compress,
    "pdfa": pdfa,
    "repair": repair,
}
