# Metrics Guide

This guide explains how to use OpenTelemetry metrics with Temporal.

## Table of Contents
- [Available Metrics](#available-metrics)
- [Setup](#setup)
- [Examples](#examples)

## Available Metrics

### Worker Metrics
- `temporal_worker_task_slots_available`
- `temporal_worker_tasks_completed`
- `temporal_worker_tasks_failed`

### Workflow Metrics
- `temporal_workflow_completed`
- `temporal_workflow_failed`
- `temporal_workflow_execution_latency`

### Task Queue Metrics
- `temporal_task_queue_depth`
- `temporal_task_queue_processing_latency`

## Setup

1. **Initialize Metrics**:
```java
// From SignozTelemetryUtils.java
public static synchronized void initializeTelemetry() {
    OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
        .setMeterProvider(MetricsExporter.createMeterProvider())
        // ... other configuration ...
        .build();
}
```

2. **Configure Client**:
```java
WorkflowServiceStubsOptions stubOptions = WorkflowServiceStubsOptions.newBuilder()
    .setMetricsScope(SignozTelemetryUtils.getMetricsScope())
    .build();

WorkflowClient client = TemporalConfig.getWorkflowClient(stubOptions);
```

## Examples

### Custom Metrics

1. **Counter Example**:
```java
// Get meter
Meter meter = SignozTelemetryUtils.getMeter();

// Create counter
LongCounter workflowCounter = meter.counterBuilder("workflow_executions")
    .setDescription("Number of workflow executions")
    .setUnit("1")
    .build();

// Use counter
workflowCounter.add(1, Attributes.of(
    AttributeKey.stringKey("workflow_type"), "HelloWorld",
    AttributeKey.stringKey("status"), "completed"
));
```

2. **Gauge Example**:
```java
// Create gauge
LongUpDownCounter queueSize = meter.upDownCounterBuilder("queue_size")
    .setDescription("Current queue size")
    .setUnit("1")
    .build();

// Update gauge
queueSize.add(1); // Increment
queueSize.add(-1); // Decrement
```

### Viewing Metrics

Access metrics in SigNoz:
1. Open http://localhost:3301
2. Navigate to Metrics → Service Metrics
3. Filter for metrics with prefix `temporal_`

For implementation details, see [`MetricsExporter.java`](../src/main/java/helloworld/config/MetricsExporter.java). 