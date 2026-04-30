package com.dkanada.gramophone.activities.base;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;

import androidx.annotation.NonNull;

import com.dkanada.gramophone.App;
import com.dkanada.gramophone.interfaces.StateListener;
import com.dkanada.gramophone.service.LoginService;
import com.dkanada.gramophone.util.NavigationUtil;

public abstract class AbsMusicContentActivity extends AbsMusicPanelActivity implements StateListener {
    private boolean onlineStateDelivered;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, @NonNull Intent intent) {
            if (intent.getAction() == null) return;

            switch(intent.getAction()) {
                case LoginService.STATE_ONLINE:
                    dispatchOnlineOnce();
                    break;
                case LoginService.STATE_OFFLINE:
                    onlineStateDelivered = false;
                    NavigationUtil.startLogin(context);
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final IntentFilter filter = new IntentFilter();
        filter.addAction(LoginService.STATE_POLLING);
        filter.addAction(LoginService.STATE_ONLINE);
        filter.addAction(LoginService.STATE_OFFLINE);

        registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);

        if (App.getApiClient() == null) {
            startService(new Intent(this, LoginService.class));
        } else {
            dispatchOnlineOnce();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (App.getApiClient() == null) {
            startService(new Intent(this, LoginService.class));
        }
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(receiver);

        super.onDestroy();
    }

    @Override
    public void onStatePolling() {
    }

    @Override
    public void onStateOffline() {
    }

    private void dispatchOnlineOnce() {
        if (onlineStateDelivered) return;
        onlineStateDelivered = true;
        onStateOnline();
    }
}
