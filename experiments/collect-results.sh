#!/usr/bin/env bash
set -euo pipefail

# Collects migration results from all running LDM instances
# Usage: ./experiments/collect-results.sh [output_dir]

OUTPUT_DIR="${1:-experiments/results}"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
RESULT_DIR="${OUTPUT_DIR}/${TIMESTAMP}"

mkdir -p "$RESULT_DIR"

echo "=== Collecting Experiment Results ==="

# Collect migrations from each LDM
for port in 8080 8081 8082 8083 8084 8085; do
    echo "Querying LDM at port ${port}..."
    if curl -s "http://localhost:${port}/api/migrations" > "${RESULT_DIR}/migrations_${port}.json" 2>/dev/null; then
        echo "  Saved: ${RESULT_DIR}/migrations_${port}.json"
    else
        echo "  Skipped: LDM at port ${port} not reachable"
        rm -f "${RESULT_DIR}/migrations_${port}.json"
    fi
done

# Collect health status
for port in 8080 8081 8082; do
    if curl -s "http://localhost:${port}/q/health" > "${RESULT_DIR}/health_${port}.json" 2>/dev/null; then
        echo "  Health saved: ${RESULT_DIR}/health_${port}.json"
    fi
done

echo ""
echo "Results saved to: ${RESULT_DIR}/"
ls -la "${RESULT_DIR}/"
