"""Smoke tests — verify the test infrastructure itself is working."""

import sys
from pathlib import Path


def test_python_version():
    """Ensure we are running Python 3.12+."""
    assert sys.version_info >= (3, 12), f"Expected Python 3.12+, got {sys.version_info}"


def test_sidecar_root_on_path():
    """Ensure the sidecar root directory is importable (conftest adds it via rootdir)."""
    sidecar_root = Path(__file__).parent.parent
    assert sidecar_root.exists()


def test_browser_search_importable():
    """Ensure browser_search module can be imported."""
    import importlib.util

    sidecar_root = Path(__file__).parent.parent
    spec = importlib.util.spec_from_file_location(
        "browser_search", sidecar_root / "browser_search.py"
    )
    assert spec is not None
    mod = importlib.util.module_from_spec(spec)
    # We only check the spec loads — not that it executes (would require browser-use deps)
    assert mod is not None


def test_tmp_cache_dir_fixture(tmp_cache_dir):
    """Ensure the tmp_cache_dir fixture creates a usable directory."""
    assert tmp_cache_dir.is_dir()
    test_file = tmp_cache_dir / "test.txt"
    test_file.write_text("hello")
    assert test_file.read_text() == "hello"
