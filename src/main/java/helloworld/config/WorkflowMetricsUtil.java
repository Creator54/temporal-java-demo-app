package helloworld.config;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import java.util.logging.Logger;

/**
 * Utility class for tracking and reporting workflow metrics.
 * Provides counters for different workflow completion states.
 */
public class WorkflowMetricsUtil {
    private static final Logger logger = Logger.getLogger(WorkflowMetricsUtil.class.getName());
    
    // OpenTelemetry counters for workflow states
    private static LongCounter workflowSuccessCounter;
    private static LongCounter workflowFailedCounter;
    private static LongCounter workflowTimeoutCounter; 
    private static LongCounter workflowTerminateCounter;
    private static LongCounter workflowCancelCounter;
    
    // Dashboard-specific operation counters
    private static LongCounter serviceRequestsCounter;
    private static LongCounter serviceErrorsCounter;
    private static LongCounter serviceErrorWithTypeCounter;
    private static LongCounter restartsCounter;
    
    // Common attribute keys
    public static final AttributeKey<String> WORKFLOW_TYPE = AttributeKey.stringKey("workflow_type");
    public static final AttributeKey<String> WORKFLOW_ID = AttributeKey.stringKey("workflow_id");
    public static final AttributeKey<String> RUN_ID = AttributeKey.stringKey("run_id");
    public static final AttributeKey<String> NAMESPACE = AttributeKey.stringKey("namespace");
    public static final AttributeKey<String> OPERATION = AttributeKey.stringKey("operation");
    public static final AttributeKey<String> SERVICE_TYPE = AttributeKey.stringKey("temporal_service_type");
    public static final AttributeKey<String> ERROR_TYPE = AttributeKey.stringKey("error_type");
    
    // Private fields for timeout metrics
    private static LongCounter scheduleToStartTimeoutCounter;
    private static LongCounter startToCloseTimeoutCounter;
    
    /**
     * Initializes all workflow metrics counters.
     * Should be called during application startup.
     */
    public static void initializeMetrics() {
        Meter meter = SignozTelemetryUtils.getMeter();
        if (meter == null) {
            System.err.println("Meter is null, cannot initialize metrics");
            return;
        }
        
        // Create counters for workflow completion states
        workflowSuccessCounter = meter.counterBuilder("workflow_success")
            .setDescription("Count of successfully completed workflow executions")
            .setUnit("{execution}")
            .build();
            
        workflowFailedCounter = meter.counterBuilder("workflow_failed")
            .setDescription("Count of failed workflow executions")
            .setUnit("{execution}")
            .build();
            
        workflowTimeoutCounter = meter.counterBuilder("workflow_timeout")
            .setDescription("Count of timed out workflow executions")
            .setUnit("{execution}")
            .build();
            
        workflowTerminateCounter = meter.counterBuilder("workflow_terminate")
            .setDescription("Count of terminated workflow executions")
            .setUnit("{execution}")
            .build();
            
        workflowCancelCounter = meter.counterBuilder("workflow_cancel")
            .setDescription("Count of canceled workflow executions")
            .setUnit("{execution}")
            .build();
            
        // Initialize service_requests counter
        serviceRequestsCounter = meter
                .counterBuilder("service_requests")
                .setDescription("Counts the number of service requests")
                .build();
                
        // Initialize service_errors counter
        serviceErrorsCounter = meter
                .counterBuilder("service_errors")
                .setDescription("Counts the number of service errors")
                .build();
                
        // Initialize service_error_with_type counter for the dashboard
        serviceErrorWithTypeCounter = meter
                .counterBuilder("service_error_with_type")
                .setDescription("Counts service errors by error type")
                .build();
                
        // Initialize restarts counter
        restartsCounter = meter
                .counterBuilder("restarts")
                .setDescription("Counts the number of service restarts")
                .build();
                
        // Initialize timeout metrics
        scheduleToStartTimeoutCounter = meter
                .counterBuilder("schedule_to_start_timeout")
                .setDescription("Schedule to start timeout metrics")
                .build();
                
        startToCloseTimeoutCounter = meter
                .counterBuilder("start_to_close_timeout")
                .setDescription("Start to close timeout metrics")
                .build();
                
        logger.info("Workflow metrics initialized successfully");
    }
    
