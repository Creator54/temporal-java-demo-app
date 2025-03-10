package helloworld.config;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.SimpleSslContextBuilder;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.net.ssl.SSLException;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Core configuration for Temporal client and service connections.
 * This class provides:
 * 1. Workflow client initialization and management
 * 2. Service connection configuration
 * 3. Environment-based setup for cloud deployment
 * 4. TLS configuration for secure connections
 * 
 * The configuration supports both local development and cloud deployment:
 * - Local: Uses default localhost settings
 * - Cloud: Uses environment variables for endpoint and credentials
 * 
 * Usage:
 * ```java
 * // Basic client creation
 * WorkflowClient client = TemporalConfig.getWorkflowClient();
 * 
 * // Client with custom options
 * WorkflowClient client = TemporalConfig.getWorkflowClient(
 *     stubOptions,
 *     clientOptions
 * );
 * ```
 */
public class TemporalConfig {
    private static final Logger logger = Logger.getLogger(TemporalConfig.class.getName());
    
    // Default configuration values
    private static final class Defaults {
        static final String TARGET = "localhost:7233";
        static final String NAMESPACE = "default";
        static final String TASK_QUEUE = "hello-world-task-queue";
    }

    // Environment variable names
    private static final class EnvVars {
        static final String HOST_URL = "TEMPORAL_HOST_URL";
        static final String NAMESPACE = "TEMPORAL_NAMESPACE";
        static final String TASK_QUEUE = "TEMPORAL_TASK_QUEUE";
        static final String TLS_CERT = "TEMPORAL_TLS_CERT";
        static final String TLS_KEY = "TEMPORAL_TLS_KEY";
    }

    private static WorkflowServiceStubs service;
    private static WorkflowClient client;

    /**
     * Gets a workflow client with default configuration.
     * Thread-safe and creates client if none exists.
     * 
     * @return WorkflowClient instance
     */
    public static WorkflowClient getWorkflowClient() {
        if (client == null) {
            initializeDefault();
        }
        return client;
    }

    /**
     * Gets a workflow client with custom service options.
     * 
     * @param stubOptions Custom service stub options
     * @return WorkflowClient instance
     */
    public static WorkflowClient getWorkflowClient(WorkflowServiceStubsOptions stubOptions) {
        return getWorkflowClient(stubOptions, null);
    }

    /**
     * Gets a workflow client with custom service and client options.
     * 
     * @param stubOptions Custom service stub options
     * @param clientOptions Custom client options
     * @return WorkflowClient instance
     */
    public static WorkflowClient getWorkflowClient(
            WorkflowServiceStubsOptions stubOptions,
            WorkflowClientOptions clientOptions) {
        initializeWithOptions(stubOptions, clientOptions);
        return client;
    }

    /**
     * Gets the workflow service stubs.
     * Thread-safe and creates service if none exists.
     * 
     * @return WorkflowServiceStubs instance
     */
    public static WorkflowServiceStubs getService() {
        if (service == null) {
            initializeDefault();
        }
        return service;
    }

    /**
     * Initializes the service and client with default configuration.
     * Thread-safe and idempotent.
     */
    private static synchronized void initializeDefault() {
        if (service != null && client != null) {
            return;
        }
        try {
            service = initializeWorkflowServiceStubs(WorkflowServiceStubsOptions.newBuilder());
            client = initializeWorkflowClient(null);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize Temporal configuration", e);
        }
    }

    /**
     * Initializes the service and client with custom options.
     * 
     * @param stubOptions Custom service stub options
     * @param clientOptions Custom client options
     */
    private static synchronized void initializeWithOptions(
            WorkflowServiceStubsOptions stubOptions,
            WorkflowClientOptions clientOptions) {
        try {
            WorkflowServiceStubsOptions.Builder builder = WorkflowServiceStubsOptions.newBuilder()
                .setMetricsScope(stubOptions.getMetricsScope())
                .setEnableKeepAlive(stubOptions.getEnableKeepAlive())
                .setKeepAliveTime(stubOptions.getKeepAliveTime())
                .setKeepAliveTimeout(stubOptions.getKeepAliveTimeout())
                .setKeepAlivePermitWithoutStream(stubOptions.getKeepAlivePermitWithoutStream());

            configureEndpoint(builder);
            
            service = WorkflowServiceStubs.newServiceStubs(builder.build());
            client = initializeWorkflowClient(clientOptions);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize Temporal configuration", e);
        }
    }

