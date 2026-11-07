package com.dkanada.gramophone.fragments.player;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.dkanada.gramophone.R;
import com.dkanada.gramophone.activities.base.AbsMusicPanelActivity;
import com.dkanada.gramophone.databinding.FragmentLandscapePlayerBinding;
import com.dkanada.gramophone.dialogs.SongDetailDialog;
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
import com.dkanada.gramophone.util.ViewUtil;
import com.dkanada.gramophone.views.PlayPauseDrawable;

/**
 * The full-window player in any window wider than it is tall, in place of the flat or card player. The cover
 * sits on the left, and the right column has the track, the controls, and a switch between the queue, the lyrics
 * and the track details. The window is always dark, with a gradient made from the cover palette, so the views are
 * inflated with a dark theme overlay whatever the app theme is.
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

    // short windows have no room for the tabs, so they show the queue and keep the lyrics and details in the overflow menu
    private boolean showTabs;

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

        setUpTabs();
        binding.landscapePlayerLyrics.setTextColor(ICON_COLOR);
        onLyricsStateChanged();

        // scrolling the queue, the lyrics or the details shouldn't drag the panel closed
        ((AbsMusicPanelActivity) requireActivity()).setAntiDragView(binding.landscapePlayerTabContent);
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

        binding.landscapePlayerMenuButton.setOnClickListener(v -> {
            PopupMenu menu = new PopupMenu(v.getContext(), v);
            menu.inflate(R.menu.menu_landscape_player);
            menu.getMenu().findItem(R.id.action_details).setVisible(!showTabs);
            menu.getMenu().findItem(R.id.action_lyrics).setVisible(!showTabs).setChecked(lyricsController.isEnabled());
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

    private void setUpTabs() {
        showTabs = getResources().getBoolean(R.bool.landscape_player_tabs);
        binding.landscapePlayerTabs.setVisibility(showTabs ? View.VISIBLE : View.GONE);

        // the tab is restored from the preference, so lyrics stay on across a rotation
        if (showTabs && lyricsController.isEnabled()) {
            binding.landscapePlayerTabs.check(R.id.landscape_player_tab_lyrics);
        }

        binding.landscapePlayerTabs.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;

            lyricsController.setEnabled(checkedId == R.id.landscape_player_tab_lyrics);
            updateTabContent();
        });
    }

    private void updateTabContent() {
        int tab = binding.landscapePlayerTabs.getCheckedButtonId();
        boolean ready = lyricsController.getState() == LyricsController.State.READY;
        boolean details = showTabs && tab == R.id.landscape_player_tab_details;
        // a short window keeps the queue until there are lyrics to swap in, but the user chose this tab
        boolean lyrics = showTabs ? tab == R.id.landscape_player_tab_lyrics : ready;

        binding.landscapePlayerDetails.setVisibility(details ? View.VISIBLE : View.GONE);
        binding.landscapePlayerLyrics.setVisibility(lyrics ? View.VISIBLE : View.GONE);
        // keep the queue's view around so it doesn't lose its place, just hide it
        if (playingQueueListFragment.getView() != null) {
            playingQueueListFragment.getView().setVisibility(details || lyrics ? View.GONE : View.VISIBLE);
        }

        if (ready) {
            binding.landscapePlayerLyrics.setLyrics(lyricsController.getLyrics());
        } else {
            boolean missing = lyricsController.getState() == LyricsController.State.MISSING;
            binding.landscapePlayerLyrics.setMessage(missing ? getString(R.string.no_lyrics) : null);
        }
    }

    @Override
    public void onLyricsStateChanged() {
        if (binding == null) return;

        // the preference can change in another activity's player, and selecting another tab is what turns it off
        if (showTabs) {
            boolean onLyricsTab = binding.landscapePlayerTabs.getCheckedButtonId() == R.id.landscape_player_tab_lyrics;
            if (lyricsController.isEnabled() && !onLyricsTab) {
                binding.landscapePlayerTabs.check(R.id.landscape_player_tab_lyrics);
            } else if (!lyricsController.isEnabled() && onLyricsTab) {
                binding.landscapePlayerTabs.check(R.id.landscape_player_tab_queue);
            }
        }

        updateTabContent();
    }

    @Override
    public void onCurrentLyricLineChanged(int line) {
        if (binding != null) binding.landscapePlayerLyrics.setCurrentLine(line);
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
        updateDetails(song);
    }

    private void updateIsFavorite() {
        Song song = MusicPlayerRemote.getCurrentSong();
        if (song == null || binding == null) return;

        binding.landscapePlayerFavoriteButton.setIconResource(song.favorite ? R.drawable.ic_favorite_white_24dp : R.drawable.ic_favorite_border_white_24dp);
        binding.landscapePlayerFavoriteButton.setContentDescription(getString(song.favorite ? R.string.action_remove_from_favorites : R.string.action_add_to_favorites));
    }

    private void updateDetails(Song song) {
        ViewGroup list = binding.landscapePlayerDetailsList;
        list.removeAllViews();

        addDetail(list, R.string.album, song.albumName);
        addDetail(list, R.string.label_artist, song.artistName);
        if (song.year > 0) addDetail(list, R.string.label_year, Integer.toString(song.year));
        if (song.trackNumber > 0) {
            addDetail(list, R.string.label_track, song.discNumber > 0 ? song.discNumber + "-" + song.trackNumber : Integer.toString(song.trackNumber));
        }
        addDetail(list, R.string.label_track_length, MusicUtil.getReadableDurationString(song.duration));
        if (song.container != null && song.codec != null) addDetail(list, R.string.label_file_format, SongDetailDialog.getFormatString(song));
        if (song.bitRate > 0) addDetail(list, R.string.label_bit_rate, SongDetailDialog.getBitRateString(song.bitRate));
        if (song.sampleRate > 0) addDetail(list, R.string.label_sample_rate, SongDetailDialog.getSampleRateString(song.sampleRate));
        if (song.bitDepth > 0) addDetail(list, R.string.label_bit_depth, SongDetailDialog.getBitDepthString(song.bitDepth));
        if (song.channels > 0) addDetail(list, R.string.label_channels, Integer.toString(song.channels));
        if (song.size > 0) addDetail(list, R.string.label_file_size, SongDetailDialog.getFileSizeString(song.size));
        addDetail(list, R.string.label_file_path, song.path);
    }

    private void addDetail(ViewGroup list, @StringRes int label, @Nullable String value) {
        if (TextUtils.isEmpty(value)) return;

        View row = LayoutInflater.from(list.getContext()).inflate(R.layout.item_landscape_player_detail, list, false);
        ((TextView) row.findViewById(R.id.detail_label)).setText(label);
        ((TextView) row.findViewById(R.id.detail_value)).setText(value);
        list.addView(row);
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
