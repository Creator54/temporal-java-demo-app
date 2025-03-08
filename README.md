# Getting Started with Temporal Java Application

A minimal Temporal application demonstrating workflow orchestration with a simple Hello World example. This project shows how to set up a basic Temporal workflow that greets a user, with OpenTelemetry integration for metrics and tracing.

## Stack

* Java 8+
* [Temporal SDK](https://github.com/temporalio/sdk-java) for workflow orchestration
* [OpenTelemetry](https://opentelemetry.io/) for metrics and tracing
* Maven for dependency management
* SLF4J for logging

## Telemetry Dependencies

The following dependencies are used in this project for telemetry integration. Add these to your `pom.xml`:

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
    <dependency>
        <groupId>io.opentelemetry</groupId>
        <artifactId>opentelemetry-sdk</artifactId>
        <version>${opentelemetry.version}</version>
    </dependency>
    <dependency>
        <groupId>io.opentelemetry</groupId>
        <artifactId>opentelemetry-exporter-otlp</artifactId>
        <version>${opentelemetry.version}</version>
    </dependency>
    <dependency>
        <groupId>io.opentelemetry</groupId>
        <artifactId>opentelemetry-sdk-metrics</artifactId>
        <version>${opentelemetry.version}</version>
    </dependency>
    <dependency>
        <groupId>io.opentelemetry</groupId>
        <artifactId>opentelemetry-extension-trace-propagators</artifactId>
        <version>${opentelemetry.version}</version>
    </dependency>
    <dependency>
        <groupId>io.opentelemetry</groupId>
        <artifactId>opentelemetry-sdk-extension-autoconfigure</artifactId>
        <version>${opentelemetry.version}</version>
    </dependency>

    <!-- OpenTracing Dependencies -->
    <dependency>
        <groupId>io.temporal</groupId>
        <artifactId>temporal-opentracing</artifactId>
        <version>${temporal.version}</version>
    </dependency>
    <dependency>
        <groupId>io.opentelemetry</groupId>
        <artifactId>opentelemetry-opentracing-shim</artifactId>
        <version>${opentelemetry.version}</version>
    </dependency>
</dependencies>
```

### Implementation Structure

Our telemetry implementation is organized into these key files:

```
src/main/java/helloworld/config/
├── SignozTelemetryUtils.java     # Main entry point for telemetry
├── OpenTelemetryConfig.java      # Core configuration
├── MetricsExporter.java          # Metrics setup
└── TracingExporter.java          # Tracing setup
```

### Core Implementation

1. **SignozTelemetryUtils.java** - Main telemetry coordinator:
```java
public final class SignozTelemetryUtils {
    // Initialize telemetry
    public static synchronized void initializeTelemetry() {
        OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
            .setTracerProvider(TracingExporter.createTracerProvider())
            .setMeterProvider(MetricsExporter.createMeterProvider())
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
            .build();
        
        OpenTelemetryConfig.setOpenTelemetry(sdk);
    }

    // Get metrics scope for Temporal
    public static Scope getMetricsScope() {
        return MetricsExporter.getMetricsScope();
    }

    // Get interceptors for tracing
    public static WorkerInterceptor getWorkerInterceptor() {
        return TracingExporter.getWorkerInterceptor();
    }

    public static WorkflowClientInterceptor getClientInterceptor() {
        return TracingExporter.getClientInterceptor();
    }
}
```

2. **Worker Integration** - From HelloWorldWorker.java:
```java
public HelloWorldWorker() {
    // Initialize OpenTelemetry
    SignozTelemetryUtils.initializeTelemetry();

    // Configure service stubs with metrics
    WorkflowServiceStubsOptions stubOptions = WorkflowServiceStubsOptions.newBuilder()
        .setMetricsScope(SignozTelemetryUtils.getMetricsScope())
        .build();

    // Configure worker with tracing
    WorkerFactoryOptions factoryOptions = WorkerFactoryOptions.newBuilder()
        .setWorkerInterceptors(SignozTelemetryUtils.getWorkerInterceptor())
        .build();

    // Create factory with telemetry
    this.factory = WorkerFactory.newInstance(
        TemporalConfig.getWorkflowClient(stubOptions),
        factoryOptions
    );
}
```

3. **Workflow Starter Integration** - From HelloWorldStarter.java:
```java
public HelloWorldStarter() {
    // Initialize OpenTelemetry
    SignozTelemetryUtils.initializeTelemetry();

    // Configure service stubs with metrics
    WorkflowServiceStubsOptions stubOptions = WorkflowServiceStubsOptions.newBuilder()
        .setMetricsScope(SignozTelemetryUtils.getMetricsScope())
        .build();

    // Configure client with tracing
    WorkflowClientOptions clientOptions = WorkflowClientOptions.newBuilder()
        .setInterceptors(SignozTelemetryUtils.getClientInterceptor())
        .build();

    // Create client with telemetry
    this.client = TemporalConfig.getWorkflowClient(stubOptions, clientOptions);
}
```

### Environment Configuration

Required environment variables for telemetry:
```bash
# OpenTelemetry Collector Endpoint (SigNoz)
export OTEL_EXPORTER_OTLP_ENDPOINT="http://localhost:4317"
export OTEL_EXPORTER_OTLP_PROTOCOL="grpc"

# Service Information
export OTEL_SERVICE_NAME="temporal-hello-world"
export OTEL_RESOURCE_ATTRIBUTES="service.name=${OTEL_SERVICE_NAME},environment=development"
```

### Verification

1. **Check Metrics Export**:
   - Access SigNoz UI: http://localhost:3301
   - Navigate to Metrics → Service Metrics
   - Look for metrics with prefix `temporal_`

2. **Check Traces Export**:
   - Access SigNoz UI: http://localhost:3301
   - Navigate to Traces
   - Filter for service name "temporal-hello-world"

3. **Verify Integration**:
```bash
# Check if telemetry is initialized
tail -f logs/application.log | grep "OpenTelemetry"

# Verify collector connection
curl -v http://localhost:4317
```

## Project Structure

```
src/main/java/helloworld/
├── config/
│   ├── TemporalConfig.java           # Temporal client configuration
│   ├── OpenTelemetryConfig.java      # Core OpenTelemetry configuration
│   ├── MetricsExporter.java          # Metrics pipeline configuration
│   ├── TracingExporter.java          # Tracing pipeline configuration
│   └── SignozTelemetryUtils.java     # Telemetry initialization and coordination
├── workflows/
│   ├── HelloWorldWorkflow.java       # Workflow interface definition
│   └── impl/
│       └── HelloWorldWorkflowImpl.java # Workflow implementation
├── workers/
│   └── HelloWorldWorker.java         # Worker process to execute workflows
└── main/
    └── HelloWorldStarter.java        # Application entry point to start workflows
```

## Features

- **Workflow Orchestration**: Simple workflow demonstrating Temporal's capabilities
- **OpenTelemetry Integration**: 
  - Metrics export via OTLP/gRPC
  - Distributed tracing with OpenTracing compatibility
  - Ready to use with SigNoz or any OpenTelemetry collector
- **Cloud Ready**: Supports both local development and cloud deployment
- **Developer Friendly**: Modular design for easy customization

## Prerequisites

Before you begin, ensure you have:
* JDK 8+ installed
* Maven installed
* [Temporal CLI](https://github.com/temporalio/cli) installed for running Temporal Server
* (Optional) [SigNoz](https://signoz.io/) or another OpenTelemetry collector for metrics and traces

## Quick Start Guide

### 1. Start Required Services

a) **Start Temporal Server**:
```bash
temporal server start-dev --ui-port 8080
```

b) **Start SigNoz** (Optional - for telemetry):
Follow [SigNoz Quick Install](https://signoz.io/docs/install/docker/) or use any OpenTelemetry collector.

### 2. Configure Environment

```bash
# Required for cloud deployment
export TEMPORAL_HOST_ADDRESS=<your-namespace>.tmprl.cloud:7233  # Optional, defaults to localhost:7233
export TEMPORAL_NAMESPACE=<your-namespace>                      # Optional, defaults to "default"

# Required for telemetry
export OTEL_EXPORTER_OTLP_ENDPOINT="http://localhost:4317"     # SigNoz/OTel collector endpoint
export OTEL_SERVICE_NAME="temporal-hello-world"                # Your service name
```

### 3. Build and Run

```bash
# Build the project
mvn clean package

# Start everything with the convenience script
./start.sh

# Or run components separately:
# Terminal 1 - Start worker
mvn exec:java -Dexec.mainClass="helloworld.workers.HelloWorldWorker"

# Terminal 2 - Run workflow
mvn exec:java -Dexec.mainClass="helloworld.main.HelloWorldStarter" -Dexec.args="YourName"
```

### 4. View Results

- **Temporal UI**: http://localhost:8080
- **SigNoz UI**: http://localhost:3301 (if using SigNoz)

## Components

### 1. Workflow Interface
Defines the contract for the workflow ([`src/main/java/helloworld/workflows/HelloWorldWorkflow.java`](src/main/java/helloworld/workflows/HelloWorldWorkflow.java)):
```java
package helloworld.workflows;

@WorkflowInterface
public interface HelloWorldWorkflow {
    @WorkflowMethod
    String sayHello(String name);
}
```

### 2. Workflow Implementation
Contains the actual workflow logic ([`src/main/java/helloworld/workflows/impl/HelloWorldWorkflowImpl.java`](src/main/java/helloworld/workflows/impl/HelloWorldWorkflowImpl.java)):
```java
package helloworld.workflows.impl;

public class HelloWorldWorkflowImpl implements HelloWorldWorkflow {
    @Override
    public String sayHello(String name) {
        return "Hello " + name + "!";
    }
}
```

### 3. Worker
Hosts and executes the workflow ([`src/main/java/helloworld/workers/HelloWorldWorker.java`](src/main/java/helloworld/workers/HelloWorldWorker.java)):
```java
package helloworld.workers;

public class HelloWorldWorker {
    private final WorkerFactory factory;
    private final Worker worker;

    public HelloWorldWorker() {
        this.factory = WorkerFactory.newInstance(TemporalConfig.getWorkflowClient());
        this.worker = factory.newWorker(TemporalConfig.TASK_QUEUE_NAME);
        this.worker.registerWorkflowImplementationTypes(HelloWorldWorkflowImpl.class);
    }

    public void start() {
        factory.start();
        System.out.println("Worker started for task queue: " + TemporalConfig.TASK_QUEUE_NAME);
    }

    public static void main(String[] args) {
        HelloWorldWorker worker = new HelloWorldWorker();
        worker.start();
    }
}
```

### 4. Starter
Initiates the workflow execution ([`src/main/java/helloworld/main/HelloWorldStarter.java`](src/main/java/helloworld/main/HelloWorldStarter.java)):
```java
package helloworld.main;

public class HelloWorldStarter {
    private final WorkflowClient client;

    public HelloWorldStarter() {
        this.client = TemporalConfig.getWorkflowClient();
    }

    public void runWorkflow(String name) {
        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setTaskQueue(TemporalConfig.TASK_QUEUE_NAME)
                .setWorkflowId("hello-world-workflow-" + System.currentTimeMillis())
                .build();

        HelloWorldWorkflow workflow = client.newWorkflowStub(HelloWorldWorkflow.class, options);
        String result = workflow.sayHello(name);
        System.out.println(result);
        System.out.println("Workflow ID: " + options.getWorkflowId());
    }

    public static void main(String[] args) {
        HelloWorldStarter starter = new HelloWorldStarter();
        starter.runWorkflow(args.length > 0 ? args[0] : "Temporal");
    }
}
```

### 5. Configuration
- Temporal configuration ([`src/main/java/helloworld/config/TemporalConfig.java`](src/main/java/helloworld/config/TemporalConfig.java))
- OpenTelemetry metrics ([`src/main/java/helloworld/config/SignozMetricsUtils.java`](src/main/java/helloworld/config/SignozMetricsUtils.java))
- OpenTelemetry tracing ([`src/main/java/helloworld/config/SignozTracingUtils.java`](src/main/java/helloworld/config/SignozTracingUtils.java))

## Configuration Details

The application supports both local and cloud deployments through environment variables:

### Local Development (Default)
No environment variables needed. The application will use:
- Temporal Server: localhost:7233
- Namespace: "default"
- Task Queue: "hello-world-task-queue"
- OpenTelemetry Endpoint: http://localhost:4317

### Temporal Cloud Configuration
Set the following environment variables:
```bash
# Required for Temporal Cloud
export TEMPORAL_HOST_ADDRESS=<your-namespace>.tmprl.cloud:7233
export TEMPORAL_NAMESPACE=<your-namespace>
export TEMPORAL_CERT_PATH=/path/to/client.pem
export TEMPORAL_KEY_PATH=/path/to/client.key

# Optional
export TEMPORAL_TASK_QUEUE=custom-task-queue  # Defaults to "hello-world-task-queue"
```

### OpenTelemetry Configuration
Set the following environment variables to customize telemetry:
```bash
# OpenTelemetry Endpoint (default: http://localhost:4317)
export OTEL_EXPORTER_OTLP_ENDPOINT="http://your-collector:4317"

# Service Information
export OTEL_SERVICE_NAME="your-service-name"
export OTEL_RESOURCE_ATTRIBUTES="service.name=${OTEL_SERVICE_NAME},environment=production"

# Protocol Configuration
export OTEL_EXPORTER_OTLP_PROTOCOL=grpc
```

### Environment Variables Reference
| Variable | Description | Default |
|----------|-------------|---------|
| `TEMPORAL_HOST_ADDRESS` | Temporal server address | localhost:7233 |
| `TEMPORAL_NAMESPACE` | Temporal namespace | default |
| `TEMPORAL_TASK_QUEUE` | Task queue name | hello-world-task-queue |
| `TEMPORAL_CERT_PATH` | Path to client certificate (for Cloud) | - |
| `TEMPORAL_KEY_PATH` | Path to client private key (for Cloud) | - |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | OpenTelemetry collector endpoint | http://localhost:4317 |
| `OTEL_SERVICE_NAME` | Service name for telemetry | temporal-hello-world |
| `OTEL_RESOURCE_ATTRIBUTES` | Additional resource attributes | - |

### Example Usage

1. **Local Development**:
```bash
# No environment variables needed
./start.sh
```

2. **Temporal Cloud**:
```bash
# Set environment variables
export TEMPORAL_HOST_ADDRESS=your-namespace.tmprl.cloud:7233
export TEMPORAL_NAMESPACE=your-namespace
export TEMPORAL_CERT_PATH=/path/to/client.pem
export TEMPORAL_KEY_PATH=/path/to/client.key

# Run the application
./start.sh
```

3. **Custom OpenTelemetry Configuration**:
```bash
# Set OpenTelemetry variables
export OTEL_EXPORTER_OTLP_ENDPOINT="http://your-collector:4317"
export OTEL_SERVICE_NAME="custom-service-name"
export OTEL_RESOURCE_ATTRIBUTES="service.name=${OTEL_SERVICE_NAME},environment=staging"

# Run the application
./start.sh
```

## Monitoring and Observability

### Temporal Web UI
- Access the Temporal Web UI at http://localhost:8080
- View workflow executions, history, and status
- Debug workflow issues

### Metrics and Tracing (with SigNoz)
- Access the SigNoz UI (default: http://localhost:3301)
- View metrics:
  - Workflow execution metrics
  - Worker metrics
  - Task queue metrics
- View traces:
  - Workflow execution traces
  - Activity traces
  - End-to-end request flows

## Troubleshooting

1. **Temporal Server Issues**
   - Verify server is running: `temporal operator cluster health`
   - Check server logs: Look for errors in the terminal running the server
   - Ensure port 7233 is not in use by another application

2. **Worker Issues**
   - Ensure worker is running before starting workflows
   - Verify task queue name matches in both worker and starter
   - Check worker logs for registration success message

3. **Workflow Issues**
   - Check workflow execution history in Temporal UI (http://localhost:8080)
   - Verify workflow ID in the UI matches the printed ID
   - Look for any error messages in the starter output

4. **OpenTelemetry Issues**
   - Verify the collector is running and accessible
   - Check collector logs for connection issues
   - Ensure the OTLP endpoint is correctly configured
   - Look for any error messages in the application logs

## Additional Resources

* [Temporal Documentation](https://docs.temporal.io/)
* [Temporal Java SDK](https://github.com/temporalio/sdk-java)
* [Temporal Java Samples](https://github.com/temporalio/samples-java)
* [OpenTelemetry Documentation](https://opentelemetry.io/docs/)
* [SigNoz Documentation](https://signoz.io/docs/)

## Telemetry Integration Guide

### Architecture Overview

This project demonstrates how to export Temporal telemetry data using OpenTelemetry:

```
┌─────────────────────────────────────────────────────────┐
│                  Your Temporal Application              │
├───────────────────────────┬─────────────────────────────┤
│      Workflow Worker      │      Workflow Starter       │
├───────────────────────────┴─────────────────────────────┤
│                  SignozTelemetryUtils                   │
├─────────────────┬──────────────────────┬────────────────┤
│ OpenTelemetry   │    MetricsExporter   │ TracingExporter│
│    Config       │    (OTLP/gRPC)       │  (OTLP/gRPC)   │
└─────────────────┴──────────────────────┴────────────────┘
                           │
                           ▼
                 ┌─────────────────────┐
                 │   OTel Collector    │
                 │     (SigNoz)        │
                 └─────────────────────┘
```

### Exporting Metrics

Temporal metrics are exported through two channels:
1. **Temporal Internal Metrics**: Worker, workflow, and task queue metrics
2. **Custom Business Metrics**: Your application-specific metrics

#### 1. Setup Temporal Metrics

```java
// Initialize telemetry first
SignozTelemetryUtils.initializeTelemetry();

// Configure Temporal client with metrics
WorkflowServiceStubsOptions stubOptions = WorkflowServiceStubsOptions.newBuilder()
    .setMetricsScope(SignozTelemetryUtils.getMetricsScope())
    .build();

// Create client with metrics enabled
WorkflowClient client = TemporalConfig.getWorkflowClient(stubOptions);
```

#### 2. Add Custom Metrics

```java
// Get meter for your component
Meter meter = SignozTelemetryUtils.getMeter();

// Create counter
LongCounter workflowCounter = meter.counterBuilder("workflow_executions")
    .setDescription("Number of workflow executions")
    .setUnit("1")
    .build();

// Use in your code
workflowCounter.add(1, Attributes.of(
    AttributeKey.stringKey("workflow_type"), "HelloWorld",
    AttributeKey.stringKey("status"), "completed"
));
```

#### Available Temporal Metrics

- **Worker Metrics**:
  - `temporal_worker_task_slots_available`
  - `temporal_worker_tasks_completed`
  - `temporal_worker_tasks_failed`

- **Workflow Metrics**:
  - `temporal_workflow_completed`
  - `temporal_workflow_failed`
  - `temporal_workflow_execution_latency`

- **Task Queue Metrics**:
  - `temporal_task_queue_depth`
  - `temporal_task_queue_processing_latency`

### Exporting Traces

Temporal traces provide end-to-end visibility of workflow executions:
1. **Workflow Traces**: Execution flow and duration
2. **Activity Traces**: Individual task execution
3. **Cross-Workflow Traces**: Dependencies between workflows

#### 1. Setup Temporal Tracing

```java
// Initialize telemetry first
SignozTelemetryUtils.initializeTelemetry();

// Configure worker with tracing
WorkerFactoryOptions options = WorkerFactoryOptions.newBuilder()
    .setWorkerInterceptors(SignozTelemetryUtils.getWorkerInterceptor())
    .build();

// Configure client with tracing
WorkflowClientOptions clientOptions = WorkflowClientOptions.newBuilder()
    .setInterceptors(SignozTelemetryUtils.getClientInterceptor())
    .build();
```

#### 2. Add Custom Traces

```java
// Get tracer for your component
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

#### Trace Context Propagation

Temporal automatically propagates trace context:
- Between workflow and activities
- Across workflow boundaries
- Through signals and queries

### Configuration

#### 1. Environment Setup

```bash
# OpenTelemetry Endpoint
export OTEL_EXPORTER_OTLP_ENDPOINT="http://localhost:4317"

# Service Information
export OTEL_SERVICE_NAME="temporal-hello-world"
export OTEL_RESOURCE_ATTRIBUTES="service.name=${OTEL_SERVICE_NAME},environment=production"

# Optional: Debug Mode
export OTEL_LOG_LEVEL=debug
```

#### 2. Resource Attributes

Add custom attributes to all telemetry data:

```java
Resource resource = Resource.create(Attributes.of(
    AttributeKey.stringKey("deployment.region"), "us-west-2",
    AttributeKey.stringKey("service.version"), "1.0.0",
    AttributeKey.stringKey("service.namespace"), "payment"
));
```

### Viewing Telemetry Data

#### 1. Metrics in SigNoz

- Access: http://localhost:3301
- Navigate: Metrics → Service Metrics
- Available Panels:
  - Workflow Execution Rate
  - Worker Task Queue Depth
  - Activity Success/Failure Rate
  - Custom Business Metrics

#### 2. Traces in SigNoz

- Access: http://localhost:3301
- Navigate: Traces
- Features:
  - Workflow Execution Timeline
  - Activity Breakdown
  - Error Analysis
  - Trace Search and Filtering

### Best Practices

1. **Initialization**:
   - Initialize telemetry before creating Temporal clients
   - Initialize in both worker and starter processes
   - Use environment variables for configuration

2. **Metrics**:
   - Use meaningful metric names and descriptions
   - Add relevant attributes for filtering
   - Monitor both system and business metrics

3. **Traces**:
   - Add business context as span attributes
   - Record errors and exceptions
   - Use appropriate sampling rates

4. **Resource Usage**:
   - Configure appropriate batch sizes
   - Set reasonable export intervals
   - Monitor collector performance

### Troubleshooting

1. **No Data in SigNoz**:
```bash
# Check collector status
curl -v http://localhost:4317

# Enable debug logging
export OTEL_LOG_LEVEL=debug
```

2. **Missing Metrics**:
- Verify MetricsScope is set in WorkflowServiceStubsOptions
- Check metric names in SigNoz query builder
- Verify collector is receiving data

3. **Missing Traces**:
- Ensure interceptors are properly configured
- Check sampling configuration
- Verify trace context propagation

4. **Performance Issues**:
```java
// Adjust batch settings in TracingExporter
BatchSpanProcessor.builder(spanExporter)
    .setMaxQueueSize(2048)
    .setMaxExportBatchSize(512)
    .setScheduleDelay(Duration.ofMillis(100))
    .build();

// Adjust metric export interval
PeriodicMetricReader.builder(metricExporter)
    .setInterval(Duration.ofSeconds(5))
    .build();
```