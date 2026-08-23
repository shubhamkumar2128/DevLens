package com.devlens.core;

import com.devlens.annotation.*;
import com.devlens.core.context.CorrelationService;
import com.devlens.core.logging.DevTraceRecord;
import com.devlens.core.logging.LoggingService;
import com.devlens.core.masking.SensitiveDataMasker;
import com.devlens.core.memory.MemoryService;
import com.devlens.core.serialization.ObjectSerializer;
import com.devlens.core.timing.TimingService;


public class DevLensInterceptorEngine {

    private static final int INPUT_TRUNCATION_LIMIT = 1024;
    private static final long MAX_RETRY_DELAY = 30_000L;

    private final ObjectSerializer serializer;
    private final SensitiveDataMasker masker;
    private final DevLensConfiguration config;
    private final TimingService timingService;
    private final LoggingService loggingService;
    private final CorrelationService correlationService;
    private final MemoryService memoryService;

    public DevLensInterceptorEngine(ObjectSerializer serializer, SensitiveDataMasker masker,
                                    DevLensConfiguration config) {
        this.serializer = serializer;
        this.masker = masker;
        this.config = config;
        this.timingService = new TimingService();
        this.loggingService = new LoggingService();
        this.correlationService = new CorrelationService();
        this.memoryService = new MemoryService();
    }


    public Object intercept(InterceptionContext context) throws Throwable {
        if (!config.isEnabled()) {
            return context.getProceedFunction().proceed();
        }

        boolean isDevTrace = context.hasAnnotation(DevTrace.class);

        if (isDevTrace) {
            return handleDevTrace(context);
        }

        return handleIndividualAnnotations(context);
    }

