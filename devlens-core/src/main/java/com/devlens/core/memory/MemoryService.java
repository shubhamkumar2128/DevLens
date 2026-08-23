package com.devlens.core.memory;


public class MemoryService {

    public long usedHeapBytes() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }


    public void requestGC() {
        System.gc();
    }
}
