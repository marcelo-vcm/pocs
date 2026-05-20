#!/usr/bin/env bash
set -euo pipefail

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

CONFIG_FILE="$(cd "$PROJECT_DIR" && pwd)/.claude/settings.json"

python3 - <<EOF
import json, os

p = "$CONFIG_FILE"
if not os.path.exists(p):
    print("Config not found: " + p)
    raise SystemExit(0)

cfg = json.loads(open(p).read())
if "jira-mcp" in cfg.get("mcpServers", {}):
    del cfg["mcpServers"]["jira-mcp"]
    open(p, "w").write(json.dumps(cfg, indent=2))
    print("Removed jira-mcp from " + p)
else:
    print("jira-mcp not found in " + p)
EOF
