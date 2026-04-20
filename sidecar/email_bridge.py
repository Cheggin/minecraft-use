"""
Email bridge for the sidecar.

IMAP + SMTP against Gmail (or any IMAP provider) using a per-user app password.
No OAuth. No Google Cloud Console. Credentials live in .env.
"""

import os
import smtplib
from datetime import datetime, timezone
from email.message import EmailMessage
from threading import Lock
from typing import Optional

from bs4 import BeautifulSoup
from fastapi import APIRouter, HTTPException
from imap_tools import MailBox, AND, MailMessageFlags
from pydantic import BaseModel

router = APIRouter(prefix="/mail", tags=["mail"])

IMAP_HOST = os.environ.get("IMAP_HOST", "imap.gmail.com")
SMTP_HOST = os.environ.get("SMTP_HOST", "smtp.gmail.com")
SMTP_PORT = int(os.environ.get("SMTP_PORT", "587"))
ARCHIVE_FOLDER = os.environ.get("MAIL_ARCHIVE_FOLDER", "[Gmail]/All Mail")
TRASH_FOLDER = os.environ.get("MAIL_TRASH_FOLDER", "[Gmail]/Trash")

_mailbox: Optional[MailBox] = None
_mailbox_lock = Lock()


def _creds():
    addr = os.environ.get("GMAIL_ADDRESS")
    pw = os.environ.get("GMAIL_APP_PASSWORD")
    if not addr or not pw:
        raise HTTPException(
            status_code=500,
            detail="GMAIL_ADDRESS and GMAIL_APP_PASSWORD must be set in .env",
        )
    # App passwords are typically shown with spaces for readability; IMAP doesn't want them.
    return addr, pw.replace(" ", "")


def _connect() -> MailBox:
    addr, pw = _creds()
    return MailBox(IMAP_HOST).login(addr, pw, initial_folder="INBOX")


def _get_mailbox() -> MailBox:
    """Return a live mailbox, reconnecting if the previous session died."""
    global _mailbox
    with _mailbox_lock:
        if _mailbox is None:
            _mailbox = _connect()
            return _mailbox
        # Cheap health check — IMAP NOOP. Reconnect on any error.
        try:
            _mailbox.client.noop()
            return _mailbox
        except Exception:
            try:
                _mailbox.logout()
            except Exception:
                pass
            _mailbox = _connect()
            return _mailbox


def _html_to_text(html: str) -> str:
    soup = BeautifulSoup(html or "", "html.parser")
    for tag in soup(["script", "style"]):
        tag.decompose()
    text = soup.get_text(separator="\n")
    # Collapse runs of blank lines
    lines = [ln.rstrip() for ln in text.splitlines()]
    out = []
    blank = 0
    for ln in lines:
        if ln.strip():
            out.append(ln)
            blank = 0
        else:
            blank += 1
            if blank <= 1:
                out.append("")
    return "\n".join(out).strip()


def _normalize(text: str) -> str:
    """Strip CR characters and other control chars Minecraft can't render."""
    if not text:
        return ""
    text = text.replace("\r\n", "\n").replace("\r", "\n")
    # Drop other C0 control chars except tab + newline
    return "".join(ch for ch in text if ch in "\t\n" or ord(ch) >= 0x20)


def _best_body(msg) -> str:
    if msg.text:
        return _normalize(msg.text)
    if msg.html:
        return _normalize(_html_to_text(msg.html))
    return ""


def _snippet(body: str, n: int = 120) -> str:
    flat = " ".join(body.split())
    return flat[:n] + ("\u2026" if len(flat) > n else "")


def _iso(dt: Optional[datetime]) -> Optional[str]:
    if not dt:
        return None
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=timezone.utc)
    return dt.astimezone(timezone.utc).isoformat()


