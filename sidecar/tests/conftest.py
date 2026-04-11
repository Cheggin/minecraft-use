"""Shared pytest fixtures for sidecar tests."""

from pathlib import Path

import pytest


@pytest.fixture
def tmp_cache_dir(tmp_path: Path) -> Path:
    """Provide a temporary directory for schema_cache during tests."""
    cache = tmp_path / "schema_cache"
    cache.mkdir()
    return cache
