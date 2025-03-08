package helloworld.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import java.util.logging.Logger;

/**
 * Core configuration for OpenTelemetry SDK.
 * This class provides:
 * 1. Global OpenTelemetry SDK instance management
 * 2. Resource configuration (service name, environment, etc.)
 * 3. Environment-based configuration handling
 * 
 * Usage:
 * - For complete setup, use SignozTelemetryUtils instead
 * - For custom setup, use this class directly
 */
public final class OpenTelemetryConfig {
    private static final Logger logger = Logger.getLogger(OpenTelemetryConfig.class.getName());

    // Default values used when environment variables are not set
    public static final String DEFAULT_SIGNOZ_ENDPOINT = "http://localhost:4317";
    public static final String DEFAULT_SERVICE_NAME = "temporal-hello-world";

    // Standard OpenTelemetry resource attribute keys
    public static final AttributeKey<String> SERVICE_NAME = AttributeKey.stringKey("service.name");
    public static final AttributeKey<String> SERVICE_NAMESPACE = AttributeKey.stringKey("service.namespace");
    public static final AttributeKey<String> DEPLOYMENT_ENVIRONMENT = AttributeKey.stringKey("deployment.environment");

    // Environment variables for configuration override
    private static final String ENV_OTEL_ENDPOINT = "OTEL_EXPORTER_OTLP_ENDPOINT";  // Collector endpoint
    private static final String ENV_SERVICE_NAME = "OTEL_SERVICE_NAME";             // Service name
    private static final String ENV_ENVIRONMENT = "OTEL_ENVIRONMENT";               // Deployment environment

    // Global SDK instance - initialized by SignozTelemetryUtils
    private static volatile OpenTelemetry openTelemetry;

    /**
     * Gets the global OpenTelemetry instance.
     * @throws IllegalStateException if called before initialization
     */
    public static OpenTelemetry getOpenTelemetry() {
        if (openTelemetry == null) {
            throw new IllegalStateException("OpenTelemetry not initialized. Call initialize() first.");
        }
        return openTelemetry;
    }

    /**
     * Creates a Resource with service and environment attributes.
     * Priority: Environment variables > Default values
     * 
     * Configured attributes:
     * - service.name: Name of the service
     * - service.namespace: Namespace (always "default")
     * - deployment.environment: Runtime environment
     */
    public static Resource createResource() {
        String serviceName = System.getenv().getOrDefault(ENV_SERVICE_NAME, DEFAULT_SERVICE_NAME);
        String environment = System.getenv().getOrDefault(ENV_ENVIRONMENT, "development");

        Resource baseResource = Resource.getDefault();
        Resource customResource = Resource.create(Attributes.of(
            SERVICE_NAME, serviceName,
            SERVICE_NAMESPACE, "default",
            DEPLOYMENT_ENVIRONMENT, environment
        ));

        logger.info("Creating resource with service name: " + serviceName + ", environment: " + environment);
        return baseResource.merge(customResource);
    }

    /**
     * Gets the OpenTelemetry collector endpoint.
     * Default: http://localhost:4317 (SigNoz default)
     */
    public static String getEndpoint() {
        return System.getenv().getOrDefault(ENV_OTEL_ENDPOINT, DEFAULT_SIGNOZ_ENDPOINT);
    }

    /**
     * Sets the global OpenTelemetry instance.
     * @throws IllegalStateException if called multiple times
     */
    protected static synchronized void setOpenTelemetry(OpenTelemetrySdk sdk) {
        if (openTelemetry != null) {
            throw new IllegalStateException("OpenTelemetry already initialized");
        }
        openTelemetry = sdk;
    }

    private OpenTelemetryConfig() {
        // Prevent instantiation - use static methods
    }
} 