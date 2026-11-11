#!/usr/bin/env bash
# Sets the released BookMap version in README.md.
#
# Usage: scripts/set-readme-version.sh X.Y.Z [file]
#
# The script changes these places:
#   - the version badge;
#   - each dependency "pt.dazz.bookmap:<module>:<version>";
#   - the texts "the version is `...`" and "at version `...`".
# The script fails if one of these places is missing, so that a change to the README
# cannot stop the update without an error.
set -euo pipefail

version="${1:?Usage: $0 X.Y.Z [file]}"
file="${2:-README.md}"

if [[ ! "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "error: '$version' is not a release version (X.Y.Z)" >&2
  exit 1
fi

# sed -i.bak works with GNU sed (Linux) and BSD sed (macOS).
sed -E -i.bak \
  -e "s#(alt=\"Version )[^\"]+\"#\1${version}\"#" \
  -e "s#(img\.shields\.io/badge/version-).*(-f89820)#\1${version}\2#" \
  -e "s#(pt\.dazz\.bookmap:bookmap-[a-z-]+:)[^\"]+\"#\1${version}\"#g" \
  -e "s#(version is |at version )\`[^\`]+\`#\1\`${version}\`#g" \
  "$file"
rm -f "$file.bak"

check() {
  local count
  count=$(grep -cF -- "$1" "$file" || true)
  if [ "$count" -lt "$2" ]; then
    echo "error: expected $2 or more lines with '$1' in $file, found $count" >&2
    exit 1
  fi
}
check "alt=\"Version ${version}\"" 1
check "badge/version-${version}-f89820" 1
check ":${version}\"" 3
check "version is \`${version}\`" 1
check "at version \`${version}\`" 1
echo "README version set to ${version}"
