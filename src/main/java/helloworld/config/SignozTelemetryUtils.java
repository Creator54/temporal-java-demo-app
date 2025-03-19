package helloworld.config;

import com.uber.m3.tally.Scope;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.temporal.common.interceptors.WorkerInterceptor;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class for configuring and accessing SigNoz telemetry in temporal applications.
 * Provides centralized access to meters, tracers, and other telemetry components.
 */
public class SignozTelemetryUtils {
    private static final Logger logger = Logger.getLogger(SignozTelemetryUtils.class.getName());
    private static volatile boolean initialized = false;
    private static Meter meter;
    private static Tracer tracer;
    
    /**
     * Initializes OpenTelemetry for the application.
     * Sets up tracing, metrics and initializes dashboard-specific metrics.
     */
    public static synchronized void initializeTelemetry() {
        if (initialized) {
            return;
        }
        
        logger.info("Initializing OpenTelemetry...");
        
        try {
            // Create resource with service info
            Resource resource = OpenTelemetryConfig.createResource();
            
            // Build SDK with metrics and tracing support
            OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
                .setTracerProvider(TracingExporter.createTracerProvider())
                .setMeterProvider(MetricsExporter.createMeterProvider())
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build();
            
            OpenTelemetryConfig.setOpenTelemetry(sdk);
            
            // Initialize meters and tracers
            meter = sdk.getMeter("io.temporal");
            tracer = sdk.getTracer("io.temporal");
            
            // Initialize workflow metrics for the dashboard
            WorkflowMetricsUtil.initializeMetrics();
            
            // Add shutdown hook for clean telemetry shutdown
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutting down OpenTelemetry...");
                
                // First, clean up WorkflowMetricsUtil resources
                WorkflowMetricsUtil.cleanup();
                
                // Force metrics export before shutdown
                try {
                    // Allow time for any pending metrics to be exported
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    logger.log(Level.WARNING, "Interrupted during shutdown delay", e);
                }
                
                // Shutdown exporters
                TracingExporter.shutdown();
                MetricsExporter.shutdown();
                
                // Reset local references
                meter = null;
                tracer = null;
                initialized = false;
            }));
            
            initialized = true;
            logger.info("OpenTelemetry initialization complete");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error initializing OpenTelemetry", e);
        }
    }
    
    /**
     * Gets the OpenTelemetry meter for recording metrics.
     * 
     * @return Configured OpenTelemetry Meter instance
     */
    public static Meter getMeter() {
        if (!initialized) {
            logger.warning("OpenTelemetry not initialized. Call initializeTelemetry() first.");
        }
        return meter;
    }
    
    /**
     * Gets the OpenTelemetry tracer for creating spans.
     * 
     * @return Configured OpenTelemetry Tracer instance
     */
    public static Tracer getTracer() {
        if (!initialized) {
            logger.warning("OpenTelemetry not initialized. Call initializeTelemetry() first.");
        }
        return tracer;
    }
    
    /**
     * Gets the metrics scope for Temporal client configuration.
     * 
     * @return Metrics scope for Temporal client
     */
    public static Scope getMetricsScope() {
        return MetricsExporter.getMetricsScope();
    }
    
    /**
     * Gets the worker interceptor for tracing.
     * 
     * @return WorkerInterceptor instance with tracing capability
     */
    public static WorkerInterceptor getWorkerInterceptor() {
        return TracingExporter.getWorkerInterceptor();
    }
    
    private SignozTelemetryUtils() {
        // Prevent instantiation - use static methods
    }
} 