package dev.kebugcheck.gelli.interfaces;

public interface StateListener {
    void onStatePolling();

    void onStateOnline();

    void onStateOffline();
}
