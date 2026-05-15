package com.dkanada.gramophone;

import android.app.Application;
import android.content.Context;
import android.os.Build;

import androidx.room.Room;

import com.dkanada.gramophone.database.JellyDatabase;
import com.dkanada.gramophone.model.User;
import com.dkanada.gramophone.util.JellyfinSdkSession;
import com.dkanada.gramophone.util.PreferenceUtil;
import com.dkanada.gramophone.views.shortcuts.DynamicShortcutManager;
import com.melegy.redscreenofdeath.RedScreenOfDeath;

public class App extends Application {
    private static App app;

    private static JellyDatabase database;

    @Override
    public void onCreate() {
        super.onCreate();

        if (BuildConfig.DEBUG) {
            RedScreenOfDeath.init(this);
        }

        app = this;
        database = createDatabase(this);
        JellyfinSdkSession.initialize(this);

        if (database.userDao().getUsers().size() == 0) {
            PreferenceUtil.getInstance(this).setServer(null);
            PreferenceUtil.getInstance(this).setUser(null);
            JellyfinSdkSession.clearSession();
        } else {
            User selectedUser = database.userDao().getUser(PreferenceUtil.getInstance(this).getUser());
            JellyfinSdkSession.updateSessionFromUser(selectedUser);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            new DynamicShortcutManager(this).initDynamicShortcuts();
        }
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

    public static JellyDatabase getDatabase() {
        return database;
    }

    public static App getInstance() {
        return app;
    }
}
