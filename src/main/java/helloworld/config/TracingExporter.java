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

public class TracingExporter {
    private static final Logger logger = Logger.getLogger(TracingExporter.class.getName());
    private static OpenTracingOptions openTracingOptions;
    private static BatchSpanProcessor spanProcessor;

    public static synchronized OpenTracingOptions getOpenTracingOptions() {
        if (openTracingOptions == null) {
            openTracingOptions = OpenTracingOptions.newBuilder()
                .setTracer(OpenTracingShim.createTracerShim(OpenTelemetryConfig.getOpenTelemetry()))
                .setSpanContextCodec(OpenTracingSpanContextCodec.TEXT_MAP_CODEC)
                .build();
        }
        return openTracingOptions;
    }

    public static WorkerInterceptor getWorkerInterceptor() {
        return new OpenTracingWorkerInterceptor(getOpenTracingOptions());
    }

    public static WorkflowClientInterceptor getClientInterceptor() {
        return new OpenTracingClientInterceptor(getOpenTracingOptions());
    }

    public static SdkTracerProvider createTracerProvider() {
        String endpoint = OpenTelemetryConfig.getEndpoint();

        // Create OTLP trace exporter
        OtlpGrpcSpanExporter spanExporter = OtlpGrpcSpanExporter.builder()
            .setEndpoint(endpoint)
            .addHeader("signoz-debug", "true")
            .setTimeout(java.time.Duration.ofSeconds(30))
            .build();

        // Create BatchSpanProcessor
        spanProcessor = BatchSpanProcessor.builder(spanExporter)
            .setScheduleDelay(java.time.Duration.ofMillis(100))
            .setMaxQueueSize(2048)
            .setMaxExportBatchSize(512)
            .build();

        // Create and return tracer provider
        return SdkTracerProvider.builder()
            .addSpanProcessor(spanProcessor)
            .setResource(OpenTelemetryConfig.createResource())
            .build();
    }

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
        // Prevent instantiation
    }
} 