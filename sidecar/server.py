"""
Minecraft Use — Python Sidecar Server

Runs alongside Minecraft, handles:
- Browser Use for searching/downloading schematics from the web
- Local schematic cache management
- Communication with the Fabric mod via HTTP on localhost:8765
"""

import json
import os
import tempfile
from pathlib import Path

import sounddevice as sd
from dotenv import load_dotenv
from fastapi import FastAPI
from fastapi.responses import JSONResponse
from openai import OpenAI
from pydantic import BaseModel

from browser_search import SchematicSearcher
from email_bridge import router as email_router
from spotify_bridge import router as spotify_router

load_dotenv(Path(__file__).parent.parent / ".env")

app = FastAPI(title="Minecraft Use Sidecar", version="1.0.0")
app.include_router(spotify_router)
app.include_router(email_router)

CACHE_DIR = Path(__file__).parent / "schema_cache"
CACHE_DIR.mkdir(exist_ok=True)

searcher = SchematicSearcher(cache_dir=CACHE_DIR)


class SearchRequest(BaseModel):
    query: str


class DownloadRequest(BaseModel):
    query: str


class TranscribeRequest(BaseModel):
    duration: float = 15.0


SAMPLE_RATE = 16000


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


_recording = False
_audio_chunks = []


@app.post("/transcribe/start-recording")
async def start_recording():
    """Start recording from mic. Call stop-recording to finish."""
    global _recording, _audio_chunks
    import numpy as np

    if _recording:
        return {"status": "already_recording"}

    _recording = True
    _audio_chunks = []
    print("[Voice] Recording started...", flush=True)

    def record_loop():
        global _recording
        while _recording:
            chunk = sd.rec(int(SAMPLE_RATE * 0.1), samplerate=SAMPLE_RATE, channels=1, dtype="float32")
            sd.wait()
            _audio_chunks.append(chunk)
        print(f"[Voice] Recording stopped. {len(_audio_chunks)} chunks captured.", flush=True)

    import threading
    t = threading.Thread(target=record_loop, daemon=True)
    t.start()

    return {"status": "recording"}


@app.post("/transcribe/stop-recording")
async def stop_recording():
    """Stop recording and transcribe via Whisper."""
    global _recording, _audio_chunks
    import numpy as np
    import wave

    _recording = False

    # Brief pause to let the recording thread finish its last chunk
    import time
    time.sleep(0.15)

    if not _audio_chunks:
        return {"text": "", "duration": 0}

    audio = np.concatenate(_audio_chunks)
    duration = len(audio) / SAMPLE_RATE
    _audio_chunks = []

    # Convert to 16-bit PCM WAV
    pcm = (audio[:, 0] * 32767).astype(np.int16)

    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        with wave.open(tmp.name, "wb") as wf:
            wf.setnchannels(1)
            wf.setsampwidth(2)
            wf.setframerate(SAMPLE_RATE)
            wf.writeframes(pcm.tobytes())
        tmp_path = tmp.name

    try:
        print(f"[Voice] Sending {duration:.1f}s audio to Whisper...", flush=True)
        client = OpenAI(api_key=os.environ.get("OPENAI_API_KEY"))
        with open(tmp_path, "rb") as audio_file:
            transcript = client.audio.transcriptions.create(
                model="whisper-1",
                file=audio_file,
            )
        os.unlink(tmp_path)
        print(f"[Voice] Transcribed: '{transcript.text}'", flush=True)
        return {"text": transcript.text, "duration": duration}
    except Exception as e:
        os.unlink(tmp_path)
        print(f"[Voice] Error: {e}", flush=True)
        return JSONResponse(status_code=500, content={"error": str(e)})


@app.get("/transcribe/test")
async def transcribe_test():
    """Quick test that the mic and Whisper API work."""
    try:
        # Record 2 seconds
        audio = sd.rec(int(2 * SAMPLE_RATE), samplerate=SAMPLE_RATE, channels=1, dtype="float32")
        sd.wait()
        has_audio = audio.max() > 0.001
        has_key = bool(os.environ.get("OPENAI_API_KEY"))
        return {"mic_works": has_audio, "api_key_set": has_key}
    except Exception as e:
        return {"error": str(e)}


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="127.0.0.1", port=8765)
