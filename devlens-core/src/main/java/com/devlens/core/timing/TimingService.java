package com.devlens.core.timing;


public class TimingService {


    public long now() {
        return System.nanoTime();
    }

    public long toMillis(long startNanos, long endNanos) {
        return (endNanos - startNanos) / 1_000_000;
    }
}