    /**
     * Records a service request operation for dashboard metrics.
     *
     * @param operation The operation type (AddActivityTask, RecordActivityTaskStarted, ResponseActivityCompleted)
     */
    public static void recordServiceRequest(String operation) {
        if (serviceRequestsCounter == null) {
            logger.warning("Service metrics not initialized. Call initializeMetrics() first.");
            return;
        }
        
        Attributes attributes = Attributes.of(
            OPERATION, operation
        );
        
        serviceRequestsCounter.add(1, attributes);
        logger.fine("Recorded service request operation: " + operation);
    }
    
    /**
     * Records AddActivityTask operation for dashboard compatibility.
     */
    public static void recordAddActivityTask() {
        recordServiceRequest("AddActivityTask");
    }
    
    /**
     * Records RecordActivityTaskStarted operation for dashboard compatibility.
     */
    public static void recordRecordActivityTaskStarted() {
        recordServiceRequest("RecordActivityTaskStarted");
    }
    
    /**
     * Records ResponseActivityCompleted operation for dashboard compatibility.
     */
    public static void recordResponseActivityCompleted() {
        recordServiceRequest("RespondActivityTaskCompleted");
    }
    
    /**
     * Records RespondActivityTaskFailed operation for dashboard compatibility.
     */
    public static void recordRespondActivityTaskFailed() {
        recordServiceRequest("RespondActivityTaskFailed");
    }
    
    /**
     * Records RespondActivityTaskCanceled operation for dashboard compatibility.
     */
    public static void recordRespondActivityTaskCanceled() {
        recordServiceRequest("RespondActivityTaskCanceled");
    }
    
    /**
     * Records a successful workflow completion.
     * 
     * @param workflowType The type of workflow
     * @param workflowId The ID of the workflow
     * @param runId The run ID of the workflow
     * @param namespace The namespace of the workflow
     */
    public static void recordSuccess(String workflowType, String workflowId, String runId, String namespace) {
        if (workflowSuccessCounter == null) {
            logger.warning("Workflow metrics not initialized. Call initializeMetrics() first.");
            return;
        }
        
        Attributes attributes = Attributes.of(
            WORKFLOW_TYPE, workflowType,
            WORKFLOW_ID, workflowId,
            RUN_ID, runId,
            NAMESPACE, namespace,
            OPERATION, "CompletionStats"
        );
        
        workflowSuccessCounter.add(1, attributes);
        logger.fine("Recorded workflow success: " + workflowType + " / " + workflowId);
    }
    
    /**
     * Records a failed workflow completion.
     * 
     * @param workflowType The type of workflow
     * @param workflowId The ID of the workflow
     * @param runId The run ID of the workflow
     * @param namespace The namespace of the workflow
     */
    public static void recordFailure(String workflowType, String workflowId, String runId, String namespace) {
        if (workflowFailedCounter == null) {
            logger.warning("Workflow metrics not initialized. Call initializeMetrics() first.");
            return;
        }
        
        Attributes attributes = Attributes.of(
            WORKFLOW_TYPE, workflowType,
            WORKFLOW_ID, workflowId,
            RUN_ID, runId,
            NAMESPACE, namespace,
            OPERATION, "CompletionStats"
        );
        
        workflowFailedCounter.add(1, attributes);
        logger.fine("Recorded workflow failure: " + workflowType + " / " + workflowId);
    }
    
    /**
     * Records a timed out workflow.
     * 
     * @param workflowType The type of workflow
     * @param workflowId The ID of the workflow
     * @param runId The run ID of the workflow
     * @param namespace The namespace of the workflow
     */
    public static void recordTimeout(String workflowType, String workflowId, String runId, String namespace) {
        if (workflowTimeoutCounter == null) {
            logger.warning("Workflow metrics not initialized. Call initializeMetrics() first.");
            return;
        }
        
        Attributes attributes = Attributes.of(
            WORKFLOW_TYPE, workflowType,
            WORKFLOW_ID, workflowId,
            RUN_ID, runId,
            NAMESPACE, namespace,
            OPERATION, "CompletionStats"
        );
        
        workflowTimeoutCounter.add(1, attributes);
        logger.fine("Recorded workflow timeout: " + workflowType + " / " + workflowId);
    }
    