    /**
     * Creates workflow service stubs with the given options.
     * 
     * @param options Service stub options builder
     * @return Configured WorkflowServiceStubs
     */
    private static WorkflowServiceStubs initializeWorkflowServiceStubs(WorkflowServiceStubsOptions.Builder options) {
        configureEndpoint(options);
        return WorkflowServiceStubs.newServiceStubs(options.build());
    }

    /**
     * Configures the service endpoint and TLS settings.
     * Uses environment variables if available, defaults otherwise.
     * 
     * @param options Service stub options builder to configure
     */
    private static void configureEndpoint(WorkflowServiceStubsOptions.Builder options) {
        String targetEndpoint = System.getenv(EnvVars.HOST_URL);
        String certPath = System.getenv(EnvVars.TLS_CERT);
        String keyPath = System.getenv(EnvVars.TLS_KEY);

        if (targetEndpoint != null && certPath != null && keyPath != null) {
            // Cloud configuration
            logger.info("Configuring for Temporal Cloud at: " + targetEndpoint);
            options.setTarget(targetEndpoint);
            configureTls(options);
        } else if (targetEndpoint != null) {
            // Cloud URL provided but missing certificates
            logger.warning("Temporal Cloud URL provided but missing TLS certificates. Please set " + 
                         EnvVars.TLS_CERT + " and " + EnvVars.TLS_KEY);
            throw new RuntimeException("Missing TLS certificates for Temporal Cloud connection");
        } else {
            // Local configuration
            logger.info("Using local Temporal server at: " + Defaults.TARGET);
            options.setTarget(Defaults.TARGET);
        }
    }

    /**
     * Configures TLS for secure connections.
     * Uses certificate and key files specified in environment variables.
     * 
     * @param options Service stub options builder to configure
     */
    private static void configureTls(WorkflowServiceStubsOptions.Builder options) {
        String certPath = System.getenv(EnvVars.TLS_CERT);
        String keyPath = System.getenv(EnvVars.TLS_KEY);

        if (certPath != null && keyPath != null) {
            try {
                // Use FileInputStream to read the certificate and key files
                try (InputStream clientCertInputStream = new FileInputStream(certPath);
                     InputStream clientKeyInputStream = new FileInputStream(keyPath)) {
                    
                    // Use SimpleSslContextBuilder.forPKCS8() as recommended for Temporal Cloud
                    SslContext sslContext = SimpleSslContextBuilder
                        .forPKCS8(clientCertInputStream, clientKeyInputStream)
                        .build();
                    
                    options.setSslContext(sslContext);
                    logger.info("TLS configuration successful");
                }
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Failed to read TLS certificate or key files", e);
                throw new RuntimeException("Failed to read TLS certificate or key files", e);
            }
        }
    }

    /**
     * Creates a workflow client with the given options.
     * 
     * @param options Custom client options (optional)
     * @return Configured WorkflowClient
     */
    private static WorkflowClient initializeWorkflowClient(WorkflowClientOptions options) {
        String namespace = getEnvOrDefault(EnvVars.NAMESPACE, Defaults.NAMESPACE);
        WorkflowClientOptions.Builder builder = WorkflowClientOptions.newBuilder()
            .setNamespace(namespace);

        if (options != null) {
            if (options.getInterceptors() != null) {
                builder.setInterceptors(options.getInterceptors());
            }
            if (options.getIdentity() != null) {
                builder.setIdentity(options.getIdentity());
            }
            if (options.getDataConverter() != null) {
                builder.setDataConverter(options.getDataConverter());
            }
        }

        logger.info("Initializing Temporal client with namespace: " + namespace);
        return WorkflowClient.newInstance(service, builder.build());
    }

    /**
     * Gets a value from environment variables or returns default.
     * 
     * @param envVar Environment variable name
     * @param defaultValue Default value if not found
     * @return Value from environment or default
     */
    private static String getEnvOrDefault(String envVar, String defaultValue) {
        String value = System.getenv(envVar);
        return value != null ? value : defaultValue;
    }

    /**
     * Gets the task queue name from environment or default.
     * 
     * @return Task queue name to use
     */
    public static String getTaskQueue() {
        return getEnvOrDefault(EnvVars.TASK_QUEUE, Defaults.TASK_QUEUE);
    }

    private TemporalConfig() {
        // Prevent instantiation - use static methods
    }
} 