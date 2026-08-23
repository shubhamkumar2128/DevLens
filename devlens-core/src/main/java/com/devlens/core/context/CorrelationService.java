package com.devlens.core.context;

import org.slf4j.MDC;

import java.util.UUID;


public class CorrelationService {

    private static final String MDC_KEY = "devlens.correlationId";
    private static final ThreadLocal<String> CORRELATION_ID = new ThreadLocal<>();
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);


    public String enter() {
        int currentDepth = DEPTH.get();
        DEPTH.set(currentDepth + 1);

        if (currentDepth == 0) {
            // Generate short correlation ID: first 8 hex chars of UUID
            String id = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            CORRELATION_ID.set(id);
            MDC.put(MDC_KEY, id);
        }

        return CORRELATION_ID.get();
    }


    public void exit() {
        int currentDepth = DEPTH.get();
        if (currentDepth <= 1) {

            CORRELATION_ID.remove();
            DEPTH.remove();
            MDC.remove(MDC_KEY);
        } else {
            DEPTH.set(currentDepth - 1);
        }
    }
    
    public String current() {
        return CORRELATION_ID.get();
    }
}
