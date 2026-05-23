from .jira_client import get_client, get_client_fresh
from .embeddings import embed
from .vector_store import upsert, query as vquery

_CHUNK_SIZE = 500
_CHUNK_OVERLAP = 100


def _extract_description(fields: dict) -> str:
    desc = fields.get("description") or ""
    if isinstance(desc, dict):
        return " ".join(
            node.get("text", "")
            for block in desc.get("content", [])
            for node in block.get("content", [])
            if node.get("type") == "text"
        )
    return desc


def _chunks(text: str) -> list[str]:
    if not text:
        return [""]
    step = _CHUNK_SIZE - _CHUNK_OVERLAP
    return [text[i:i + _CHUNK_SIZE] for i in range(0, len(text), step)]


def _fetch(jql: str) -> list[dict]:
    params = {"jql": jql, "maxResults": 50, "fields": "summary,description,status,issuetype"}
    with get_client() as client:
        r = client.get("/search/jql", params=params)
        if r.status_code == 401:
            with get_client_fresh() as fresh:
                r = fresh.get("/search/jql", params=params)
        r.raise_for_status()
        return r.json().get("issues", [])


def _index(issues: list[dict]) -> None:
    records = []
    for issue in issues:
        fields = issue.get("fields", {})
        key = issue["key"]
        summary = fields.get("summary", "")
        status = fields.get("status", {}).get("name", "")
        issue_type = fields.get("issuetype", {}).get("name", "")
        header = f"{key}: {summary}\nType: {issue_type} | Status: {status}\n"
        desc = _extract_description(fields)
        for idx, chunk in enumerate(_chunks(desc)):
            records.append({
                "id": f"{key}-{idx}",
                "key": key,
                "summary": summary,
                "status": status,
                "issue_type": issue_type,
                "chunk_index": idx,
                "text": header + chunk,
            })
    upsert(records, embed([r["text"] for r in records]))


def semantic_query(jql: str, query: str, top_k: int = 5) -> list[dict]:
    issues = _fetch(jql)
    if not issues:
        return []
    _index(issues)
    [query_embedding] = embed([query])
    return vquery(query_embedding, top_k=top_k)