def _summarize(msg, include_snippet: bool = False) -> dict:
    snippet = _snippet(_best_body(msg)) if include_snippet else ""
    return {
        "uid": int(msg.uid) if msg.uid else None,
        "from": msg.from_ or "",
        "subject": msg.subject or "(no subject)",
        "snippet": snippet,
        "date": _iso(msg.date),
        "read": MailMessageFlags.SEEN in (msg.flags or []),
    }


# ---------- endpoints ----------

@router.get("/status")
async def status():
    addr = os.environ.get("GMAIL_ADDRESS")
    if not addr or not os.environ.get("GMAIL_APP_PASSWORD"):
        return {"authenticated": False, "reason": "credentials_missing"}
    try:
        _get_mailbox()
    except Exception as e:
        return {"authenticated": False, "reason": str(e), "address": addr}
    return {"authenticated": True, "address": addr}


@router.get("/inbox")
async def inbox(limit: int = 20, offset: int = 0):
    mailbox = _get_mailbox()
    mailbox.folder.set("INBOX")

    # Headers-only + bulk = one IMAP round-trip, no body fetching.
    # Drops the snippet but keeps the list endpoint under ~1s for 25 messages.
    messages = list(
        mailbox.fetch(
            criteria=AND(all=True),
            reverse=True,
            limit=limit + offset,
            mark_seen=False,
            headers_only=True,
            bulk=True,
        )
    )[offset:offset + limit]

    return {
        "folder": "INBOX",
        "messages": [_summarize(m, include_snippet=False) for m in messages],
    }


@router.get("/message/{uid}")
async def message(uid: int):
    mailbox = _get_mailbox()
    mailbox.folder.set("INBOX")
    matches = list(mailbox.fetch(AND(uid=str(uid)), mark_seen=False, limit=1))
    if not matches:
        raise HTTPException(status_code=404, detail=f"message {uid} not found")
    msg = matches[0]

    attachments = [
        {"filename": a.filename, "content_type": a.content_type, "size": a.size}
        for a in (msg.attachments or [])
    ]

    return {
        "uid": int(msg.uid) if msg.uid else uid,
        "from": msg.from_ or "",
        "to": list(msg.to or ()),
        "cc": list(msg.cc or ()),
        "subject": msg.subject or "(no subject)",
        "date": _iso(msg.date),
        "body": _best_body(msg),
        "read": MailMessageFlags.SEEN in (msg.flags or []),
        "attachments": attachments,
    }


class MarkReadRequest(BaseModel):
    uid: int
    read: bool = True


@router.post("/mark-read")
async def mark_read(req: MarkReadRequest):
    mailbox = _get_mailbox()
    mailbox.folder.set("INBOX")
    mailbox.flag([str(req.uid)], [MailMessageFlags.SEEN], req.read)
    return {"ok": True, "uid": req.uid, "read": req.read}


class UidRequest(BaseModel):
    uid: int


@router.post("/archive")
async def archive(req: UidRequest):
    mailbox = _get_mailbox()
    mailbox.folder.set("INBOX")
    mailbox.move([str(req.uid)], ARCHIVE_FOLDER)
    return {"ok": True, "uid": req.uid}


@router.post("/trash")
async def trash(req: UidRequest):
    mailbox = _get_mailbox()
    mailbox.folder.set("INBOX")
    mailbox.move([str(req.uid)], TRASH_FOLDER)
    return {"ok": True, "uid": req.uid}


class SendRequest(BaseModel):
    to: str
    subject: str
    body: str


@router.post("/send")
async def send(req: SendRequest):
    addr, pw = _creds()
    msg = EmailMessage()
    msg["From"] = addr
    msg["To"] = req.to
    msg["Subject"] = req.subject
    msg.set_content(req.body)

    try:
        with smtplib.SMTP(SMTP_HOST, SMTP_PORT, timeout=20) as s:
            s.starttls()
            s.login(addr, pw)
            s.send_message(msg)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"SMTP send failed: {e}")

    return {"ok": True}