    private Object handleIndividualAnnotations(InterceptionContext context) throws Throwable {
        String className = context.getClassName();
        String methodName = context.getMethodName();
        Throwable methodException = null;
        Object result = null;

        // Pre-processing: Correlation ID
        boolean correlationManaged = false;
        try {
            if (config.isCorrelationIdEnabled() && context.hasAnnotation(CorrelationId.class)) {
                correlationService.enter();
                correlationManaged = true;
            }
        } catch (Exception e) {
            loggingService.logInternalError("correlation-enter", e);
        }

        String correlationId = correlationService.current();

        // Pre-processing: Memory before
        long memoryBefore = 0;
        boolean trackMemory = false;
        try {
            if (config.isMemoryUsageEnabled() && context.hasAnnotation(MemoryUsage.class)) {
                trackMemory = true;
                MemoryUsage memAnn = context.getAnnotation(MemoryUsage.class);
                if (memAnn != null && memAnn.forceGC()) {
                    memoryService.requestGC();
                }
                memoryBefore = memoryService.usedHeapBytes();
            }
        } catch (Exception e) {
            loggingService.logInternalError("memory-before", e);
        }

        // Pre-processing: Log input
        try {
            if (config.isLogInputEnabled() && context.hasAnnotation(LogInput.class)) {
                logInput(context, correlationId);
            }
        } catch (Exception e) {
            loggingService.logInternalError("log-input", e);
        }

        // Pre-processing: Thread info (START)
        try {
            if (config.isThreadInfoEnabled() && context.hasAnnotation(ThreadInfo.class)) {
                ThreadInfo threadAnn = context.getAnnotation(ThreadInfo.class);
                if (threadAnn != null && (threadAnn.logAt() == LogAt.START || threadAnn.logAt() == LogAt.BOTH)) {
                    loggingService.logThreadInfo(className, methodName, Thread.currentThread(), "ENTRY", correlationId);
                }
            }
        } catch (Exception e) {
            loggingService.logInternalError("thread-info-start", e);
        }

        // Execute the method
        long startNanos = timingService.now();
        try {
            // Handle @Retry
            if (config.isRetryEnabled() && context.hasAnnotation(Retry.class)) {
                result = handleRetry(context, correlationId);
            } else {
                result = context.getProceedFunction().proceed();
            }
        } catch (Throwable t) {
            methodException = t;
        }
        long endNanos = timingService.now();
        long elapsedMs = timingService.toMillis(startNanos, endNanos);

        // Post-processing: Execution time
        try {
            if (config.isExecutionTimeEnabled() && context.hasAnnotation(ExecutionTime.class)) {
                ExecutionTime ann = context.getAnnotation(ExecutionTime.class);
                long threshold = ann != null ? Math.max(0, ann.threshold()) : config.getExecutionTimeThreshold();
                loggingService.logExecutionTime(className, methodName, elapsedMs, threshold, correlationId);
            }
        } catch (Exception e) {
            loggingService.logInternalError("execution-time", e);
        }

        // Post-processing: Slow method
        try {
            if (config.isExecutionTimeEnabled() && context.hasAnnotation(SlowMethod.class)) {
                SlowMethod ann = context.getAnnotation(SlowMethod.class);
                if (ann != null) {
                    long threshold = Math.max(1, ann.threshold());
                    if (elapsedMs > threshold) {
                        loggingService.logSlowMethod(className, methodName, elapsedMs, threshold, correlationId);
                    }
                }
            }
        } catch (Exception e) {
            loggingService.logInternalError("slow-method", e);
        }

        // Post-processing: External call
        try {
            if (config.isExternalCallEnabled() && context.hasAnnotation(ExternalCall.class)) {
                ExternalCall ann = context.getAnnotation(ExternalCall.class);
                if (ann != null) {
                    String serviceName = (ann.value() == null || ann.value().isBlank()) ? "UNKNOWN" : ann.value();
                    String status = methodException == null ? "SUCCESS" : "FAILED";
                    loggingService.logExternalCall(serviceName, methodName, elapsedMs, status,
                            ann.threshold(), correlationId, methodException);
                }
            }
        } catch (Exception e) {
            loggingService.logInternalError("external-call", e);
        }

        // Post-processing: Log output
        try {
            if (config.isLogOutputEnabled() && context.hasAnnotation(LogOutput.class) && methodException == null) {
                logOutput(context, result, correlationId);
            }
        } catch (Exception e) {
            loggingService.logInternalError("log-output", e);
        }

        // Post-processing: Log exception
        try {
            if (config.isLogExceptionEnabled() && context.hasAnnotation(LogException.class) && methodException != null) {
                LogException ann = context.getAnnotation(LogException.class);
                boolean includeStackTrace = ann == null || ann.includeStackTrace();
                loggingService.logException(className, methodName, methodException, elapsedMs,
                        Thread.currentThread().getName(), correlationId, includeStackTrace);
            }
        } catch (Exception e) {
            loggingService.logInternalError("log-exception", e);
        }

        // Post-processing: Thread info (END)
        try {
            if (config.isThreadInfoEnabled() && context.hasAnnotation(ThreadInfo.class)) {
                ThreadInfo threadAnn = context.getAnnotation(ThreadInfo.class);
                if (threadAnn != null && (threadAnn.logAt() == LogAt.END || threadAnn.logAt() == LogAt.BOTH)) {
                    loggingService.logThreadInfo(className, methodName, Thread.currentThread(), "EXIT", correlationId);
                }
            }
        } catch (Exception e) {
            loggingService.logInternalError("thread-info-end", e);
        }

        // Post-processing: Memory after
        try {
            if (trackMemory) {
                long memoryAfter = memoryService.usedHeapBytes();
                long delta = memoryAfter - memoryBefore;
                loggingService.logMemoryUsage(className, methodName, memoryBefore, memoryAfter, delta, correlationId);
            }
        } catch (Exception e) {
            loggingService.logInternalError("memory-after", e);
        }

        // Correlation ID cleanup
        try {
            if (correlationManaged) {
                correlationService.exit();
            }
        } catch (Exception e) {
            loggingService.logInternalError("correlation-exit", e);
        }

        // Re-throw the original exception if any
        if (methodException != null) {
            throw methodException;
        }
        return result;
    }

