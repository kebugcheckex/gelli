package com.dkanada.gramophone;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Room;

import com.dkanada.gramophone.database.JellyDatabase;
import com.dkanada.gramophone.helper.EventListener;
import com.dkanada.gramophone.util.PreferenceUtil;
import com.dkanada.gramophone.views.shortcuts.DynamicShortcutManager;
import com.melegy.redscreenofdeath.RedScreenOfDeath;

import org.jellyfin.apiclient.interaction.AndroidDevice;
import org.jellyfin.apiclient.interaction.ApiClient;
import org.jellyfin.apiclient.interaction.VolleyHttpClient;
import org.jellyfin.apiclient.interaction.device.IDevice;
import org.jellyfin.apiclient.interaction.http.IAsyncHttpClient;
import org.jellyfin.apiclient.logging.AndroidLogger;
import org.jellyfin.apiclient.logging.ILogger;

public class App extends Application {
    private static App app;

    private static JellyDatabase database;
    private static ApiClient apiClient;

    private static int startedActivities;
    @Nullable
    private static Runnable foregroundChangeListener;

    @Override
    public void onCreate() {
        super.onCreate();

        if (BuildConfig.DEBUG) {
            RedScreenOfDeath.init(this);
        }

        app = this;
        database = createDatabase(this);
        apiClient = createApiClient(this);

        if (database.userDao().getUsers().size() == 0) {
            PreferenceUtil.getInstance(this).setServer(null);
            PreferenceUtil.getInstance(this).setUser(null);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            new DynamicShortcutManager(this).initDynamicShortcuts();
        }

        registerActivityLifecycleCallbacks(new ForegroundTracker());
    }

    public static boolean isForeground() {
        return startedActivities > 0;
    }

    public static void setForegroundChangeListener(@Nullable Runnable listener) {
        foregroundChangeListener = listener;
    }

    private static void notifyForegroundChanged() {
        Runnable listener = foregroundChangeListener;
        if (listener != null) listener.run();
    }

    private static final class ForegroundTracker implements ActivityLifecycleCallbacks {
        @Override public void onActivityCreated(@NonNull Activity a, @Nullable Bundle b) {}
        @Override public void onActivityStarted(@NonNull Activity a) {
            startedActivities++;
            if (startedActivities == 1) notifyForegroundChanged();
        }
        @Override public void onActivityResumed(@NonNull Activity a) {}
        @Override public void onActivityPaused(@NonNull Activity a) {}
        @Override public void onActivityStopped(@NonNull Activity a) {
            if (startedActivities > 0) startedActivities--;
            if (startedActivities == 0) notifyForegroundChanged();
        }
        @Override public void onActivitySaveInstanceState(@NonNull Activity a, @NonNull Bundle b) {}
        @Override public void onActivityDestroyed(@NonNull Activity a) {}
    }

    public static JellyDatabase createDatabase(Context context) {
        return Room.databaseBuilder(context, JellyDatabase.class, "database")
                .allowMainThreadQueries()
                .addMigrations(JellyDatabase.Migration2)
                .addMigrations(JellyDatabase.Migration3)
                .addMigrations(JellyDatabase.Migration4)
                .addMigrations(JellyDatabase.Migration5)
                .addMigrations(JellyDatabase.Migration6)
                .addMigrations(JellyDatabase.Migration7)
                .addMigrations(JellyDatabase.Migration8)
                .build();
    }

    public static ApiClient createApiClient(Context context) {
        String appName = context.getString(R.string.app_name);
        String appVersion = BuildConfig.VERSION_NAME;

        @SuppressLint("HardwareIds")
        String deviceId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        String deviceName = android.os.Build.MODEL;
        String server = PreferenceUtil.getInstance(context).getServer();

        ILogger logger = new AndroidLogger(context.getClass().getName());
        IAsyncHttpClient httpClient = new VolleyHttpClient(logger, context);
        IDevice device = new AndroidDevice(deviceId, deviceName);
        EventListener eventListener = new EventListener();

        return new ApiClient(httpClient, logger, server, appName, appVersion, device, eventListener);
    }

    public static JellyDatabase getDatabase() {
        return database;
    }

    public static ApiClient getApiClient() {
        return apiClient;
    }

    public static App getInstance() {
        return app;
    }
}
