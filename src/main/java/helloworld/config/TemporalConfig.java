package helloworld.config;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import java.io.File;

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
    // Default configuration values
    private static final class Defaults {
        static final String TARGET = "localhost:7233";
        static final String NAMESPACE = "default";
        static final String TASK_QUEUE = "hello-world-task-queue";
    }

    // Environment variable names
    private static final class EnvVars {
        static final String HOST = "TEMPORAL_HOST_ADDRESS";
        static final String NAMESPACE = "TEMPORAL_NAMESPACE";
        static final String TASK_QUEUE = "TEMPORAL_TASK_QUEUE";
        static final String CERT_PATH = "TEMPORAL_CERT_PATH";
        static final String KEY_PATH = "TEMPORAL_KEY_PATH";
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
    public static WorkflowClient getWorkflowClient(WorkflowServiceStubsOptions stubOptions, WorkflowClientOptions clientOptions) {
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
            // Create a new builder with the provided options
            WorkflowServiceStubsOptions.Builder builder = WorkflowServiceStubsOptions.newBuilder()
                .setMetricsScope(stubOptions.getMetricsScope())
                .setEnableKeepAlive(stubOptions.getEnableKeepAlive())
                .setKeepAliveTime(stubOptions.getKeepAliveTime())
                .setKeepAliveTimeout(stubOptions.getKeepAliveTimeout())
                .setKeepAlivePermitWithoutStream(stubOptions.getKeepAlivePermitWithoutStream());

            // Configure endpoint and TLS
            configureEndpoint(builder);
            
            // Initialize services with the combined options
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
        String targetEndpoint = System.getenv(EnvVars.HOST);
        if (targetEndpoint != null) {
            options.setTarget(targetEndpoint);
            configureTls(options);
        } else {
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
        String certPath = System.getenv(EnvVars.CERT_PATH);
        String keyPath = System.getenv(EnvVars.KEY_PATH);

        if (certPath != null && keyPath != null) {
            try {
                options.setSslContext(
                    io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder.forClient()
                        .keyManager(new File(certPath), new File(keyPath))
                        .build()
                );
            } catch (Exception e) {
                throw new RuntimeException("Failed to configure TLS", e);
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