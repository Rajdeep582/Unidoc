"""Unidoc Cloud: stateless conversion API for the Unidoc Android app.

POST /v1/convert/{tool}  multipart: files=<one or more>, options=<JSON object of strings>
  → 200 with the result file (X-Unidoc-Filename header) | 4xx JSON {detail} | 5xx
GET  /health → {"status":"ok","version":...,"features":[...]}

Auth: header X-Unidoc-Key must equal the UNIDOC_API_KEY Space secret (if set).
"""
from __future__ import annotations

import asyncio
import hmac
import json
import os
import shutil
import tempfile
from pathlib import Path

from fastapi import FastAPI, File, Form, Header, HTTPException, UploadFile
from fastapi.responses import FileResponse
from starlette.background import BackgroundTask

from . import tools

VERSION = "1.0.0"
MAX_UPLOAD = int(os.environ.get("UNIDOC_MAX_MB", "60")) * 1024 * 1024
API_KEY = os.environ.get("UNIDOC_API_KEY", "")
# One heavy job at a time keeps the free 2-vCPU Space responsive.
_slots = asyncio.Semaphore(int(os.environ.get("UNIDOC_CONCURRENCY", "2")))

app = FastAPI(title="Unidoc Cloud", version=VERSION, docs_url=None, redoc_url=None)


def _check_key(key: str | None) -> None:
    if API_KEY and not hmac.compare_digest(key or "", API_KEY):
        raise HTTPException(status_code=401, detail="Invalid key")


@app.get("/")
def root():
    return {"service": "Unidoc Cloud", "version": VERSION}


@app.get("/health")
def health(x_unidoc_key: str | None = Header(default=None)):
    _check_key(x_unidoc_key)
    return {"status": "ok", "version": VERSION, "features": sorted(tools.TOOLS)}


@app.post("/v1/convert/{tool}")
async def convert(
    tool: str,
    files: list[UploadFile] = File(...),
    options: str = Form("{}"),
    x_unidoc_key: str | None = Header(default=None),
):
    _check_key(x_unidoc_key)
    if tool not in tools.TOOLS:
        raise HTTPException(status_code=404, detail=f"Unknown tool '{tool}'")
    try:
        opts = {str(k): str(v) for k, v in json.loads(options or "{}").items()}
    except (ValueError, AttributeError):
        raise HTTPException(status_code=400, detail="options must be a JSON object")

    work = Path(tempfile.mkdtemp(prefix="unidoc-"))
    try:
        inputs: list[Path] = []
        total = 0
        for i, upload in enumerate(files):
            name = tools.safe_name(upload.filename or f"input{i}")
            dest = work / f"{i}_{name}"
            with dest.open("wb") as out:
                while chunk := await upload.read(1 << 20):
                    total += len(chunk)
                    if total > MAX_UPLOAD:
                        raise HTTPException(status_code=413, detail=f"Files exceed {MAX_UPLOAD // (1 << 20)} MB")
                    out.write(chunk)
            inputs.append(dest)
        if not inputs:
            raise HTTPException(status_code=400, detail="No file uploaded")

        async with _slots:
            try:
                result = await asyncio.wait_for(
                    asyncio.to_thread(tools.TOOLS[tool], inputs, opts, work), timeout=540
                )
            except tools.BadInput as e:
                raise HTTPException(status_code=422, detail=str(e))
            except asyncio.TimeoutError:
                raise HTTPException(status_code=504, detail="Conversion took too long")
    except BaseException:
        shutil.rmtree(work, ignore_errors=True)
        raise

    return FileResponse(
        result,
        filename=result.name,
        headers={"X-Unidoc-Filename": result.name},
        background=BackgroundTask(shutil.rmtree, work, True),
    )
