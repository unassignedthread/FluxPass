#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
JAR="$SCRIPT_DIR/target/fluxpass.jar"

if [ ! -f "$JAR" ]; then
    echo "Building FluxPass..."
    cd "$SCRIPT_DIR" && mvn package -q
fi

exec java -jar "$JAR" "$@"
