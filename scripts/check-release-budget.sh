#!/usr/bin/env bash
# Stops a release when a calendar month already has the maximum number of releases.
# Maven Central limits the publishing volume of each organization per calendar month (file count,
# release size, and release count). See "Maven Central limits" in RELEASING.md.
#
# Usage: scripts/check-release-budget.sh <repository> <maximum> [month]   (default month: the current UTC month)
# Example: scripts/check-release-budget.sh dazz-pt/copymapper 4 2026-12
#
# The script counts the published GitHub releases (not the drafts) of the repository in the month.
# Each GitHub release of this repository is also a release on Maven Central.
# It prints the count. It exits with 1 if the count is the maximum or more.
# It needs the GitHub CLI (gh). In a workflow, set GH_TOKEN.
set -euo pipefail

usage="usage: scripts/check-release-budget.sh <repository> <maximum> [month]"
repository="${1:?$usage}"
maximum="${2:?$usage}"
month="${3:-$(date -u +%Y-%m)}"

if ! [[ "$maximum" =~ ^[1-9][0-9]*$ ]]; then
  echo "error: the maximum must be a positive integer, not '$maximum'" >&2
  exit 2
fi
if ! [[ "$month" =~ ^[0-9]{4}-(0[1-9]|1[0-2])$ ]]; then
  echo "error: the month must have the format YYYY-MM, not '$month'" >&2
  exit 2
fi

# Read the dates first, on their own line: if gh fails, set -e stops the script (fail closed).
dates=$(gh api --paginate "repos/$repository/releases?per_page=100" \
  --jq '.[] | select(.draft | not) | .published_at')
count=$(grep -c "^$month-" <<<"$dates" || true)

if [ "$count" -ge "$maximum" ]; then
  echo "::error::The month $month already has $count releases (maximum $maximum)." \
    "Wait for the next month, or run the workflow with 'force' for an urgent fix. See RELEASING.md."
  exit 1
fi
echo "The month $month has $count releases (maximum $maximum). The release can continue."
