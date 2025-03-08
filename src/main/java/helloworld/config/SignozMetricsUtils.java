package helloworld.config;

import com.google.common.base.Splitter;
import com.uber.m3.tally.RootScopeBuilder;
import com.uber.m3.tally.Scope;
import com.uber.m3.util.Duration;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.temporal.common.reporter.MicrometerClientStatsReporter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SignozMetricsUtils {
    private static final Logger logger = Logger.getLogger(SignozMetricsUtils.class.getName());
    private static final String DEFAULT_SIGNOZ_ENDPOINT = "http://localhost:4317";
    private static final String DEFAULT_SERVICE_NAME = "temporal-hello-world";

    // Define attribute keys
    private static final AttributeKey<String> SERVICE_NAME = AttributeKey.stringKey("service.name");
    private static final AttributeKey<String> SERVICE_NAMESPACE = AttributeKey.stringKey("service.namespace");
    private static final AttributeKey<String> DEPLOYMENT_ENVIRONMENT = AttributeKey.stringKey("deployment.environment");

    public static Scope getSignozMetricsScope() {
        try {
            String endpoint = System.getenv().getOrDefault("OTEL_EXPORTER_OTLP_ENDPOINT", DEFAULT_SIGNOZ_ENDPOINT);
            logger.info("Configuring OpenTelemetry Metrics with endpoint: " + endpoint);

            // Set system properties for OTLP configuration
            System.setProperty("otel.metrics.exporter", "otlp");
            System.setProperty("otel.exporter.otlp.protocol", "grpc");
            System.setProperty("otel.exporter.otlp.endpoint", endpoint);
            System.setProperty("otel.traces.exporter", "none");
            System.setProperty("otel.logs.exporter", "none");
            System.setProperty("otel.metric.export.interval", "1000");
            System.setProperty("otel.exporter.otlp.metrics.temporality.preference", "delta");

            // Log all relevant environment variables and properties
            logger.info("OTEL_METRICS_EXPORTER: " + System.getProperty("otel.metrics.exporter"));
            logger.info("OTEL_EXPORTER_OTLP_PROTOCOL: " + System.getProperty("otel.exporter.otlp.protocol"));
            logger.info("OTEL_EXPORTER_OTLP_ENDPOINT: " + System.getProperty("otel.exporter.otlp.endpoint"));
            logger.info("OTEL_RESOURCE_ATTRIBUTES: " + System.getenv().getOrDefault("OTEL_RESOURCE_ATTRIBUTES", "not set"));

            // Get resource attributes from environment or use defaults
            String resourceAttrs = System.getenv().getOrDefault("OTEL_RESOURCE_ATTRIBUTES",
                    "service.name=" + DEFAULT_SERVICE_NAME + ",environment=development");

            logger.info("Using resource attributes: " + resourceAttrs);

            // Create base resource with service name and required attributes
            Resource baseResource = Resource.getDefault();
            Resource customResource = Resource.create(Attributes.of(
                SERVICE_NAME, DEFAULT_SERVICE_NAME,
                SERVICE_NAMESPACE, "default",
                DEPLOYMENT_ENVIRONMENT, "development"
            ));

            Resource resource = baseResource.merge(customResource);

            // Parse additional resource attributes
            for (String attr : Splitter.on(',').split(resourceAttrs)) {
                List<String> keyValue = Splitter.on('=').splitToList(attr);
                if (keyValue.size() == 2) {
                    String key = keyValue.get(0).trim();
                    String value = keyValue.get(1).trim();

                    if (key.equals("service.name")) {
                        resource = resource.merge(Resource.create(Attributes.of(SERVICE_NAME, value)));
                    } else if (key.equals("environment")) {
                        resource = resource.merge(Resource.create(Attributes.of(DEPLOYMENT_ENVIRONMENT, value)));
                    }
                }
            }

            logger.info("Final service name: " + resource.getAttribute(SERVICE_NAME));
            logger.info("Final resource attributes: " + resource.getAttributes());

            // Create OTLP metric exporter with debug
            OtlpGrpcMetricExporter metricExporter = OtlpGrpcMetricExporter.builder()
                .setEndpoint(endpoint)
                .addHeader("signoz-debug", "true")
                .setTimeout(java.time.Duration.ofSeconds(30))
                .build();

            logger.info("Created metric exporter with endpoint: " + endpoint);

            // Create metric reader with more aggressive configuration
            PeriodicMetricReader metricReader = PeriodicMetricReader.builder(metricExporter)
                .setInterval(java.time.Duration.ofSeconds(1))
                .build();

            logger.info("Created metric reader with 1 second interval");

            // Create meter provider with explicit configuration
            SdkMeterProvider meterProvider = SdkMeterProvider.builder()
                .setResource(resource)
                .registerMetricReader(metricReader)
                .build();

            // Create a test metric to verify export
            io.opentelemetry.api.metrics.Meter meter = meterProvider.get(SignozMetricsUtils.class.getName());
            meter.counterBuilder("test_metric")
                .setDescription("Test metric to verify export")
                .setUnit("1")
                .build()
                .add(1);

            logger.info("Created and incremented test metric");
            logger.info("Created meter provider with resource: " + resource);

            // Build and register OpenTelemetry SDK globally
            OpenTelemetrySdk.builder()
                .setMeterProvider(meterProvider)
                .buildAndRegisterGlobal();

            logger.info("Registered OpenTelemetry SDK globally");

            // Create OpenTelemetry meter for Temporal metrics
            io.opentelemetry.api.metrics.Meter temporalMeter = meterProvider.get("temporal-metrics");

            // Create a simple meter registry that will forward to OpenTelemetry
            MeterRegistry registry = new SimpleMeterRegistry();

            // Get resource attributes for tagging
            final String serviceName = resource.getAttribute(SERVICE_NAME);
            final String environment = resource.getAttribute(DEPLOYMENT_ENVIRONMENT);

            // Create and return the metrics scope with OpenTelemetry integration
            return new RootScopeBuilder()
                .reporter(new MicrometerClientStatsReporter(registry) {
                    private final Map<String, io.opentelemetry.api.metrics.LongCounter> counters = new ConcurrentHashMap<>();

                    private io.opentelemetry.api.metrics.LongCounter getOrCreateCounter(String name) {
                        return counters.computeIfAbsent(name, k -> temporalMeter
                            .counterBuilder(k)
                            .setDescription("Temporal metric: " + k)
                            .setUnit("1")
                            .build());
                    }

                    @Override
                    public void reportCounter(String name, Map<String, String> tags, long value) {
                        // Forward all Temporal metrics to OpenTelemetry
                        if (name.startsWith("temporal_")) {
                            io.opentelemetry.api.metrics.LongCounter counter = getOrCreateCounter(name);
                            counter.add(value);
                            logger.fine("Forwarded Temporal metric: " + name + " with value: " + value + " tags: " + tags);
                        }
                        super.reportCounter(name, tags, value);
                    }

                    @Override
                    public void reportGauge(String name, Map<String, String> tags, double value) {
                        if (name.startsWith("temporal_")) {
                            logger.fine("Received gauge metric: " + name + " with value: " + value + " tags: " + tags);
                        }
                        super.reportGauge(name, tags, value);
                    }
                })
                .tags(Map.of("service.name", serviceName, "environment", environment))
                .reportEvery(Duration.ofSeconds(1));

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error configuring OpenTelemetry metrics", e);
            throw new RuntimeException("Failed to configure OpenTelemetry metrics", e);
        }
    }
} 