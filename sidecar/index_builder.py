"""
Index builder — scans schema_cache/*.json and aggregates into catalog_index.json.

Normalizes categories and tags for consistent search/filtering.
"""

import json
import re
from pathlib import Path

CATALOG_INDEX_FILENAME = "catalog_index.json"

# Canonical category name mapping (lowercase input -> canonical name)
CATEGORY_ALIASES: dict[str, str] = {
    "building": "buildings",
    "buildings": "buildings",
    "house": "houses",
    "houses": "houses",
    "castle": "castles",
    "castles": "castles",
    "farm": "farms",
    "farms": "farms",
    "redstone": "redstone",
    "ship": "ships",
    "ships": "ships",
    "statue": "statues",
    "statues": "statues",
    "temple": "temples",
    "temples": "temples",
    "tower": "towers",
    "towers": "towers",
    "other": "other",
}


def normalize_category(raw: str) -> str:
    """Map a raw category string to a canonical category name."""
    key = raw.strip().lower()
    return CATEGORY_ALIASES.get(key, key)


def normalize_tags(raw_tags: list[str] | str | None) -> list[str]:
    """Normalize a tag list: lowercase, strip whitespace, deduplicate."""
    if raw_tags is None:
        return []
    if isinstance(raw_tags, str):
        # Split comma-separated or space-separated tags
        parts = re.split(r"[,\s]+", raw_tags)
    else:
        parts = list(raw_tags)
    seen: set[str] = set()
    result = []
    for tag in parts:
        cleaned = tag.strip().lower()
        if cleaned and cleaned not in seen:
            seen.add(cleaned)
            result.append(cleaned)
    return result


def build_index(cache_dir: Path) -> dict:
    """
    Scan cache_dir for *.json files (excluding catalog_index.json itself).

    Returns the catalog index dict and writes it to cache_dir/catalog_index.json.
    """
    entries = []
    seen_ids: set[str] = set()

    json_files = sorted(cache_dir.glob("*.json"))
    for json_file in json_files:
        if json_file.name == CATALOG_INDEX_FILENAME:
            continue
        try:
            data = json.loads(json_file.read_text(encoding="utf-8"))
        except (json.JSONDecodeError, OSError):
            continue

        schematic_id = str(data.get("id", json_file.stem))
        if schematic_id in seen_ids:
            continue
        seen_ids.add(schematic_id)

        raw_category = data.get("category", "other")
        if isinstance(raw_category, list):
            category = normalize_category(raw_category[0]) if raw_category else "other"
        else:
            category = normalize_category(str(raw_category))

        entry = {
            "id": schematic_id,
            "name": data.get("name", ""),
            "author": data.get("author", ""),
            "description": data.get("description", ""),
            "category": category,
            "tags": normalize_tags(data.get("tags")),
            "url": data.get("url", ""),
            "thumbnail_url": data.get("thumbnail_url", ""),
            "rating": data.get("rating"),
            "downloads": data.get("downloads"),
        }
        entries.append(entry)

    catalog = {
        "version": 1,
        "count": len(entries),
        "entries": entries,
    }

    output_path = cache_dir / CATALOG_INDEX_FILENAME
    output_path.write_text(json.dumps(catalog, indent=2, ensure_ascii=False))

    return catalog


if __name__ == "__main__":
    import sys

    cache_path = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).parent / "schema_cache"
    result = build_index(cache_path)
    print(f"Built index with {result['count']} entries -> {cache_path / CATALOG_INDEX_FILENAME}")
