"""
Spotify bridge for the sidecar.

Pure Spotify Web API — no AppleScript. Talks to Spotify's servers, which then
push commands to whatever Spotify Connect device is currently active (your
local desktop app, your phone, etc). The desktop app stays in the background;
no focus theft.

Auth: Authorization Code with PKCE. Tokens cached to .spotify-token.json and
refreshed transparently. Requires Spotify Premium for playback control.
"""

import base64
import hashlib
import json
import os
import secrets
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Optional

from fastapi import APIRouter, HTTPException
from fastapi.responses import HTMLResponse
from pydantic import BaseModel

router = APIRouter(prefix="/spotify", tags=["spotify"])

SPOTIFY_TOKEN_URL = "https://accounts.spotify.com/api/token"
SPOTIFY_AUTH_URL = "https://accounts.spotify.com/authorize"
SPOTIFY_API_BASE = "https://api.spotify.com/v1"

TOKEN_FILE = Path(__file__).parent / ".spotify-token.json"
DEFAULT_REDIRECT_URI = "http://127.0.0.1:8765/spotify/auth/callback"
USER_SCOPES = (
    "user-read-playback-state "
    "user-modify-playback-state "
    "user-read-currently-playing "
    "playlist-read-private "
    "playlist-read-collaborative "
    "user-library-read"
)

_app_token_cache = {"access_token": None, "expires_at": 0.0}
_pending_pkce: dict = {}  # state -> verifier
_context_name_cache: dict = {}  # uri -> {"name": str, "type": str, "fetched_at": float}
_CONTEXT_CACHE_TTL = 600.0  # 10 minutes


# ---------- helpers ----------

def _client_id() -> str:
    cid = os.environ.get("SPOTIFY_CLIENT_ID")
    if not cid:
        raise HTTPException(status_code=500, detail="SPOTIFY_CLIENT_ID is not set")
    return cid


def _redirect_uri() -> str:
    return os.environ.get("SPOTIFY_REDIRECT_URI", DEFAULT_REDIRECT_URI)


