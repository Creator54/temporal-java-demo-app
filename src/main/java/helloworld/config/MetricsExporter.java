package helloworld.config;

import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import com.uber.m3.tally.NoopScope;
import com.uber.m3.tally.Scope;
import java.util.logging.Logger;
import java.util.concurrent.TimeUnit;

public class MetricsExporter {
    private static final Logger logger = Logger.getLogger(MetricsExporter.class.getName());
    private static Scope metricsScope;
    private static PeriodicMetricReader metricReader;

    public static synchronized Scope getMetricsScope() {
        if (metricsScope == null) {
            metricsScope = new NoopScope();
        }
        return metricsScope;
    }

    public static SdkMeterProvider createMeterProvider() {
        String endpoint = OpenTelemetryConfig.getEndpoint();
        
        // Create OTLP metric exporter
        OtlpGrpcMetricExporter metricExporter = OtlpGrpcMetricExporter.builder()
            .setEndpoint(endpoint)
            .addHeader("signoz-debug", "true")
            .setTimeout(java.time.Duration.ofSeconds(30))
            .build();

        // Create metric reader
        metricReader = PeriodicMetricReader.builder(metricExporter)
            .setInterval(java.time.Duration.ofSeconds(1))
            .build();

        // Create and return meter provider
        return SdkMeterProvider.builder()
            .setResource(OpenTelemetryConfig.createResource())
            .registerMetricReader(metricReader)
            .build();
    }

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
        // Prevent instantiation
    }
} 