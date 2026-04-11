"""
Browser Use integration for searching and downloading Minecraft schematics.

Uses Browser Use to navigate schematic hosting sites, search for builds,
and download .schem files.
"""

import asyncio
import os
from pathlib import Path
from typing import Optional

from browser_use import Agent
from langchain_anthropic import ChatAnthropic


SCHEMATIC_SITES = [
    {
        "name": "Planet Minecraft",
        "search_url": "https://www.planetminecraft.com/projects/?keywords={query}&share_type=schematic",
        "type": "projects",
    },
    {
        "name": "Minecraft Schematics",
        "search_url": "https://www.minecraft-schematics.com/search/?q={query}",
        "type": "schematics",
    },
]


class SchematicSearcher:
    def __init__(self, cache_dir: Path):
        self.cache_dir = cache_dir
        self.llm = ChatAnthropic(
            model_name="claude-sonnet-4-20250514",
            timeout=60,
            stop=None,
        )

    async def search(self, query: str) -> list[dict]:
        """Search for schematics across multiple sites. Returns metadata, does not download."""
        agent = Agent(
            task=f"""Search for Minecraft schematics matching: "{query}"

Go to Planet Minecraft: {SCHEMATIC_SITES[0]['search_url'].format(query=query)}

Find the top 5 results that have schematic downloads available.
For each result, extract:
- name: the build name
- url: the page URL
- author: who made it
- description: short description
- has_schematic: whether it has a .schem or .schematic download

Return the results as a JSON array.""",
            llm=self.llm,
        )

        result = await agent.run()
        # Parse the agent's response into structured data
        return self._parse_search_results(result)

    async def search_and_download(self, query: str) -> dict:
        """Search for a schematic and download the best match."""
        agent = Agent(
            task=f"""Find and download a Minecraft schematic matching: "{query}"

1. Go to Planet Minecraft: {SCHEMATIC_SITES[0]['search_url'].format(query=query)}
2. Find the best matching result that has a schematic download
3. Click into the project page
4. Find and click the schematic download button/link
5. Download the .schem or .schematic file

If Planet Minecraft doesn't have results, try:
{SCHEMATIC_SITES[1]['search_url'].format(query=query)}

Tell me the filename you downloaded and which site it came from.""",
            llm=self.llm,
        )

        result = await agent.run()

        # Move downloaded file to cache directory
        downloaded = self._find_latest_download()
        if downloaded:
            dest = self.cache_dir / downloaded.name
            downloaded.rename(dest)
            return {
                "status": "success",
                "filename": downloaded.name,
                "path": str(dest),
                "source": "Planet Minecraft",
            }

        return {
            "status": "error",
            "message": "Could not download schematic. Browser Use may need manual intervention.",
        }

    def _find_latest_download(self) -> Optional[Path]:
        """Find the most recently downloaded schematic file."""
        downloads_dir = Path.home() / "Downloads"
        schematic_extensions = {".schem", ".schematic", ".litematic", ".nbt"}

        candidates = [
            f for f in downloads_dir.iterdir()
            if f.suffix in schematic_extensions
        ]

        if not candidates:
            return None

        return max(candidates, key=lambda f: f.stat().st_mtime)

    def _parse_search_results(self, agent_result) -> list[dict]:
        """Parse Browser Use agent output into structured search results."""
        # The agent returns its final message as text — parse it
        # This is a best-effort extraction; Browser Use output is semi-structured
        final_text = str(agent_result)

        # Try to extract JSON if the agent returned it
        import json
        import re

        json_match = re.search(r'\[.*\]', final_text, re.DOTALL)
        if json_match:
            try:
                return json.loads(json_match.group())
            except json.JSONDecodeError:
                pass

        # Fallback: return raw text as a single result
        return [{"name": "Search results", "raw": final_text}]
