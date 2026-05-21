import os
import httpx
from .auth import get_cookies, clear_cookies


def _build_client(cookies: list) -> httpx.Client:
    jira_url = os.environ["JIRA_URL"].rstrip("/")
    jira_host = jira_url.split("//")[-1]
    cookie_jar = {
        c["name"]: c["value"]
        for c in cookies
        if jira_host in c.get("domain", "") or c.get("domain", "").lstrip(".") in jira_host
    }
    return httpx.Client(
        base_url=f"{jira_url}/rest/api/3",
        cookies=cookie_jar,
        headers={"Accept": "application/json"},
    )


def get_client() -> httpx.Client:
    return _build_client(get_cookies())


def get_client_fresh() -> httpx.Client:
    """Re-opens the browser when the session has expired."""
    clear_cookies()
    return _build_client(get_cookies(force=True))
