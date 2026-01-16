package com.extracrates.sync;

public interface SyncPublisher {
    void publish(SyncEvent event);
}
