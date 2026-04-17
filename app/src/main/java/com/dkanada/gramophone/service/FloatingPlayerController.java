package com.dkanada.gramophone.service;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.dkanada.gramophone.R;
import com.dkanada.gramophone.activities.MainActivity;
import com.dkanada.gramophone.glide.CustomGlideRequest;
import com.dkanada.gramophone.model.Song;
import com.dkanada.gramophone.util.PreferenceUtil;

public class FloatingPlayerController {
    private final MusicService service;
    private final WindowManager windowManager;

    @Nullable
    private View overlayView;
    @Nullable
    private WindowManager.LayoutParams params;

    private ImageView coverView;
    private TextView titleView;
    private ImageButton playPauseButton;

    public FloatingPlayerController(MusicService service) {
        this.service = service;
        this.windowManager = (WindowManager) service.getSystemService(Context.WINDOW_SERVICE);
    }

    public static boolean isSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
    }

    public static boolean canShow(Context context) {
        return isSupported() && Settings.canDrawOverlays(context);
    }

    public boolean isVisible() {
        return overlayView != null;
    }

    public void show(Song song, boolean playing) {
        if (!canShow(service)) return;
        if (overlayView != null) {
            updateMeta(song);
            updateState(playing);
            return;
        }

        overlayView = LayoutInflater.from(service).inflate(R.layout.floating_player, null);
        coverView = overlayView.findViewById(R.id.floating_player_image);
        titleView = overlayView.findViewById(R.id.floating_player_title);
        playPauseButton = overlayView.findViewById(R.id.floating_player_play_pause);
        ImageButton nextButton = overlayView.findViewById(R.id.floating_player_next);
        ImageButton closeButton = overlayView.findViewById(R.id.floating_player_close);

        playPauseButton.setOnClickListener(v -> {
            if (service.isPlaying()) {
                service.pause();
            } else {
                service.play();
            }
        });
        nextButton.setOnClickListener(v -> service.playNextSong());
        closeButton.setOnClickListener(v -> service.quit());

        params = buildLayoutParams();
        attachDragListener(overlayView);

        try {
            windowManager.addView(overlayView, params);
        } catch (WindowManager.BadTokenException | SecurityException e) {
            overlayView = null;
            params = null;
            return;
        }

        updateMeta(song);
        updateState(playing);
    }

    public void hide() {
        if (overlayView == null) return;
        try {
            windowManager.removeView(overlayView);
        } catch (IllegalArgumentException ignored) {
        }
        overlayView = null;
        params = null;
        coverView = null;
        titleView = null;
        playPauseButton = null;
    }

    public void updateState(boolean playing) {
        if (playPauseButton == null) return;
        playPauseButton.setImageResource(
            playing ? R.drawable.ic_pause_white_24dp : R.drawable.ic_play_arrow_white_24dp);
    }

    public void updateMeta(@Nullable Song song) {
        if (overlayView == null || song == null) return;
        titleView.setText(song.title);
        CustomGlideRequest.Builder
            .from(service, song.primary, song.blurHash)
            .build()
            .into(coverView);
    }

    private WindowManager.LayoutParams buildLayoutParams() {
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.START;

        PreferenceUtil prefs = PreferenceUtil.getInstance(service);
        int savedX = prefs.getFloatingPlayerX();
        int savedY = prefs.getFloatingPlayerY();
        if (savedX < 0 || savedY < 0) {
            int density = (int) service.getResources().getDisplayMetrics().density;
            p.x = 16 * density;
            p.y = 96 * density;
        } else {
            p.x = savedX;
            p.y = savedY;
        }
        return p;
    }

    @SuppressLint("ClickableViewAccessibility")
    private void attachDragListener(View root) {
        final int touchSlop = ViewConfiguration.get(service).getScaledTouchSlop();

        root.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float downRawX;
            private float downRawY;
            private boolean dragging;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                if (params == null || overlayView == null) return false;

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        downRawX = event.getRawX();
                        downRawY = event.getRawY();
                        dragging = false;
                        return true;

                    case MotionEvent.ACTION_MOVE: {
                        int dx = (int) (event.getRawX() - downRawX);
                        int dy = (int) (event.getRawY() - downRawY);
                        if (!dragging && (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop)) {
                            dragging = true;
                        }
                        if (dragging) {
                            params.x = clampX(initialX + dx);
                            params.y = clampY(initialY + dy);
                            try {
                                windowManager.updateViewLayout(overlayView, params);
                            } catch (IllegalArgumentException ignored) {
                            }
                        }
                        return true;
                    }

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (dragging) {
                            PreferenceUtil.getInstance(service)
                                .setFloatingPlayerPosition(params.x, params.y);
                        } else if (event.getAction() == MotionEvent.ACTION_UP) {
                            view.performClick();
                            openApp();
                        }
                        return true;
                }
                return false;
            }
        });

        root.setOnClickListener(v -> { /* handled in ACTION_UP */ });
    }

    private int clampX(int x) {
        if (overlayView == null) return x;
        int screen = service.getResources().getDisplayMetrics().widthPixels;
        int width = overlayView.getWidth();
        int max = Math.max(0, screen - width);
        return Math.max(0, Math.min(x, max));
    }

    private int clampY(int y) {
        if (overlayView == null) return y;
        int screen = service.getResources().getDisplayMetrics().heightPixels;
        int height = overlayView.getHeight();
        int max = Math.max(0, screen - height);
        return Math.max(0, Math.min(y, max));
    }

    private void openApp() {
        Intent intent = new Intent(service, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        service.startActivity(intent);
    }
}
