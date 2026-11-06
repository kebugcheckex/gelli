package com.dkanada.gramophone.fragments.player;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.dkanada.gramophone.R;
import com.dkanada.gramophone.activities.base.AbsMusicPanelActivity;
import com.dkanada.gramophone.databinding.FragmentPlayerBarBinding;
import com.dkanada.gramophone.fragments.AbsMusicServiceFragment;
import com.dkanada.gramophone.glide.CustomGlideRequest;
import com.dkanada.gramophone.helper.MusicPlayerRemote;
import com.dkanada.gramophone.helper.MusicProgressViewUpdateHelper;
import com.dkanada.gramophone.helper.PlayPauseButtonOnClickHandler;
import com.dkanada.gramophone.interfaces.base.SimpleOnSeekbarChangeListener;
import com.dkanada.gramophone.model.Song;
import com.dkanada.gramophone.service.QueueManager;
import com.dkanada.gramophone.util.MusicUtil;
import com.dkanada.gramophone.util.ThemeUtil;
import com.dkanada.gramophone.views.PlayPauseDrawable;

/**
 * The collapsed player at the expanded width, in place of the mini player. It sits on the theme surface,
 * so the controls use the accent colour for anything that is switched on.
 */
public class PlayerBarFragment extends AbsMusicServiceFragment implements MusicProgressViewUpdateHelper.Callback {
    private FragmentPlayerBarBinding binding;

    private PlayPauseDrawable playPauseDrawable;
    private MusicProgressViewUpdateHelper progressViewUpdateHelper;

    private int primaryColor;
    private int iconColor;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        progressViewUpdateHelper = new MusicProgressViewUpdateHelper(this);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentPlayerBarBinding.inflate(inflater, container, false);

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Context context = requireContext();
        primaryColor = ThemeUtil.getColorResource(context, R.attr.colorPrimary);
        iconColor = ThemeUtil.getColorResource(context, R.attr.colorOnSurfaceVariant);
        int transportColor = ThemeUtil.getColorResource(context, R.attr.colorOnSurface);

        view.setOnClickListener(v -> getPanelActivity().expandPanel());

        playPauseDrawable = new PlayPauseDrawable(context);
        binding.playerBarPlayPauseButton.setImageDrawable(playPauseDrawable);
        binding.playerBarPlayPauseButton.setBackgroundTintList(ColorStateList.valueOf(primaryColor));
        binding.playerBarPlayPauseButton.setColorFilter(ThemeUtil.getColorResource(context, R.attr.colorOnPrimary), PorterDuff.Mode.SRC_IN);
        binding.playerBarPlayPauseButton.setOnClickListener(new PlayPauseButtonOnClickHandler());

        binding.playerBarPrevButton.setColorFilter(transportColor, PorterDuff.Mode.SRC_IN);
        binding.playerBarPrevButton.setOnClickListener(v -> MusicPlayerRemote.back());
        binding.playerBarNextButton.setColorFilter(transportColor, PorterDuff.Mode.SRC_IN);
        binding.playerBarNextButton.setOnClickListener(v -> MusicPlayerRemote.playNextSong());

        binding.playerBarShuffleButton.setOnClickListener(v -> MusicPlayerRemote.toggleShuffleMode());
        binding.playerBarRepeatButton.setOnClickListener(v -> MusicPlayerRemote.cycleRepeatMode());

        binding.playerBarFavoriteButton.setOnClickListener(v -> {
            Song song = MusicPlayerRemote.getCurrentSong();
            if (song == null) return;
            MusicUtil.toggleFavorite(song);
            updateIsFavorite();
        });

        binding.playerBarQueueButton.setOnClickListener(v -> getPanelActivity().toggleQueueSheet());
        binding.playerBarExpandButton.setColorFilter(iconColor, PorterDuff.Mode.SRC_IN);
        binding.playerBarExpandButton.setOnClickListener(v -> getPanelActivity().expandPanel());

        setUpProgressSlider();
        updateQueueButton();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private AbsMusicPanelActivity getPanelActivity() {
        return (AbsMusicPanelActivity) requireActivity();
    }

