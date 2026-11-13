#!/usr/bin/env bash
# Writes the SHA-256 checksums of all plugins and libraries in the Gradle dependency
# verification files:
#   - gradle/verification-metadata.xml (the root build);
#   - bookmap-examples-gradle-plugin/gradle/verification-metadata.xml (the standalone example).
# The example is a separate root build, so Gradle does not read the file of the root build there.
#
# Usage: scripts/update-verification-metadata.sh [Gradle options]
# Example: scripts/update-verification-metadata.sh --no-daemon
#
# The script removes all checksums first, so that the files keep no old versions.
# It downloads all dependencies again into a temporary Gradle user home.
# It keeps the <configuration> section of each file.
# Gradle takes the checksums from the downloaded files. Review the diff before a commit.
set -euo pipefail

cd "$(dirname "$0")/.."

root_file="gradle/verification-metadata.xml"
example_file="bookmap-examples-gradle-plugin/gradle/verification-metadata.xml"

clear_components() {
  local file="$1"
  [ -f "$file" ] || return 0
  awk '
    /<components>/ { print; skip = 1; next }
    /<\/components>/ { skip = 0 }
    !skip { print }
  ' "$file" > "$file.tmp"
  mv "$file.tmp" "$file"
}

clear_components "$root_file"
clear_components "$example_file"

# Use an empty Gradle user home. With a full cache, Gradle does not read some POM and module
# files again, and it does not write their checksums. CI starts with an empty cache, so it needs them.
gradle_home="$(mktemp -d)"
trap 'rm -rf "$gradle_home"' EXIT

# The release job runs publishAndReleaseToMavenCentral. Its dependencies are the same as the
# dependencies of publishToMavenLocal.
./gradlew --gradle-user-home "$gradle_home" --write-verification-metadata sha256 \
  clean build publishToMavenLocal "$@"
./gradlew --gradle-user-home "$gradle_home" -p bookmap-examples-gradle-plugin \
  --write-verification-metadata sha256 clean build "$@"

echo "Updated $root_file and $example_file"
