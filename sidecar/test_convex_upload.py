"""
Test: Upload a downloaded .schem file to Convex storage.

Flow:
1. Get an upload URL from Convex (via generateUploadUrl mutation)
2. Upload the file bytes to that URL
3. Store the schematic metadata in the schematics table
4. Verify by querying it back
"""

import asyncio
import os
from pathlib import Path

import httpx

CONVEX_URL = "https://sincere-bandicoot-65.convex.cloud"
SCHEMATIC_FILE = Path(__file__).parent / "schema_cache" / "27283.schem"


async def test_upload():
    if not SCHEMATIC_FILE.exists():
        print(f"ERROR: {SCHEMATIC_FILE} not found. Run test_download.py first.")
        return

    file_bytes = SCHEMATIC_FILE.read_bytes()
    print(f"File: {SCHEMATIC_FILE.name}")
    print(f"Size: {len(file_bytes)} bytes ({len(file_bytes)/1024:.1f} KB)")
    print()

    async with httpx.AsyncClient() as http:
        # Step 1: Get upload URL from Convex
        print("=== Step 1: Get upload URL ===")
        resp = await http.post(
            f"{CONVEX_URL}/api/mutation",
            json={
                "path": "schematics:generateUploadUrl",
                "args": {},
            },
        )
        resp.raise_for_status()
        result = resp.json()
        upload_url = result["value"]
        print(f"Upload URL: {upload_url[:80]}...")

        # Step 2: Upload the file
        print()
        print("=== Step 2: Upload file ===")
        resp = await http.post(
            upload_url,
            content=file_bytes,
            headers={"Content-Type": "application/octet-stream"},
        )
        resp.raise_for_status()
        upload_result = resp.json()
        storage_id = upload_result["storageId"]
        print(f"Storage ID: {storage_id}")

        # Step 3: Store schematic metadata
        print()
        print("=== Step 3: Store metadata ===")
        resp = await http.post(
            f"{CONVEX_URL}/api/mutation",
            json={
                "path": "schematics:storeSchematic",
                "args": {
                    "name": "Test Schematic 27283",
                    "fileName": "27283.schem",
                    "fileId": storage_id,
                    "fileSize": len(file_bytes),
                    "category": "unknown",
                    "tags": ["test", "downloaded"],
                    "sourceUrl": "https://www.minecraft-schematics.com/schematic/27283/",
                },
            },
        )
        resp.raise_for_status()
        doc_id = resp.json()["value"]
        print(f"Document ID: {doc_id}")

        # Step 4: Verify by querying
        print()
        print("=== Step 4: Verify ===")
        resp = await http.post(
            f"{CONVEX_URL}/api/query",
            json={
                "path": "schematics:getSchematicFile",
                "args": {"id": doc_id},
            },
        )
        resp.raise_for_status()
        schematic = resp.json()["value"]
        print(f"Name: {schematic['name']}")
        print(f"File: {schematic['fileName']}")
        print(f"Size: {schematic['fileSize']} bytes")
        print(f"Storage URL: {schematic.get('fileUrl', 'N/A')}")
        print(f"Category: {schematic.get('category', 'N/A')}")
        print(f"Tags: {schematic.get('tags', [])}")

        # Step 5: Verify we can download from Convex
        if schematic.get("fileUrl"):
            print()
            print("=== Step 5: Download from Convex ===")
            resp = await http.get(schematic["fileUrl"])
            resp.raise_for_status()
            downloaded = resp.content
            print(f"Downloaded: {len(downloaded)} bytes")
            print(f"Matches original: {downloaded == file_bytes}")
            print()
            print("=== SUCCESS: Full pipeline verified ===")
            print("Browser Use -> Download -> Convex Upload -> Convex Query -> Convex Download")
        else:
            print("WARNING: No file URL returned")


if __name__ == "__main__":
    asyncio.run(test_upload())