    private void setUpProgressSlider() {
        int trackColor = ThemeUtil.getColorResource(requireContext(), R.attr.colorOutlineVariant);
        binding.playerBarProgressSlider.setThumbTintList(ColorStateList.valueOf(primaryColor));
        binding.playerBarProgressSlider.setProgressTintList(ColorStateList.valueOf(primaryColor));
        binding.playerBarProgressSlider.setProgressBackgroundTintList(ColorStateList.valueOf(trackColor));

        binding.playerBarProgressSlider.setOnSeekBarChangeListener(new SimpleOnSeekbarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    MusicPlayerRemote.seekTo(progress);
                    onUpdateProgressViews(MusicPlayerRemote.getSongProgressMillis(), MusicPlayerRemote.getSongDurationMillis());
                }
            }
        });
    }

    @Override
    public void onServiceConnected() {
        updateCurrentSong();
        updatePlayPauseDrawableState(false);
        updateRepeatState();
        updateShuffleState();
    }

    @Override
    public void onPlayMetadataChanged() {
        updateCurrentSong();
    }

    @Override
    public void onPlayStateChanged() {
        updatePlayPauseDrawableState(true);
    }

    @Override
    public void onRepeatModeChanged() {
        updateRepeatState();
    }

    @Override
    public void onShuffleModeChanged() {
        updateShuffleState();
    }

    @Override
    public void onResume() {
        super.onResume();
        progressViewUpdateHelper.start();
    }

    @Override
    public void onPause() {
        super.onPause();
        progressViewUpdateHelper.stop();
    }

    @Override
    public void onUpdateProgressViews(int progress, int total) {
        if (binding == null) return;

        binding.playerBarProgressSlider.setMax(total);
        binding.playerBarProgressSlider.setProgress(progress);

        binding.playerBarTotalTime.setText(MusicUtil.getReadableDurationString(total));
        binding.playerBarCurrentProgress.setText(MusicUtil.getReadableDurationString(progress));
    }

    private void updateCurrentSong() {
        Song song = MusicPlayerRemote.getCurrentSong();
        if (song == null || binding == null) return;

        binding.playerBarTitle.setText(song.title);
        binding.playerBarText.setText(MusicUtil.buildInfoString(song.artistName, song.albumName));

        CustomGlideRequest.Builder
            .from(requireContext(), song.primary, song.blurHash)
            .build().into(binding.playerBarImage);

        updateIsFavorite();
    }

    /**
     * The expanded player can change the favorite too, so the activity calls this when the panel collapses.
     */
    public void updateIsFavorite() {
        Song song = MusicPlayerRemote.getCurrentSong();
        if (song == null || binding == null) return;

        binding.playerBarFavoriteButton.setImageResource(song.favorite ? R.drawable.ic_favorite_white_24dp : R.drawable.ic_favorite_border_white_24dp);
        binding.playerBarFavoriteButton.setColorFilter(song.favorite ? primaryColor : iconColor, PorterDuff.Mode.SRC_IN);
        binding.playerBarFavoriteButton.setContentDescription(getString(song.favorite ? R.string.action_remove_from_favorites : R.string.action_add_to_favorites));
    }

    public void updateQueueButton() {
        if (binding == null) return;

        boolean open = getPanelActivity().isQueueSheetOpen();
        binding.playerBarQueueButton.setColorFilter(open ? primaryColor : iconColor, PorterDuff.Mode.SRC_IN);
        binding.playerBarQueueButton.setActivated(open);
    }

    private void updatePlayPauseDrawableState(boolean animate) {
        if (MusicPlayerRemote.isPlaying()) {
            playPauseDrawable.setPause(animate);
        } else {
            playPauseDrawable.setPlay(animate);
        }
    }

    private void updateShuffleState() {
        boolean on = MusicPlayerRemote.getShuffleMode() == QueueManager.SHUFFLE_MODE_SHUFFLE;
        binding.playerBarShuffleButton.setColorFilter(on ? primaryColor : iconColor, PorterDuff.Mode.SRC_IN);
    }

    private void updateRepeatState() {
        int mode = MusicPlayerRemote.getRepeatMode();
        binding.playerBarRepeatButton.setImageResource(mode == QueueManager.REPEAT_MODE_THIS ? R.drawable.ic_repeat_one_white_24dp : R.drawable.ic_repeat_white_24dp);
        binding.playerBarRepeatButton.setColorFilter(mode == QueueManager.REPEAT_MODE_NONE ? iconColor : primaryColor, PorterDuff.Mode.SRC_IN);
    }
}
