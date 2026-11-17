# Unidoc Cloud — free Hugging Face Space (Docker SDK, CPU basic, 16 GB RAM).
FROM python:3.12-slim-bookworm

ENV DEBIAN_FRONTEND=noninteractive PYTHONUNBUFFERED=1 PIP_NO_CACHE_DIR=1 HOME=/home/user

# LibreOffice (Office → PDF), Ghostscript (compress, PDF/A), qpdf (repair), Tesseract + Indian
# language packs (OCR), Chromium (HTML/URL → PDF), Noto fonts for every Indian script.
RUN apt-get update && apt-get install -y --no-install-recommends \
      libreoffice-writer libreoffice-calc libreoffice-impress libreoffice-java-common default-jre-headless \
      ghostscript qpdf unpaper pngquant \
      tesseract-ocr tesseract-ocr-eng tesseract-ocr-hin tesseract-ocr-mar tesseract-ocr-nep tesseract-ocr-san \
      tesseract-ocr-ben tesseract-ocr-asm tesseract-ocr-tam tesseract-ocr-tel tesseract-ocr-kan tesseract-ocr-mal \
      tesseract-ocr-guj tesseract-ocr-pan tesseract-ocr-ori tesseract-ocr-urd \
      chromium fonts-noto-core fonts-noto-ui-core fonts-noto-extra fonts-dejavu fonts-liberation2 fonts-crosextra-carlito fonts-crosextra-caladea \
    && rm -rf /var/lib/apt/lists/*

RUN useradd -m -u 1000 user
WORKDIR /app
COPY requirements.txt .
RUN pip install -r requirements.txt
COPY --chown=user app ./app
USER user

EXPOSE 7860
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "7860", "--workers", "1", "--timeout-keep-alive", "30"]
