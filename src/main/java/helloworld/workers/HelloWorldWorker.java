package helloworld.workers;

import helloworld.config.TemporalConfig;
import helloworld.workflows.impl.HelloWorldWorkflowImpl;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class HelloWorldWorker implements AutoCloseable {
    private final WorkerFactory factory;
    private final Worker worker;
    private final CountDownLatch shutdownLatch;

    public HelloWorldWorker() {
        this.factory = WorkerFactory.newInstance(TemporalConfig.getWorkflowClient());
        this.worker = factory.newWorker(TemporalConfig.getTaskQueue());
        this.worker.registerWorkflowImplementationTypes(HelloWorldWorkflowImpl.class);
        this.shutdownLatch = new CountDownLatch(1);
    }

    public void start() {
        try {
            factory.start();
            System.out.println("Worker started for task queue: " + TemporalConfig.getTaskQueue());
            
            // Add shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Shutting down worker...");
                close();
                shutdownLatch.countDown();
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
        factory.shutdown();
        factory.awaitTermination(30, TimeUnit.SECONDS);
    }

    public static void main(String[] args) {
        try (HelloWorldWorker worker = new HelloWorldWorker()) {
            worker.start();
        }
    }
} 