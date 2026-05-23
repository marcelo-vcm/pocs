# jira-mcp

A [Model Context Protocol (MCP)](https://modelcontextprotocol.io) server that lets Claude search Jira issues using natural language. It fetches issues via JQL, indexes them locally with embeddings, and returns the top results ranked by semantic similarity.

## How it works

1. You give it a JQL query (to scope which issues to look at) and a natural language question
2. It fetches up to 50 matching issues from your Jira instance
3. Each issue description is split into **500-char chunks with 100-char overlap** — every chunk is prefixed with the ticket key, summary, type, and status so identity is never lost
4. Chunks are embedded with `all-MiniLM-L6-v2` (via `sentence-transformers`) and stored in a local ChromaDB vector store (`~/.jira-mcp/chroma/`)
5. It returns the top-k chunks most semantically similar to your question

Authentication is browser-based: on first use a Chromium window opens for you to log in to Jira. The session cookies are cached at `~/.jira-mcp/cookies.json` and reused on subsequent calls.

## Requirements

- [uv](https://docs.astral.sh/uv/) — Python package manager
- [Claude Code](https://claude.ai/code) CLI (`claude`)
- A Jira Cloud instance you can log in to via browser

## Install into a project

Clone this repo, then from your project directory run:

```bash
/path/to/jira-mcp-poc/install.sh
```

Or target a specific project directory:

```bash
/path/to/jira-mcp-poc/install.sh --project /path/to/your/project
```

The script will:
1. Prompt you for your Jira URL (e.g. `https://yourorg.atlassian.net`)
2. Install Python dependencies and Playwright's Chromium browser
3. Register the MCP server locally in the target project (not checked into git)

On first use Claude will trigger a browser login. Log in to Jira and wait for the page to load — cookies are saved automatically.

## Uninstall

```bash
/path/to/jira-mcp-poc/uninstall.sh

# or for a specific project:
/path/to/jira-mcp-poc/uninstall.sh --project /path/to/your/project
```

To also clear cached cookies and the local vector store:

```bash
rm -rf ~/.jira-mcp
```

## Available tool

Once installed, Claude has access to one tool:

### `semantic_query(jql, query, top_k=5)`

| Parameter | Type | Description |
|-----------|------|-------------|
| `jql` | string | JQL filter to scope the issue set (e.g. `project = XY AND sprint in openSprints()`) |
| `query` | string | Natural language question (e.g. `"XYZ AI agent requirements"`) |
| `top_k` | int | Number of results to return (default: 5) |

**Example prompt to Claude:**

> Search Jira for tickets about the XYZ AI Agent feature using JQL `project = XY` and verify if the code change is matching the requirements for XYZ AI Agent.

## Project structure

```
jira-mcp-poc/
├── install.sh          # Interactive installer
├── uninstall.sh        # Uninstaller
├── pyproject.toml
└── src/jira_mcp/
    ├── server.py       # MCP server entry point
    ├── tools.py        # semantic_query implementation
    ├── jira_client.py  # Jira REST API client (cookie auth)
    ├── auth.py         # Browser-based login + cookie cache
    ├── embeddings.py   # sentence-transformers wrapper
    └── vector_store.py # ChromaDB persistence
```

## Notes

- The local vector store persists between calls — issues already indexed are not re-fetched unless the JQL returns them again. Each issue is stored as multiple chunk vectors (`{KEY}-0`, `{KEY}-1`, …); run `rm -rf ~/.jira-mcp/chroma` to force a full re-index
- Cookies expire with your Jira session; when they do, the next call opens the browser again automatically
- Token usage and costs depend entirely on your own Claude Code usage, not on this server
