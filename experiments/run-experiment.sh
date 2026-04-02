#!/usr/bin/env bash
set -euo pipefail

# Usage: ./experiments/run-experiment.sh <exp_number> [compose_file]
# Example: ./experiments/run-experiment.sh 4
#          ./experiments/run-experiment.sh 5 docker-compose-exp.yml

EXP_NUM="${1:?Usage: $0 <exp_number> [compose_file]}"
COMPOSE_FILE="${2:-docker-compose.yml}"
EXP_DIR="experiments/exp${EXP_NUM}"

if [ ! -d "$EXP_DIR" ]; then
    echo "Error: Experiment directory $EXP_DIR does not exist"
    exit 1
fi

echo "=== Running Experiment ${EXP_NUM} ==="
echo "Compose file: ${COMPOSE_FILE}"
echo "Data directory: ${EXP_DIR}"

# Update volume mounts in compose file to point to the correct experiment
TEMP_COMPOSE=$(mktemp)
sed "s|experiments/exp[0-9]*/|experiments/exp${EXP_NUM}/|g" "$COMPOSE_FILE" > "$TEMP_COMPOSE"

# Clean up previous run
echo "Stopping any running containers..."
docker compose -f "$TEMP_COMPOSE" down -v 2>/dev/null || true

# Clean raft storage
echo "Cleaning raft storage..."
find "$EXP_DIR" -name "raft-storage" -type d -exec rm -rf {} + 2>/dev/null || true
for dir in "$EXP_DIR"/ldm*/; do
    mkdir -p "${dir}raft-storage"
done

# Start the cluster
echo "Starting LDM cluster..."
docker compose -f "$TEMP_COMPOSE" up -d

echo ""
echo "=== Experiment ${EXP_NUM} started ==="
echo "Dashboard: http://localhost:3000/dashboard"
echo "LDM1 API:  http://localhost:8080/api/migrations"
echo "LDM2 API:  http://localhost:8081/api/migrations"
echo "LDM3 API:  http://localhost:8082/api/migrations"
echo "Health:    http://localhost:8080/q/health"
echo ""
echo "To view logs: docker compose -f $COMPOSE_FILE logs -f"
echo "To stop:      docker compose -f $COMPOSE_FILE down"

rm -f "$TEMP_COMPOSE"
