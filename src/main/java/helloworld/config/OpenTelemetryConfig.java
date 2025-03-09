package helloworld.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.HashMap;
import java.util.Map;

/**
 * Core configuration for OpenTelemetry SDK.
 * This class provides:
 * 1. Global OpenTelemetry SDK instance management
 * 2. Resource configuration (environment, etc.)
 * 3. Environment-based configuration handling
 * 
 * Note: Service name is configured via OTEL_RESOURCE_ATTRIBUTES environment variable
 * 
 * Usage:
 * - For complete setup, use SignozTelemetryUtils instead
 * - For custom setup, use this class directly
 */
public final class OpenTelemetryConfig {
    private static final Logger logger = Logger.getLogger(OpenTelemetryConfig.class.getName());

    // Default values used when environment variables are not set
    public static final String DEFAULT_SIGNOZ_ENDPOINT = "http://localhost:4317";

    // Standard OpenTelemetry resource attribute keys for custom attribute creation
    public static final AttributeKey<String> SERVICE_NAME = AttributeKey.stringKey("service.name");
    public static final AttributeKey<String> SERVICE_NAMESPACE = AttributeKey.stringKey("service.namespace");
    public static final AttributeKey<String> DEPLOYMENT_ENVIRONMENT = AttributeKey.stringKey("deployment.environment");

    // Environment variables for configuration override
    private static final String ENV_OTEL_ENDPOINT = "OTEL_EXPORTER_OTLP_ENDPOINT";  // Collector endpoint
    private static final String ENV_ENVIRONMENT = "OTEL_ENVIRONMENT";               // Deployment environment
    private static final String ENV_RESOURCE_ATTRIBUTES = "OTEL_RESOURCE_ATTRIBUTES"; // Resource attributes

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
     * Creates a Resource with environment attributes.
     * Service name is automatically configured via OTEL_RESOURCE_ATTRIBUTES.
     * 
     * Resource attributes:
     * - service.name: Required, configured via OTEL_RESOURCE_ATTRIBUTES environment variable
     * - service.namespace: Always set to "default"
     * - deployment.environment: From OTEL_ENVIRONMENT or defaults to "development"
     * 
     * @throws IllegalStateException if service.name is not configured in OTEL_RESOURCE_ATTRIBUTES
     */
    public static Resource createResource() {
        String environment = System.getenv().getOrDefault(ENV_ENVIRONMENT, "development");
        
        // Parse OTEL_RESOURCE_ATTRIBUTES
        String resourceAttrs = System.getenv(ENV_RESOURCE_ATTRIBUTES);
        Map<AttributeKey<String>, String> attributes = new HashMap<>();
        
        if (resourceAttrs != null) {
            // Parse comma-separated key-value pairs
            for (String pair : resourceAttrs.split(",")) {
                String[] keyValue = pair.trim().split("=", 2);
                if (keyValue.length == 2) {
                    String key = keyValue[0].trim();
                    String value = keyValue[1].trim();
                    if ("service.name".equals(key)) {
                        attributes.put(SERVICE_NAME, value);
                    }
                }
            }
        }

        // Verify service.name is set
        if (!attributes.containsKey(SERVICE_NAME)) {
            throw new IllegalStateException(
                "service.name must be configured via OTEL_RESOURCE_ATTRIBUTES environment variable. " +
                "Example: OTEL_RESOURCE_ATTRIBUTES=service.name=your-service-name"
            );
        }

        // Create resource with attributes
        Resource resource = Resource.create(
            Attributes.of(
                SERVICE_NAME, attributes.get(SERVICE_NAME),
                SERVICE_NAMESPACE, "default",
                DEPLOYMENT_ENVIRONMENT, environment
            )
        );
        
        logger.info("Creating resource with service.name: " + attributes.get(SERVICE_NAME));
        logger.info("Environment: " + environment);
        logger.info("Resource attributes: " + resourceAttrs);
        
        return Resource.getDefault().merge(resource);
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