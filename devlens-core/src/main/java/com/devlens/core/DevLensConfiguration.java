package com.devlens.core;


public class DevLensConfiguration {

    private final boolean enabled;
    private final boolean executionTimeEnabled;
    private final boolean logInputEnabled;
    private final boolean logOutputEnabled;
    private final boolean logExceptionEnabled;
    private final boolean threadInfoEnabled;
    private final boolean correlationIdEnabled;
    private final boolean externalCallEnabled;
    private final boolean retryEnabled;
    private final boolean memoryUsageEnabled;
    private final int maxOutputLength;
    private final long executionTimeThreshold;

    private DevLensConfiguration(Builder builder) {
        this.enabled = builder.enabled;
        this.executionTimeEnabled = builder.executionTimeEnabled;
        this.logInputEnabled = builder.logInputEnabled;
        this.logOutputEnabled = builder.logOutputEnabled;
        this.logExceptionEnabled = builder.logExceptionEnabled;
        this.threadInfoEnabled = builder.threadInfoEnabled;
        this.correlationIdEnabled = builder.correlationIdEnabled;
        this.externalCallEnabled = builder.externalCallEnabled;
        this.retryEnabled = builder.retryEnabled;
        this.memoryUsageEnabled = builder.memoryUsageEnabled;
        this.maxOutputLength = builder.maxOutputLength;
        this.executionTimeThreshold = builder.executionTimeThreshold;
    }

    public boolean isEnabled() { return enabled; }
    public boolean isExecutionTimeEnabled() { return executionTimeEnabled; }
    public boolean isLogInputEnabled() { return logInputEnabled; }
    public boolean isLogOutputEnabled() { return logOutputEnabled; }
    public boolean isLogExceptionEnabled() { return logExceptionEnabled; }
    public boolean isThreadInfoEnabled() { return threadInfoEnabled; }
    public boolean isCorrelationIdEnabled() { return correlationIdEnabled; }
    public boolean isExternalCallEnabled() { return externalCallEnabled; }
    public boolean isRetryEnabled() { return retryEnabled; }
    public boolean isMemoryUsageEnabled() { return memoryUsageEnabled; }
    public int getMaxOutputLength() { return maxOutputLength; }
    public long getExecutionTimeThreshold() { return executionTimeThreshold; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private boolean enabled = true;
        private boolean executionTimeEnabled = true;
        private boolean logInputEnabled = true;
        private boolean logOutputEnabled = true;
        private boolean logExceptionEnabled = true;
        private boolean threadInfoEnabled = true;
        private boolean correlationIdEnabled = true;
        private boolean externalCallEnabled = true;
        private boolean retryEnabled = true;
        private boolean memoryUsageEnabled = true;
        private int maxOutputLength = 5000;
        private long executionTimeThreshold = 0;

        public Builder enabled(boolean enabled) { this.enabled = enabled; return this; }
        public Builder executionTimeEnabled(boolean v) { this.executionTimeEnabled = v; return this; }
        public Builder logInputEnabled(boolean v) { this.logInputEnabled = v; return this; }
        public Builder logOutputEnabled(boolean v) { this.logOutputEnabled = v; return this; }
        public Builder logExceptionEnabled(boolean v) { this.logExceptionEnabled = v; return this; }
        public Builder threadInfoEnabled(boolean v) { this.threadInfoEnabled = v; return this; }
        public Builder correlationIdEnabled(boolean v) { this.correlationIdEnabled = v; return this; }
        public Builder externalCallEnabled(boolean v) { this.externalCallEnabled = v; return this; }
        public Builder retryEnabled(boolean v) { this.retryEnabled = v; return this; }
        public Builder memoryUsageEnabled(boolean v) { this.memoryUsageEnabled = v; return this; }
        public Builder maxOutputLength(int v) { this.maxOutputLength = v; return this; }
        public Builder executionTimeThreshold(long v) { this.executionTimeThreshold = v; return this; }

        public DevLensConfiguration build() { return new DevLensConfiguration(this); }
    }
}
