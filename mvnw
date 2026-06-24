#!/bin/sh

set -e

if command -v mvn >/dev/null 2>&1; then
  exec mvn "$@"
fi

echo "Maven is not installed or not available on PATH." >&2
echo "Install Maven, or run this project with your IDE's bundled Maven." >&2
exit 1
