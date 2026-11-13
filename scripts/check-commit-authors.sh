#!/usr/bin/env bash
# Fails if a commit of the history names an AI assistant as author, committer, or co-author.
# The project rule: the git history shows only the human authors and the release bots.
# Usage: scripts/check-commit-authors.sh [revision]   (default: HEAD)
set -euo pipefail

revision="${1:-HEAD}"
status=0

identities=$(git log "$revision" --format='%h author=%an <%ae> committer=%cn <%ce>' \
  | grep -i -E '@anthropic\.com|=Claude <' || true)
if [ -n "$identities" ]; then
  echo "error: these commits have an AI assistant as author or committer:"
  echo "$identities"
  status=1
fi

trailers=$(git log "$revision" --format='%h %B' \
  | grep -i -E 'Co-Authored-By: *Claude|Generated with \[?Claude Code' || true)
if [ -n "$trailers" ]; then
  echo "error: these commit messages have an AI assistant signature:"
  echo "$trailers"
  status=1
fi

if [ "$status" -ne 0 ]; then
  echo "Set your own git identity (git config user.name and user.email), remove the"
  echo "signature lines, and rewrite the commits of the branch."
  exit 1
fi
echo "The commit authors and messages are correct."
