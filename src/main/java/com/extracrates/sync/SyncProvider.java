package com.extracrates.sync;

public interface SyncProvider extends SyncPublisher {
    void start(SyncListener listener);

    void shutdown();

    boolean isHealthy();

    String getName();
}
