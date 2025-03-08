# Tracing Guide

This guide explains how to use OpenTelemetry tracing with Temporal.

## Table of Contents
- [Setup](#setup)
- [Examples](#examples)
- [Context Propagation](#context-propagation)

## Setup

1. **Initialize Tracing**:
```java
// From SignozTelemetryUtils.java
public static synchronized void initializeTelemetry() {
    OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
        .setTracerProvider(TracingExporter.createTracerProvider())
        .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
        .build();
}
```

2. **Configure Worker**:
```java
WorkerFactoryOptions options = WorkerFactoryOptions.newBuilder()
    .setWorkerInterceptors(SignozTelemetryUtils.getWorkerInterceptor())
    .build();

WorkerFactory factory = WorkerFactory.newInstance(client, options);
```

3. **Configure Client**:
```java
WorkflowClientOptions clientOptions = WorkflowClientOptions.newBuilder()
    .setInterceptors(SignozTelemetryUtils.getClientInterceptor())
    .build();
```

## Examples

### Basic Span Creation

```java
// Get tracer
Tracer tracer = SignozTelemetryUtils.getTracer();

// Create and use spans
Span span = tracer.spanBuilder("process_payment")
    .setAttribute("payment_id", paymentId)
    .setAttribute("amount", amount)
    .startSpan();

try (Scope scope = span.makeCurrent()) {
    // Your business logic here
    processPayment(paymentId, amount);
} catch (Exception e) {
    span.recordException(e);
    span.setStatus(StatusCode.ERROR);
    throw e;
} finally {
    span.end();
}
```

### Nested Spans

```java
Span parentSpan = tracer.spanBuilder("parent_operation").startSpan();
try (Scope scope = parentSpan.makeCurrent()) {
    // Parent operation logic
    
    Span childSpan = tracer.spanBuilder("child_operation").startSpan();
    try (Scope childScope = childSpan.makeCurrent()) {
        // Child operation logic
    } finally {
        childSpan.end();
    }
} finally {
    parentSpan.end();
}
```

## Context Propagation

Temporal automatically propagates trace context:

1. **Between Activities**:
```java
@ActivityInterface
public interface PaymentActivity {
    @ActivityMethod
    void processPayment(String paymentId);
}

// Trace context is automatically propagated
@Override
public void processPayment(String paymentId) {
    // Current span is automatically created
    // Child spans will be properly parented
}
```

2. **Across Workflows**:
```java
// Parent workflow
@Override
public void parentWorkflow() {
    // Start child workflow
    ChildWorkflow child = workflowClient.newChildWorkflowStub(ChildWorkflow.class);
    child.run();  // Trace context is automatically propagated
}
```

3. **Through Signals**:
```java
// Signal method
@SignalMethod
public void handleSignal(String data) {
    // New span is created with proper context
    // from the signal sender
}
```

For implementation details, see [`TracingExporter.java`](../src/main/java/helloworld/config/TracingExporter.java). 