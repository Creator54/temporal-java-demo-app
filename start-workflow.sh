#!/bin/bash

# OpenTelemetry Configuration
export OTEL_EXPORTER_OTLP_ENDPOINT="http://localhost:4317"
export OTEL_RESOURCE_ATTRIBUTES="service.name=temporal-hello-world"

echo "Starting workflow..."
# First ensure we have a current build and dependencies
mvn clean package
mvn dependency:copy-dependencies -DoutputDirectory=target/dependency

# Run using the jar file directly with the classpath
java -cp target/hello-world-1.0.0.jar:target/classes:target/dependency/* \
    -Dorg.slf4j.simpleLogger.defaultLogLevel=info \
    -Dotel.java.global-autoconfigure.enabled=true \
    helloworld.main.HelloWorldStarter

echo "Workflow completed." 