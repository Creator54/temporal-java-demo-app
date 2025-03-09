package helloworld.config;

import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.temporal.common.interceptors.WorkerInterceptor;
import io.temporal.common.interceptors.WorkflowClientInterceptor;
import io.temporal.opentracing.OpenTracingClientInterceptor;
import io.temporal.opentracing.OpenTracingOptions;
import io.temporal.opentracing.OpenTracingSpanContextCodec;
import io.temporal.opentracing.OpenTracingWorkerInterceptor;
import io.opentelemetry.opentracingshim.OpenTracingShim;
import java.util.logging.Logger;
import java.util.concurrent.TimeUnit;

/**
 * Configures and manages OpenTelemetry tracing export for Temporal.
 * This class provides:
 * 1. OTLP trace exporter configuration
 * 2. OpenTracing compatibility layer
 * 3. Temporal worker and client interceptors
 * 4. Batch span processing
 * 
 * The tracing pipeline is configured to:
 * - Export traces via OTLP/gRPC protocol
 * - Use batch processing for efficient export
 * - Support OpenTracing for Temporal compatibility
 * - Include standard resource attributes
 * 
 * Usage:
 * ```java
 * // Configure worker with tracing
 * WorkerFactoryOptions options = WorkerFactoryOptions.newBuilder()
 *     .setWorkerInterceptors(TracingExporter.getWorkerInterceptor())
 *     .build();
 * 
 * // Configure client with tracing
 * WorkflowClientOptions clientOptions = WorkflowClientOptions.newBuilder()
 *     .setInterceptors(TracingExporter.getClientInterceptor())
 *     .build();
 * ```
 */
public class TracingExporter {
    private static final Logger logger = Logger.getLogger(TracingExporter.class.getName());
    private static OpenTracingOptions openTracingOptions;
    private static BatchSpanProcessor spanProcessor;

    /**
     * Gets OpenTracing options configured for Temporal.
     * Thread-safe and creates options if none exist.
     * 
     * @return OpenTracingOptions for Temporal integration
     */
    public static synchronized OpenTracingOptions getOpenTracingOptions() {
        if (openTracingOptions == null) {
            openTracingOptions = OpenTracingOptions.newBuilder()
                .setTracer(OpenTracingShim.createTracerShim(OpenTelemetryConfig.getOpenTelemetry()))
                .setSpanContextCodec(OpenTracingSpanContextCodec.TEXT_MAP_CODEC)
                .build();
        }
        return openTracingOptions;
    }

    /**
     * Creates a worker interceptor for tracing Temporal workflows.
     * 
     * @return WorkerInterceptor configured with OpenTracing
     */
    public static WorkerInterceptor getWorkerInterceptor() {
        return new OpenTracingWorkerInterceptor(getOpenTracingOptions());
    }

    /**
     * Creates a client interceptor for tracing Temporal workflow clients.
     * 
     * @return WorkflowClientInterceptor configured with OpenTracing
     */
    public static WorkflowClientInterceptor getClientInterceptor() {
        return new OpenTracingClientInterceptor(getOpenTracingOptions());
    }

    /**
     * Creates and configures the OpenTelemetry tracer provider.
     * Sets up:
     * - OTLP gRPC exporter
     * - Batch span processor
     * - Resource attributes
     * 
     * @return Configured SdkTracerProvider
     */
    public static SdkTracerProvider createTracerProvider() {
        String endpoint = OpenTelemetryConfig.getEndpoint();
        String accessToken = OpenTelemetryConfig.getAccessToken();
        
        // Parse the header key and value
        String headerKey = null;
        String headerValue = accessToken;
        if (accessToken != null && accessToken.contains("=")) {
            String[] parts = accessToken.split("=", 2);
            headerKey = parts[0];
            headerValue = parts[1];
        }

        // Create OTLP trace exporter
        OtlpGrpcSpanExporter spanExporter;
        if (headerKey != null && headerValue != null && !headerValue.isEmpty() && !endpoint.contains("localhost")) {
            logger.info("Tracing header key: " + headerKey);
            logger.info("Tracing endpoint: " + endpoint);
            spanExporter = OtlpGrpcSpanExporter.builder()
                .setEndpoint(endpoint)
                .addHeader(headerKey, headerValue)
                .setTimeout(java.time.Duration.ofSeconds(60))
                .build();
        } else {
            spanExporter = OtlpGrpcSpanExporter.builder()
                .setEndpoint(endpoint)
                .setTimeout(java.time.Duration.ofSeconds(60))
                .build();
        }

        // Create BatchSpanProcessor with optimized settings
        spanProcessor = BatchSpanProcessor.builder(spanExporter)
            .setScheduleDelay(java.time.Duration.ofSeconds(1))
            .setMaxQueueSize(4096)
            .setMaxExportBatchSize(512)
            .setExporterTimeout(java.time.Duration.ofSeconds(60))
            .build();

        // Create and return tracer provider
        return SdkTracerProvider.builder()
            .addSpanProcessor(spanProcessor)
            .setResource(OpenTelemetryConfig.createResource())
            .build();
    }

    /**
     * Gracefully shuts down the tracing pipeline.
     * Ensures all pending spans are exported before shutdown.
     */
    public static void shutdown() {
        if (spanProcessor != null) {
            try {
                logger.info("Shutting down span processor...");
                spanProcessor.forceFlush().join(10, TimeUnit.SECONDS);
                spanProcessor.shutdown().join(10, TimeUnit.SECONDS);
                logger.info("Span processor shutdown completed");
            } catch (Exception e) {
                logger.severe("Error during span processor shutdown: " + e.getMessage());
            }
        }
    }

    private TracingExporter() {
        // Prevent instantiation - use static methods
    }
} 