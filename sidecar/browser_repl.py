"""
Browser Use REPL — interactive prompt for running Browser Use tasks.

Usage:
    python browser_repl.py

Commands:
    get-schematics <query>   Search for schematics using SchematicSearcher
    quit                     Exit the REPL
    <any other text>         Run as a Browser Use agent task
"""

import asyncio
import sys
from pathlib import Path

from browser_use import Agent
from langchain_anthropic import ChatAnthropic

from browser_search import SchematicSearcher

SENTINEL = "DONE"
CACHE_DIR = Path(__file__).parent / "schema_cache"
LLM_MODEL = "claude-sonnet-4-20250514"
LLM_TIMEOUT = 60


def _make_llm() -> ChatAnthropic:
    return ChatAnthropic(model_name=LLM_MODEL, timeout=LLM_TIMEOUT, stop=None)


async def run_browser_task(task: str) -> str:
    """Run an arbitrary task through the Browser Use agent. Returns result as string."""
    agent = Agent(task=task, llm=_make_llm())
    result = await agent.run()
    return str(result)


async def run_get_schematics(query: str) -> str:
    """Search for schematics and return formatted results."""
    searcher = SchematicSearcher(cache_dir=CACHE_DIR)
    results = await searcher.search(query)
    if not results:
        return "No results found."
    lines = []
    for i, r in enumerate(results, 1):
        name = r.get("name", "Unknown")
        url = r.get("url", "")
        author = r.get("author", "")
        desc = r.get("description", r.get("raw", ""))
        lines.append(f"{i}. {name}")
        if author:
            lines.append(f"   Author: {author}")
        if url:
            lines.append(f"   URL: {url}")
        if desc:
            lines.append(f"   {desc}")
    return "\n".join(lines)


async def handle_command(line: str) -> tuple[str, bool]:
    """
    Process one REPL input line.

    Returns (output, should_quit).
    """
    stripped = line.strip()

    if stripped == "quit":
        return ("Goodbye.", True)

    if stripped == "get-schematics" or stripped.startswith("get-schematics "):
        query = stripped[len("get-schematics"):].strip()
        if not query:
            return ("Usage: get-schematics <query>", False)
        try:
            result = await run_get_schematics(query)
        except Exception as exc:
            result = f"Error: {exc}"
        return (result, False)

    if not stripped:
        return ("", False)

    # Generic Browser Use task
    try:
        result = await run_browser_task(stripped)
    except Exception as exc:
        result = f"Error: {exc}"
    return (result, False)


async def repl() -> None:
    """Run the interactive REPL loop."""
    CACHE_DIR.mkdir(exist_ok=True)

    while True:
        try:
            sys.stdout.write(">>> ")
            sys.stdout.flush()
            line = sys.stdin.readline()
        except (EOFError, KeyboardInterrupt):
            print("\nGoodbye.")
            print(SENTINEL)
            break

        if not line:
            # EOF
            print(SENTINEL)
            break

        output, should_quit = await handle_command(line)
        if output:
            print(output)
        print(SENTINEL)
        sys.stdout.flush()

        if should_quit:
            break


if __name__ == "__main__":
    asyncio.run(repl())
