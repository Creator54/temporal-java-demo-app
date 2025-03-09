# Temporal Java Hello World with OpenTelemetry

A minimal Temporal application demonstrating workflow orchestration with OpenTelemetry integration for metrics and tracing.

## Documentation

- [Telemetry Integration Guide](docs/telemetry.md)
  - [Metrics Guide](docs/metrics.md)
  - [Tracing Guide](docs/tracing.md)

## Quick Start

1. **Prerequisites**:
   - JDK 8+
   - Maven
   - [Temporal CLI](https://github.com/temporalio/cli)
   - (Optional) [SigNoz](https://signoz.io/) for telemetry

2. **Start Services**:
```bash
# Start Temporal
temporal server start-dev --ui-port 8080

# Start SigNoz (optional)
# Follow https://signoz.io/docs/install/docker/
```

3. **Configure**:
```bash
# Required for telemetry
export OTEL_EXPORTER_OTLP_ENDPOINT="http://localhost:4317"
export OTEL_RESOURCE_ATTRIBUTES="service.name=temporal-hello-world,environment=development"
```

4. **Run**:
```bash
# Build and start
./start.sh

# Or run components separately:
mvn exec:java -Dexec.mainClass="helloworld.workers.HelloWorldWorker"
mvn exec:java -Dexec.mainClass="helloworld.main.HelloWorldStarter"
```

5. **View**:
- Temporal UI: http://localhost:8080
- SigNoz UI: http://localhost:3301

## Project Structure

```
src/main/java/helloworld/
├── config/                          # Configuration
│   ├── TemporalConfig.java         # Temporal setup
│   ├── SignozTelemetryUtils.java   # Telemetry coordinator
│   ├── OpenTelemetryConfig.java    # OTel configuration
│   ├── MetricsExporter.java        # Metrics setup
│   └── TracingExporter.java        # Tracing setup
├── workflows/                       # Workflow definitions
├── workers/                        # Worker implementation
└── main/                          # Application entry points
```

## Key Components

- [`HelloWorldWorkflow.java`](src/main/java/helloworld/workflows/HelloWorldWorkflow.java) - Workflow interface
- [`HelloWorldWorker.java`](src/main/java/helloworld/workers/HelloWorldWorker.java) - Worker process
- [`HelloWorldStarter.java`](src/main/java/helloworld/main/HelloWorldStarter.java) - Workflow starter
- [`SignozTelemetryUtils.java`](src/main/java/helloworld/config/SignozTelemetryUtils.java) - Telemetry setup

## Features

- ✨ Simple workflow demonstration
- 📊 OpenTelemetry metrics export
- 🔍 Distributed tracing
- ☁️ Cloud-ready configuration
- 🛠️ Developer-friendly setup

## Additional Resources

- [Temporal Documentation](https://docs.temporal.io/)
- [OpenTelemetry Documentation](https://opentelemetry.io/docs/)
- [SigNoz Documentation](https://signoz.io/docs/)