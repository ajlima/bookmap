#!/usr/bin/env bash
# Publishes the wiki pages to the GitHub wiki of the repository.
#
# Usage: scripts/publish-wiki.sh <wiki-git-url>
#
# The script copies docs/wiki/ to the wiki repository, and ROADMAP.md to the page Roadmap.md.
# It deletes the wiki pages that are not in docs/wiki/. Thus, docs/wiki/ is the only source.
# It makes a commit and a push only if a page changed.
#
# GitHub creates the wiki repository only after the first page exists. If the clone fails,
# create one page in the web UI (Wiki > Create the first page), then run the script again.
set -euo pipefail

wiki_url="${1:?Usage: $0 <wiki-git-url>}"
root="$(cd "$(dirname "$0")/.." && pwd)"
source_dir="$root/docs/wiki"
work_dir="$(mktemp -d)"
trap 'rm -rf "$work_dir"' EXIT

if [ ! -f "$source_dir/Home.md" ]; then
  echo "error: $source_dir/Home.md does not exist" >&2
  exit 1
fi

if ! git clone --quiet "$wiki_url" "$work_dir/wiki" 2>"$work_dir/clone.log"; then
  sed 's#://[^@/]*@#://***@#' "$work_dir/clone.log" >&2
  echo "error: cannot clone the wiki repository." \
    "Enable the wiki and create the first page in the web UI, then run this script again." >&2
  exit 1
fi

rsync --recursive --delete --checksum --exclude '.git' "$source_dir/" "$work_dir/wiki/"
cp "$root/ROADMAP.md" "$work_dir/wiki/Roadmap.md"

cd "$work_dir/wiki"
git add --all
if git diff --cached --quiet; then
  echo "The wiki is already up to date"
  exit 0
fi

source_commit="$(git -C "$root" rev-parse --short HEAD)"
git -c user.name="github-actions[bot]" \
    -c user.email="41898282+github-actions[bot]@users.noreply.github.com" \
    commit --quiet --message "docs: publish the wiki from $source_commit"
git push --quiet origin HEAD
echo "Wiki published from $source_commit"
