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
ga_status="${PIPESTATUS[0]}"
if [[ "$ga_status" -ne 0 ]]; then
	exit "$ga_status"
fi

echo | tee -a "$LOG_FILE"
echo "===== FCFS CloudSim Plus Baseline =====" | tee -a "$LOG_FILE"
mvn exec:java -Dexec.args=fcfs 2>&1 | tee -a "$LOG_FILE"
fcfs_status="${PIPESTATUS[0]}"
if [[ "$fcfs_status" -ne 0 ]]; then
	exit "$fcfs_status"
fi

echo | tee -a "$LOG_FILE"
echo "===== GA vs FCFS Metrics =====" | tee -a "$LOG_FILE"
echo "--- Genetic Algorithm ---" | tee -a "$LOG_FILE"
cat results/metrics.txt | tee -a "$LOG_FILE"
echo "--- FCFS ---" | tee -a "$LOG_FILE"
cat results/fcfs_metrics.txt | tee -a "$LOG_FILE"

exit 0