from .jira_client import get_client, get_client_fresh
from .embeddings import embed
from .vector_store import upsert, query as vquery


def _issue_text(issue: dict) -> str:
    fields = issue.get("fields", {})
    summary = fields.get("summary", "")
    desc = fields.get("description") or ""
    if isinstance(desc, dict):
        desc = " ".join(
            node.get("text", "")
            for block in desc.get("content", [])
            for node in block.get("content", [])
            if node.get("type") == "text"
        )
    status = fields.get("status", {}).get("name", "")
    issue_type = fields.get("issuetype", {}).get("name", "")
    return f"{issue['key']}: {summary}\nType: {issue_type} | Status: {status}\n{desc[:600]}".strip()


def _fetch(jql: str) -> list[dict]:
    with get_client() as client:
        r = client.get("/search", params={"jql": jql, "maxResults": 50})
        if r.status_code == 401:
            with get_client_fresh() as fresh:
                r = fresh.get("/search", params={"jql": jql, "maxResults": 50})
        r.raise_for_status()
        return r.json().get("issues", [])


def _index(issues: list[dict]) -> None:
    records = []
    for issue in issues:
        fields = issue.get("fields", {})
        records.append({
            "key": issue["key"],
            "summary": fields.get("summary", ""),
            "status": fields.get("status", {}).get("name", ""),
            "issue_type": fields.get("issuetype", {}).get("name", ""),
            "text": _issue_text(issue),
        })
    upsert(records, embed([r["text"] for r in records]))


def semantic_query(jql: str, query: str, top_k: int = 5) -> list[dict]:
    issues = _fetch(jql)
    if not issues:
        return []
    _index(issues)
    [query_embedding] = embed([query])
    return vquery(query_embedding, top_k=top_k)
