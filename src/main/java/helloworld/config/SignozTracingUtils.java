package helloworld.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.opentracingshim.OpenTracingShim;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.temporal.common.interceptors.WorkerInterceptor;
import io.temporal.common.interceptors.WorkflowClientInterceptor;
import io.temporal.opentracing.OpenTracingClientInterceptor;
import io.temporal.opentracing.OpenTracingOptions;
import io.temporal.opentracing.OpenTracingSpanContextCodec;
import io.temporal.opentracing.OpenTracingWorkerInterceptor;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SignozTracingUtils {
    private static final Logger logger = Logger.getLogger(SignozTracingUtils.class.getName());
    private static final String DEFAULT_SIGNOZ_ENDPOINT = "http://localhost:4317";
    private static final String DEFAULT_SERVICE_NAME = "temporal-hello-world";

    // Define attribute keys
    private static final AttributeKey<String> SERVICE_NAME = AttributeKey.stringKey("service.name");
    private static final AttributeKey<String> SERVICE_NAMESPACE = AttributeKey.stringKey("service.namespace");
    private static final AttributeKey<String> DEPLOYMENT_ENVIRONMENT = AttributeKey.stringKey("deployment.environment");

    private static OpenTracingOptions openTracingOptions;

    public static synchronized OpenTracingOptions getOpenTracingOptions() {
        if (openTracingOptions == null) {
            initializeTracing();
        }
        return openTracingOptions;
    }

    public static WorkerInterceptor getWorkerInterceptor() {
        return new OpenTracingWorkerInterceptor(getOpenTracingOptions());
    }

    public static WorkflowClientInterceptor getClientInterceptor() {
        return new OpenTracingClientInterceptor(getOpenTracingOptions());
    }

    private static void initializeTracing() {
        try {
            String endpoint = System.getenv().getOrDefault("OTEL_EXPORTER_OTLP_ENDPOINT", DEFAULT_SIGNOZ_ENDPOINT);
            logger.info("Configuring OpenTelemetry Tracing with endpoint: " + endpoint);

            // Set system properties for OTLP configuration
            System.setProperty("otel.traces.exporter", "otlp");
            System.setProperty("otel.exporter.otlp.protocol", "grpc");
            System.setProperty("otel.exporter.otlp.endpoint", endpoint);
            System.setProperty("otel.metrics.exporter", "none");
            System.setProperty("otel.logs.exporter", "none");

            // Create base resource with service name and required attributes
            Resource baseResource = Resource.getDefault();
            Resource customResource = Resource.create(Attributes.of(
                SERVICE_NAME, DEFAULT_SERVICE_NAME,
                SERVICE_NAMESPACE, "default",
                DEPLOYMENT_ENVIRONMENT, "development"
            ));

            Resource resource = baseResource.merge(customResource);

            // Create OTLP exporter with debug logging
            OtlpGrpcSpanExporter spanExporter = OtlpGrpcSpanExporter.builder()
                .setEndpoint(endpoint)
                .addHeader("signoz-debug", "true")
                .setTimeout(java.time.Duration.ofSeconds(30))
                .build();

            logger.info("Created span exporter with endpoint: " + endpoint);

            // Create BatchSpanProcessor with more aggressive configuration
            BatchSpanProcessor batchSpanProcessor = BatchSpanProcessor.builder(spanExporter)
                .setScheduleDelay(java.time.Duration.ofMillis(100))
                .setMaxQueueSize(2048)
                .setMaxExportBatchSize(512)
                .build();

            logger.info("Created batch span processor");

            // Register shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    logger.info("Starting OpenTelemetry shutdown...");
                    batchSpanProcessor.forceFlush();
                    logger.info("Forced flush of remaining spans");
                    batchSpanProcessor.shutdown();
                    logger.info("BatchSpanProcessor shutdown complete");
                    spanExporter.shutdown();
                    logger.info("SpanExporter shutdown complete");
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error during OpenTelemetry shutdown", e);
                }
            }));

            // Create tracer provider
            SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(batchSpanProcessor)
                .setResource(resource)
                .build();

            logger.info("Created tracer provider with resource: " + resource);

            // Build OpenTelemetry SDK
            OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setPropagators(ContextPropagators.create(TextMapPropagator.composite(
                    W3CTraceContextPropagator.getInstance()
                )))
                .build();

            // Create OpenTracing shim
            openTracingOptions = OpenTracingOptions.newBuilder()
                .setTracer(OpenTracingShim.createTracerShim(openTelemetry))
                .setSpanContextCodec(OpenTracingSpanContextCodec.TEXT_MAP_CODEC)
                .build();

            logger.info("OpenTelemetry SDK initialized successfully with OpenTracing shim");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error configuring OpenTelemetry tracing", e);
            throw new RuntimeException("Failed to configure OpenTelemetry tracing", e);
        }
    }

    private SignozTracingUtils() {
        // Prevent instantiation
    }
} 