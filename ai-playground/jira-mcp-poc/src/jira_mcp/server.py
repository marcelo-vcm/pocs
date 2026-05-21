from mcp.server.fastmcp import FastMCP
from jira_mcp import tools

mcp = FastMCP("jira-mcp")


@mcp.tool()
def semantic_query(jql: str, query: str, top_k: int = 5) -> list:
    """Search Jira issues with JQL, index them locally, and return the top results
    ranked by semantic similarity to the query."""
    return tools.semantic_query(jql, query, top_k)


def main() -> None:
    mcp.run()
