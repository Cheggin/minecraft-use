"""Tests for browser_repl.py — mocks Browser Use and SchematicSearcher."""

import sys
from pathlib import Path
from unittest.mock import AsyncMock, patch

import pytest

sys.path.insert(0, str(Path(__file__).parent.parent))

from browser_repl import SENTINEL, handle_command  # noqa: E402

# ---------------------------------------------------------------------------
# handle_command — quit
# ---------------------------------------------------------------------------


@pytest.mark.asyncio
async def test_quit_command():
    output, should_quit = await handle_command("quit")
    assert should_quit is True
    assert "Goodbye" in output


@pytest.mark.asyncio
async def test_quit_command_with_whitespace():
    output, should_quit = await handle_command("  quit  ")
    assert should_quit is True


# ---------------------------------------------------------------------------
# handle_command — empty input
# ---------------------------------------------------------------------------


@pytest.mark.asyncio
async def test_empty_line_returns_empty():
    output, should_quit = await handle_command("")
    assert output == ""
    assert should_quit is False


@pytest.mark.asyncio
async def test_whitespace_line_returns_empty():
    output, should_quit = await handle_command("   ")
    assert output == ""
    assert should_quit is False


# ---------------------------------------------------------------------------
# handle_command — get-schematics
# ---------------------------------------------------------------------------


@pytest.mark.asyncio
async def test_get_schematics_calls_searcher():
    mock_return = "1. Cool Castle\n   Author: Bob"
    with patch(
        "browser_repl.run_get_schematics", new=AsyncMock(return_value=mock_return)
    ) as mock_fn:
        output, should_quit = await handle_command("get-schematics castle")
        mock_fn.assert_called_once_with("castle")
        assert "Cool Castle" in output
        assert should_quit is False


@pytest.mark.asyncio
async def test_get_schematics_missing_query():
    output, should_quit = await handle_command("get-schematics ")
    assert "Usage" in output
    assert should_quit is False


@pytest.mark.asyncio
async def test_get_schematics_error_handled():
    with patch(
        "browser_repl.run_get_schematics",
        new=AsyncMock(side_effect=RuntimeError("network error")),
    ):
        output, should_quit = await handle_command("get-schematics house")
        assert "Error" in output
        assert "network error" in output
        assert should_quit is False


# ---------------------------------------------------------------------------
# handle_command — generic browser task
# ---------------------------------------------------------------------------


@pytest.mark.asyncio
async def test_generic_task_calls_browser_use():
    with patch(
        "browser_repl.run_browser_task", new=AsyncMock(return_value="task result")
    ) as mock_fn:
        output, should_quit = await handle_command("go to google.com and search cats")
        mock_fn.assert_called_once_with("go to google.com and search cats")
        assert "task result" in output
        assert should_quit is False


@pytest.mark.asyncio
async def test_generic_task_error_handled():
    with patch(
        "browser_repl.run_browser_task",
        new=AsyncMock(side_effect=Exception("browser crashed")),
    ):
        output, should_quit = await handle_command("do something")
        assert "Error" in output
        assert "browser crashed" in output
        assert should_quit is False


# ---------------------------------------------------------------------------
# Sentinel constant
# ---------------------------------------------------------------------------


def test_sentinel_value():
    assert SENTINEL == "DONE"


# ---------------------------------------------------------------------------
# run_get_schematics — unit test with mocked SchematicSearcher
# ---------------------------------------------------------------------------


@pytest.mark.asyncio
async def test_run_get_schematics_formats_results():
    from browser_repl import run_get_schematics

    mock_results = [
        {
            "name": "Tower",
            "url": "https://example.com/tower",
            "author": "Alice",
            "description": "Tall tower",
        },
        {"name": "Bridge", "url": "https://example.com/bridge", "author": "", "description": ""},
    ]

    with patch("browser_repl.SchematicSearcher") as MockSearcher:
        instance = MockSearcher.return_value
        instance.search = AsyncMock(return_value=mock_results)
        result = await run_get_schematics("tower")

    assert "Tower" in result
    assert "Alice" in result
    assert "Tall tower" in result
    assert "Bridge" in result


@pytest.mark.asyncio
async def test_run_get_schematics_empty_results():
    from browser_repl import run_get_schematics

    with patch("browser_repl.SchematicSearcher") as MockSearcher:
        instance = MockSearcher.return_value
        instance.search = AsyncMock(return_value=[])
        result = await run_get_schematics("nothing")

    assert "No results" in result
