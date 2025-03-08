package helloworld.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.temporal.common.interceptors.WorkerInterceptor;
import io.temporal.common.interceptors.WorkflowClientInterceptor;
import io.temporal.opentracing.OpenTracingClientInterceptor;
import io.temporal.opentracing.OpenTracingOptions;
import io.temporal.opentracing.OpenTracingSpanContextCodec;
import io.temporal.opentracing.OpenTracingWorkerInterceptor;
import io.opentelemetry.opentracingshim.OpenTracingShim;
import com.uber.m3.tally.NoopScope;
import com.uber.m3.tally.Scope;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.temporal.common.reporter.MicrometerClientStatsReporter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SignozTelemetryUtils {
    private static final Logger logger = Logger.getLogger(SignozTelemetryUtils.class.getName());
    private static final String DEFAULT_SIGNOZ_ENDPOINT = "http://localhost:4317";
    private static final String DEFAULT_SERVICE_NAME = "temporal-hello-world";

    // Define attribute keys
    private static final AttributeKey<String> SERVICE_NAME = AttributeKey.stringKey("service.name");
    private static final AttributeKey<String> SERVICE_NAMESPACE = AttributeKey.stringKey("service.namespace");
    private static final AttributeKey<String> DEPLOYMENT_ENVIRONMENT = AttributeKey.stringKey("deployment.environment");

    private static OpenTelemetry openTelemetry;
    private static Tracer tracer;
    private static Meter meter;
    private static Scope metricsScope;
    private static OpenTracingOptions openTracingOptions;

    public static synchronized OpenTelemetry getOpenTelemetry() {
        if (openTelemetry == null) {
            initializeTelemetry();
        }
        return openTelemetry;
    }

    public static synchronized Tracer getTracer() {
        if (tracer == null) {
            tracer = getOpenTelemetry().getTracer(SignozTelemetryUtils.class.getName());
        }
        return tracer;
    }

    public static synchronized Meter getMeter() {
        if (meter == null) {
            meter = getOpenTelemetry().getMeter(SignozTelemetryUtils.class.getName());
        }
        return meter;
    }

    public static synchronized Scope getMetricsScope() {
        if (metricsScope == null) {
            initializeTelemetry();
        }
        return metricsScope;
    }

    public static synchronized OpenTracingOptions getOpenTracingOptions() {
        if (openTracingOptions == null) {
            initializeTelemetry();
        }
        return openTracingOptions;
    }

    public static WorkerInterceptor getWorkerInterceptor() {
        return new OpenTracingWorkerInterceptor(getOpenTracingOptions());
    }

    public static WorkflowClientInterceptor getClientInterceptor() {
        return new OpenTracingClientInterceptor(getOpenTracingOptions());
    }

    private static void initializeTelemetry() {
        try {
            String endpoint = System.getenv().getOrDefault("OTEL_EXPORTER_OTLP_ENDPOINT", DEFAULT_SIGNOZ_ENDPOINT);
            logger.info("Configuring OpenTelemetry with endpoint: " + endpoint);

            // Set system properties for OTLP configuration
            System.setProperty("otel.exporter.otlp.protocol", "grpc");
            System.setProperty("otel.exporter.otlp.endpoint", endpoint);

            // Create base resource with service name and required attributes
            Resource baseResource = Resource.getDefault();
            Resource customResource = Resource.create(Attributes.of(
                SERVICE_NAME, DEFAULT_SERVICE_NAME,
                SERVICE_NAMESPACE, "default",
                DEPLOYMENT_ENVIRONMENT, "development"
            ));

            Resource resource = baseResource.merge(customResource);

            // Create OTLP trace exporter
            OtlpGrpcSpanExporter spanExporter = OtlpGrpcSpanExporter.builder()
                .setEndpoint(endpoint)
                .addHeader("signoz-debug", "true")
                .setTimeout(java.time.Duration.ofSeconds(30))
                .build();

            // Create BatchSpanProcessor
            BatchSpanProcessor spanProcessor = BatchSpanProcessor.builder(spanExporter)
                .setScheduleDelay(java.time.Duration.ofMillis(100))
                .setMaxQueueSize(2048)
                .setMaxExportBatchSize(512)
                .build();

            // Create tracer provider
            SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(spanProcessor)
                .setResource(resource)
                .build();

            // Create OTLP metric exporter
            OtlpGrpcMetricExporter metricExporter = OtlpGrpcMetricExporter.builder()
                .setEndpoint(endpoint)
                .addHeader("signoz-debug", "true")
                .setTimeout(java.time.Duration.ofSeconds(30))
                .build();

            // Create metric reader
            PeriodicMetricReader metricReader = PeriodicMetricReader.builder(metricExporter)
                .setInterval(java.time.Duration.ofSeconds(1))
                .build();

            // Create meter provider
            SdkMeterProvider meterProvider = SdkMeterProvider.builder()
                .setResource(resource)
                .registerMetricReader(metricReader)
                .build();

            // Build OpenTelemetry SDK
            openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setMeterProvider(meterProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build();

            // Create metrics scope
            metricsScope = new NoopScope();

            // Create OpenTracing shim
            openTracingOptions = OpenTracingOptions.newBuilder()
                .setTracer(OpenTracingShim.createTracerShim(openTelemetry))
                .setSpanContextCodec(OpenTracingSpanContextCodec.TEXT_MAP_CODEC)
                .build();

            // Register shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    logger.info("Starting OpenTelemetry shutdown...");
                    spanProcessor.forceFlush().join(10, TimeUnit.SECONDS);
                    spanProcessor.shutdown().join(10, TimeUnit.SECONDS);
                    metricReader.shutdown().join(10, TimeUnit.SECONDS);
                    logger.info("OpenTelemetry shutdown completed");
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error during OpenTelemetry shutdown", e);
                }
            }));

            logger.info("OpenTelemetry initialized successfully");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error configuring OpenTelemetry", e);
            throw new RuntimeException("Failed to configure OpenTelemetry", e);
        }
    }

    private SignozTelemetryUtils() {
        // Prevent instantiation
    }
} 