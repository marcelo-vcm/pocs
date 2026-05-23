from pathlib import Path
from functools import lru_cache
import chromadb

_STORE_DIR = Path.home() / ".jira-mcp" / "chroma"
_COLLECTION = "jira_issues"


@lru_cache(maxsize=1)
def _client() -> chromadb.PersistentClient:
    _STORE_DIR.mkdir(parents=True, exist_ok=True)
    return chromadb.PersistentClient(path=str(_STORE_DIR))


def get_collection() -> chromadb.Collection:
    return _client().get_or_create_collection(_COLLECTION, metadata={"hnsw:space": "cosine"})


def upsert(issues: list[dict], embeddings: list[list[float]]) -> None:
    col = get_collection()
    col.upsert(
        ids=[i["id"] for i in issues],
        embeddings=embeddings,
        documents=[i["text"] for i in issues],
        metadatas=[{k: v for k, v in i.items() if k != "text"} for i in issues],
    )


def query(embedding: list[float], top_k: int = 5) -> list[dict]:
    col = get_collection()
    result = col.query(query_embeddings=[embedding], n_results=top_k, include=["documents", "metadatas", "distances"])
    hits = []
    for doc, meta, dist in zip(result["documents"][0], result["metadatas"][0], result["distances"][0]):
        hits.append({"score": round(1 - dist, 4), "document": doc, **meta})
    return hits
