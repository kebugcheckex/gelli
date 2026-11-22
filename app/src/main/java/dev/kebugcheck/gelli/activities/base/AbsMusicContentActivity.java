package dev.kebugcheck.gelli.activities.base;

import android.os.Bundle;

import androidx.annotation.NonNull;

import dev.kebugcheck.gelli.helper.EventListener;
import dev.kebugcheck.gelli.interfaces.StateListener;
import dev.kebugcheck.gelli.util.ConnectionState;
import dev.kebugcheck.gelli.util.JellyfinSdkSession;
import dev.kebugcheck.gelli.util.NavigationUtil;
import dev.kebugcheck.gelli.util.ServerConnection;

public abstract class AbsMusicContentActivity extends AbsMusicPanelActivity implements StateListener {
    private boolean onlineStateDelivered;
    private boolean unreachable;

    private final ServerConnection.Listener connectionListener = this::onConnectionChanged;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ServerConnection.addListener(connectionListener);

        if (JellyfinSdkSession.getCurrentUserId() == null) {
            ServerConnection.start(this);
        } else {
            // requests go to the address used last time until one is picked for this network
            dispatchOnlineOnce();
        }

        onConnectionChanged(ServerConnection.getState());
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (JellyfinSdkSession.getCurrentUserId() == null) {
            ServerConnection.start(this);
        }
    }

    @Override
    protected void onDestroy() {
        ServerConnection.removeListener(connectionListener);

        super.onDestroy();
    }

    /** Called with every connection state, e.g. to show that the server can't be reached. */
    protected void onUnreachableChanged(boolean unreachable) {
    }

    private void onConnectionChanged(@NonNull ConnectionState state) {
        if (state == ConnectionState.Rejected.INSTANCE) {
            onlineStateDelivered = false;
            EventListener.stop();
            NavigationUtil.startLogin(this);
            return;
        }

        if (state instanceof ConnectionState.Online) {
            // what loaded while the server was away came back empty
            if (unreachable) onlineStateDelivered = false;
            dispatchOnlineOnce();
        } else if (state == ConnectionState.Unreachable.INSTANCE) {
            // the screen still opens, so stored songs can be played and settings reached
            dispatchOnlineOnce();
        }

        unreachable = state == ConnectionState.Unreachable.INSTANCE;
        onUnreachableChanged(unreachable);
    }

    private void dispatchOnlineOnce() {
        if (onlineStateDelivered) return;
        onlineStateDelivered = true;
        onStateOnline();
    }
}
