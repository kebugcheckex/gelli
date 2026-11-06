package com.dkanada.gramophone.dialogs;

import android.app.AlarmManager;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.dkanada.gramophone.R;
import com.dkanada.gramophone.databinding.DialogSleepTimerBinding;
import com.dkanada.gramophone.helper.MusicPlayerRemote;
import com.dkanada.gramophone.service.MusicService;
import com.dkanada.gramophone.util.MusicUtil;
import com.dkanada.gramophone.util.PreferenceUtil;
import com.dkanada.gramophone.util.ThemeUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.triggertrap.seekarc.SeekArc;

public class SleepTimerDialog extends DialogFragment {
    private DialogSleepTimerBinding binding;

    private TimerUpdater timerUpdater;
    private AlertDialog dialog;

    private int seekArcProgress;

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);

        timerUpdater.cancel();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireActivity());
        binding = DialogSleepTimerBinding.inflate(LayoutInflater.from(builder.getContext()));

        timerUpdater = new TimerUpdater();
        dialog = builder
                .setTitle(R.string.action_sleep_timer)
                .setView(binding.getRoot())
                .setPositiveButton(R.string.action_set, (d, which) -> {
                    PreferenceUtil.getInstance(getActivity()).setSleepTimerFinishMusic(binding.shouldFinishLastSong.isChecked());

                    final int minutes = seekArcProgress;

                    PendingIntent pi = makeTimerPendingIntent(PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);

                    final long nextSleepTimerElapsedTime = SystemClock.elapsedRealtime() + minutes * 60 * 1000;
                    PreferenceUtil.getInstance(getActivity()).setNextSleepTimerElapsedRealtime(nextSleepTimerElapsedTime);
                    AlarmManager am = (AlarmManager) requireActivity().getSystemService(Context.ALARM_SERVICE);
                    scheduleSleepTimer(am, nextSleepTimerElapsedTime, pi);

                    Toast.makeText(getActivity(), requireActivity().getResources().getString(R.string.sleep_timer_set, minutes), Toast.LENGTH_SHORT).show();
                })
                // shown only while a timer is running, see updateCancelButton
                .setNeutralButton(R.string.cancel_current_timer, (d, which) -> {
                    final PendingIntent previous = makeTimerPendingIntent(PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
                    if (previous != null) {
                        AlarmManager am = (AlarmManager) requireActivity().getSystemService(Context.ALARM_SERVICE);
                        am.cancel(previous);
                        previous.cancel();
                        PreferenceUtil.getInstance(getActivity()).setNextSleepTimerElapsedRealtime(-1);
                        Toast.makeText(getActivity(), requireActivity().getResources().getString(R.string.sleep_timer_canceled), Toast.LENGTH_SHORT).show();
                    }

                    MusicService musicService = MusicPlayerRemote.musicService;
                    if (musicService != null && musicService.pendingQuit) {
                        musicService.pendingQuit = false;
                        PreferenceUtil.getInstance(getActivity()).setNextSleepTimerElapsedRealtime(-1);
                        Toast.makeText(getActivity(), requireActivity().getResources().getString(R.string.sleep_timer_canceled), Toast.LENGTH_SHORT).show();
                    }
                })
                .create();

        dialog.setOnShowListener(d -> {
            updateCancelButton();
            if (makeTimerPendingIntent(PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE) != null) {
                timerUpdater.start();
            }
        });

        boolean finishMusic = PreferenceUtil.getInstance(getActivity()).getSleepTimerFinishMusic();
        binding.shouldFinishLastSong.setChecked(finishMusic);

        int primaryColor = ThemeUtil.getColorResource(requireActivity(), R.attr.colorPrimary);

        binding.seekArc.setProgressColor(primaryColor);
        binding.seekArc.setThumbColor(primaryColor);

        binding.seekArc.post(() -> {
            int width = binding.seekArc.getWidth();
            int height = binding.seekArc.getHeight();
            int small = Math.min(width, height);

            FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(binding.seekArc.getLayoutParams());
            layoutParams.height = small;
            binding.seekArc.setLayoutParams(layoutParams);
        });

        seekArcProgress = PreferenceUtil.getInstance(getActivity()).getLastSleepTimerValue();
        updateTimeDisplayTime();
        binding.seekArc.setProgress(seekArcProgress);

        binding.seekArc.setOnSeekArcChangeListener(new SeekArc.OnSeekArcChangeListener() {
            @Override
            public void onProgressChanged(@NonNull SeekArc seekArc, int i, boolean b) {
                if (i < 1) {
                    seekArc.setProgress(1);
                    return;
                }

                seekArcProgress = i;
                updateTimeDisplayTime();
            }

            @Override
            public void onStartTrackingTouch(SeekArc seekArc) {
            }

            @Override
            public void onStopTrackingTouch(SeekArc seekArc) {
                PreferenceUtil.getInstance(getActivity()).setLastSleepTimerValue(seekArcProgress);
            }
        });

        return dialog;
    }

    private void updateTimeDisplayTime() {
        binding.timerDisplay.setText(getString(R.string.sleep_timer_display, seekArcProgress));
    }

    private void scheduleSleepTimer(AlarmManager alarmManager, long triggerAtMillis, PendingIntent pendingIntent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtMillis, pendingIntent);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtMillis, pendingIntent);
            } else {
                alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtMillis, pendingIntent);
            }
        } catch (SecurityException ignored) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtMillis, pendingIntent);
            } else {
                alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtMillis, pendingIntent);
            }
        }
    }

    private PendingIntent makeTimerPendingIntent(int flag) {
        return PendingIntent.getService(getActivity(), 0, makeTimerIntent(), flag);
    }

    private Intent makeTimerIntent() {
        Intent intent = new Intent(getActivity(), MusicService.class);
        if (binding.shouldFinishLastSong.isChecked()) {
            return intent.setAction(MusicService.ACTION_PENDING_QUIT);
        }

        return intent.setAction(MusicService.ACTION_QUIT);
    }

    private void updateCancelButton() {
        MusicService musicService = MusicPlayerRemote.musicService;
        if (musicService != null && musicService.pendingQuit) {
            setCancelButtonText(getString(R.string.cancel_current_timer));
        } else {
            setCancelButtonText(null);
        }
    }

    // a null text hides the button
    private void setCancelButtonText(String text) {
        Button button = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
        if (button == null) return;

        button.setVisibility(text == null ? View.GONE : View.VISIBLE);
        if (text != null) button.setText(text);
    }

    private class TimerUpdater extends CountDownTimer {
        public TimerUpdater() {
            super(PreferenceUtil.getInstance(getActivity()).getNextSleepTimerElapsedRealTime() - SystemClock.elapsedRealtime(), 1000);
        }

        @Override
        public void onTick(long millisUntilFinished) {
            setCancelButtonText(getString(R.string.cancel_current_timer) + " (" + MusicUtil.getReadableDurationString(millisUntilFinished) + ")");
        }

        @Override
        public void onFinish() {
            updateCancelButton();
        }
    }
}
