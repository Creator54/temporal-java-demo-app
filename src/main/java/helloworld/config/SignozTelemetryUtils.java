package helloworld.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.temporal.common.interceptors.WorkerInterceptor;
import io.temporal.common.interceptors.WorkflowClientInterceptor;
import com.uber.m3.tally.Scope;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main entry point for Temporal telemetry integration with OpenTelemetry.
 * 
 * This class provides:
 * 1. One-line initialization of metrics and tracing
 * 2. Access to configured telemetry components
 * 3. Integration with Temporal's worker and client interceptors
 * 
 * IMPORTANT: Call initializeTelemetry() before creating any Temporal clients or workers.
 * Initialize in both worker and workflow starter processes.
 * 
 * Typical usage:
 * ```java
 * // Initialize telemetry FIRST
 * SignozTelemetryUtils.initializeTelemetry();
 * 
 * // Then configure Temporal client
 * WorkflowClient client = TemporalConfig.getWorkflowClient(
 *     WorkflowServiceStubsOptions.newBuilder()
 *         .setMetricsScope(SignozTelemetryUtils.getMetricsScope())
 *         .build(),
 *     WorkflowClientOptions.newBuilder()
 *         .setInterceptors(SignozTelemetryUtils.getClientInterceptor())
 *         .build()
 * );
 * ```
 */
public final class SignozTelemetryUtils {
    private static final Logger logger = Logger.getLogger(SignozTelemetryUtils.class.getName());
    private static volatile boolean isInitialized = false;
    private static volatile Tracer tracer;
    private static volatile Meter meter;

    /**
     * Initializes OpenTelemetry for both metrics and tracing.
     * 
     * This method:
     * 1. Configures the OpenTelemetry SDK
     * 2. Sets up metrics export via OTLP
     * 3. Sets up tracing with OpenTracing compatibility
     * 4. Registers a shutdown hook for cleanup
     * 
     * Thread-safe and idempotent - safe to call multiple times.
     */
    public static synchronized void initializeTelemetry() {
        if (isInitialized) {
            logger.info("OpenTelemetry already initialized");
            return;
        }

        try {
            logger.info("Configuring OpenTelemetry with endpoint: " + OpenTelemetryConfig.getEndpoint());

            // Configure OTLP protocol and endpoint
            System.setProperty("otel.exporter.otlp.protocol", "grpc");
            System.setProperty("otel.exporter.otlp.endpoint", OpenTelemetryConfig.getEndpoint());

            // Build SDK with metrics and tracing support
            OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
                .setTracerProvider(TracingExporter.createTracerProvider())
                .setMeterProvider(MetricsExporter.createMeterProvider())
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build();

            OpenTelemetryConfig.setOpenTelemetry(sdk);
            isInitialized = true;

            // Ensure clean shutdown
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    logger.info("Starting OpenTelemetry shutdown...");
                    TracingExporter.shutdown();
                    MetricsExporter.shutdown();
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

    /**
     * Gets the OpenTelemetry instance for custom instrumentation.
     * Rarely needed - prefer getTracer() or getMeter().
     */
    public static OpenTelemetry getOpenTelemetry() {
        ensureInitialized();
        return OpenTelemetryConfig.getOpenTelemetry();
    }

    /**
     * Gets a Tracer for creating custom spans.
     * Thread-safe, caches the Tracer instance.
     */
    public static synchronized Tracer getTracer() {
        if (tracer == null) {
            tracer = getOpenTelemetry().getTracer(SignozTelemetryUtils.class.getName());
        }
        return tracer;
    }

    /**
     * Gets a Meter for creating custom metrics.
     * Thread-safe, caches the Meter instance.
     */
    public static synchronized Meter getMeter() {
        if (meter == null) {
            meter = getOpenTelemetry().getMeter(SignozTelemetryUtils.class.getName());
        }
        return meter;
    }

    /**
     * Gets the metrics scope for Temporal client configuration.
     * Used with WorkflowServiceStubsOptions.setMetricsScope().
     */
    public static Scope getMetricsScope() {
        ensureInitialized();
        return MetricsExporter.getMetricsScope();
    }

    /**
     * Gets the worker interceptor for Temporal worker configuration.
     * Used with WorkerFactoryOptions.setWorkerInterceptors().
     */
    public static WorkerInterceptor getWorkerInterceptor() {
        ensureInitialized();
        return TracingExporter.getWorkerInterceptor();
    }

    /**
     * Gets the client interceptor for Temporal client configuration.
     * Used with WorkflowClientOptions.setInterceptors().
     */
    public static WorkflowClientInterceptor getClientInterceptor() {
        ensureInitialized();
        return TracingExporter.getClientInterceptor();
    }

    /**
     * Ensures telemetry is initialized before accessing components.
     * @throws IllegalStateException if initializeTelemetry() wasn't called
     */
    private static void ensureInitialized() {
        if (!isInitialized) {
            throw new IllegalStateException("OpenTelemetry not initialized. Call initializeTelemetry() first.");
        }
    }

    private SignozTelemetryUtils() {
        // Prevent instantiation - use static methods
    }
} 