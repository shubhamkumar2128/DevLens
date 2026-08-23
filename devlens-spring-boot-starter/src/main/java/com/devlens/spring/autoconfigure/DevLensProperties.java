package com.devlens.spring.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;


@ConfigurationProperties(prefix = "devlens")
public class DevLensProperties {

    private boolean enabled = true;
    private int maxOutputLength = 5000;

    private ExecutionTimeProperties executionTime = new ExecutionTimeProperties();
    private FeatureToggle logInput = new FeatureToggle();
    private FeatureToggle logOutput = new FeatureToggle();
    private FeatureToggle logException = new FeatureToggle();
    private FeatureToggle threadInfo = new FeatureToggle();
    private FeatureToggle correlationId = new FeatureToggle();
    private FeatureToggle externalCall = new FeatureToggle();
    private FeatureToggle retry = new FeatureToggle();
    private FeatureToggle memoryUsage = new FeatureToggle();
    private MaskingProperties masking = new MaskingProperties();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getMaxOutputLength() { return maxOutputLength; }
    public void setMaxOutputLength(int maxOutputLength) { this.maxOutputLength = maxOutputLength; }

    public ExecutionTimeProperties getExecutionTime() { return executionTime; }
    public void setExecutionTime(ExecutionTimeProperties executionTime) { this.executionTime = executionTime; }

    public FeatureToggle getLogInput() { return logInput; }
    public void setLogInput(FeatureToggle logInput) { this.logInput = logInput; }

    public FeatureToggle getLogOutput() { return logOutput; }
    public void setLogOutput(FeatureToggle logOutput) { this.logOutput = logOutput; }

    public FeatureToggle getLogException() { return logException; }
    public void setLogException(FeatureToggle logException) { this.logException = logException; }

    public FeatureToggle getThreadInfo() { return threadInfo; }
    public void setThreadInfo(FeatureToggle threadInfo) { this.threadInfo = threadInfo; }

    public FeatureToggle getCorrelationId() { return correlationId; }
    public void setCorrelationId(FeatureToggle correlationId) { this.correlationId = correlationId; }

    public FeatureToggle getExternalCall() { return externalCall; }
    public void setExternalCall(FeatureToggle externalCall) { this.externalCall = externalCall; }

    public FeatureToggle getRetry() { return retry; }
    public void setRetry(FeatureToggle retry) { this.retry = retry; }

    public FeatureToggle getMemoryUsage() { return memoryUsage; }
    public void setMemoryUsage(FeatureToggle memoryUsage) { this.memoryUsage = memoryUsage; }

    public MaskingProperties getMasking() { return masking; }
    public void setMasking(MaskingProperties masking) { this.masking = masking; }

    public static class FeatureToggle {
        private boolean enabled = true;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    public static class ExecutionTimeProperties {
        private boolean enabled = true;
        private long threshold = 0;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public long getThreshold() { return threshold; }
        public void setThreshold(long threshold) { this.threshold = threshold; }
    }

    public static class MaskingProperties {
        private List<String> fields = new ArrayList<>();

        public List<String> getFields() { return fields; }
        public void setFields(List<String> fields) { this.fields = fields; }
    }
}
