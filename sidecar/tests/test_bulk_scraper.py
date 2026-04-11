"""Tests for bulk_scraper.py — mocks Browser Use agent."""

import json
import sys
from pathlib import Path
from unittest.mock import AsyncMock, patch

import pytest

sys.path.insert(0, str(Path(__file__).parent.parent))

from bulk_scraper import BulkScraper  # noqa: E402

# ---------------------------------------------------------------------------
# _extract_id_from_url
# ---------------------------------------------------------------------------


def test_extract_id_standard_url(tmp_cache_dir):
    scraper = BulkScraper(tmp_cache_dir)
    url = "https://www.minecraft-schematics.com/schematic/12345/"
    assert scraper._extract_id_from_url(url) == "12345"


def test_extract_id_no_trailing_slash(tmp_cache_dir):
    scraper = BulkScraper(tmp_cache_dir)
    assert scraper._extract_id_from_url("https://www.minecraft-schematics.com/schematic/99") == "99"


def test_extract_id_no_id(tmp_cache_dir):
    scraper = BulkScraper(tmp_cache_dir)
    url = "https://www.minecraft-schematics.com/category/buildings/"
    assert scraper._extract_id_from_url(url) == ""


# ---------------------------------------------------------------------------
# _is_cached / _save_metadata
# ---------------------------------------------------------------------------


def test_is_cached_false_when_missing(tmp_cache_dir):
    scraper = BulkScraper(tmp_cache_dir)
    assert scraper._is_cached("99999") is False


def test_is_cached_true_after_save(tmp_cache_dir):
    scraper = BulkScraper(tmp_cache_dir)
    scraper._save_metadata("42", {"id": "42", "name": "Test"})
    assert scraper._is_cached("42") is True


def test_save_metadata_writes_json(tmp_cache_dir):
    scraper = BulkScraper(tmp_cache_dir)
    metadata = {"id": "7", "name": "My Build", "author": "Alice"}
    scraper._save_metadata("7", metadata)
    written = json.loads((tmp_cache_dir / "7.json").read_text())
    assert written["name"] == "My Build"
    assert written["author"] == "Alice"


# ---------------------------------------------------------------------------
# _parse_url_list
# ---------------------------------------------------------------------------


def test_parse_url_list_from_json(tmp_cache_dir):
    scraper = BulkScraper(tmp_cache_dir)
    output = 'Here are the results: ["https://example.com/schematic/1/", "https://example.com/schematic/2/"]'
    urls = scraper._parse_url_list(output)
    assert "https://example.com/schematic/1/" in urls
    assert "https://example.com/schematic/2/" in urls


def test_parse_url_list_fallback_regex(tmp_cache_dir):
    scraper = BulkScraper(tmp_cache_dir)
    output = "Found: https://example.com/a and https://example.com/b"
    urls = scraper._parse_url_list(output)
    assert len(urls) == 2


def test_parse_url_list_empty(tmp_cache_dir):
    scraper = BulkScraper(tmp_cache_dir)
    urls = scraper._parse_url_list("No URLs here at all")
    assert urls == []


# ---------------------------------------------------------------------------
# _parse_metadata
# ---------------------------------------------------------------------------


def test_parse_metadata_from_json(tmp_cache_dir):
    scraper = BulkScraper(tmp_cache_dir)
    output = '{"id": "5", "name": "Castle", "author": "Bob"}'
    meta = scraper._parse_metadata(output, "https://example.com/schematic/5/")
    assert meta["id"] == "5"
    assert meta["name"] == "Castle"


def test_parse_metadata_fallback(tmp_cache_dir):
    scraper = BulkScraper(tmp_cache_dir)
    meta = scraper._parse_metadata("no json here", "https://example.com/schematic/99/")
    assert meta["id"] == "99"
    assert meta["url"] == "https://example.com/schematic/99/"


# ---------------------------------------------------------------------------
# crawl_category (mocked agent)
# ---------------------------------------------------------------------------


@pytest.mark.asyncio
async def test_crawl_category_returns_urls(tmp_cache_dir):
    scraper = BulkScraper(tmp_cache_dir)
    mock_result = '["https://www.minecraft-schematics.com/schematic/1/", "https://www.minecraft-schematics.com/schematic/2/"]'

    with patch("bulk_scraper.Agent") as MockAgent:
        instance = MockAgent.return_value
        instance.run = AsyncMock(return_value=mock_result)
        urls = await scraper.crawl_category("https://www.minecraft-schematics.com/category/buildings/")

    assert len(urls) == 2
    assert "https://www.minecraft-schematics.com/schematic/1/" in urls


# ---------------------------------------------------------------------------
# scrape_schematic (mocked agent)
# ---------------------------------------------------------------------------


@pytest.mark.asyncio
async def test_scrape_schematic_saves_metadata(tmp_cache_dir):
    scraper = BulkScraper(tmp_cache_dir)
    mock_result = '{"id": "123", "name": "Big Castle", "author": "Dave", "category": "castles"}'

    with patch("bulk_scraper.Agent") as MockAgent:
        instance = MockAgent.return_value
        instance.run = AsyncMock(return_value=mock_result)
        metadata = await scraper.scrape_schematic("https://www.minecraft-schematics.com/schematic/123/")

    assert metadata["id"] == "123"
    assert metadata["name"] == "Big Castle"
    assert scraper._is_cached("123")


@pytest.mark.asyncio
async def test_scrape_schematic_handles_error(tmp_cache_dir):
    scraper = BulkScraper(tmp_cache_dir)

    with patch("bulk_scraper.Agent") as MockAgent:
        instance = MockAgent.return_value
        instance.run = AsyncMock(side_effect=RuntimeError("browser failed"))
        with pytest.raises(RuntimeError, match="browser failed"):
            await scraper.scrape_schematic("https://www.minecraft-schematics.com/schematic/999/")


# ---------------------------------------------------------------------------
# crawl_all — resume logic
# ---------------------------------------------------------------------------


@pytest.mark.asyncio
async def test_crawl_all_skips_cached(tmp_cache_dir):
    scraper = BulkScraper(tmp_cache_dir)
    # Pre-cache schematic 1
    scraper._save_metadata("1", {"id": "1", "name": "Cached"})

    category_result = '["https://www.minecraft-schematics.com/schematic/1/"]'

    with (
        patch("bulk_scraper.Agent") as MockAgent,
        patch("bulk_scraper._rate_limit"),
        patch("bulk_scraper.CATEGORY_PATHS", ["/category/buildings/"]),
    ):
        instance = MockAgent.return_value
        instance.run = AsyncMock(return_value=category_result)
        results = await scraper.crawl_all()

    # scrape_schematic should NOT have been called since it was cached
    # crawl_category was called once (for the category), scrape not called
    assert results == []