def _post_form(url: str, data: dict) -> dict:
    body = urllib.parse.urlencode(data).encode()
    req = urllib.request.Request(
        url,
        data=body,
        headers={"Content-Type": "application/x-www-form-urlencoded"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            return json.loads(resp.read())
    except urllib.error.HTTPError as e:
        raise HTTPException(status_code=e.code, detail=e.read().decode())


# ---------- app token (Client Credentials, used for catalog search) ----------

def _get_app_token() -> str:
    now = time.time()
    if _app_token_cache["access_token"] and now < _app_token_cache["expires_at"] - 60:
        return _app_token_cache["access_token"]

    secret = os.environ.get("SPOTIFY_CLIENT_SECRET")
    if not secret:
        raise HTTPException(status_code=500, detail="SPOTIFY_CLIENT_SECRET is not set")

    payload = _post_form(SPOTIFY_TOKEN_URL, {
        "grant_type": "client_credentials",
        "client_id": _client_id(),
        "client_secret": secret,
    })
    _app_token_cache["access_token"] = payload["access_token"]
    _app_token_cache["expires_at"] = now + payload.get("expires_in", 3600)
    return _app_token_cache["access_token"]


# ---------- user token (Authorization Code with PKCE) ----------

def _save_token(payload: dict, refresh_fallback: Optional[str] = None) -> None:
    payload["expires_at"] = time.time() + payload.get("expires_in", 3600)
    if "refresh_token" not in payload and refresh_fallback:
        payload["refresh_token"] = refresh_fallback
    TOKEN_FILE.write_text(json.dumps(payload))


def _get_user_token() -> str:
    if not TOKEN_FILE.exists():
        raise HTTPException(status_code=401, detail="not_authenticated")

    data = json.loads(TOKEN_FILE.read_text())
    if time.time() < data.get("expires_at", 0) - 60:
        return data["access_token"]

    refreshed = _post_form(SPOTIFY_TOKEN_URL, {
        "grant_type": "refresh_token",
        "refresh_token": data["refresh_token"],
        "client_id": _client_id(),
    })
    _save_token(refreshed, refresh_fallback=data["refresh_token"])
    return refreshed["access_token"]


# ---------- generic Spotify API call ----------

def _api(method: str, path: str, *,
         params: Optional[dict] = None,
         body: Optional[dict] = None,
         use_app_token: bool = False) -> dict:
    token = _get_app_token() if use_app_token else _get_user_token()
    url = f"{SPOTIFY_API_BASE}{path}"
    if params:
        url += "?" + urllib.parse.urlencode(params)

    headers = {"Authorization": f"Bearer {token}"}
    data = None
    if body is not None:
        headers["Content-Type"] = "application/json"
        data = json.dumps(body).encode()

    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            raw = resp.read()
            if not raw:
                return {}
            return json.loads(raw)
    except urllib.error.HTTPError as e:
        if e.code == 204:
            return {}
        body_text = e.read().decode(errors="replace")
        raise HTTPException(status_code=e.code, detail=body_text)


def _ensure_active_device() -> Optional[str]:
    """If no device is active, pick a Computer-type one and transfer playback to it.
    Returns the device id we transferred to, or None if no devices exist."""
    devices = _api("GET", "/me/player/devices").get("devices", [])
    if not devices:
        return None
    # Prefer an already-active one
    for d in devices:
        if d.get("is_active"):
            return d["id"]
    # Prefer the user's computer
    computer = next((d for d in devices if d.get("type") == "Computer"), devices[0])
    _api("PUT", "/me/player", body={"device_ids": [computer["id"]], "play": False})
    # Spotify needs a beat after transfer before commands work
    time.sleep(0.4)
    return computer["id"]


# ---------- OAuth endpoints ----------

@router.get("/auth/login")
async def auth_login():
    """Returns a browser URL that starts the PKCE flow."""
    verifier = secrets.token_urlsafe(64)[:128]
    challenge = base64.urlsafe_b64encode(
        hashlib.sha256(verifier.encode()).digest()
    ).rstrip(b"=").decode()
    state = secrets.token_urlsafe(16)
    _pending_pkce[state] = verifier

    params = {
        "client_id": _client_id(),
        "response_type": "code",
        "redirect_uri": _redirect_uri(),
        "scope": USER_SCOPES,
        "code_challenge_method": "S256",
        "code_challenge": challenge,
        "state": state,
    }
    return {"auth_url": f"{SPOTIFY_AUTH_URL}?{urllib.parse.urlencode(params)}"}


@router.get("/auth/callback")
async def auth_callback(code: Optional[str] = None,
                        state: Optional[str] = None,
                        error: Optional[str] = None):
    """Spotify redirects the user's browser here after consent."""
    if error:
        return HTMLResponse(f"<html><body><h1>Spotify auth failed</h1><p>{error}</p></body></html>",
                            status_code=400)
    if not code or not state or state not in _pending_pkce:
        return HTMLResponse("<html><body><h1>Invalid auth callback</h1></body></html>",
                            status_code=400)

    verifier = _pending_pkce.pop(state)
    payload = _post_form(SPOTIFY_TOKEN_URL, {
        "grant_type": "authorization_code",
        "code": code,
        "redirect_uri": _redirect_uri(),
        "client_id": _client_id(),
        "code_verifier": verifier,
    })
    _save_token(payload)
    return HTMLResponse(
        "<html><body style='font-family:sans-serif;text-align:center;padding:60px'>"
        "<h1 style='color:#1DB954'>Connected to Spotify</h1>"
        "<p>You can return to Minecraft now.</p>"
        "</body></html>"
    )


@router.get("/auth/status")
async def auth_status():
    return {"authenticated": TOKEN_FILE.exists()}


@router.post("/auth/logout")
async def auth_logout():
    if TOKEN_FILE.exists():
        TOKEN_FILE.unlink()
    return {"ok": True}


# ---------- player endpoints ----------

def _resolve_context(context: Optional[dict]) -> Optional[dict]:
    """Look up the playable context's display name (playlist / album / artist),
    caching by URI so we don't hit the API on every now-playing poll."""
    if not context or not context.get("uri") or not context.get("type"):
        return None

    uri = context["uri"]
    ctype = context["type"]
    now = time.time()
    cached = _context_name_cache.get(uri)
    if cached and now - cached["fetched_at"] < _CONTEXT_CACHE_TTL:
        return {"type": cached["type"], "uri": uri, "name": cached["name"]}

    # Extract the id from the URI: spotify:playlist:abc → abc
    parts = uri.split(":")
    if len(parts) < 3:
        return None
    obj_id = parts[-1]

    path = {
        "playlist": f"/playlists/{obj_id}",
        "album": f"/albums/{obj_id}",
        "artist": f"/artists/{obj_id}",
        "show": f"/shows/{obj_id}",
    }.get(ctype)
    if not path:
        return {"type": ctype, "uri": uri, "name": ""}

    try:
        data = _api("GET", path, params={"fields": "name"} if ctype == "playlist" else None)
    except HTTPException:
        return {"type": ctype, "uri": uri, "name": ""}

    name = data.get("name", "")
    _context_name_cache[uri] = {"name": name, "type": ctype, "fetched_at": now}
    return {"type": ctype, "uri": uri, "name": name}


@router.get("/now-playing")
async def now_playing():
    if not TOKEN_FILE.exists():
        return {"running": False, "authenticated": False}

    state = _api("GET", "/me/player")
    if not state or not state.get("device"):
        return {"running": False, "authenticated": True, "state": "no_device"}

    item = state.get("item")
    device = state.get("device", {})
    if not item:
        return {
            "running": True,
            "authenticated": True,
            "state": "stopped",
            "volume": device.get("volume_percent", 0),
        }

    payload = {
        "running": True,
        "authenticated": True,
        "state": "playing" if state.get("is_playing") else "paused",
        "name": item["name"],
        "artist": ", ".join(a["name"] for a in item["artists"]),
        "album": item["album"]["name"],
        "track_id": item["uri"],
        "duration_seconds": item["duration_ms"] / 1000.0,
        "position_seconds": state.get("progress_ms", 0) / 1000.0,
        "volume": device.get("volume_percent", 0),
    }
    context = _resolve_context(state.get("context"))
    if context:
        payload["context_type"] = context["type"]
        payload["context_name"] = context["name"]
        payload["context_uri"] = context["uri"]
    return payload


@router.post("/playpause")
async def playpause():
    state = _api("GET", "/me/player")
    if state and state.get("is_playing"):
        _api("PUT", "/me/player/pause")
    else:
        _ensure_active_device()
        _api("PUT", "/me/player/play")
    return {"ok": True}


@router.post("/play")
async def play():
    _ensure_active_device()
    _api("PUT", "/me/player/play")
    return {"ok": True}


@router.post("/pause")
async def pause():
    _api("PUT", "/me/player/pause")
    return {"ok": True}


@router.post("/next")
async def next_track():
    _api("POST", "/me/player/next")
    return {"ok": True}


@router.post("/previous")
async def previous_track():
    _api("POST", "/me/player/previous")
    return {"ok": True}


class VolumeRequest(BaseModel):
    volume: int


@router.post("/volume")
async def set_volume(req: VolumeRequest):
    v = max(0, min(100, req.volume))
    _api("PUT", "/me/player/volume", params={"volume_percent": v})
    return {"ok": True, "volume": v}


class SeekRequest(BaseModel):
    position: float  # seconds


@router.post("/seek")
async def seek(req: SeekRequest):
    pos_ms = max(0, int(req.position * 1000))
    _api("PUT", "/me/player/seek", params={"position_ms": pos_ms})
    return {"ok": True}


class PlayUriRequest(BaseModel):
    uri: str
    context_uri: Optional[str] = None  # e.g. spotify:playlist:... so next/prev navigates
    uris: Optional[list] = None        # ad-hoc queue (e.g. all current search results)


@router.post("/play-uri")
async def play_uri(req: PlayUriRequest):
    _ensure_active_device()
    body: dict
    if req.context_uri:
        body = {"context_uri": req.context_uri, "offset": {"uri": req.uri}}
    elif req.uris:
        body = {"uris": req.uris, "offset": {"uri": req.uri}}
    elif req.uri.startswith("spotify:track:"):
        body = {"uris": [req.uri]}
    else:
        body = {"context_uri": req.uri}
    _api("PUT", "/me/player/play", body=body)
    return {"ok": True}


@router.get("/queue")
async def get_queue():
    data = _api("GET", "/me/player/queue")
    queue_items = data.get("queue", []) or []
    tracks = []
    for t in queue_items:
        if not t or not t.get("uri"):
            continue
        tracks.append({
            "uri": t["uri"],
            "name": t.get("name", ""),
            "artist": ", ".join(a["name"] for a in t.get("artists", [])),
            "album": (t.get("album") or {}).get("name", ""),
            "duration_ms": t.get("duration_ms", 0),
        })
    return {"tracks": tracks}


# ---------- library (user playlists + liked songs) ----------

@router.get("/playlists")
async def list_playlists():
    data = _api("GET", "/me/playlists", params={"limit": 50})
    items = data.get("items", [])
    playlists = []
    for p in items:
        if not p:
            continue
        playlists.append({
            "id": p["id"],
            "name": p["name"],
            "uri": p["uri"],
            "owner": (p.get("owner") or {}).get("display_name", ""),
            "track_count": (p.get("tracks") or {}).get("total", 0),
        })
    return {"playlists": playlists}


@router.get("/playlists/{playlist_id}/tracks")
async def playlist_tracks(playlist_id: str):
    data = _api("GET", f"/playlists/{playlist_id}/tracks",
                params={"limit": 100, "fields": "items(track(uri,name,artists(name),album(name),duration_ms))"})
    items = data.get("items", [])
    tracks = []
    for entry in items:
        t = (entry or {}).get("track")
        if not t or not t.get("uri"):
            continue
        tracks.append({
            "uri": t["uri"],
            "name": t["name"],
            "artist": ", ".join(a["name"] for a in t.get("artists", [])),
            "album": (t.get("album") or {}).get("name", ""),
            "duration_ms": t.get("duration_ms", 0),
        })
    return {"tracks": tracks}


@router.get("/liked")
async def liked_tracks():
    data = _api("GET", "/me/tracks", params={"limit": 50})
    items = data.get("items", [])
    tracks = []
    for entry in items:
        t = (entry or {}).get("track")
        if not t or not t.get("uri"):
            continue
        tracks.append({
            "uri": t["uri"],
            "name": t["name"],
            "artist": ", ".join(a["name"] for a in t.get("artists", [])),
            "album": (t.get("album") or {}).get("name", ""),
            "duration_ms": t.get("duration_ms", 0),
        })
    return {"tracks": tracks}


# ---------- catalog search (uses app token, no user OAuth) ----------

@router.get("/search")
async def search(q: str, type: str = "track", limit: int = 20):
    if not q.strip():
        return {"tracks": []}

    data = _api("GET", "/search",
                params={"q": q, "type": type, "limit": limit},
                use_app_token=True)
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
