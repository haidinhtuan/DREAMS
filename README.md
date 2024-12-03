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

## Compiling Protobuf files

Run the following command to generate the protoc command:
```
protoc -I=. --java_out=. migration_action.proto
protoc -I=. --java_out=. migration_action.proto
protoc -I=src/main/proto/com/ldm/infrastructure/serialization --java_out=src/main/java src/main/proto/com/ldm/infrastructure/serialization/migration_action.proto
```


# REST APIs to trigger a leadership change
The leader change has to be triggered on the current leader, e.g.:
```
http://localhost:8080/api/ratis/trigger-leader-change
http://localhost:8081/api/ratis/trigger-leader-change
http://localhost:8082/api/ratis/trigger-leader-change
```

# Troubleshooting
Kill old processes if something was not terminated properly, e.g.:
```
lsof -i :6000
kill 75605

# Force Kill
lsof -i :6000 | awk 'NR>1 {print $2}' | xargs kill -9
```
