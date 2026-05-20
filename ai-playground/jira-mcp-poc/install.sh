#!/usr/bin/env bash
set -euo pipefail

SRC_DIR="$(cd "$(dirname "$0")" && pwd)"

usage() {
  echo "Usage: $0 --project <path>"
  exit 1
}

PROJECT_DIR=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --project) PROJECT_DIR="$2"; shift 2 ;;
    *) usage ;;
  esac
done

[[ -n "$PROJECT_DIR" ]] || { echo "Error: --project <path> is required"; usage; }
[[ -d "$PROJECT_DIR" ]] || { echo "Error: directory not found: $PROJECT_DIR"; exit 1; }

command -v uv >/dev/null || { echo "Need uv on PATH (curl -LsSf https://astral.sh/uv/install.sh | sh)"; exit 1; }

: "${JIRA_URL:?Set JIRA_URL before installing (e.g. https://yourorg.atlassian.net)}"

cd "$SRC_DIR"
uv sync
uv run playwright install chromium

CONFIG_FILE="$(cd "$PROJECT_DIR" && pwd)/.claude/settings.json"
mkdir -p "$(dirname "$CONFIG_FILE")"

python3 - <<EOF
import json, os

p = "$CONFIG_FILE"
cfg = {}
if os.path.exists(p):
    try:
        cfg = json.loads(open(p).read())
    except Exception:
        cfg = {}

cfg.setdefault("mcpServers", {})
cfg["mcpServers"]["jira-mcp"] = {
    "command": "uv",
    "args": ["run", "--directory", "$SRC_DIR", "jira-mcp"],
    "env": {
        "JIRA_URL": os.environ["JIRA_URL"],
    },
}
open(p, "w").write(json.dumps(cfg, indent=2))
print("Registered jira-mcp in " + p)
EOF

echo ""
echo "Config:    $CONFIG_FILE"
echo "Uninstall: $SRC_DIR/uninstall.sh --project $PROJECT_DIR"
