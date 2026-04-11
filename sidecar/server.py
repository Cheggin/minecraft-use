"""
Minecraft Use — Python Sidecar Server

Runs alongside Minecraft, handles:
- Browser Use for searching/downloading schematics from the web
- Local schematic cache management
- Communication with the Fabric mod via HTTP on localhost:8765
"""

import json
import os
from pathlib import Path

from fastapi import FastAPI
from fastapi.responses import JSONResponse
from pydantic import BaseModel

from browser_search import SchematicSearcher

app = FastAPI(title="Minecraft Use Sidecar", version="1.0.0")

CACHE_DIR = Path(__file__).parent / "schema_cache"
CACHE_DIR.mkdir(exist_ok=True)

searcher = SchematicSearcher(cache_dir=CACHE_DIR)


class SearchRequest(BaseModel):
    query: str


class DownloadRequest(BaseModel):
    query: str


@app.get("/ping")
async def ping():
    return {"status": "ok", "version": "1.0.0"}


@app.get("/list")
async def list_schematics():
    """List all locally cached schematics."""
    schematics = []
    for f in CACHE_DIR.iterdir():
        if f.suffix in (".schem", ".schematic", ".litematic", ".nbt"):
            size_kb = f.stat().st_size / 1024
            schematics.append({
                "name": f.stem,
                "filename": f.name,
                "size": f"{size_kb:.1f} KB",
                "format": f.suffix,
                "path": str(f),
            })
    return {"schematics": schematics}


@app.post("/search")
async def search(request: SearchRequest):
    """Search for schematics using Browser Use (returns results without downloading)."""
    results = await searcher.search(request.query)
    return {"results": results, "query": request.query}


@app.post("/download")
async def download(request: DownloadRequest):
    """Search for and download the best matching schematic."""
    result = await searcher.search_and_download(request.query)
    return result


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="127.0.0.1", port=8765)
