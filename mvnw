#!/bin/sh
# Lightweight, repository-local Maven wrapper. It downloads Maven only when needed.
set -eu
BASE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
VERSION=3.9.9
DIST_DIR="$BASE_DIR/.mvn/wrapper/dists/apache-maven-$VERSION"
MAVEN="$DIST_DIR/apache-maven-$VERSION/bin/mvn"
if [ ! -x "$MAVEN" ]; then
  mkdir -p "$DIST_DIR"
  ARCHIVE="$DIST_DIR/maven.tar.gz"
  URL="https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$VERSION/apache-maven-$VERSION-bin.tar.gz"
  if command -v curl >/dev/null 2>&1; then curl -fsSL "$URL" -o "$ARCHIVE"; elif command -v wget >/dev/null 2>&1; then wget -qO "$ARCHIVE" "$URL"; else echo "curl or wget is required to bootstrap Maven" >&2; exit 1; fi
  tar -xzf "$ARCHIVE" -C "$DIST_DIR"
  rm -f "$ARCHIVE"
fi
exec "$MAVEN" "$@"
