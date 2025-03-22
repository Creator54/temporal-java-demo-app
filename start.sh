#!/bin/bash

# Function to cleanup processes
cleanup() {
    echo -e "\nCleaning up processes..."
    pkill -f "helloworld.workers.HelloWorldWorker" 2>/dev/null || true
    exit 0
}

# Set up trap for Ctrl+C (SIGINT) and SIGTERM
trap cleanup SIGINT SIGTERM

# Function to check if a port is open
check_port() {
    local host=$1
    local port=$2
    local retries=$3
    local wait_time=$4
    local count=0

    while [ $count -lt $retries ]; do
        nc -z $host $port > /dev/null 2>&1
        if [ $? -eq 0 ]; then
            return 0
        fi
        echo "Attempt $((count + 1))/$retries: Waiting for $host:$port..."
        sleep $wait_time
        count=$((count + 1))
    done
    return 1
}

# OpenTelemetry Configuration
export OTEL_EXPORTER_OTLP_ENDPOINT="http://localhost:4317"
export OTEL_RESOURCE_ATTRIBUTES="service.name=temporal-hello-world"

echo "Starting application..."

# Check Temporal Server
echo "Checking Temporal Server..."
if ! check_port localhost 7233 3 2; then
    echo "ERROR: Temporal Server is not running. Please start it first."
    exit 1
fi

# Check SigNoz/OpenTelemetry Collector
echo "Checking SigNoz/OpenTelemetry Collector..."
if ! check_port localhost 4317 3 2; then
    echo "WARNING: SigNoz/OpenTelemetry Collector is not running. Metrics and traces will not be exported."
fi

# Clean existing processes
echo "Cleaning up existing processes..."
pkill -f "helloworld.workers.HelloWorldWorker" 2>/dev/null || true
sleep 2

# Build and run
echo "Building application..."
mvn clean package
mvn dependency:copy-dependencies -DoutputDirectory=target/dependency

# Start the worker and keep it running
echo "Starting worker..."
echo "Press Ctrl+C to stop the worker"

# Run using the jar file directly
java -cp target/hello-world-1.0.0.jar:target/classes:target/dependency/* \
    -Dorg.slf4j.simpleLogger.defaultLogLevel=info \
    -Dotel.java.global-autoconfigure.enabled=true \
    helloworld.workers.HelloWorldWorker