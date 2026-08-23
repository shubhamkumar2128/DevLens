package com.devlens.core.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class LoggingService {

    private static final Logger log = LoggerFactory.getLogger("com.devlens");
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    public void logExecutionTime(String className, String method, long elapsedMs, long threshold, String correlationId) {
        String msg = formatBase("ExecutionTime", className, method, correlationId)
                + " executionTime=" + elapsedMs + "ms";
        if (threshold > 0 && elapsedMs > threshold) {
            log.warn(msg);
        } else {
            log.info(msg);
        }
    }

    public void logSlowMethod(String className, String method, long elapsedMs, long threshold, String correlationId) {
        String msg = "[DevLens][SLOW] class=" + className + " method=" + method
                + " executionTime=" + elapsedMs + "ms threshold=" + threshold + "ms"
                + formatCorrelation(correlationId);
        log.warn(msg);
    }

    public void logInput(String className, String method, String serializedArgs, String correlationId) {
        String msg = formatBase("LogInput", className, method, correlationId)
                + " args=" + serializedArgs;
        log.info(msg);
    }

    public void logOutput(String className, String method, String serializedOutput, String correlationId) {
        String msg = formatBase("LogOutput", className, method, correlationId)
                + " result=" + serializedOutput;
        log.info(msg);
    }

    public void logException(String className, String method, Throwable ex, long elapsedMs,
                             String threadName, String correlationId, boolean includeStackTrace) {
        StringBuilder msg = new StringBuilder();
        msg.append("[DevLens] Exception class=").append(className)
                .append(" method=").append(method)
                .append(" exception=").append(ex.getClass().getName())
                .append(" message=").append(ex.getMessage())
                .append(" executionTime=").append(elapsedMs).append("ms")
                .append(" thread=").append(threadName);
        if (correlationId != null) {
            msg.append(" correlationId=").append(correlationId);
        }
        if (includeStackTrace) {
            msg.append("\n").append(getStackTrace(ex));
            log.error(msg.toString());
        } else {
            log.error(msg.toString());
        }
    }

    public void logThreadInfo(String className, String method, Thread thread, String phase, String correlationId) {
        String msg = formatBase("ThreadInfo", className, method, correlationId)
                + " thread=" + thread.getName()
                + " threadId=" + thread.getId()
                + " state=" + thread.getState()
                + " phase=" + phase;
        log.info(msg);
    }

    public void logExternalCall(String serviceName, String method, long elapsedMs, String status,
                                long threshold, String correlationId, Throwable ex) {
        String msg = "[DevLens] ExternalCall service=" + serviceName
                + " method=" + method
                + " executionTime=" + elapsedMs + "ms"
                + " status=" + status
                + formatCorrelation(correlationId);

        if ("FAILED".equals(status)) {
            if (ex != null) {
                msg += " exception=" + ex.getClass().getName();
            }
            log.error(msg);
        } else if (threshold > 0 && elapsedMs > threshold) {
            log.warn(msg);
        } else {
            log.info(msg);
        }
    }

    public void logRetry(String method, int attempt, int maxAttempts, String status,
                         Throwable ex, String correlationId) {
        String msg = "[DevLens] Retry method=" + method
                + " attempt=" + attempt + "/" + maxAttempts
                + " status=" + status
                + formatCorrelation(correlationId);
        if (ex != null) {
            msg += " exception=" + ex.getClass().getName();
        }

        switch (status) {
            case "SUCCESS" -> log.info(msg);
            case "FAILED" -> log.warn(msg);
            case "EXHAUSTED" -> log.error(msg);
            default -> log.debug(msg);
        }
    }

    public void logMemoryUsage(String className, String method, long beforeBytes, long afterBytes,
                               long deltaBytes, String correlationId) {
        String msg = formatBase("MemoryUsage", className, method, correlationId)
                + " before=" + beforeBytes + "B"
                + " after=" + afterBytes + "B"
                + " delta=" + (deltaBytes >= 0 ? "+" : "") + deltaBytes + "B";
        log.info(msg);
    }

    public void logDevTrace(DevTraceRecord record) {
        StringBuilder sb = new StringBuilder();
        sb.append("[DevLens] ═══════ DevTrace ═══════\n");
        sb.append(" class     : ").append(record.getClassName()).append("\n");
        sb.append(" method    : ").append(record.getMethodName()).append("\n");
        sb.append(" thread    : ").append(record.getThreadName())
                .append(" (id=").append(record.getThreadId()).append(")\n");
        sb.append(" start     : ").append(formatTimestamp(record.getStartTimestamp())).append("\n");
        sb.append(" end       : ").append(formatTimestamp(record.getEndTimestamp())).append("\n");
        sb.append(" elapsed   : ").append(record.getExecutionTimeMs()).append("ms\n");
        sb.append(" status    : ").append(record.getStatus()).append("\n");
        if (record.getCorrelationId() != null) {
            sb.append(" correlationId : ").append(record.getCorrelationId()).append("\n");
        }
        sb.append(" input     : ").append(record.getInputParameters()).append("\n");
        if ("FAILED".equals(record.getStatus()) && record.getExceptionInfo() != null) {
            sb.append(" exception : ").append(record.getExceptionInfo()).append("\n");
        } else {
            sb.append(" output    : ").append(record.getOutputValue()).append("\n");
        }
        sb.append("[DevLens] ═══════════════════════");

        log.info(sb.toString());
    }

    public void logInternalError(String context, Throwable ex) {
        log.warn("[DevLens] Internal error in {}: {}", context, ex.getMessage());
    }

    private String formatBase(String type, String className, String method, String correlationId) {
        return "[DevLens] " + type + " class=" + className + " method=" + method
                + formatCorrelation(correlationId);
    }

    private String formatCorrelation(String correlationId) {
        return correlationId != null ? " correlationId=" + correlationId : "";
    }

    private String formatTimestamp(long epochMillis) {
        return TIME_FORMATTER.format(Instant.ofEpochMilli(epochMillis));
    }

    private String getStackTrace(Throwable ex) {
        StringWriter sw = new StringWriter();
        ex.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
