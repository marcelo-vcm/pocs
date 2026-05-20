import json
import os
from pathlib import Path
from playwright.sync_api import sync_playwright

_COOKIE_FILE = Path.home() / ".jira-mcp" / "cookies.json"


def _save(cookies: list) -> None:
    _COOKIE_FILE.parent.mkdir(parents=True, exist_ok=True)
    _COOKIE_FILE.write_text(json.dumps(cookies, indent=2))


def _load() -> list | None:
    return json.loads(_COOKIE_FILE.read_text()) if _COOKIE_FILE.exists() else None


def _browser_login(jira_url: str) -> list:
    print(f"Opening browser — log in to Jira at {jira_url} then wait for the page to load.")
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=False)
        ctx = browser.new_context()
        page = ctx.new_page()
        page.goto(jira_url)
        # wait until the user lands back on the Jira domain (not id.atlassian.com)
        page.wait_for_url(
            lambda url: jira_url.rstrip("/") in url and "id.atlassian.com" not in url,
            timeout=120_000,
        )
        cookies = ctx.cookies()
        browser.close()
    return cookies


def get_cookies(force: bool = False) -> list:
    if not force:
        cookies = _load()
        if cookies:
            return cookies
    jira_url = os.environ["JIRA_URL"]
    cookies = _browser_login(jira_url)
    _save(cookies)
    return cookies


def clear_cookies() -> None:
    if _COOKIE_FILE.exists():
        _COOKIE_FILE.unlink()