    /**
     * Records a terminated workflow.
     * 
     * @param workflowType The type of workflow
     * @param workflowId The ID of the workflow
     * @param runId The run ID of the workflow
     * @param namespace The namespace of the workflow
     */
    public static void recordTermination(String workflowType, String workflowId, String runId, String namespace) {
        if (workflowTerminateCounter == null) {
            logger.warning("Workflow metrics not initialized. Call initializeMetrics() first.");
            return;
        }
        
        Attributes attributes = Attributes.of(
            WORKFLOW_TYPE, workflowType,
            WORKFLOW_ID, workflowId,
            RUN_ID, runId,
            NAMESPACE, namespace,
            OPERATION, "CompletionStats"
        );
        
        workflowTerminateCounter.add(1, attributes);
        logger.fine("Recorded workflow termination: " + workflowType + " / " + workflowId);
    }
    
    /**
     * Records a canceled workflow.
     * 
     * @param workflowType The type of workflow
     * @param workflowId The ID of the workflow
     * @param runId The run ID of the workflow
     * @param namespace The namespace of the workflow
     */
    public static void recordCancellation(String workflowType, String workflowId, String runId, String namespace) {
        if (workflowCancelCounter == null) {
            logger.warning("Workflow metrics not initialized. Call initializeMetrics() first.");
            return;
        }
        
        Attributes attributes = Attributes.of(
            WORKFLOW_TYPE, workflowType,
            WORKFLOW_ID, workflowId,
            RUN_ID, runId,
            NAMESPACE, namespace,
            OPERATION, "CompletionStats"
        );
        
        workflowCancelCounter.add(1, attributes);
        logger.fine("Recorded workflow cancellation: " + workflowType + " / " + workflowId);
    }
    
    // Error metrics methods
    public static void recordAddActivityTaskError() {
        recordServiceError("AddActivityTask");
    }
    
    public static void recordRecordActivityTaskStartedError() {
        recordServiceError("RecordActivityTaskStarted");
    }
    
    public static void recordRespondActivityTaskCompletedError() {
        recordServiceError("RespondActivityTaskCompleted");
    }
    
    public static void recordRespondActivityTaskFailedError() {
        recordServiceError("RespondActivityTaskFailed");
    }
    
    public static void recordRespondActivityTaskCanceledError() {
        recordServiceError("RespondActivityTaskCanceled");
    }
    
    private static void recordServiceError(String operation) {
        if (serviceErrorsCounter != null) {
            serviceErrorsCounter.add(1, Attributes.of(OPERATION, operation));
        } else {
            logger.warning("Service errors counter not initialized. Call initializeMetrics() first.");
        }
    }
    
    /**
     * Records a service restart event.
     * 
     * @param serviceType The type of service being restarted (e.g., "worker", "workflow-service", etc.)
     */
    public static void recordServiceRestart(String serviceType) {
        if (restartsCounter == null) {
            logger.warning("Restart metrics not initialized. Call initializeMetrics() first.");
            return;
        }
        
        Attributes attributes = Attributes.of(
            SERVICE_TYPE, serviceType
        );
        
        restartsCounter.add(1, attributes);
        logger.info("Recorded service restart: " + serviceType);
    }

    /**
     * Records AddWorkflowTask operation.
     */
    public static void recordAddWorkflowTask() {
        recordServiceRequest("AddWorkflowTask");
    }

    /**
     * Records RecordWorkflowTaskStarted operation.
     */
    public static void recordRecordWorkflowTaskStarted() {
        recordServiceRequest("RecordWorkflowTaskStarted");
    }

    /**
     * Records RespondWorkflowTaskCompleted operation.
     */
    public static void recordRespondWorkflowTaskCompleted() {
        recordServiceRequest("RespondWorkflowTaskCompleted");
    }

    /**
     * Records RespondWorkflowTaskFailed operation.
     */
    public static void recordRespondWorkflowTaskFailed() {
        recordServiceRequest("RespondWorkflowTaskFailed");
    }

