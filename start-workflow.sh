#!/bin/bash

# OpenTelemetry Configuration
export OTEL_EXPORTER_OTLP_ENDPOINT="http://localhost:4317"
export OTEL_RESOURCE_ATTRIBUTES="service.name=temporal-hello-world"

echo "Starting workflow..."
mvn exec:java \
    -Dexec.mainClass="helloworld.main.HelloWorldStarter" \
    -Dorg.slf4j.simpleLogger.defaultLogLevel=info \
    -Dotel.java.global-autoconfigure.enabled=true

echo "Workflow completed." 