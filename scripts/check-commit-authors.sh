#!/usr/bin/env bash
# Fails if a commit of the history names an AI assistant as author, committer, or co-author.
# The project rule: the git history shows only the human authors and the release bots.
# Usage: scripts/check-commit-authors.sh [revision]   (default: HEAD)
set -euo pipefail

revision="${1:-HEAD}"
status=0

# Read the history first, on its own line: if git fails, set -e stops the script (fail closed).
identities=$(git log "$revision" --format='%h%x09%an%x09%ae%x09%cn%x09%ce')
messages=$(git log "$revision" --format='%h %B')
if [ -z "$identities" ]; then
  echo "error: no commit history to check in $revision"
  exit 1
fi

# Test every field of each line, so a tab in a name cannot move a value to another field.
# A name or an email that contains "claude" or "anthropic" (with any case) is an AI assistant.
bad_identities=$(awk -F '\t' '
  function bad(v) { v = tolower(v); gsub(/[^a-z0-9.@]/, "", v); return v ~ /claude|anthropic/ }
  {
    for (i = 2; i <= NF; i++) {
      if (bad($i)) {
        print $1 " author=" $2 " <" $3 "> committer=" $4 " <" $5 ">"
        next
      }
    }
  }' <<<"$identities")
if [ -n "$bad_identities" ]; then
  echo "error: these commits have an AI assistant as author or committer:"
  echo "$bad_identities"
  status=1
fi

# grep exits with 1 when nothing matches, and with 2 or more on an error.
set +e
bad_trailers=$(grep -i -E 'co-authored-by:.*(claude|anthropic)|generated (with|by) .*claude' \
  <<<"$messages")
grep_status=$?
set -e
if [ "$grep_status" -gt 1 ]; then
  echo "error: the check of the commit messages failed"
  exit 1
fi
if [ -n "$bad_trailers" ]; then
  echo "error: these commit messages have an AI assistant signature:"
  echo "$bad_trailers"
  status=1
fi

if [ "$status" -ne 0 ]; then
  echo "Set your own git identity (git config user.name and user.email), remove the"
  echo "signature lines, and rewrite the commits of the branch."
  exit 1
fi
echo "The commit authors and messages are correct."
