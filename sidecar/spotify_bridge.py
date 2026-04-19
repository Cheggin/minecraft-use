"""
Spotify bridge for the sidecar.

Local playback + state via AppleScript (no auth, no Premium needed).
Search via Spotify Web API using Client Credentials flow (read-only catalog).
"""

import os
import subprocess
import time
import urllib.parse
import urllib.request
import json
from typing import Optional

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel

router = APIRouter(prefix="/spotify", tags=["spotify"])

SPOTIFY_TOKEN_URL = "https://accounts.spotify.com/api/token"
SPOTIFY_API_BASE = "https://api.spotify.com/v1"

_token_cache = {"access_token": None, "expires_at": 0.0}


def _osa(script: str) -> str:
    """Run an AppleScript snippet against Spotify, return stdout."""
    try:
        result = subprocess.run(
            ["osascript", "-e", script],
            capture_output=True,
            text=True,
            timeout=5,
        )
    except subprocess.TimeoutExpired:
        raise HTTPException(status_code=504, detail="osascript timed out")

    if result.returncode != 0:
        err = result.stderr.strip()
        if "Spotify got an error" in err or "isn't running" in err.lower():
            raise HTTPException(status_code=503, detail="Spotify is not running")
        raise HTTPException(status_code=500, detail=f"osascript failed: {err}")

    return result.stdout.strip()


def _spotify_running() -> bool:
    out = _osa('tell application "System Events" to (name of processes) contains "Spotify"')
    return out.lower() == "true"


def _get_app_token() -> str:
    """Client Credentials token, cached until expiry."""
    now = time.time()
    if _token_cache["access_token"] and now < _token_cache["expires_at"] - 60:
        return _token_cache["access_token"]

    client_id = os.environ.get("SPOTIFY_CLIENT_ID")
    client_secret = os.environ.get("SPOTIFY_CLIENT_SECRET")
    if not client_id or not client_secret:
        raise HTTPException(
            status_code=500,
            detail="SPOTIFY_CLIENT_ID and SPOTIFY_CLIENT_SECRET must be set in .env",
        )

    body = urllib.parse.urlencode({
        "grant_type": "client_credentials",
        "client_id": client_id,
        "client_secret": client_secret,
    }).encode()

    req = urllib.request.Request(
        SPOTIFY_TOKEN_URL,
        data=body,
        headers={"Content-Type": "application/x-www-form-urlencoded"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=10) as resp:
        payload = json.loads(resp.read())

    _token_cache["access_token"] = payload["access_token"]
    _token_cache["expires_at"] = now + payload.get("expires_in", 3600)
    return _token_cache["access_token"]


def _api_get(path: str, params: dict) -> dict:
    token = _get_app_token()
    url = f"{SPOTIFY_API_BASE}{path}?{urllib.parse.urlencode(params)}"
    req = urllib.request.Request(url, headers={"Authorization": f"Bearer {token}"})
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            return json.loads(resp.read())
    except urllib.error.HTTPError as e:
        raise HTTPException(status_code=e.code, detail=f"Spotify API: {e.read().decode()}")


_NOW_PLAYING_SCRIPT = '''
if application "Spotify" is not running then return "NOT_RUNNING"
tell application "Spotify"
    set s to player state as text
    if s is "stopped" then return "STOPPED"
    set t to current track
    return s & "\\t" & (name of t) & "\\t" & (artist of t) & "\\t" & (album of t) & "\\t" & (id of t) & "\\t" & (duration of t) & "\\t" & (player position) & "\\t" & (sound volume)
end tell
'''


@router.get("/now-playing")
async def now_playing():
    out = _osa(_NOW_PLAYING_SCRIPT)
    if out == "NOT_RUNNING":
        return {"running": False}
    if out == "STOPPED":
        return {"running": True, "state": "stopped"}

    parts = out.split("\t")
    if len(parts) != 8:
        raise HTTPException(status_code=500, detail=f"unexpected osascript output: {out!r}")

    state, name, artist, album, track_id, duration_ms, position_s, volume = parts
    return {
        "running": True,
        "state": state,
        "name": name,
        "artist": artist,
        "album": album,
        "track_id": track_id,
        "duration_seconds": int(duration_ms) / 1000.0,
        "position_seconds": float(position_s),
        "volume": int(volume),
    }


@router.post("/playpause")
async def playpause():
    _osa('tell application "Spotify" to playpause')
    return {"ok": True}


@router.post("/play")
async def play():
    _osa('tell application "Spotify" to play')
    return {"ok": True}


@router.post("/pause")
async def pause():
    _osa('tell application "Spotify" to pause')
    return {"ok": True}


@router.post("/next")
async def next_track():
    _osa('tell application "Spotify" to next track')
    return {"ok": True}


@router.post("/previous")
async def previous_track():
    _osa('tell application "Spotify" to previous track')
    return {"ok": True}


class VolumeRequest(BaseModel):
    volume: int


@router.post("/volume")
async def set_volume(req: VolumeRequest):
    v = max(0, min(100, req.volume))
    _osa(f'tell application "Spotify" to set sound volume to {v}')
    return {"ok": True, "volume": v}


class SeekRequest(BaseModel):
    position: float


@router.post("/seek")
async def seek(req: SeekRequest):
    pos = max(0.0, req.position)
    _osa(f'tell application "Spotify" to set player position to {pos}')
    return {"ok": True, "position": pos}


class PlayUriRequest(BaseModel):
    uri: str
    context: Optional[str] = None


@router.post("/play-uri")
async def play_uri(req: PlayUriRequest):
    if req.context:
        _osa(
            f'tell application "Spotify" to play track "{req.uri}" '
            f'in context "{req.context}"'
        )
    else:
        _osa(f'tell application "Spotify" to play track "{req.uri}"')
    return {"ok": True}


@router.get("/search")
async def search(q: str, type: str = "track", limit: int = 20):
    if not q.strip():
        return {"tracks": []}

    data = _api_get("/search", {"q": q, "type": type, "limit": limit})
    items = data.get("tracks", {}).get("items", [])
    tracks = [
        {
            "uri": t["uri"],
            "name": t["name"],
            "artist": ", ".join(a["name"] for a in t["artists"]),
            "album": t["album"]["name"],
            "duration_ms": t["duration_ms"],
        }
        for t in items
    ]
    return {"tracks": tracks}
