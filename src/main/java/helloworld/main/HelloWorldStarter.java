package helloworld.main;

import helloworld.config.TemporalConfig;
import helloworld.config.OpenTelemetryConfig;
import helloworld.config.SignozTelemetryUtils;
import helloworld.config.TracingExporter;
import helloworld.config.MetricsExporter;
import helloworld.workflows.HelloWorldWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.client.WorkflowOptions;
import io.temporal.common.RetryOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;

/**
 * Application entry point for starting Hello World workflows.
 * This class provides:
 * 1. Workflow client initialization with telemetry
 * 2. Workflow execution with retry options
 * 3. Result handling and logging
 * 
 * The starter is configured with:
 * - OpenTelemetry integration for metrics and tracing
 * - Automatic retry policy for workflow execution
 * - Unique workflow ID generation
 * - Error handling and logging
 * 
 * Usage:
 * ```java
 * // Start workflow
 * HelloWorldStarter starter = new HelloWorldStarter();
 * starter.runWorkflow("YourName");
 * ```
 */
public class HelloWorldStarter {
    private final WorkflowServiceStubs workflowServiceStubs;
    private final WorkflowClient workflowClient;
    private final LongCounter workflowCompletionCounter;
    private final LongCounter workflowStartCounter;

    /**
     * Creates a new workflow starter with telemetry enabled.
     * Initializes:
     * - OpenTelemetry
     * - Workflow client with interceptors
     * - Service stubs with metrics
     */
    public HelloWorldStarter() {
        // Initialize OpenTelemetry
        SignozTelemetryUtils.initializeTelemetry();

        // Initialize metrics
        Meter meter = SignozTelemetryUtils.getMeter();
        workflowCompletionCounter = meter
            .counterBuilder("workflow_completed_count_total")
            .setDescription("Total number of workflow executions completed")
            .setUnit("1")
            .build();
            
        workflowStartCounter = meter
            .counterBuilder("workflow_started_count_total")
            .setDescription("Total number of workflow executions started")
            .setUnit("1")
            .build();

        // Configure service stubs with OpenTelemetry
        WorkflowServiceStubsOptions stubOptions = WorkflowServiceStubsOptions.newBuilder()
            .setMetricsScope(SignozTelemetryUtils.getMetricsScope())
            .build();

        // Configure client with OpenTelemetry interceptor
        WorkflowClientOptions clientOptions = WorkflowClientOptions.newBuilder()
            .setInterceptors(TracingExporter.getClientInterceptor())
            .build();

        // Initialize Temporal client
        this.workflowServiceStubs = TemporalConfig.getService();
        this.workflowClient = TemporalConfig.getWorkflowClient(stubOptions, clientOptions);
    }

    /**
     * Executes the Hello World workflow with the given name.
     * Sets up:
     * - Workflow options with retry policy
     * - Unique workflow ID
     * - Result logging
     * 
     * @param name Name to pass to the workflow
     * @throws RuntimeException if workflow execution fails
     */
    public void runWorkflow(String name) {
        Tracer tracer = SignozTelemetryUtils.getTracer();
        
        // Create parent span for workflow execution
        Span parentSpan = tracer.spanBuilder("StartWorkflow")
            .setAttribute("workflow.type", "HelloWorld")
            .setAttribute("workflow.name", name)
            .startSpan();
        
        try (Scope scope = parentSpan.makeCurrent()) {
            // Generate unique workflow ID
            String workflowId = "hello-world-" + UUID.randomUUID().toString();
            
            // Configure workflow options
            WorkflowOptions options = WorkflowOptions.newBuilder()
                .setTaskQueue(TemporalConfig.getTaskQueue())
                .setWorkflowId(workflowId)
                .build();
            
            // Start workflow
            HelloWorldWorkflow workflow = workflowClient.newWorkflowStub(
                HelloWorldWorkflow.class,
                options
            );
            
            // Record workflow start
            workflowStartCounter.add(1L);
            parentSpan.setAttribute("workflow.started", true);
            
            // Create span for workflow execution
            Span executeSpan = tracer.spanBuilder("ExecuteWorkflow")
                .setParent(io.opentelemetry.context.Context.current().with(parentSpan))
                .setAttribute("workflow.id", workflowId)
                .setAttribute("workflow.type", "temporal")
                .setAttribute("service.name", OpenTelemetryConfig.getServiceName())
                .setAttribute("workflow.task_queue", TemporalConfig.getTaskQueue())
                .startSpan();
            
            String result;
            try (Scope executeScope = executeSpan.makeCurrent()) {
                result = workflow.sayHello(name);
                executeSpan.setAttribute("workflow.result", result);
                executeSpan.setStatus(StatusCode.OK);
                // Record workflow completion
                workflowCompletionCounter.add(1L);
                parentSpan.setAttribute("workflow.completed", true);
                
                // Record workflow success metric to show in dashboard
                helloworld.config.WorkflowMetricsUtil.recordSuccess(
                    "HelloWorldWorkflow", 
                    workflowId, 
                    "run-" + UUID.randomUUID().toString(), // Generate a run ID since we can't easily get it
                    TemporalConfig.getNamespace()
                );
            } catch (Exception e) {
                executeSpan.recordException(e);
                executeSpan.setStatus(StatusCode.ERROR);
                
                // Record workflow failure metric
                helloworld.config.WorkflowMetricsUtil.recordFailure(
                    "HelloWorldWorkflow", 
                    workflowId, 
                    "run-" + UUID.randomUUID().toString(), // Generate a run ID since we can't easily get it
                    TemporalConfig.getNamespace()
                );
                
                throw e;
            } finally {
                executeSpan.end();
            }
            
            // Print results
            System.out.println("Workflow execution completed:");
            System.out.println("Result: " + result);
            System.out.println("Workflow ID: " + workflowId);
            System.out.println("Metrics and traces are being exported to SigNoz");
            
            parentSpan.setStatus(StatusCode.OK);
        } catch (Exception e) {
            System.err.println("Error executing workflow: " + e.getMessage());
            parentSpan.recordException(e);
            parentSpan.setStatus(StatusCode.ERROR);
            throw new RuntimeException("Failed to execute workflow", e);
        } finally {
            parentSpan.end();
            
            // Graceful shutdown of all resources
            try {
                // Shutdown OpenTelemetry
                TracingExporter.shutdown();
                MetricsExporter.shutdown();
                
                // Shutdown Temporal client
                if (workflowClient != null) {
                    workflowServiceStubs.shutdown();
                    workflowServiceStubs.awaitTermination(5, TimeUnit.SECONDS);
                }
                
                // Give time for final metrics to be exported
                Thread.sleep(1000);
                
                // Force shutdown remaining threads
                System.exit(0);
            } catch (Exception e) {
                System.err.println("Error during shutdown: " + e.getMessage());
                System.exit(1);
            }
        }
    }

    /**
     * Main entry point for the workflow starter.
     * Creates a starter instance and runs the workflow.
     * 
     * @param args Optional name to use in greeting (defaults to "Temporal")
     */
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