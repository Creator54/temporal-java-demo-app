package helloworld.main;

import helloworld.config.TemporalConfig;
import helloworld.config.SignozTelemetryUtils;
import helloworld.workflows.HelloWorldWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.client.WorkflowOptions;
import io.temporal.common.RetryOptions;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import java.time.Duration;
import java.util.UUID;

public class HelloWorldStarter {
    private final WorkflowClient client;

    public HelloWorldStarter() {
        // Initialize OpenTelemetry
        SignozTelemetryUtils.initializeTelemetry();

        // Configure service stubs with OpenTelemetry
        WorkflowServiceStubsOptions stubOptions = WorkflowServiceStubsOptions.newBuilder()
            .setMetricsScope(SignozTelemetryUtils.getMetricsScope())
            .build();

        // Configure client with OpenTelemetry interceptor
        WorkflowClientOptions clientOptions = WorkflowClientOptions.newBuilder()
            .setInterceptors(SignozTelemetryUtils.getClientInterceptor())
            .build();

        // Create client with configured options
        this.client = TemporalConfig.getWorkflowClient(stubOptions, clientOptions);
    }

    public void runWorkflow(String name) {
        try {
            // Create workflow options with retry policy
            WorkflowOptions options = WorkflowOptions.newBuilder()
                    .setTaskQueue(TemporalConfig.getTaskQueue())
                    .setWorkflowId("hello-world-" + UUID.randomUUID())
                    .setRetryOptions(RetryOptions.newBuilder()
                        .setInitialInterval(Duration.ofSeconds(1))
                        .setMaximumInterval(Duration.ofSeconds(10))
                        .setBackoffCoefficient(2.0)
                        .setMaximumAttempts(3)
                        .build())
                    .build();

            // Create and execute the workflow
            HelloWorldWorkflow workflow = client.newWorkflowStub(HelloWorldWorkflow.class, options);
            String result = workflow.sayHello(name);
            
            // Print results
            System.out.println("Workflow execution completed:");
            System.out.println("Result: " + result);
            System.out.println("Workflow ID: " + options.getWorkflowId());
            System.out.println("Metrics and traces are being exported to SigNoz");
        } catch (Exception e) {
            System.err.println("Error executing workflow: " + e.getMessage());
            throw new RuntimeException("Failed to execute workflow", e);
        }
    }

    public static void main(String[] args) {
        try {
            HelloWorldStarter starter = new HelloWorldStarter();
            String name = args.length > 0 ? args[0] : "Temporal";
            starter.runWorkflow(name);
        } catch (Exception e) {
            System.err.println("Application error: " + e.getMessage());
            System.exit(1);
        }
    }
} 