#!/usr/bin/env bash

set -u
set -o pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAVA_DIR="$SCRIPT_DIR/cloudsim-plus-ga"
LOG_FILE="$JAVA_DIR/results/run.log"

mkdir -p "$(dirname "$LOG_FILE")"
: > "$LOG_FILE"

echo | tee -a "$LOG_FILE"
echo "===== Java CloudSim Plus Genetic Algorithm =====" | tee -a "$LOG_FILE"
cd "$JAVA_DIR" || exit 1
mvn compile exec:java 2>&1 | tee -a "$LOG_FILE"
exit "${PIPESTATUS[0]}"