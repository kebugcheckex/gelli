package com.dkanada.gramophone.fragments.player;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.dkanada.gramophone.R;
import com.dkanada.gramophone.activities.base.AbsMusicPanelActivity;
import com.dkanada.gramophone.databinding.FragmentLandscapePlayerBinding;
import com.dkanada.gramophone.helper.LyricsController;
import com.dkanada.gramophone.helper.MusicPlayerRemote;
import com.dkanada.gramophone.helper.MusicProgressViewUpdateHelper;
import com.dkanada.gramophone.helper.PlayPauseButtonOnClickHandler;
import com.dkanada.gramophone.interfaces.base.SimpleOnSeekbarChangeListener;
import com.dkanada.gramophone.model.Album;
import com.dkanada.gramophone.model.Song;
import com.dkanada.gramophone.service.QueueManager;
import com.dkanada.gramophone.util.MusicUtil;
import com.dkanada.gramophone.util.NavigationUtil;
import com.dkanada.gramophone.util.PreferenceUtil;
import com.dkanada.gramophone.util.ViewUtil;
import com.dkanada.gramophone.views.PlayPauseDrawable;

/**
 * The full-window player in any window wider than it is tall, in place of the flat or card player. The cover
 * sits on the left with the lyrics laid over it, and the right column has the track, the controls and the queue.
 * The track details are in the overflow menu. The window is always dark, with a gradient made from the cover
 * palette, so the views are inflated with a dark theme overlay whatever the app theme is.
 */
public class LandscapePlayerFragment extends AbsPlayerFragment implements PlayerAlbumCoverFragment.Callbacks, MusicProgressViewUpdateHelper.Callback {
    @ColorInt
    private static final int DEFAULT_COLOR = 0xFF424242;
    @ColorInt
    private static final int ICON_COLOR = Color.WHITE;
    @ColorInt
    private static final int ICON_COLOR_OFF = 0x99FFFFFF;

    private FragmentLandscapePlayerBinding binding;
    private PlayerAlbumCoverFragment playerAlbumCoverFragment;
    private PlayingQueueListFragment playingQueueListFragment;

    private MusicProgressViewUpdateHelper progressViewUpdateHelper;
    private PlayPauseDrawable playPauseDrawable;

    private final GradientDrawable background = new GradientDrawable();
    private ValueAnimator backgroundAnimator;
    private int[] backgroundColors;
    private int lastColor = DEFAULT_COLOR;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        progressViewUpdateHelper = new MusicProgressViewUpdateHelper(this);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Context themed = new ContextThemeWrapper(requireContext(), R.style.ThemeOverlay_Gelli_LandscapePlayer);
        binding = FragmentLandscapePlayerBinding.inflate(inflater.cloneInContext(themed), container, false);

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // the player draws behind the system bars and a display cutout, and keeps its content clear of them
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            binding.landscapePlayerContent.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        setUpBackground();
        setUpHeader();
        setUpControls();
        setUpProgressSlider();

        playerAlbumCoverFragment = (PlayerAlbumCoverFragment) getChildFragmentManager().findFragmentById(R.id.player_album_cover_fragment);
        playerAlbumCoverFragment.setCallbacks(this);
        playingQueueListFragment = (PlayingQueueListFragment) getChildFragmentManager().findFragmentById(R.id.landscape_player_queue_fragment);

        // a short window's cover has room for the current line alone, and a taller one for its neighbors too
        binding.landscapePlayerLyrics.setFocusedLine(true, getResources().getBoolean(R.bool.landscape_player_lyrics_neighbors));
        binding.landscapePlayerLyrics.setTextColor(ICON_COLOR);
        updateLyricsScrim();
        onLyricsStateChanged();

