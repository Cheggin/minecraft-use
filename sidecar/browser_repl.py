"""
Browser Use REPL — interactive prompt for running Browser Use Cloud API tasks.

Downloads schematic files and stores them in Convex.

Usage:
    python browser_repl.py

Commands:
    get-schematics <query>   Search and download schematics, store in Convex
    quit                     Exit the REPL
    <any other text>         Run as a Browser Use Cloud API task
"""

import asyncio
import json
import os
import sys
from pathlib import Path

from dotenv import load_dotenv

load_dotenv(Path(__file__).parent.parent / ".env")

import httpx
from browser_use_sdk import AsyncBrowserUse

SENTINEL = "DONE"
CACHE_DIR = Path(__file__).parent / "schema_cache"
PROFILE_ID = "6ff9cec3-a922-4ba8-bc33-4dd4d2b384e6"
CONVEX_URL = "https://sincere-bandicoot-65.convex.cloud"
POLL_INTERVAL = 3


def _get_client() -> AsyncBrowserUse:
    api_key = os.environ.get("BROWSER_USE_API_KEY")
    if not api_key:
        raise RuntimeError("BROWSER_USE_API_KEY not set in environment")
    return AsyncBrowserUse(api_key=api_key)


async def run_browser_task(task: str, start_url: str = None) -> dict:
    """Run a task via Browser Use Cloud API. Returns dict with output, live_url, task_id."""
    client = _get_client()

    # Create session with profile for persistent cookies
    session = await client.sessions.create_session(profile_id=PROFILE_ID)
    live_url = session.live_url or f"https://cloud.browser-use.com/session/{session.id}"

    print(f"TASK_STARTED:{live_url}")
    sys.stdout.flush()

    # Create task in that session
    create_kwargs = {
        "task": task,
        "session_id": session.id,
    }
    if start_url:
        create_kwargs["start_url"] = start_url

    task_response = await client.tasks.create_task(**create_kwargs)

    task_id = task_response.id
    sys.stdout.flush()

    while True:
        status = await client.tasks.get_task_status(task_id)
        print(f"STATUS:{status.status}", flush=True)
        if status.status in ("finished", "stopped"):
            break
        await asyncio.sleep(POLL_INTERVAL)

    result = await client.tasks.get_task(task_id)
    print(f"RESULT:output={result.output}, files={len(result.output_files or [])}", flush=True)

    return {
        "task_id": task_id,
        "live_url": live_url,
        "output": result.output or "(no output)",
        "output_files": result.output_files or [],
        "result": result,
        "client": client,
    }


async def upload_to_convex(file_bytes: bytes, file_name: str, metadata: dict) -> str:
    """Upload a schematic file to Convex storage and store metadata. Returns doc ID."""
    async with httpx.AsyncClient() as http:
        # Get upload URL
        resp = await http.post(
            f"{CONVEX_URL}/api/mutation",
            json={"path": "schematics:generateUploadUrl", "args": {}},
        )
        resp.raise_for_status()
        upload_url = resp.json()["value"]

        # Upload file
        resp = await http.post(
            upload_url,
            content=file_bytes,
            headers={"Content-Type": "application/octet-stream"},
        )
        resp.raise_for_status()
        storage_id = resp.json()["storageId"]

        # Store metadata
        resp = await http.post(
            f"{CONVEX_URL}/api/mutation",
            json={
                "path": "schematics:storeSchematic",
                "args": {
                    "name": metadata.get("name", file_name),
                    "fileName": file_name,
                    "fileId": storage_id,
                    "fileSize": len(file_bytes),
                    "category": metadata.get("category", "unknown"),
                    "tags": metadata.get("tags", []),
                    "sourceUrl": metadata.get("source_url", ""),
                },
            },
        )
        resp.raise_for_status()
        return resp.json()["value"]


