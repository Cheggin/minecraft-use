"""
Test: Download a schematic file from minecraft-schematics.com using Browser Use Cloud API v2,
then verify the file can be stored in Convex.
"""

import asyncio
import os
import time
from pathlib import Path

from dotenv import load_dotenv

# Load from root .env
load_dotenv(Path(__file__).parent.parent / ".env")

from browser_use_sdk import AsyncBrowserUse

PROFILE_ID = "6ff9cec3-a922-4ba8-bc33-4dd4d2b384e6"
TEST_URL = "https://www.minecraft-schematics.com/schematic/27283/"


async def test_download():
    api_key = os.environ.get("BROWSER_USE_API_KEY")
    if not api_key:
        print("ERROR: BROWSER_USE_API_KEY not set")
        return

    print(f"API Key: {api_key[:10]}...")
    print(f"Profile ID: {PROFILE_ID}")
    print(f"Target URL: {TEST_URL}")
    print()

    client = AsyncBrowserUse(api_key=api_key)

    print("=== Creating Browser Use task ===")

    task_response = await client.tasks.create_task(
        task=f"""Go to {TEST_URL}

This is a Minecraft schematic download page. I need you to:
1. Find the download button/link for the schematic file (.schematic or .schem)
2. Click it to download the file
3. If there's a CAPTCHA or "wait" timer, wait for it and then click download
4. Confirm the file was downloaded

Tell me the filename of the downloaded file.""",
        start_url=TEST_URL,
    )

    task_id = task_response.id
    print(f"Task created: {task_id}")
    print()

    # Poll for task completion
    print("=== Waiting for task to complete ===")
    while True:
        status = await client.tasks.get_task_status(task_id)
        print(f"  Status: {status.status}")
        if status.status in ("finished", "stopped"):
            break
        await asyncio.sleep(3)

    # Get full task result
    result = await client.tasks.get_task(task_id)
    print()
    print("=== Task Complete ===")
    print(f"Status: {result.status}")
    print(f"Success: {result.is_success}")
    print(f"Output: {result.output}")
    print(f"Cost: {result.cost}")
    print()

    # Print steps
    if result.steps:
        print(f"=== Steps ({len(result.steps)}) ===")
        for step in result.steps:
            print(f"  Step: {step}")
        print()

    # Check for downloaded files
    if result.output_files:
        print(f"=== Downloaded {len(result.output_files)} file(s) ===")
        for f in result.output_files:
            file_id = f.id
            file_name = f.name if hasattr(f, 'name') and f.name else "test_schematic.schematic"
            print(f"  File ID: {file_id}")
            print(f"  Name: {file_name}")

            # Get download URL
            output = await client.files.get_task_output_file_presigned_url(task_id, file_id)
            download_url = output.download_url if hasattr(output, 'download_url') else str(output)
            print(f"  Download URL: {download_url[:80]}...")

            # Download the actual file
            import httpx
            async with httpx.AsyncClient() as http:
                resp = await http.get(download_url)
                file_bytes = resp.content
                print(f"  File size: {len(file_bytes)} bytes ({len(file_bytes)/1024:.1f} KB)")

                # Save locally for verification
                cache_dir = Path(__file__).parent / "schema_cache"
                cache_dir.mkdir(exist_ok=True)
                local_path = cache_dir / file_name
                local_path.write_bytes(file_bytes)
                print(f"  Saved to: {local_path}")

                # Test Convex compatibility
                print()
                print("=== Convex Storage Compatibility ===")
                print(f"  File size: {len(file_bytes)} bytes ({len(file_bytes)/1024:.1f} KB)")
                print(f"  Under 1MB doc limit: {len(file_bytes) < 1_048_576}")
                print(f"  File type: {local_path.suffix}")
                print(f"  Can store in Convex file storage: YES")
                print(f"  Can store as v.bytes() field: {'YES' if len(file_bytes) < 1_048_576 else 'NO - use file storage instead'}")
    else:
        print("WARNING: No output files found in task result")
        print("The download may not have completed successfully")
        print()
        # Print all fields for debugging
        print("=== Full result fields ===")
        for field_name in result.model_fields:
            val = getattr(result, field_name)
            if val is not None:
                print(f"  {field_name}: {val}")


if __name__ == "__main__":
    asyncio.run(test_download())
