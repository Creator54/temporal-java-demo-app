# Telemetry Integration Guide

This guide explains how to use OpenTelemetry with Temporal for metrics and tracing.

## Table of Contents
- [Dependencies](#dependencies)
- [Implementation](#implementation)
- [Configuration](#configuration)
- [Usage Examples](#usage-examples)

## Dependencies

Add these dependencies to your `pom.xml`:

```xml
<properties>
    <opentelemetry.version>1.34.1</opentelemetry.version>
    <temporal.version>1.24.1</temporal.version>
</properties>

<dependencies>
    <!-- Temporal Dependencies -->
    <dependency>
        <groupId>io.temporal</groupId>
        <artifactId>temporal-sdk</artifactId>
        <version>${temporal.version}</version>
    </dependency>

    <!-- OpenTelemetry Dependencies -->
    <dependency>
        <groupId>io.opentelemetry</groupId>
        <artifactId>opentelemetry-api</artifactId>
        <version>${opentelemetry.version}</version>
    </dependency>
    <!-- ... rest of dependencies ... -->
</dependencies>
```

For complete dependencies, see [`pom.xml`](../pom.xml).

## Implementation

Our telemetry implementation consists of these key components:

- [`SignozTelemetryUtils.java`](../src/main/java/helloworld/config/SignozTelemetryUtils.java) - Main entry point
- [`OpenTelemetryConfig.java`](../src/main/java/helloworld/config/OpenTelemetryConfig.java) - Core configuration
- [`MetricsExporter.java`](../src/main/java/helloworld/config/MetricsExporter.java) - Metrics pipeline
- [`TracingExporter.java`](../src/main/java/helloworld/config/TracingExporter.java) - Tracing pipeline

### Architecture

```
┌─────────────────────────────────────────────────────────┐
│                  Your Temporal Application               │
├───────────────────────────┬─────────────────────────────┤
│      Workflow Worker      │      Workflow Starter       │
├───────────────────────────┴─────────────────────────────┤
│                  SignozTelemetryUtils                   │
├─────────────────┬──────────────────────┬───────────────┤
│ OpenTelemetry   │    MetricsExporter   │ TracingExporter│
│    Config       │    (OTLP/gRPC)       │  (OTLP/gRPC)  │
└─────────────────┴──────────────────────┴───────────────┘
                           │
                           ▼
                 ┌─────────────────────┐
                 │   OTel Collector    │
                 │     (SigNoz)        │
                 └─────────────────────┘
```

For detailed implementation examples, see:
- [Metrics Guide](metrics.md)
- [Tracing Guide](tracing.md)

## Configuration

### Environment Variables

```bash
# OpenTelemetry Collector Endpoint (SigNoz)
export OTEL_EXPORTER_OTLP_ENDPOINT="http://localhost:4317"
export OTEL_EXPORTER_OTLP_PROTOCOL="grpc"

# Service Information
export OTEL_SERVICE_NAME="temporal-hello-world"
export OTEL_RESOURCE_ATTRIBUTES="service.name=${OTEL_SERVICE_NAME},environment=development"
```

## Usage Examples

### Basic Integration

```java
// Initialize telemetry
SignozTelemetryUtils.initializeTelemetry();

// Configure service stubs with metrics
WorkflowServiceStubsOptions stubOptions = WorkflowServiceStubsOptions.newBuilder()
    .setMetricsScope(SignozTelemetryUtils.getMetricsScope())
    .build();

// Configure worker with tracing
WorkerFactoryOptions factoryOptions = WorkerFactoryOptions.newBuilder()
    .setWorkerInterceptors(SignozTelemetryUtils.getWorkerInterceptor())
    .build();
```

For more examples, see:
- [Metrics Examples](metrics.md#examples)
- [Tracing Examples](tracing.md#examples) 