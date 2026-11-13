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

# One field at a time: an email at anthropic.com, or the name Claude (with any case).
bad_identities=$(awk -F '\t' '
  function bad_name(n) { n = tolower(n); return n == "claude" || n ~ /^claude[ (]/ }
  function bad_email(e) { return tolower(e) ~ /anthropic\.com/ }
  bad_name($2) || bad_email($3) || bad_name($4) || bad_email($5) {
    print $1 " author=" $2 " <" $3 "> committer=" $4 " <" $5 ">"
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
