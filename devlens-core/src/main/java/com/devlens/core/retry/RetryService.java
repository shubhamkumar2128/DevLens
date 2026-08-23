package com.devlens.core.retry;

import com.devlens.annotation.Retry;
import com.devlens.core.ProceedFunction;
import com.devlens.core.logging.LoggingService;


public class RetryService {

    private static final long MAX_DELAY_MS = 30000;

    public Object executeWithRetry(ProceedFunction proceed, Retry annotation, String methodName,
                                   String correlationId, LoggingService loggingService) throws Throwable {
        int maxAttempts = annotation.maxAttempts();
        long delay = annotation.delay();
        boolean exponentialBackoff = annotation.exponentialBackoff();
        Class<? extends Throwable>[] retryOn = annotation.retryOn();
        Class<? extends Throwable>[] noRetryOn = annotation.noRetryOn();

        Throwable lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                Object result = proceed.proceed();

                // If this is a retry (attempt > 1), log success
                if (attempt > 1) {
                    loggingService.logRetry(methodName, attempt, maxAttempts, "SUCCESS", null, correlationId);
                }

                return result;
            } catch (Throwable ex) {
                lastException = ex;

                // Check if we should retry this exception
                if (!shouldRetry(ex, retryOn, noRetryOn)) {
                    // Not retryable — log failure and re-throw immediately
                    loggingService.logRetry(methodName, attempt, maxAttempts, "FAILED", ex, correlationId);
                    throw ex;
                }

                loggingService.logRetry(methodName, attempt, maxAttempts, "FAILED", ex, correlationId);

                if (attempt >= maxAttempts) {
                    break;
                }

                long currentDelay = calculateDelay(delay, attempt, exponentialBackoff);

                try {
                    Thread.sleep(currentDelay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw lastException;
                }
            }
        }

        loggingService.logRetry(methodName, maxAttempts, maxAttempts, "EXHAUSTED", lastException, correlationId);
        throw lastException;
    }


    private boolean shouldRetry(Throwable ex, Class<? extends Throwable>[] retryOn,
                                Class<? extends Throwable>[] noRetryOn) {
        // Check noRetryOn first — these always prevent retry
        if (noRetryOn != null && noRetryOn.length > 0) {
            for (Class<? extends Throwable> noRetryClass : noRetryOn) {
                if (noRetryClass.isInstance(ex)) {
                    return false;
                }
            }
        }

        // If retryOn is empty, retry everything except InterruptedException
        if (retryOn == null || retryOn.length == 0) {
            return !(ex instanceof InterruptedException);
        }

        // If retryOn is specified, only retry if exception matches
        for (Class<? extends Throwable> retryClass : retryOn) {
            if (retryClass.isInstance(ex)) {
                return true;
            }
        }

        return false;
    }

    private long calculateDelay(long baseDelay, int attempt, boolean exponentialBackoff) {
        if (!exponentialBackoff) {
            return baseDelay;
        }

        // Exponential backoff: delay * 2^(k-1) where k is the retry number (attempt)
        long multiplier = 1L << (attempt - 1); // 2^(attempt-1)
        long calculatedDelay = baseDelay * multiplier;

        return Math.min(calculatedDelay, MAX_DELAY_MS);
    }
}
