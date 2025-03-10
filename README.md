# Temporal Java Hello World with OpenTelemetry

A minimal Temporal application demonstrating workflow orchestration with OpenTelemetry integration for metrics and tracing.

## Documentation

- [Telemetry Integration Guide](docs/telemetry.md)
  - [Metrics Guide](docs/metrics.md)
  - [Tracing Guide](docs/tracing.md)

## Prerequisites

- Java 11 or later
- Maven
- [Temporal CLI](https://docs.temporal.io/cli) (for local development)
- [SigNoz](https://signoz.io/) (for metrics and tracing)

## Configuration

The application can be configured to work with either a local or cloud setup for both Temporal and SigNoz.

### Temporal Configuration

#### Local Development Setup

1. Start the Temporal Server locally:
```bash
temporal server start-dev --ui-port 8080
```

2. Run the application:
```bash
bash start.sh
```

The application will automatically connect to the local Temporal server at `localhost:7233`.

#### Temporal Cloud Setup

To use Temporal Cloud, set the following environment variables:

```bash
# Required for Temporal Cloud
export TEMPORAL_HOST_URL=<your-namespace>.tmprl.cloud:7233
export TEMPORAL_NAMESPACE=<your-namespace>
export TEMPORAL_TLS_CERT=<path-to-certificate.pem>
export TEMPORAL_TLS_KEY=<path-to-private-key.key>

# Optional
export TEMPORAL_TASK_QUEUE=<your-task-queue-name>  # Defaults to "hello-world-task-queue"
```

Example:
```bash
export TEMPORAL_HOST_URL=default.jq7hr.tmprl.cloud:7233
export TEMPORAL_NAMESPACE=default.jq7hr
export TEMPORAL_TLS_CERT=ca.pem
export TEMPORAL_TLS_KEY=ca.key
bash start.sh
```

### SigNoz Configuration

#### Self-hosted SigNoz Setup

1. Setup SigNoz and configure OpenTelemetry endpoint:
```bash
export OTEL_EXPORTER_OTLP_ENDPOINT="http://localhost:4317"
export OTEL_RESOURCE_ATTRIBUTES="service.name=temporal-hello-world,environment=development"
```

Access SigNoz UI at: http://localhost:3301

#### SigNoz Cloud Setup

1. Sign up for [SigNoz Cloud](https://signoz.io/teams/)

2. Configure OpenTelemetry with your cloud endpoint and token:
```bash
export OTEL_EXPORTER_OTLP_ENDPOINT="https://<your-ingestion-endpoint>"
export OTEL_EXPORTER_OTLP_HEADERS="signoz-ingestion-key=<your-token>"
export OTEL_RESOURCE_ATTRIBUTES="service.name=temporal-hello-world"
```

## Configuration Priority

### Temporal Priority
1. If `TEMPORAL_HOST_URL`, `TEMPORAL_TLS_CERT`, and `TEMPORAL_TLS_KEY` are set, it will connect to Temporal Cloud
2. If no environment variables are set, it will connect to local Temporal server at `localhost:7233`

### SigNoz Priority
1. If `OTEL_EXPORTER_OTLP_HEADERS` is set with access token, it will use SigNoz Cloud
2. If only `OTEL_EXPORTER_OTLP_ENDPOINT` is set, it will use that endpoint
3. If no variables are set, it will default to local endpoint (`http://localhost:4317`)

## OpenTelemetry Integration

The application includes OpenTelemetry instrumentation for observability. For detailed information, see:
- [Telemetry Integration Guide](docs/telemetry.md) - Overview of telemetry setup
- [Metrics Guide](docs/metrics.md) - Detailed metrics configuration
- [Tracing Guide](docs/tracing.md) - Tracing setup and usage

Quick start:
- Traces and metrics are exported to SigNoz via OpenTelemetry
- Default endpoint: `http://localhost:4317` (self-hosted SigNoz)
- Configure using environment variables shown in SigNoz Configuration section

## Project Structure

```
.
├── docs/
│   ├── telemetry.md                # Telemetry integration guide
│   ├── metrics.md                  # Metrics configuration guide
│   └── tracing.md                  # Tracing setup guide
├── src/
│   └── main/
│       └── java/
│           └── helloworld/
│               ├── workflows/                       # Workflow definitions
│               └── workers/                         # Worker implementations
│               └── config/                          # Configuration classes
│               |    ├── TemporalConfig.java         # Temporal setup
│               |    ├── SignozTelemetryUtils.java   # Telemetry coordinator
│               |    ├── OpenTelemetryConfig.java    # OTel configuration
│               |    ├── MetricsExporter.java        # Metrics setup
│               |    └── TracingExporter.java        # Tracing setup
│               └── main/                            # Main application classes
├── start.sh                                         # Startup script
└── README.md                                        # This file
```

## Development

### Building the Project

```bash
mvn clean package
```

### Running the Worker

```bash
mvn exec:java -Dexec.mainClass="helloworld.workers.HelloWorldWorker"
```

### Starting a Workflow

```bash
mvn exec:java -Dexec.mainClass="helloworld.main.HelloWorldStarter"
```

## Troubleshooting

1. **Connection Issues**
   - For local development: Ensure Temporal Server is running (`temporal server start-dev`)
   - For Temporal Cloud: Verify all environment variables are set correctly

2. **Certificate Issues**
   - Ensure certificate files exist and are readable
   - Verify certificate format (should be PKCS8)
   - Check certificate permissions

3. **Namespace Issues**
   - For local: Default namespace is "default"
   - For Cloud: Use the full namespace from your Temporal Cloud console

4. **Telemetry Issues**
   - Check the [Telemetry Guide](docs/telemetry.md) for detailed troubleshooting
   - Verify OpenTelemetry collector configuration
   - Review [Metrics](docs/metrics.md) and [Tracing](docs/tracing.md) guides
   - For SigNoz Cloud: Verify your access token and ingestion endpoint
   - For self-hosted SigNoz: Ensure Docker containers are running (`docker ps`)

## Additional Resources

- [Temporal Java SDK Documentation](https://docs.temporal.io/dev-guide/java)
- [Temporal Cloud Documentation](https://docs.temporal.io/cloud)
- [OpenTelemetry Documentation](https://opentelemetry.io/docs/)
- [SigNoz Documentation](https://signoz.io/docs/)
- [SigNoz Cloud Getting Started](https://signoz.io/docs/cloud/getting-started/)