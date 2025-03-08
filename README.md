# Getting Started with Temporal Java Application

A minimal Temporal application demonstrating workflow orchestration with a simple Hello World example. This project shows how to set up a basic Temporal workflow that greets a user, with OpenTelemetry integration for metrics and tracing.

## Stack

* Java 8+
* [Temporal SDK](https://github.com/temporalio/sdk-java) for workflow orchestration
* [OpenTelemetry](https://opentelemetry.io/) for metrics and tracing
* Maven for dependency management
* SLF4J for logging

## Project Structure

```
src/main/java/helloworld/
├── config/
│   ├── TemporalConfig.java           # Temporal client configuration
│   ├── SignozMetricsUtils.java       # OpenTelemetry metrics configuration
│   └── SignozTracingUtils.java       # OpenTelemetry tracing configuration
├── workflows/
│   ├── HelloWorldWorkflow.java       # Workflow interface definition
│   └── impl/
│       └── HelloWorldWorkflowImpl.java # Workflow implementation
├── workers/
│   └── HelloWorldWorker.java         # Worker process to execute workflows
└── main/
    └── HelloWorldStarter.java        # Application entry point to start workflows
```

## Prerequisites

Before you begin, ensure you have:
* JDK 8+ installed
* Maven installed
* [Temporal CLI](https://github.com/temporalio/cli) installed for running Temporal Server
* (Optional) [SigNoz](https://signoz.io/) or another OpenTelemetry collector for metrics and traces

## Quick Start

### 1. Install Temporal CLI

Install the Temporal CLI by following the instructions in the [Temporal CLI Repository](https://github.com/temporalio/cli#quick-install).

### 2. Start Temporal Server

Start the Temporal development server with Web UI:

```bash
temporal server start-dev --ui-port 8080
```

This command starts:
- Temporal Server on port 7233 (default)
- Web Interface on port 8080 - Access at http://localhost:8080

Verify the server is running:
```bash
temporal operator cluster health
```

### 3. (Optional) Start SigNoz or OpenTelemetry Collector

If you want to collect metrics and traces, you'll need a running instance of SigNoz or another OpenTelemetry collector. The default configuration expects the collector to be running at `http://localhost:4317`.

For SigNoz, follow their [installation guide](https://signoz.io/docs/install/).

### 4. Build the Project

```bash
mvn clean package
```

### 5. Run the Application

The application requires two components to be running:

1. **Start the Worker** (in one terminal):
```bash
mvn exec:java -Dexec.mainClass="helloworld.workers.HelloWorldWorker"
```
This starts a worker that listens for workflow tasks on the "hello-world-task-queue".

2. **Execute the Workflow** (in another terminal):
```bash
# Basic execution with default name "Temporal"
mvn exec:java -Dexec.mainClass="helloworld.main.HelloWorldStarter"

# Or with a custom name
mvn exec:java -Dexec.mainClass="helloworld.main.HelloWorldStarter" -Dexec.args="YourName"
```

Alternatively, use the provided start script that handles both components:
```bash
./start.sh
```

Expected Output:
- Worker terminal: "Worker started for task queue: hello-world-task-queue"
- Workflow terminal: 
  ```
  Hello YourName!
  Workflow ID: hello-world-workflow-{timestamp}
  Metrics are being exported to SigNoz
  ```

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