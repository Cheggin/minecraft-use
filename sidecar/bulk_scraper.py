"""
Bulk scraper for minecraft-schematics.com.

Downloads schematic metadata, .schem files, and thumbnails into schema_cache/.
Supports resuming: skips schematics whose {id}.json already exists.
"""

import json
import random
import time
from pathlib import Path

from browser_use import Agent
from langchain_anthropic import ChatAnthropic

BASE_URL = "https://www.minecraft-schematics.com"
CATEGORY_PATHS = [
    "/category/buildings/",
    "/category/houses/",
    "/category/castles/",
    "/category/farms/",
    "/category/redstone/",
    "/category/ships/",
    "/category/statues/",
    "/category/temples/",
    "/category/towers/",
    "/category/other/",
]
RATE_LIMIT_MIN = 2.0
RATE_LIMIT_MAX = 5.0
LLM_MODEL = "claude-sonnet-4-20250514"
LLM_TIMEOUT = 120


def _make_llm() -> ChatAnthropic:
    return ChatAnthropic(model_name=LLM_MODEL, timeout=LLM_TIMEOUT, stop=None)


def _rate_limit() -> None:
    """Sleep for a random delay between requests."""
    delay = random.uniform(RATE_LIMIT_MIN, RATE_LIMIT_MAX)
    time.sleep(delay)


class BulkScraper:
    def __init__(self, cache_dir: Path):
        self.cache_dir = cache_dir
        self.cache_dir.mkdir(exist_ok=True)

    def _is_cached(self, schematic_id: str) -> bool:
        """Return True if {id}.json already exists (resume support)."""
        return (self.cache_dir / f"{schematic_id}.json").exists()

    def _save_metadata(self, schematic_id: str, metadata: dict) -> None:
        """Persist metadata JSON for a schematic."""
        dest = self.cache_dir / f"{schematic_id}.json"
        dest.write_text(json.dumps(metadata, indent=2, ensure_ascii=False))

    async def crawl_category(self, category_url: str) -> list[str]:
        """
        Paginate through a category page and collect individual schematic page URLs.

        Returns a list of absolute URLs.
        """
        agent = Agent(
            task=f"""Go to {category_url}

Collect all schematic page links on this page and every subsequent pagination page.
For each schematic listed, extract the URL to its detail page.

Return a JSON array of URLs (strings), e.g.:
["https://www.minecraft-schematics.com/schematic/12345/", ...]

Only include URLs that point to individual schematic detail pages.
Paginate through all available pages until there are no more pages.""",
            llm=_make_llm(),
        )
        result = await agent.run()
        return self._parse_url_list(str(result))

    async def scrape_schematic(self, url: str) -> dict:
        """
        Scrape metadata from a schematic detail page.

        Downloads the .schem file and thumbnail into cache_dir.
        Returns the metadata dict (also saved as {id}.json).
        """
        agent = Agent(
            task=f"""Go to {url}

Extract the following information from this Minecraft schematic page:
- id: the numeric ID from the URL (e.g. 12345 from /schematic/12345/)
- name: the schematic title
- author: the uploader username
- description: the description text
- category: the category/tags listed
- download_url: the direct download link for the .schem or .schematic file
- thumbnail_url: the URL of the preview image
- rating: rating score if shown
- downloads: download count if shown

Then:
1. Download the .schem/.schematic file and save it
2. Download the thumbnail image and save it

Return the extracted metadata as a JSON object.""",
            llm=_make_llm(),
        )
        result = await agent.run()
        metadata = self._parse_metadata(str(result), url)

        schematic_id = metadata.get("id", "")
        if schematic_id:
            self._save_metadata(str(schematic_id), metadata)

        return metadata

    async def crawl_all(self) -> list[dict]:
        """
        Iterate all categories, crawl each for schematic URLs, then scrape each.

        Skips schematics already present in cache_dir ({id}.json exists).
        Returns list of all scraped metadata dicts.
        """
        all_results = []

        for category_path in CATEGORY_PATHS:
            category_url = BASE_URL + category_path
            try:
                urls = await self.crawl_category(category_url)
            except Exception as exc:
                print(f"Error crawling {category_url}: {exc}")
                continue

            _rate_limit()

            for schematic_url in urls:
                schematic_id = self._extract_id_from_url(schematic_url)
                if schematic_id and self._is_cached(schematic_id):
                    continue

                try:
                    metadata = await self.scrape_schematic(schematic_url)
                    all_results.append(metadata)
                except Exception as exc:
                    print(f"Error scraping {schematic_url}: {exc}")

                _rate_limit()

        return all_results

    # ------------------------------------------------------------------
    # Private parsing helpers
    # ------------------------------------------------------------------

    def _extract_id_from_url(self, url: str) -> str:
        """Extract numeric ID from a schematic URL like /schematic/12345/."""
        parts = [p for p in url.rstrip("/").split("/") if p]
        for part in reversed(parts):
            if part.isdigit():
                return part
        return ""

    def _parse_url_list(self, agent_output: str) -> list[str]:
        """Parse a JSON array of URLs from agent output."""
        import re

        json_match = re.search(r"\[.*?\]", agent_output, re.DOTALL)
        if json_match:
            try:
                candidates = json.loads(json_match.group())
                return [u for u in candidates if isinstance(u, str) and u.startswith("http")]
            except json.JSONDecodeError:
                pass
        # Fallback: extract URLs line by line
        urls = re.findall(r'https?://[^\s"\']+', agent_output)
        return urls

    def _parse_metadata(self, agent_output: str, source_url: str) -> dict:
        """Parse metadata JSON from agent output."""
        import re

        json_match = re.search(r"\{.*\}", agent_output, re.DOTALL)
        if json_match:
            try:
                return json.loads(json_match.group())
            except json.JSONDecodeError:
                pass
        # Fallback: return minimal metadata with source URL
        schematic_id = self._extract_id_from_url(source_url)
        return {"id": schematic_id, "url": source_url, "raw": agent_output}
