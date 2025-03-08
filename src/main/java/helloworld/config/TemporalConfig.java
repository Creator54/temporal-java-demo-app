package helloworld.config;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import java.io.File;

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

    public static WorkflowClient getWorkflowClient() {
        if (client == null) {
            initializeDefault();
        }
        return client;
    }

    public static WorkflowClient getWorkflowClient(WorkflowServiceStubsOptions stubOptions) {
        return getWorkflowClient(stubOptions, null);
    }

    public static WorkflowClient getWorkflowClient(WorkflowServiceStubsOptions stubOptions, WorkflowClientOptions clientOptions) {
        initializeWithOptions(stubOptions, clientOptions);
        return client;
    }

    public static WorkflowServiceStubs getService() {
        if (service == null) {
            initializeDefault();
        }
        return service;
    }

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

    private static WorkflowServiceStubs initializeWorkflowServiceStubs(WorkflowServiceStubsOptions.Builder options) {
        configureEndpoint(options);
        return WorkflowServiceStubs.newServiceStubs(options.build());
    }

    private static void configureEndpoint(WorkflowServiceStubsOptions.Builder options) {
        String targetEndpoint = System.getenv(EnvVars.HOST);
        if (targetEndpoint != null) {
            options.setTarget(targetEndpoint);
            configureTls(options);
        } else {
            options.setTarget(Defaults.TARGET);
        }
    }

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

    private static String getEnvOrDefault(String envVar, String defaultValue) {
        String value = System.getenv(envVar);
        return value != null ? value : defaultValue;
    }

    public static String getTaskQueue() {
        return getEnvOrDefault(EnvVars.TASK_QUEUE, Defaults.TASK_QUEUE);
    }

    private TemporalConfig() {
        // Prevent instantiation
    }
} 