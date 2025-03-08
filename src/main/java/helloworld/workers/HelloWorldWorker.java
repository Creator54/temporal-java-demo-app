package helloworld.workers;

import helloworld.config.TemporalConfig;
import helloworld.config.SignozMetricsUtils;
import helloworld.config.SignozTracingUtils;
import helloworld.workflows.impl.HelloWorldWorkflowImpl;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import io.temporal.worker.WorkerFactoryOptions;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class HelloWorldWorker implements AutoCloseable {
    private final WorkerFactory factory;
    private final Worker worker;
    private final CountDownLatch shutdownLatch;
    private volatile boolean isShuttingDown = false;

    public HelloWorldWorker() {
        // Configure service stubs with metrics
        WorkflowServiceStubsOptions stubOptions = WorkflowServiceStubsOptions.newBuilder()
            .setMetricsScope(SignozMetricsUtils.getSignozMetricsScope())
            .build();

        // Configure worker factory with OpenTelemetry tracing
        WorkerFactoryOptions factoryOptions = WorkerFactoryOptions.newBuilder()
            .setWorkerInterceptors(SignozTracingUtils.getWorkerInterceptor())
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

    public static void main(String[] args) {
        try (HelloWorldWorker worker = new HelloWorldWorker()) {
            worker.start();
        }
    }
} 