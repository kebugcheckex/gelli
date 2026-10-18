package com.dkanada.gramophone.service.playback;

public interface PlaybackListener {
    void onStateChanged(int state);

    void onReadyChanged(boolean ready, int reason);

    void onPlayingChanged(boolean playing);

    void onTrackChanged(int reason);
}
