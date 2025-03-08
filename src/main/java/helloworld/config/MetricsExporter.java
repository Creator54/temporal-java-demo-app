package helloworld.config;

import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import com.uber.m3.tally.NoopScope;
import com.uber.m3.tally.Scope;
import java.util.logging.Logger;
import java.util.concurrent.TimeUnit;

/**
 * Configures and manages OpenTelemetry metrics export for Temporal.
 * This class provides:
 * 1. OTLP metrics exporter configuration
 * 2. Metrics scope for Temporal client
 * 3. Periodic metrics reader setup
 * 
 * The metrics pipeline is configured to:
 * - Export metrics via OTLP/gRPC protocol
 * - Use periodic batch export with configurable intervals
 * - Include standard resource attributes
 * 
 * Usage:
 * ```java
 * // Get metrics scope for Temporal client
 * Scope scope = MetricsExporter.getMetricsScope();
 * 
 * // Create meter provider for SDK initialization
 * SdkMeterProvider provider = MetricsExporter.createMeterProvider();
 * ```
 */
public class MetricsExporter {
    private static final Logger logger = Logger.getLogger(MetricsExporter.class.getName());
    private static Scope metricsScope;
    private static PeriodicMetricReader metricReader;

    /**
     * Gets the metrics scope for Temporal client configuration.
     * Thread-safe and creates a NoopScope if none exists.
     * 
     * @return Scope instance for Temporal metrics
     */
    public static synchronized Scope getMetricsScope() {
        if (metricsScope == null) {
            metricsScope = new NoopScope();
        }
        return metricsScope;
    }

    /**
     * Creates and configures the OpenTelemetry meter provider.
     * Sets up:
     * - OTLP gRPC exporter
     * - Periodic metric reader
     * - Resource attributes
     * 
     * @return Configured SdkMeterProvider
     */
    public static SdkMeterProvider createMeterProvider() {
        String endpoint = OpenTelemetryConfig.getEndpoint();
        
        // Create OTLP metric exporter
        OtlpGrpcMetricExporter metricExporter = OtlpGrpcMetricExporter.builder()
            .setEndpoint(endpoint)
            .addHeader("signoz-debug", "true")
            .setTimeout(java.time.Duration.ofSeconds(30))
            .build();

        // Create metric reader with 1-second interval
        metricReader = PeriodicMetricReader.builder(metricExporter)
            .setInterval(java.time.Duration.ofSeconds(1))
            .build();

        // Create and return meter provider
        return SdkMeterProvider.builder()
            .setResource(OpenTelemetryConfig.createResource())
            .registerMetricReader(metricReader)
            .build();
    }

    /**
     * Gracefully shuts down the metrics pipeline.
     * Ensures all pending metrics are exported before shutdown.
     */
    public static void shutdown() {
        if (metricReader != null) {
            try {
                logger.info("Shutting down metrics reader...");
                metricReader.shutdown().join(10, TimeUnit.SECONDS);
                logger.info("Metrics reader shutdown completed");
            } catch (Exception e) {
                logger.severe("Error during metrics reader shutdown: " + e.getMessage());
            }
        }
    }

    private MetricsExporter() {
        // Prevent instantiation - use static methods
    }
} 