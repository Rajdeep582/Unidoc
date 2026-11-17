"""Run: pip install -r requirements.txt pytest httpx && pytest -q  (tools needing system binaries are skipped if absent)."""
import io
import os
import shutil

import pytest

os.environ["UNIDOC_API_KEY"] = "test-key"

from fastapi.testclient import TestClient  # noqa: E402

from app import main, tools  # noqa: E402

main.API_KEY = "test-key"
client = TestClient(main.app)
H = {"X-Unidoc-Key": "test-key"}


def _pdf(text="Hello Unidoc 123") -> bytes:
    import fitz

    doc = fitz.open()
    page = doc.new_page()
    page.insert_text((72, 72), text, fontsize=14)
    page.insert_text((72, 100), "Item    Qty    Price", fontsize=12)
    page.insert_text((72, 120), "Pen    2    10.50", fontsize=12)
    data = doc.tobytes()
    doc.close()
    return data


def test_health_requires_key():
    assert client.get("/health").status_code == 401
    r = client.get("/health", headers=H)
    assert r.status_code == 200
    assert "office" in r.json()["features"]


def test_unknown_tool():
    r = client.post("/v1/convert/nope", headers=H, files={"files": ("a.pdf", b"%PDF-1.4")})
    assert r.status_code == 404


def test_bad_options():
    r = client.post("/v1/convert/pdf2xlsx", headers=H, files={"files": ("a.pdf", _pdf())}, data={"options": "[1]"})
    assert r.status_code == 400


def test_not_pdf_rejected():
    r = client.post("/v1/convert/pdf2xlsx", headers=H, files={"files": ("a.pdf", b"hello")}, data={"options": "{}"})
    assert r.status_code == 422


def test_pdf2xlsx_roundtrip():
    r = client.post("/v1/convert/pdf2xlsx", headers=H, files={"files": ("invoice.pdf", _pdf())}, data={"options": '{"ocr":"false"}'})
    assert r.status_code == 200, r.text
    assert r.headers["x-unidoc-filename"] == "invoice.xlsx"
    from openpyxl import load_workbook

    wb = load_workbook(io.BytesIO(r.content))
    values = [c.value for row in wb.active.iter_rows() for c in row if c.value is not None]
    assert "Pen" in values and 10.5 in values


def test_pdf2docx():
    r = client.post("/v1/convert/pdf2docx", headers=H, files={"files": ("letter.pdf", _pdf())}, data={"options": '{"ocr":"false"}'})
    assert r.status_code == 200, r.text
    assert r.content[:2] == b"PK"


def test_pdf2pptx():
    r = client.post("/v1/convert/pdf2pptx", headers=H, files={"files": ("deck.pdf", _pdf())})
    assert r.status_code == 200, r.text
    assert r.headers["x-unidoc-filename"] == "deck.pptx"


@pytest.mark.skipif(shutil.which("gs") is None, reason="ghostscript not installed")
def test_compress():
    r = client.post("/v1/convert/compress", headers=H, files={"files": ("a.pdf", _pdf())})
    assert r.status_code == 200 and r.content.startswith(b"%PDF")


def test_safe_name():
    assert tools.safe_name("../../etc/passwd") == "passwd"
    assert tools.safe_name('a<b>:c.pdf') == "a b  c.pdf"