    private Object handleDevTrace(InterceptionContext context) throws Throwable {
        String className = context.getClassName();
        String methodName = context.getMethodName();
        DevTrace devTrace = context.getAnnotation(DevTrace.class);
        Throwable methodException = null;
        Object result = null;

        // Correlation ID
        boolean correlationManaged = false;
        try {
            if (config.isCorrelationIdEnabled() && context.hasAnnotation(CorrelationId.class)) {
                correlationService.enter();
                correlationManaged = true;
            }
        } catch (Exception e) {
            loggingService.logInternalError("devtrace-correlation", e);
        }

        String correlationId = correlationService.current();
        Thread currentThread = Thread.currentThread();
        long startTimestamp = System.currentTimeMillis();
        long startNanos = timingService.now();

        // Serialize input
        String inputStr = "";
        try {
            inputStr = serializeArguments(context, devTrace != null ? devTrace.maskFields() : new String[0]);
        } catch (Exception e) {
            inputStr = "[serialization-failed]";
            loggingService.logInternalError("devtrace-input", e);
        }

        // Execute method
        try {
            if (config.isRetryEnabled() && context.hasAnnotation(Retry.class)) {
                result = handleRetry(context, correlationId);
            } else {
                result = context.getProceedFunction().proceed();
            }
        } catch (Throwable t) {
            methodException = t;
        }

        long endNanos = timingService.now();
        long endTimestamp = System.currentTimeMillis();
        long elapsedMs = timingService.toMillis(startNanos, endNanos);

        // Build trace record
        try {
            DevTraceRecord record = new DevTraceRecord();
            record.setClassName(className);
            record.setMethodName(methodName);
            record.setThreadName(currentThread.getName());
            record.setThreadId(currentThread.getId());
            record.setStartTimestamp(startTimestamp);
            record.setEndTimestamp(endTimestamp);
            record.setExecutionTimeMs(elapsedMs);
            record.setCorrelationId(correlationId);
            record.setInputParameters(inputStr);

            if (methodException != null) {
                record.setStatus("FAILED");
                boolean includeStack = devTrace == null || devTrace.includeStackTrace();
                record.setExceptionInfo(methodException.getClass().getName() + ": " + methodException.getMessage());
                if (includeStack) {
                    record.setOutputValue(methodException.toString());
                }
            } else {
                record.setStatus("SUCCESS");
                String outputStr = serializeResult(context, result, devTrace);
                record.setOutputValue(outputStr);
            }

            loggingService.logDevTrace(record);
        } catch (Exception e) {
            loggingService.logInternalError("devtrace-output", e);
        }


        try {
            if (config.isExecutionTimeEnabled() && context.hasAnnotation(ExecutionTime.class)) {
                // DevTrace already logs execution time, but @ExecutionTime may have its own threshold for WARN
                ExecutionTime ann = context.getAnnotation(ExecutionTime.class);
                long threshold = ann != null ? Math.max(0, ann.threshold()) : 0;
                if (threshold > 0 && elapsedMs > threshold) {
                    loggingService.logExecutionTime(className, methodName, elapsedMs, threshold, correlationId);
                }
                // Don't log at DEBUG — DevTrace already covers it
            }
        } catch (Exception e) {
            loggingService.logInternalError("devtrace-execution-time", e);
        }

        // SlowMethod check
        try {
            if (config.isExecutionTimeEnabled() && context.hasAnnotation(SlowMethod.class)) {
                SlowMethod ann = context.getAnnotation(SlowMethod.class);
                if (ann != null) {
                    long threshold = Math.max(1, ann.threshold());
                    if (elapsedMs > threshold) {
                        loggingService.logSlowMethod(className, methodName, elapsedMs, threshold, correlationId);
                    }
                }
            }
        } catch (Exception e) {
            loggingService.logInternalError("devtrace-slow-method", e);
        }

        // External call (if also annotated)
        try {
            if (config.isExternalCallEnabled() && context.hasAnnotation(ExternalCall.class)) {
                ExternalCall ann = context.getAnnotation(ExternalCall.class);
                if (ann != null) {
                    String serviceName = (ann.value() == null || ann.value().isBlank()) ? "UNKNOWN" : ann.value();
                    String status = methodException == null ? "SUCCESS" : "FAILED";
                    loggingService.logExternalCall(serviceName, methodName, elapsedMs, status,
                            ann.threshold(), correlationId, methodException);
                }
            }
        } catch (Exception e) {
            loggingService.logInternalError("devtrace-external-call", e);
        }

        // Memory usage
        try {
            if (config.isMemoryUsageEnabled() && context.hasAnnotation(MemoryUsage.class)) {
                long memoryAfter = memoryService.usedHeapBytes();
                loggingService.logMemoryUsage(className, methodName, 0, memoryAfter, memoryAfter, correlationId);
            }
        } catch (Exception e) {
            loggingService.logInternalError("devtrace-memory", e);
        }

        // Correlation cleanup
        try {
            if (correlationManaged) {
                correlationService.exit();
            }
        } catch (Exception e) {
            loggingService.logInternalError("devtrace-correlation-exit", e);
        }

        if (methodException != null) {
            throw methodException;
        }
        return result;
    }