async def run_get_schematics(query: str) -> str:
    """Search for schematics, download them, and store in Convex."""
    task = f"""Go to https://www.minecraft-schematics.com/search/?q={query}

Search for Minecraft schematics matching: {query}

For the top result:
1. Click into the schematic page
2. Find and click the download button
3. Download the .schem or .schematic file
4. Tell me the name, author, and category of the schematic

If there's a wait timer or CAPTCHA, handle it."""

    search_url = f"https://www.minecraft-schematics.com/search/?q={query}"
    result = await run_browser_task(task, start_url=search_url)
    output_lines = [result["output"]]

    # Check for downloaded files and upload to Convex
    if result["output_files"]:
        client = result.get("client") or _get_client()
        for f in result["output_files"]:
            try:
                file_id = f.id
                # Try to get filename from the file object, or derive from query
                raw_name = f.name if hasattr(f, "name") and f.name else None
                if raw_name:
                    file_name = raw_name
                else:
                    # Use query as name, preserve .schematic if that's what was downloaded
                    safe_query = query.replace(" ", "_").lower()
                    file_name = f"{safe_query}.schematic"

                # Get download URL from Browser Use
                presigned = await client.files.get_task_output_file_presigned_url(
                    result["task_id"], file_id
                )
                download_url = (
                    presigned.download_url
                    if hasattr(presigned, "download_url")
                    else str(presigned)
                )

                # Download the file
                print(f"DOWNLOADING:{download_url[:80]}...", flush=True)
                async with httpx.AsyncClient() as http:
                    resp = await http.get(download_url)
                    file_bytes = resp.content

                print(f"DOWNLOADED:{len(file_bytes)} bytes as {file_name}", flush=True)

                # Save locally
                CACHE_DIR.mkdir(exist_ok=True)
                local_path = CACHE_DIR / file_name
                local_path.write_bytes(file_bytes)

                # Upload to Convex
                print(f"UPLOADING_TO_CONVEX:{file_name}", flush=True)
                doc_id = await upload_to_convex(
                    file_bytes,
                    file_name,
                    {
                        "name": query,
                        "category": "downloaded",
                        "tags": [query],
                        "source_url": f"https://www.minecraft-schematics.com/search/?q={query}",
                    },
                )

                output_lines.append(
                    f"Downloaded: {file_name} ({len(file_bytes)} bytes)"
                )
                output_lines.append(f"Saved to Convex: {doc_id}")
                output_lines.append(f"Cached locally: {local_path}")
            except Exception as exc:
                output_lines.append(f"File download error: {exc}")
    else:
        output_lines.append("No files were downloaded by the browser agent.")

    return "\n".join(output_lines)


async def handle_command(line: str) -> tuple[str, bool]:
    """Process one REPL input line. Returns (output, should_quit)."""
    stripped = line.strip()

    if stripped == "quit":
        return ("Goodbye.", True)

    if stripped == "get-schematics" or stripped.startswith("get-schematics "):
        query = stripped[len("get-schematics"):].strip()
        if not query:
            return ("Usage: get-schematics <query>", False)
        try:
            result = await run_get_schematics(query)
        except Exception as exc:
            result = f"Error: {exc}"
        return (result, False)

    if not stripped:
        return ("", False)

    # Generic Browser Use task
    try:
        result = await run_browser_task(stripped)
        return (result["output"], False)
    except Exception as exc:
        return (f"Error: {exc}", False)


async def repl() -> None:
    """Run the interactive REPL loop."""
    CACHE_DIR.mkdir(exist_ok=True)

    while True:
        try:
            sys.stdout.write(">>> ")
            sys.stdout.flush()
            line = sys.stdin.readline()
        except (EOFError, KeyboardInterrupt):
            print("\nGoodbye.")
            print(SENTINEL)
            break

        if not line:
            print(SENTINEL)
            break

        output, should_quit = await handle_command(line)
        if output:
            print(output)
        print(SENTINEL)
        sys.stdout.flush()

        if should_quit:
            break


if __name__ == "__main__":
    asyncio.run(repl())
