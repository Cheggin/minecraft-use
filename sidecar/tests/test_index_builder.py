"""Tests for index_builder.py."""

import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))

from index_builder import (  # noqa: E402
    CATALOG_INDEX_FILENAME,
    build_index,
    normalize_category,
    normalize_tags,
)

# ---------------------------------------------------------------------------
# normalize_category
# ---------------------------------------------------------------------------


def test_normalize_category_singular():
    assert normalize_category("building") == "buildings"


def test_normalize_category_plural():
    assert normalize_category("buildings") == "buildings"


def test_normalize_category_case_insensitive():
    assert normalize_category("Castle") == "castles"


def test_normalize_category_unknown_passthrough():
    assert normalize_category("mushroom_village") == "mushroom_village"


def test_normalize_category_strips_whitespace():
    assert normalize_category("  farms  ") == "farms"


# ---------------------------------------------------------------------------
# normalize_tags
# ---------------------------------------------------------------------------


def test_normalize_tags_list():
    assert normalize_tags(["Medieval", "Stone", "Castle"]) == ["medieval", "stone", "castle"]


def test_normalize_tags_string_comma():
    assert normalize_tags("medieval, stone, castle") == ["medieval", "stone", "castle"]


def test_normalize_tags_string_space():
    assert normalize_tags("medieval stone castle") == ["medieval", "stone", "castle"]


def test_normalize_tags_deduplicates():
    assert normalize_tags(["stone", "Stone", "STONE"]) == ["stone"]


def test_normalize_tags_none():
    assert normalize_tags(None) == []


def test_normalize_tags_empty_list():
    assert normalize_tags([]) == []


# ---------------------------------------------------------------------------
# build_index
# ---------------------------------------------------------------------------


def _write_schematic(cache_dir: Path, schematic_id: str, data: dict) -> None:
    (cache_dir / f"{schematic_id}.json").write_text(json.dumps(data))


def test_build_index_empty_cache(tmp_cache_dir):
    catalog = build_index(tmp_cache_dir)
    assert catalog["version"] == 1
    assert catalog["count"] == 0
    assert catalog["entries"] == []
    assert (tmp_cache_dir / CATALOG_INDEX_FILENAME).exists()


def test_build_index_single_entry(tmp_cache_dir):
    _write_schematic(
        tmp_cache_dir,
        "42",
        {
            "id": "42",
            "name": "Big Castle",
            "author": "Alice",
            "category": "castle",
            "tags": ["medieval", "stone"],
        },
    )
    catalog = build_index(tmp_cache_dir)
    assert catalog["count"] == 1
    entry = catalog["entries"][0]
    assert entry["id"] == "42"
    assert entry["name"] == "Big Castle"
    assert entry["category"] == "castles"
    assert "medieval" in entry["tags"]


def test_build_index_multiple_entries(tmp_cache_dir):
    for i in range(3):
        _write_schematic(tmp_cache_dir, str(i), {"id": str(i), "name": f"Build {i}"})
    catalog = build_index(tmp_cache_dir)
    assert catalog["count"] == 3


def test_build_index_skips_catalog_itself(tmp_cache_dir):
    # Write catalog_index.json itself — should be excluded
    (tmp_cache_dir / CATALOG_INDEX_FILENAME).write_text('{"version":1,"count":0,"entries":[]}')
    _write_schematic(tmp_cache_dir, "1", {"id": "1", "name": "A"})
    catalog = build_index(tmp_cache_dir)
    assert catalog["count"] == 1


def test_build_index_skips_invalid_json(tmp_cache_dir):
    (tmp_cache_dir / "bad.json").write_text("not valid json {{{")
    _write_schematic(tmp_cache_dir, "1", {"id": "1", "name": "Good"})
    catalog = build_index(tmp_cache_dir)
    assert catalog["count"] == 1


def test_build_index_deduplicates_by_id(tmp_cache_dir):
    # Write same id in two files (shouldn't happen normally but test resilience)
    _write_schematic(tmp_cache_dir, "dup", {"id": "5", "name": "First"})
    _write_schematic(tmp_cache_dir, "5", {"id": "5", "name": "Second"})
    catalog = build_index(tmp_cache_dir)
    assert catalog["count"] == 1


def test_build_index_writes_output_file(tmp_cache_dir):
    _write_schematic(tmp_cache_dir, "7", {"id": "7", "name": "Tower"})
    build_index(tmp_cache_dir)
    written = json.loads((tmp_cache_dir / CATALOG_INDEX_FILENAME).read_text())
    assert written["count"] == 1
    assert written["entries"][0]["name"] == "Tower"


def test_build_index_normalizes_list_category(tmp_cache_dir):
    _write_schematic(
        tmp_cache_dir, "9", {"id": "9", "name": "Ship", "category": ["ship", "ocean"]}
    )
    catalog = build_index(tmp_cache_dir)
    assert catalog["entries"][0]["category"] == "ships"
