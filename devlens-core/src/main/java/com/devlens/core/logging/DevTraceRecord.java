package com.devlens.core.logging;

/**
 * Data holder used to accumulate information for @DevTrace formatted output.
 */
public class DevTraceRecord {

    private String className;
    private String methodName;
    private String threadName;
    private long threadId;
    private long startTimestamp;
    private long endTimestamp;
    private long executionTimeMs;
    private String status;
    private String inputParameters;
    private String outputValue;
    private String correlationId;
    private String exceptionInfo;

    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }

    public String getMethodName() { return methodName; }
    public void setMethodName(String methodName) { this.methodName = methodName; }

    public String getThreadName() { return threadName; }
    public void setThreadName(String threadName) { this.threadName = threadName; }

    public long getThreadId() { return threadId; }
    public void setThreadId(long threadId) { this.threadId = threadId; }

    public long getStartTimestamp() { return startTimestamp; }
    public void setStartTimestamp(long startTimestamp) { this.startTimestamp = startTimestamp; }

    public long getEndTimestamp() { return endTimestamp; }
    public void setEndTimestamp(long endTimestamp) { this.endTimestamp = endTimestamp; }

    public long getExecutionTimeMs() { return executionTimeMs; }
    public void setExecutionTimeMs(long executionTimeMs) { this.executionTimeMs = executionTimeMs; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getInputParameters() { return inputParameters; }
    public void setInputParameters(String inputParameters) { this.inputParameters = inputParameters; }

    public String getOutputValue() { return outputValue; }
    public void setOutputValue(String outputValue) { this.outputValue = outputValue; }

    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

    public String getExceptionInfo() { return exceptionInfo; }
    public void setExceptionInfo(String exceptionInfo) { this.exceptionInfo = exceptionInfo; }
}
