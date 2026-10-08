package com.dkanada.gramophone.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import com.dkanada.gramophone.App;
import com.dkanada.gramophone.BuildConfig;
import com.dkanada.gramophone.model.Song;
import com.dkanada.gramophone.service.notifications.DownloadNotification;
import com.dkanada.gramophone.util.DownloadUtil;
import com.dkanada.gramophone.util.MusicUtil;
import com.dkanada.gramophone.util.PreferenceUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SuppressWarnings("ResultOfMethodCallIgnored")
public class DownloadService extends Service {
    private static final String TAG = DownloadService.class.getSimpleName();

    public static final String PACKAGE_NAME = BuildConfig.APPLICATION_ID;
    public static final String ACTION_START = PACKAGE_NAME + ".action.start";
    public static final String ACTION_CANCEL = PACKAGE_NAME + ".action.cancel";
    public static final String EXTRA_SONGS = PACKAGE_NAME + ".extra.songs";

    private ExecutorService executor;
    private DownloadNotification notification;

    @Override
    public void onCreate() {
        super.onCreate();

        executor = Executors.newFixedThreadPool(4);
        notification = new DownloadNotification(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            return super.onStartCommand(null, flags, startId);
        }

        switch (intent.getAction()) {
            case DownloadService.ACTION_CANCEL:
                executor.shutdownNow();
                notification.stop(null);
                stopSelf();
                break;
            case DownloadService.ACTION_START:
                List<Song> songs = intent.getParcelableArrayListExtra(EXTRA_SONGS);
                for (Song song : songs) {
                    notification.start(song);
                    download(song);
                }
        }

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @SuppressWarnings("ConstantConditions")
    public void download(Song song) {
        // capture the account now in case it changes while the download is queued
        String userId = PreferenceUtil.getInstance(this).getUser();

        executor.execute(() -> {
            if (userId == null || DownloadUtil.getLocalFile(song) != null) {
                notification.stop(song);
                return;
            }

            String cache = PreferenceUtil.getInstance(App.getInstance()).getLocationCache();
            File download = new File(cache, "download/" + song.id);
            File audio = new File(MusicUtil.getFileUri(song));
            boolean writingAudio = false;

            try {
                URL url = new URL(MusicUtil.getDownloadUri(song));
                URLConnection connection = url.openConnection();

                download.getParentFile().mkdirs();

                byte[] data = new byte[1048576];
                int count;

                try (InputStream input = connection.getInputStream(); OutputStream output = new FileOutputStream(download)) {
                    notification.update(0, connection.getContentLength());
                    while ((count = input.read(data)) != -1) {
                        output.write(data, 0, count);
                        notification.update(count, 0);
                    }
                }

                long expected = connection.getContentLengthLong();
                if (expected > 0 && download.length() != expected) {
                    throw new IOException("incomplete download: " + download.length() + " of " + expected);
                }

                // the temporary file lives in app storage so it can't be renamed into shared storage
                audio.getParentFile().mkdirs();
                writingAudio = true;

                try (InputStream input = new FileInputStream(download); OutputStream output = new FileOutputStream(audio)) {
                    while ((count = input.read(data)) != -1) {
                        output.write(data, 0, count);
                    }
                }

                DownloadUtil.recordDownload(song, userId, audio);
            } catch (Exception e) {
                Log.e(TAG, "download failed for " + song.id, e);
                if (writingAudio) audio.delete();
            } finally {
                download.delete();
                notification.stop(song);
            }
        });
    }
}
