#!/usr/bin/env bash
# Prints "true" if a change contains only documentation files. Else prints "false".
# The CI workflows use it to skip the Gradle steps in a pull request that changes only documentation.
#
# Usage: scripts/docs-only-change.sh <base> [head]   (default head: HEAD)
# Example: scripts/docs-only-change.sh HEAD^1   (a pull request: the merge commit and its base)
#
# A documentation file is a Markdown file (*.md in any folder) or a file in the docs/ folder.
# A change without files prints "false", so the build runs.
set -euo pipefail

base="${1:?usage: scripts/docs-only-change.sh <base> [head]}"
head="${2:-HEAD}"

# Read the file list first, on its own line: if git fails, set -e stops the script (fail closed).
files=$(git diff --name-only "$base" "$head")
if [ -z "$files" ]; then
  echo false
  exit 0
fi

if grep -qvE '(\.md$|^docs/)' <<<"$files"; then
  echo false
else
  echo true
fi