        // scrolling the queue shouldn't drag the panel closed
        ((AbsMusicPanelActivity) requireActivity()).setAntiDragView(binding.landscapePlayerQueueFragment);
    }

    @Override
    public void onDestroyView() {
        if (backgroundAnimator != null) {
            backgroundAnimator.cancel();
            backgroundAnimator = null;
        }

        binding = null;
        super.onDestroyView();
    }

    @Override
    public void onResume() {
        super.onResume();
        progressViewUpdateHelper.start();
        // the opacity may have changed in the settings
        if (binding != null) updateLyricsScrim();
    }

    @Override
    public void onPause() {
        super.onPause();
        progressViewUpdateHelper.stop();
    }

    private void setUpBackground() {
        background.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        background.setGradientCenter(0.2f, 0.3f);
        backgroundColors = getBackgroundColors(lastColor);
        background.setColors(backgroundColors);

        binding.landscapePlayerBackground.setBackground(background);
        binding.landscapePlayerBackground.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
            background.setGradientRadius(Math.max(right - left, bottom - top) * 0.9f));
    }

    private void setUpHeader() {
        binding.landscapePlayerCloseButton.setOnClickListener(v -> ((AbsMusicPanelActivity) requireActivity()).collapsePanel());

        binding.landscapePlayerSource.setOnClickListener(v -> {
            Song song = MusicPlayerRemote.getCurrentSong();
            if (song != null) NavigationUtil.startAlbum(requireActivity(), new Album(song), null);
        });

        binding.landscapePlayerLyricsButton.setIconTint(ColorStateList.valueOf(ICON_COLOR));
        binding.landscapePlayerLyricsButton.setOnClickListener(v -> lyricsController.setEnabled(!lyricsController.isEnabled()));

        binding.landscapePlayerMenuButton.setOnClickListener(v -> {
            PopupMenu menu = new PopupMenu(v.getContext(), v);
            menu.inflate(R.menu.menu_landscape_player);
            menu.setOnMenuItemClickListener(this::onMenuItemClick);
            menu.show();
        });
    }

    private void setUpControls() {
        playPauseDrawable = new PlayPauseDrawable(requireContext());
        binding.landscapePlayerPlayPauseButton.setIcon(playPauseDrawable);
        binding.landscapePlayerPlayPauseButton.setOnClickListener(new PlayPauseButtonOnClickHandler());
        binding.landscapePlayerBufferingIndicator.setIndicatorColor(0xFF1A1A1A);

        binding.landscapePlayerPrevButton.setIconTint(ColorStateList.valueOf(ICON_COLOR));
        binding.landscapePlayerPrevButton.setOnClickListener(v -> MusicPlayerRemote.back());
        binding.landscapePlayerNextButton.setIconTint(ColorStateList.valueOf(ICON_COLOR));
        binding.landscapePlayerNextButton.setOnClickListener(v -> MusicPlayerRemote.playNextSong());

        binding.landscapePlayerShuffleButton.setOnClickListener(v -> MusicPlayerRemote.toggleShuffleMode());
        binding.landscapePlayerRepeatButton.setOnClickListener(v -> MusicPlayerRemote.cycleRepeatMode());

        binding.landscapePlayerCloseButton.setIconTint(ColorStateList.valueOf(ICON_COLOR));
        binding.landscapePlayerMenuButton.setIconTint(ColorStateList.valueOf(ICON_COLOR));
        binding.landscapePlayerFavoriteButton.setIconTint(ColorStateList.valueOf(ICON_COLOR));
        binding.landscapePlayerFavoriteButton.setOnClickListener(v -> {
            Song song = MusicPlayerRemote.getCurrentSong();
            if (song != null) toggleFavorite(song);
        });
    }

    private void setUpProgressSlider() {
        binding.landscapePlayerProgressSlider.setOnSeekBarChangeListener(new SimpleOnSeekbarChangeListener() {
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
    public void onLyricsStateChanged() {
        if (binding == null) return;

        boolean enabled = lyricsController.isEnabled();
        boolean ready = lyricsController.getState() == LyricsController.State.READY;

        binding.landscapePlayerLyrics.setLyrics(lyricsController.getLyrics());
        binding.landscapePlayerLyrics.setVisibility(ready ? View.VISIBLE : View.GONE);

        binding.landscapePlayerLyricsButton.setIconResource(enabled ? R.drawable.ic_lyrics_white_24dp : R.drawable.ic_lyrics_border_white_24dp);
        binding.landscapePlayerLyricsButton.setContentDescription(getString(enabled ? R.string.action_hide_lyrics : R.string.action_show_lyrics));
    }

    /** The scrim behind the lyrics is the deep edge of the background gradient, at the opacity from the settings. */
    private void updateLyricsScrim() {
        int opacity = PreferenceUtil.getInstance(requireContext()).getLyricsOpacity();
        int edge = backgroundColors[backgroundColors.length - 1];
        binding.landscapePlayerLyrics.setBackgroundColor(ColorUtils.setAlphaComponent(edge, Math.round(opacity * 255 / 100f)));
    }

    @Override
    public void onLyricsPositionChanged(int line, float fill) {
        if (binding != null) binding.landscapePlayerLyrics.setPosition(line, fill);
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        updateCurrentSong();
        updatePlayPauseDrawableState(false);
        updateRepeatState();
        updateShuffleState();
    }

    @Override
    public void onPlayMetadataChanged() {
        super.onPlayMetadataChanged();
        updateCurrentSong();
    }

    @Override
    public void onPlayStateChanged() {
        super.onPlayStateChanged();
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
    public void onUpdateProgressViews(int progress, int total) {
        if (binding == null) return;

        binding.landscapePlayerProgressSlider.setMax(total);
        binding.landscapePlayerProgressSlider.setProgress(progress);

        binding.landscapePlayerTotalTime.setText(MusicUtil.getReadableDurationString(total));
        binding.landscapePlayerCurrentProgress.setText(MusicUtil.getReadableDurationString(progress));
        binding.landscapePlayerBufferingIndicator.setVisibility(MusicPlayerRemote.isLoading() ? View.VISIBLE : View.GONE);
    }

    private void updateCurrentSong() {
        Song song = MusicPlayerRemote.getCurrentSong();
        if (song == null || binding == null) return;

        binding.landscapePlayerTitle.setText(song.title);
        // the marquee only runs on a selected view
        binding.landscapePlayerTitle.setSelected(true);
        binding.landscapePlayerText.setText(MusicUtil.buildInfoString(song.artistName, song.albumName));
        binding.landscapePlayerAlbum.setText(song.albumName);

        updateIsFavorite();
    }

    private void updateIsFavorite() {
        Song song = MusicPlayerRemote.getCurrentSong();
        if (song == null || binding == null) return;

        binding.landscapePlayerFavoriteButton.setIconResource(song.favorite ? R.drawable.ic_favorite_white_24dp : R.drawable.ic_favorite_border_white_24dp);
        binding.landscapePlayerFavoriteButton.setContentDescription(getString(song.favorite ? R.string.action_remove_from_favorites : R.string.action_add_to_favorites));
    }

    private void updatePlayPauseDrawableState(boolean animate) {
        if (MusicPlayerRemote.isPlaying()) {
            playPauseDrawable.setPause(animate);
        } else {
            playPauseDrawable.setPlay(animate);
        }
    }

    private void updateShuffleState() {
        if (binding == null) return;

        boolean on = MusicPlayerRemote.getShuffleMode() == QueueManager.SHUFFLE_MODE_SHUFFLE;
        binding.landscapePlayerShuffleButton.setIconTint(ColorStateList.valueOf(on ? ICON_COLOR : ICON_COLOR_OFF));
        binding.landscapePlayerShuffleButton.setActivated(on);
    }

    private void updateRepeatState() {
        if (binding == null) return;

        int mode = MusicPlayerRemote.getRepeatMode();
        binding.landscapePlayerRepeatButton.setIconResource(mode == QueueManager.REPEAT_MODE_THIS ? R.drawable.ic_repeat_one_white_24dp : R.drawable.ic_repeat_white_24dp);
        binding.landscapePlayerRepeatButton.setIconTint(ColorStateList.valueOf(mode == QueueManager.REPEAT_MODE_NONE ? ICON_COLOR_OFF : ICON_COLOR));
    }

    /**
     * Darkens the palette colour so white text always reads on it: a lighter centre behind the cover that fades
     * to a deep edge.
     */
    private static int[] getBackgroundColors(@ColorInt int color) {
        float[] hsl = new float[3];
        ColorUtils.colorToHSL(color, hsl);
        float lightness = Math.min(hsl[2], 0.32f);

        return new int[]{
            ColorUtils.HSLToColor(new float[]{hsl[0], hsl[1], lightness}),
            ColorUtils.HSLToColor(new float[]{hsl[0], hsl[1], lightness * 0.6f}),
            ColorUtils.HSLToColor(new float[]{hsl[0], hsl[1], lightness * 0.35f}),
        };
    }

    private void animateBackground(@ColorInt int color) {
        if (backgroundAnimator != null) backgroundAnimator.cancel();

        int[] from = backgroundColors.clone();
        int[] to = getBackgroundColors(color);
        backgroundAnimator = ValueAnimator.ofFloat(0f, 1f).setDuration(ViewUtil.PHONOGRAPH_ANIM_TIME);
        backgroundAnimator.addUpdateListener(animation -> {
            float fraction = (float) animation.getAnimatedValue();
            for (int i = 0; i < to.length; i++) {
                backgroundColors[i] = ColorUtils.blendARGB(from[i], to[i], fraction);
            }
            background.setColors(backgroundColors);
            if (binding != null) updateLyricsScrim();
        });
        backgroundAnimator.start();
    }

    @Override
    @ColorInt
    public int getPaletteColor() {
        return lastColor;
    }

    @Override
    public void onColorChanged(int color) {
        if (binding != null) animateBackground(color);
        lastColor = color;
        getCallbacks().onPaletteColorChanged();
    }

    @Override
    public void onFavoriteToggled() {
        Song song = MusicPlayerRemote.getCurrentSong();
        if (song != null) toggleFavorite(song);
    }

    @Override
    public void onToolbarToggled() {
        // there is no toolbar over the cover to hide
    }

    @Override
    protected void toggleFavorite(Song song) {
        super.toggleFavorite(song);

        Song current = MusicPlayerRemote.getCurrentSong();
        if (current != null && song.id.equals(current.id)) {
            if (song.favorite) playerAlbumCoverFragment.showHeartAnimation();
            updateIsFavorite();
        }
    }

    @Override
    public void onShow() {
        lyricsController.setPlayerVisible(true);
        if (playingQueueListFragment != null) playingQueueListFragment.scrollToCurrent();
    }

    @Override
    public void onHide() {
        lyricsController.setPlayerVisible(false);
    }

    @Override
    public boolean onBackPressed() {
        return false;
    }
}