    /**
     * Records TimerActiveTaskWorkflowTimeout operation.
     */
    public static void recordTimerActiveTaskWorkflowTimeout() {
        recordServiceRequest("TimerActiveTaskWorkflowTimeout");
    }

    // Error metrics for workflow tasks
    public static void recordAddWorkflowTaskError() {
        recordServiceError("AddWorkflowTask");
    }

    public static void recordRecordWorkflowTaskStartedError() {
        recordServiceError("RecordWorkflowTaskStarted");
    }

    public static void recordRespondWorkflowTaskCompletedError() {
        recordServiceError("RespondWorkflowTaskCompleted");
    }

    public static void recordRespondWorkflowTaskFailedError() {
        recordServiceError("RespondWorkflowTaskFailed");
    }

    /**
     * Records a schedule_to_start_timeout metric.
     * 
     * @param operation The operation (e.g., "TimerActiveTaskWorkflowTimeout")
     */
    public static void recordScheduleToStartTimeout(String operation) {
        if (scheduleToStartTimeoutCounter == null) {
            logger.warning("Timeout metrics not initialized. Call initializeMetrics() first.");
            return;
        }
        
        Attributes attributes = Attributes.of(
            OPERATION, operation
        );
        
        scheduleToStartTimeoutCounter.add(1, attributes);
        logger.fine("Recorded schedule to start timeout: " + operation);
    }

    /**
     * Records a start_to_close_timeout metric.
     * 
     * @param operation The operation (e.g., "TimerActiveTaskWorkflowTimeout")
     */
    public static void recordStartToCloseTimeout(String operation) {
        if (startToCloseTimeoutCounter == null) {
            logger.warning("Timeout metrics not initialized. Call initializeMetrics() first.");
            return;
        }
        
        Attributes attributes = Attributes.of(
            OPERATION, operation
        );
        
        startToCloseTimeoutCounter.add(1, attributes);
        logger.fine("Recorded start to close timeout: " + operation);
    }

    /**
     * Records schedule to start timeout for TimerActiveTaskWorkflowTimeout.
     */
    public static void recordScheduleToStartWorkflowTimeout() {
        recordScheduleToStartTimeout("TimerActiveTaskWorkflowTimeout");
    }

    /**
     * Records start to close timeout for TimerActiveTaskWorkflowTimeout.
     */
    public static void recordStartToCloseWorkflowTimeout() {
        recordStartToCloseTimeout("TimerActiveTaskWorkflowTimeout");
    }

    /**
     * Records an error with a specific error type.
     * 
     * @param errorType The type of error that occurred
     */
    public static void recordErrorWithType(String errorType) {
        if (serviceErrorWithTypeCounter == null) {
            logger.warning("Error with type metrics not initialized. Call initializeMetrics() first.");
            return;
        }
        
        Attributes attributes = Attributes.of(
            ERROR_TYPE, errorType
        );
        
        serviceErrorWithTypeCounter.add(1, attributes);
        logger.fine("Recorded error with type: " + errorType);
    }
    
    /**
     * Records a validation error.
     */
    public static void recordValidationError() {
        recordErrorWithType("validation");
    }
    
    /**
     * Records a timeout error.
     */
    public static void recordTimeoutError() {
        recordErrorWithType("timeout");
    }
    
    /**
     * Records a business rule error.
     */
    public static void recordBusinessRuleError() {
        recordErrorWithType("business_rule");
    }
    
    /**
     * Records a system error.
     */
    public static void recordSystemError() {
        recordErrorWithType("system");
    }

    /**
     * Cleans up all metrics resources. Should be called during application shutdown.
     */
    public static void cleanup() {
        logger.info("Cleaning up workflow metrics resources...");
        
        // Reset all counters to null to allow garbage collection
        workflowSuccessCounter = null;
        workflowFailedCounter = null;
        workflowTimeoutCounter = null;
        workflowTerminateCounter = null;
        workflowCancelCounter = null;
        serviceRequestsCounter = null;
        serviceErrorsCounter = null;
        serviceErrorWithTypeCounter = null;
        restartsCounter = null;
        scheduleToStartTimeoutCounter = null;
        startToCloseTimeoutCounter = null;
        
        logger.info("Workflow metrics resources cleaned up successfully");
    }
} 