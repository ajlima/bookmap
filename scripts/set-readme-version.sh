#!/usr/bin/env bash
# Sets the released BookMap version in the README files.
#
# Usage: scripts/set-readme-version.sh X.Y.Z
#
# The script changes these places in README.md and bookmap-copybook-gradle-plugin/README.md:
#   - the version badge;
#   - each dependency "pt.dazz.bookmap:<module>:<version>";
#   - each plugin declaration id("pt.dazz.bookmap.copybook") version "<version>";
#   - the texts "the version is `...`" and "at version `...`".
# The script fails if one of these places is missing, so that a change to a README
# cannot stop the update without an error.
set -euo pipefail

version="${1:?Usage: $0 X.Y.Z}"
root="README.md"
plugin="bookmap-copybook-gradle-plugin/README.md"

if [[ ! "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "error: '$version' is not a release version (X.Y.Z)" >&2
  exit 1
fi

for file in "$root" "$plugin"; do
  # sed -i.bak works with GNU sed (Linux) and BSD sed (macOS).
  sed -E -i.bak \
    -e "s#(alt=\"Version )[^\"]+\"#\1${version}\"#" \
    -e "s#(img\.shields\.io/badge/version-).*(-f89820)#\1${version}\2#" \
    -e "s#(pt\.dazz\.bookmap:bookmap-[a-z-]+:)[^\"]+\"#\1${version}\"#g" \
    -e "s#(id\(\"pt\.dazz\.bookmap\.copybook\"\) version \")[^\"]+\"#\1${version}\"#g" \
    -e "s#(version is |at version )\`[^\`]+\`#\1\`${version}\`#g" \
    "$file"
  rm -f "$file.bak"
done

check() {
  local file="$1" text="$2" minimum="$3" count
  count=$(grep -cF -- "$text" "$file" || true)
  if [ "$count" -lt "$minimum" ]; then
    echo "error: expected $minimum or more lines with '$text' in $file, found $count" >&2
    exit 1
  fi
}
check "$root" "alt=\"Version ${version}\"" 1
check "$root" "badge/version-${version}-f89820" 1
check "$root" ":${version}\"" 3
check "$root" "id(\"pt.dazz.bookmap.copybook\") version \"${version}\"" 1
check "$root" "version is \`${version}\`" 1
check "$root" "at version \`${version}\`" 1
check "$plugin" ":${version}\"" 2
check "$plugin" "id(\"pt.dazz.bookmap.copybook\") version \"${version}\"" 1
echo "README version set to ${version}"
