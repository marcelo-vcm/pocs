#!/usr/bin/env bash
set -euo pipefail

SRC_DIR="$(cd "$(dirname "$0")" && pwd)"

PROJECT_DIR="${PWD}"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --project) PROJECT_DIR="$(cd "$2" && pwd)"; shift 2 ;;
    *) echo "Unknown option: $1"; echo "Usage: $0 [--project <path>]"; exit 1 ;;
  esac
done

command -v uv >/dev/null     || { echo "uv not found. Install: curl -LsSf https://astral.sh/uv/install.sh | sh"; exit 1; }
command -v claude >/dev/null || { echo "claude CLI not found. Install Claude Code first."; exit 1; }

if [[ -z "${JIRA_URL:-}" ]]; then
  read -r -p "Jira URL (e.g. https://yourorg.atlassian.net): " JIRA_URL
fi
[[ -n "$JIRA_URL" ]] || { echo "Error: JIRA_URL cannot be empty."; exit 1; }

echo "Installing dependencies..."
cd "$SRC_DIR"
uv sync
uv run playwright install chromium

echo "Registering jira-mcp in $PROJECT_DIR ..."
cd "$PROJECT_DIR"
claude mcp add jira-mcp --scope local -e "JIRA_URL=$JIRA_URL" -- \
  uv run --directory "$SRC_DIR" jira-mcp

echo ""
echo "Done. jira-mcp is active for this project."
echo "On first use a browser will open — log in to Jira and the session is cached."
echo "To remove: $SRC_DIR/uninstall.sh [--project $PROJECT_DIR]"
