# Unidoc Cloud (free Hugging Face Space)

Stateless conversion server used by the Unidoc app for: Office → PDF (LibreOffice), PDF → Word/Excel/PowerPoint,
OCR for Bengali / Tamil / Telugu / Kannada / Malayalam / Gujarati / Punjabi / Odia / Assamese / Urdu (Tesseract),
Ghostscript compression, PDF/A and repair. Files are deleted right after each response. Cost: ₹0.

## Deploy (≈10 min, one time)

| # | Step |
|---|------|
| 1 | Sign up at <https://huggingface.co/join> (free). |
| 2 | **New Space** → name `unidoc-cloud` → SDK **Docker** → template **Blank** → hardware **CPU basic (free)** → visibility **Public** (Private works too but the app then also needs an HF token — not supported). |
| 3 | Space → **Settings → Variables and secrets → New secret**: `UNIDOC_API_KEY` = a long random string (e.g. output of `openssl rand -hex 24`). |
| 4 | Upload this folder's contents (`Dockerfile`, `requirements.txt`, `app/`, this `README.md`) — either **Files → Add file → Upload files**, or with git (below). |
| 5 | Wait for the build (~8–12 min first time, status turns **Running**). |
| 6 | Open `https://<your-user>-unidoc-cloud.hf.space/` — should show `{"service":"Unidoc Cloud",...}`. |
| 7 | App → **Settings → Unidoc Cloud** → turn on → URL `https://<your-user>-unidoc-cloud.hf.space` → key from step 3 → **Test connection**. |

Git upload (needs a write token from <https://huggingface.co/settings/tokens>):

```bash
git clone https://huggingface.co/spaces/<your-user>/unidoc-cloud
cp -r Dockerfile requirements.txt app README.md unidoc-cloud/
cd unidoc-cloud && git add . && git commit -m "Unidoc Cloud" && git push
```

HF needs this front-matter at the top of the Space's README.md (the Space template creates it; keep it):

```yaml
---
title: Unidoc Cloud
sdk: docker
app_port: 7860
---
```

## Behaviour / limits

| Item | Value |
|------|-------|
| Sleep | Free Spaces sleep after 48 h without requests; the app wakes it (first request waits ~30–90 s). |
| Upload cap | 60 MB per request (`UNIDOC_MAX_MB` variable). |
| Parallel jobs | 2 (`UNIDOC_CONCURRENCY`). |
| Timeout | 9 min per job. |
| Privacy | Temp dir per request, removed after the response; no logs of file contents. |

Fallback host (if HF is ever unavailable): Oracle Cloud Always Free ARM VM →
`docker build -t unidoc-cloud . && docker run -d -p 443:7860 -e UNIDOC_API_KEY=... unidoc-cloud` behind Caddy for HTTPS.

## API

| Method | Path | Notes |
|--------|------|-------|
| GET | `/health` | `{"status":"ok","version","features"}` |
| POST | `/v1/convert/{tool}` | multipart `files` (1+), `options` (JSON of strings); header `X-Unidoc-Key`. Returns file + `X-Unidoc-Filename`. |

Tools: `office`, `html`, `pdf2docx`, `pdf2xlsx`, `pdf2pptx`, `ocr` (`lang`: en hi mr ne sa bn as ta te kn ml gu pa or ur), `compress` (`level`), `pdfa` (`level`: 1b/2b/3b), `repair`.

## Local test

```bash
pip install -r requirements.txt pytest httpx
pytest -q
```
