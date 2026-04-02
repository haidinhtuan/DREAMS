# Experiments

## Running

```bash
# Run a specific experiment (1-5)
./run-experiment.sh 4

# Run with 6-node cluster
./run-experiment.sh 5 docker-compose-exp.yml

# Collect results
./collect-results.sh
```

## Experiment Overview

| # | Nodes | Microservices | Scenario |
|---|-------|---------------|----------|
| 1 | 3 | 10 | Optimal baseline |
| 2 | 3 | 10 | Single migration |
| 3 | 3 | 10 | Single migration (testing mode) |
| 4 | 3 | 10 | Multi-migration |
| 5 | 6 | 20 | Full E2E optimization |
