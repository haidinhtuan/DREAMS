# local-domain-manager

This project uses Quarkus, the Supersonic Subatomic Java Framework.

If you want to learn more about Quarkus, please visit its website: <https://quarkus.io/>.

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:

```shell script
./gradlew quarkusDev
```

> **_NOTE:_**  Quarkus now ships with a Dev UI, which is available in dev mode only at <http://localhost:8080/q/dev/>.

## Packaging and running the application

The application can be packaged using:

```shell script
./gradlew build
```

It produces the `quarkus-run.jar` file in the `build/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `build/quarkus-app/lib/` directory.

The application is now runnable using `java -jar build/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:

```shell script
./gradlew build -Dquarkus.package.jar.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar build/*-runner.jar`.

## Creating a native executable

You can create a native executable using:

```shell script
./gradlew build -Dquarkus.native.enabled=true
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using:

```shell script
./gradlew build -Dquarkus.native.enabled=true -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./build/local-domain-manager-1.0.0-SNAPSHOT-runner`

If you want to learn more about building native executables, please consult <https://quarkus.io/guides/gradle-tooling>.

## Creating a Docker image using Jib
```
./gradlew clean build -Dquarkus.container-image.build=true -Dquarkus.container-image.push=false
```

## Starting a LDM from the IDE

Make sure to add the following entries on the `hosts` file if you want to run a LDM outside of Docker (e.g. localhost via the IDE), e.g.:
```
127.0.0.1 host.docker.internal
127.0.0.1 ldm1
127.0.0.1 ldm2
127.0.0.1 ldm3
```

## Compiling Protobuf files

Run the following command to generate the protoc command from the project root to generate the protobuf files:
```
protoc -I=src/main/proto/com/ldm/infrastructure/serialization --java_out=src/main/java src/main/proto/com/ldm/infrastructure/serialization/migration_action.proto
protoc -I=src/main/proto/com/ldm/infrastructure/serialization --java_out=src/main/java src/main/proto/com/ldm/infrastructure/serialization/ping_pong.proto
protoc -I=src/main/proto/com/ldm/infrastructure/serialization --java_out=src/main/java src/main/proto/com/ldm/infrastructure/serialization/evaluate_migration_proposal.proto
```


# REST APIs to trigger a leadership change
The leader change has to be triggered on the current leader, e.g.:
```
http://localhost:8080/api/ratis/trigger-leader-change/54f34e52-b466-49f8-b525-230cd107148b
http://localhost:8081/api/ratis/trigger-leader-change/54f34e52-b466-49f8-b525-230cd107148b
http://localhost:8082/api/ratis/trigger-leader-change/54f34e52-b466-49f8-b525-230cd107148b

http://localhost:8082/api/ratis/trigger-leader-change/66472086-5a7b-461e-883e-2d4d4763e34d

http://localhost:8082/api/ratis/trigger-leader-change/36f06b8b-cd19-4c72-a9a6-2f37baf4a422

http://localhost:8082/api/ratis/trigger-leader-change/54f34e52-b466-49f8-b525-230cd107148b

```

# REST APIs to read from the Raft Storage
```
http://localhost:8080/api/migrations
http://localhost:8081/api/migrations
http://localhost:8082/api/migrations
```

# Troubleshooting
Kill old processes if something was not terminated properly, e.g.:
```
lsof -i :6000
kill 75605

# Force Kill
lsof -i :6000 | awk 'NR>1 {print $2}' | xargs kill -9
```

# Running the Experiments

Make sure that only the first started LDM sets up the database schema by setting these environment variables for Liquibase:

```
LIQUIBASE_MIGRATE_AT_START=true
LIQUIBASE_CLEAN_AT_START=true  # Drops the schema before migration
```

The other LDMs should have these variables set to `false`. Otherwise, inconsistencies might occur in the database.

<<<<<<< Updated upstream
# Accessing the React Dashboard

Access the dashboard to see the microservice graph with the migration statistics:
```
http://localhost:3000/graph
```
_The frontend will connect to this websocket of a LDM, e.g.: `localhost:8080/dashboard`_
=======
Furthermore, set `LEADER_ELECTION_MODE=DEFAULT` for a realistic scenario, where the leader election follows the Raft protocol. 
The `LEADER_ELECTION_MODE=TESTING` is just used for quicker testing of the LDM core functionalities, where the leader election always goes to the `DEFAULT_LEADER`.

## Experiment 1
### Setting:
- LEADER_ELECTION_MODE=DEFAULT (leader election follows the criteria of the Raft Protocol)

### Description:
-Best case scenario: All clusters are already optimal. Therefore, no migration is needed.

### Goal:
- Should not carry out any migration since all clusters are already optimal

## Experiment 2
### Setting:
- LEADER_ELECTION_MODE=DEFAULT (leader election follows the criteria of the Raft Protocol)

### Description:
- Tests if `ldm2` makes a proper migration proposal, which should be approved by the `ldm1` and `ldm3`

### Goal:
- `MS3` should be migrated from `ldm2` in `Berlin (36f06b8b-cd19-4c72-a9a6-2f37baf4a422)` to `ldm1` in `New York (54f34e52-b466-49f8-b525-230cd107148b}` since it has the highest affinity to that cluster

## Experiment 3
### Setting:
- LEADER_ELECTION_MODE=TESTING (leader election goes to the default leader) on `ldm1` and `ldm2`

### Description:
- Tests if `ldm3` makes a proper migration proposal, which should be approved by the `ldm1` and `ldm2`
- `LEADER_ELECTION_MODE` is set to `testing` for quicker testing since the `DEFAULT_LEADER` is always elected by `ldm1` and `ldm2`, primarily used to test the core functionalities of the ldm

### Goal:
- `MS10` should be migrated from `ldm3` in `Singapore (66472086-5a7b-461e-883e-2d4d4763e34d)` to `ldm2` in `Berlin (36f06b8b-cd19-4c72-a9a6-2f37baf4a422)` since it has the highest affinity to that cluster

## Experiment 4
### Setting:
- `LEADER_ELECTION_MODE=DEFAULT` (leader election follows the criteria of the Raft Protocol)

### Description:
- `MS3` and `MS10` should be migrated to the cluster where their affinity is highest

### Goal:
- `MS3` should be migrated from `ldm3` in `Singapore (66472086-5a7b-461e-883e-2d4d4763e34d)` to `ldm1` in `New York (54f34e52-b466-49f8-b525-230cd107148b}`
- `MS10` should be migrated from `ldm2` in `Berlin (36f06b8b-cd19-4c72-a9a6-2f37baf4a422)` to `ldm3` in `Singapore (66472086-5a7b-461e-883e-2d4d4763e34d)`
>>>>>>> Stashed changes