    private Object handleRetry(InterceptionContext context, String correlationId) throws Throwable {
        Retry retryAnn = context.getAnnotation(Retry.class);
        if (retryAnn == null) {
            return context.getProceedFunction().proceed();
        }

        int maxAttempts = Math.max(1, retryAnn.maxAttempts());
        long delay = retryAnn.delay();
        boolean exponential = retryAnn.exponentialBackoff();
        Class<? extends Throwable>[] retryOn = retryAnn.retryOn();
        Class<? extends Throwable>[] noRetryOn = retryAnn.noRetryOn();
        String methodName = context.getMethodName();

        Throwable lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                Object result = context.getProceedFunction().proceed();
                if (attempt > 1) {
                    loggingService.logRetry(methodName, attempt, maxAttempts, "SUCCESS", null, correlationId);
                }
                return result;
            } catch (Throwable t) {
                lastException = t;

                // Check noRetryOn first
                if (isInstanceOfAny(t, noRetryOn)) {
                    break;
                }

                // Check if we should retry
                boolean shouldRetry;
                if (retryOn.length == 0) {
                    shouldRetry = !(t instanceof InterruptedException);
                } else {
                    shouldRetry = isInstanceOfAny(t, retryOn);
                }

                if (!shouldRetry || attempt == maxAttempts) {
                    if (attempt > 1 || maxAttempts > 1) {
                        loggingService.logRetry(methodName, attempt, maxAttempts, "EXHAUSTED", t, correlationId);
                    }
                    break;
                }

                // Log failed attempt
                loggingService.logRetry(methodName, attempt, maxAttempts, "FAILED", t, correlationId);

                // Wait before next attempt
                try {
                    long currentDelay = exponential
                            ? Math.min(delay * (long) Math.pow(2, attempt - 1), MAX_RETRY_DELAY)
                            : delay;
                    Thread.sleep(currentDelay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        throw lastException;
    }

    private boolean isInstanceOfAny(Throwable t, Class<? extends Throwable>[] classes) {
        if (classes == null || classes.length == 0) return false;
        for (Class<? extends Throwable> clazz : classes) {
            if (clazz.isInstance(t)) return true;
        }
        return false;
    }

    private void logInput(InterceptionContext context, String correlationId) {
        String className = context.getClassName();
        String methodName = context.getMethodName();
        Object[] args = context.getArguments();
        String[] paramNames = context.getParameterNames();

        if (args == null || args.length == 0) {
            loggingService.logInput(className, methodName, "[no arguments]", correlationId);
            return;
        }

        LogInput ann = context.getAnnotation(LogInput.class);
        String[] maskFields = ann != null ? ann.maskFields() : new String[0];

        String serialized = serializeArguments(context, maskFields);
        loggingService.logInput(className, methodName, serialized, correlationId);
    }

    private String serializeArguments(InterceptionContext context, String[] maskFields) {
        Object[] args = context.getArguments();
        String[] paramNames = context.getParameterNames();

        if (args == null || args.length == 0) {
            return "[no arguments]";
        }

        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(", ");

            String name = (paramNames != null && i < paramNames.length) ? paramNames[i] : "arg" + i;
            String value;
            try {
                value = serializer.serialize(args[i]);
            } catch (Exception e) {
                value = args[i] != null ? args[i].getClass().getSimpleName() + "[serialization-failed]" : "null[serialization-failed]";
            }

            // Truncate individual arguments
            if (value.length() > INPUT_TRUNCATION_LIMIT) {
                value = value.substring(0, INPUT_TRUNCATION_LIMIT) + "...[truncated]";
            }

            sb.append(name).append("=").append(value);
        }
        sb.append("}");

        String result = sb.toString();
        // Apply masking
        try {
            result = masker.mask(result, maskFields);
        } catch (Exception e) {
            // Use unmasked if masking fails
            loggingService.logInternalError("masking", e);
        }

        return result;
    }

    private void logOutput(InterceptionContext context, Object result, String correlationId) {
        String className = context.getClassName();
        String methodName = context.getMethodName();

        // Void method
        if (context.getMethod().getReturnType() == void.class || context.getMethod().getReturnType() == Void.TYPE) {
            loggingService.logOutput(className, methodName, "[void]", correlationId);
            return;
        }

        // Null return
        if (result == null) {
            loggingService.logOutput(className, methodName, "null", correlationId);
            return;
        }

        // Serialize
        String serialized;
        try {
            serialized = serializer.serialize(result);
        } catch (Exception e) {
            loggingService.logOutput(className, methodName,
                    result.getClass().getSimpleName() + "[serialization-failed]", correlationId);
            return;
        }

        // Truncate
        LogOutput ann = context.getAnnotation(LogOutput.class);
        int maxLength = (ann != null && ann.maxLength() > 0) ? ann.maxLength() : config.getMaxOutputLength();
        if (serialized.length() > maxLength) {
            serialized = serialized.substring(0, maxLength) + "...[truncated]";
        }

        loggingService.logOutput(className, methodName, serialized, correlationId);
    }

    private String serializeResult(InterceptionContext context, Object result, DevTrace devTrace) {
        if (context.getMethod().getReturnType() == void.class || context.getMethod().getReturnType() == Void.TYPE) {
            return "[void]";
        }
        if (result == null) {
            return "null";
        }
        try {
            String serialized = serializer.serialize(result);
            int maxLength = (devTrace != null && devTrace.maxOutputLength() > 0)
                    ? devTrace.maxOutputLength()
                    : config.getMaxOutputLength();
            if (serialized.length() > maxLength) {
                serialized = serialized.substring(0, maxLength) + "...[truncated]";
            }
            return serialized;
        } catch (Exception e) {
            return result.getClass().getSimpleName() + "[serialization-failed]";
        }
    }
}
