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

    private static final WorkflowServiceStubs service;
    private static final WorkflowClient client;

    static {
        try {
            service = initializeWorkflowServiceStubs();
            client = initializeWorkflowClient();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize Temporal configuration", e);
        }
    }

    private static WorkflowServiceStubs initializeWorkflowServiceStubs() {
        WorkflowServiceStubsOptions.Builder options = WorkflowServiceStubsOptions.newBuilder();
        
        String targetEndpoint = System.getenv(EnvVars.HOST);
        if (targetEndpoint != null) {
            options.setTarget(targetEndpoint);
            configureTls(options);
        } else {
            options.setTarget(Defaults.TARGET);
        }

        return WorkflowServiceStubs.newServiceStubs(options.build());
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

    private static WorkflowClient initializeWorkflowClient() {
        String namespace = getEnvOrDefault(EnvVars.NAMESPACE, Defaults.NAMESPACE);
        return WorkflowClient.newInstance(service,
            WorkflowClientOptions.newBuilder()
                .setNamespace(namespace)
                .build());
    }

    private static String getEnvOrDefault(String envVar, String defaultValue) {
        String value = System.getenv(envVar);
        return value != null ? value : defaultValue;
    }

    public static String getTaskQueue() {
        return getEnvOrDefault(EnvVars.TASK_QUEUE, Defaults.TASK_QUEUE);
    }

    public static WorkflowClient getWorkflowClient() {
        return client;
    }

    public static WorkflowServiceStubs getService() {
        return service;
    }

    private TemporalConfig() {
        // Prevent instantiation
    }
} 