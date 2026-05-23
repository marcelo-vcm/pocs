#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="${PWD}"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --project) PROJECT_DIR="$(cd "$2" && pwd)"; shift 2 ;;
    *) echo "Unknown option: $1"; echo "Usage: $0 [--project <path>]"; exit 1 ;;
  esac
done

command -v claude >/dev/null || { echo "claude CLI not found."; exit 1; }

cd "$PROJECT_DIR"
claude mcp remove jira-mcp --scope local 2>/dev/null && echo "Removed jira-mcp from $PROJECT_DIR" \
  || echo "jira-mcp was not registered in $PROJECT_DIR"
