package helloworld.workflows.impl;

import helloworld.workflows.HelloWorldWorkflow;

/**
 * Implementation of the Hello World workflow.
 * This class provides a simple example of a Temporal workflow that:
 * 1. Receives a name as input
 * 2. Constructs a greeting message
 * 3. Returns the greeting
 * 
 * While this is a basic example, it demonstrates:
 * - Workflow method implementation
 * - Input parameter handling
 * - String manipulation in workflows
 * 
 * In a real application, this workflow could:
 * - Call activities to perform actual work
 * - Handle timeouts and retries
 * - Maintain workflow state
 * - Process signals and queries
 */
public class HelloWorldWorkflowImpl implements HelloWorldWorkflow {
    /**
     * Creates a greeting message for the given name.
     * This implementation simply concatenates "Hello" with the name.
     * 
     * @param name Name to include in greeting
     * @return Formatted greeting message
     */
    @Override
    public String sayHello(String name) {
        return "Hello " + name + "!";
    }
} 