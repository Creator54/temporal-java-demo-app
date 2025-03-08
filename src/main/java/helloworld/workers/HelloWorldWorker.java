package helloworld.workers;

import helloworld.config.TemporalConfig;
import helloworld.config.SignozTelemetryUtils;
import helloworld.workflows.impl.HelloWorldWorkflowImpl;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import io.temporal.worker.WorkerFactoryOptions;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Worker process that hosts and executes the Hello World workflow.
 * This class provides:
 * 1. Worker initialization with telemetry
 * 2. Workflow implementation registration
 * 3. Graceful shutdown handling
 * 
 * The worker is configured with:
 * - OpenTelemetry integration for metrics and tracing
 * - Automatic workflow registration
 * - Graceful shutdown with timeout
 * - Error handling and logging
 * 
 * Usage:
 * ```java
 * // Start worker
 * try (HelloWorldWorker worker = new HelloWorldWorker()) {
 *     worker.start();
 * }
 * ```
 */
public class HelloWorldWorker implements AutoCloseable {
    private final WorkerFactory factory;
    private final Worker worker;
    private final CountDownLatch shutdownLatch;
    private volatile boolean isShuttingDown = false;

    /**
     * Creates a new worker with telemetry enabled.
     * Initializes:
     * - OpenTelemetry
     * - Worker factory with interceptors
     * - Worker instance
     * - Workflow registration
     */
    public HelloWorldWorker() {
        // Initialize OpenTelemetry
        SignozTelemetryUtils.initializeTelemetry();

        // Configure service stubs with OpenTelemetry
        WorkflowServiceStubsOptions stubOptions = WorkflowServiceStubsOptions.newBuilder()
            .setMetricsScope(SignozTelemetryUtils.getMetricsScope())
            .build();

        // Configure worker factory with OpenTelemetry interceptor
        WorkerFactoryOptions factoryOptions = WorkerFactoryOptions.newBuilder()
            .setWorkerInterceptors(SignozTelemetryUtils.getWorkerInterceptor())
            .build();

        // Create client and factory with configured options
        this.factory = WorkerFactory.newInstance(
            TemporalConfig.getWorkflowClient(stubOptions),
            factoryOptions
        );

        this.worker = factory.newWorker(TemporalConfig.getTaskQueue());
        this.worker.registerWorkflowImplementationTypes(HelloWorldWorkflowImpl.class);
        this.shutdownLatch = new CountDownLatch(1);
    }

    /**
     * Starts the worker and keeps it running.
     * Sets up:
     * - Shutdown hook for graceful termination
     * - Error handling
     * - Status logging
     */
    public void start() {
        try {
            factory.start();
            System.out.println("Worker started for task queue: " + TemporalConfig.getTaskQueue());
            System.out.println("Metrics and traces are being exported to SigNoz");
            
            // Add shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (!isShuttingDown) {
                    System.out.println("Shutting down worker...");
                    close();
                    shutdownLatch.countDown();
                }
            }));

            // Keep the worker running
            shutdownLatch.await();
        } catch (Exception e) {
            System.err.println("Error in worker: " + e.getMessage());
            close();
        }
    }

    /**
     * Performs graceful shutdown of the worker.
     * Shutdown steps:
     * 1. Attempt graceful shutdown with timeout
     * 2. Force shutdown if graceful fails
     * 3. Clean up resources
     */
    @Override
    public void close() {
        if (isShuttingDown) {
            return;
        }
        isShuttingDown = true;

        try {
            // First attempt graceful shutdown
            System.out.println("Initiating graceful shutdown...");
            factory.shutdown();
            factory.awaitTermination(3, TimeUnit.SECONDS);
            
            // Then shutdown the service
            System.out.println("Shutting down gRPC connections...");
            TemporalConfig.getService().shutdown();
            
            // If graceful shutdown doesn't complete quickly, force shutdown
            if (!factory.isShutdown()) {
                System.out.println("Force shutting down worker factory...");
                factory.shutdownNow();
                factory.awaitTermination(2, TimeUnit.SECONDS);
            }

            // Force shutdown service if still running
            System.out.println("Force shutting down service...");
            TemporalConfig.getService().shutdownNow();
            System.out.println("Worker shutdown completed");
        } catch (Exception e) {
            System.err.println("Error during shutdown: " + e.getMessage());
            // Last resort: force shutdown everything
            try {
                factory.shutdownNow();
                TemporalConfig.getService().shutdownNow();
            } catch (Exception ignored) {
                // Ignore any errors during force shutdown
            }
        }
    }

    /**
     * Main entry point for the worker process.
     * Creates and starts a worker instance.
     * 
     * @param args Command line arguments (not used)
     */
    public static void main(String[] args) {
        try (HelloWorldWorker worker = new HelloWorldWorker()) {
            worker.start();
        }
    }
} 