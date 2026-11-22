package dev.kebugcheck.gelli.fragments.player;

import android.animation.AnimatorSet;
import android.content.Context;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;

import dev.kebugcheck.gelli.R;
import dev.kebugcheck.gelli.dialogs.AddToPlaylistDialog;
import dev.kebugcheck.gelli.dialogs.CreatePlaylistDialog;
import dev.kebugcheck.gelli.dialogs.SleepTimerDialog;
import dev.kebugcheck.gelli.dialogs.SongDetailDialog;
import dev.kebugcheck.gelli.dialogs.SongShareDialog;
import dev.kebugcheck.gelli.helper.LyricsController;
import dev.kebugcheck.gelli.helper.MusicPlayerRemote;
import dev.kebugcheck.gelli.interfaces.PaletteColorHolder;
import dev.kebugcheck.gelli.model.Album;
import dev.kebugcheck.gelli.model.Artist;
import dev.kebugcheck.gelli.model.Song;
import dev.kebugcheck.gelli.fragments.AbsMusicServiceFragment;
import dev.kebugcheck.gelli.util.MusicUtil;
import dev.kebugcheck.gelli.util.NavigationUtil;

public abstract class AbsPlayerFragment extends AbsMusicServiceFragment implements Toolbar.OnMenuItemClickListener, PaletteColorHolder, LyricsController.Listener {
    private static boolean isToolbarShown = true;

    protected static AnimatorSet currentAnimatorSet = null;

    protected LyricsController lyricsController;

    private Callbacks callbacks;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        try {
            callbacks = (Callbacks) context;
        } catch (ClassCastException e) {
            throw new RuntimeException(context.getClass().getSimpleName() + " must implement " + Callbacks.class.getSimpleName());
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        callbacks = null;
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        lyricsController = new LyricsController(this);
    }

    @Override
    public void onDestroyView() {
        lyricsController.release();
        super.onDestroyView();
    }

    @Override
    public void onResume() {
        super.onResume();
        lyricsController.setResumed(true);
    }

    @Override
    public void onPause() {
        lyricsController.setResumed(false);
        super.onPause();
    }

    @Override
    public void onServiceConnected() {
        lyricsController.onSongChanged();
    }

    @Override
    public void onPlayMetadataChanged() {
        lyricsController.onSongChanged();
    }

    @Override
    public void onQueueChanged() {
        lyricsController.onSongChanged();
    }

    @Override
    public void onPlayStateChanged() {
        lyricsController.onPlayStateChanged();
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        final Song song = MusicPlayerRemote.getCurrentSong();
        if (item.getItemId() == R.id.action_lyrics) {
            lyricsController.setEnabled(!lyricsController.isEnabled());
            return true;
        } else if (item.getItemId() == R.id.action_sleep_timer) {
            new SleepTimerDialog().show(getParentFragmentManager(), "SET_SLEEP_TIMER");
            return true;
        } else if (item.getItemId() == R.id.action_toggle_favorite) {
            toggleFavorite(song);
            return true;
        } else if (item.getItemId() == R.id.action_share) {
            SongShareDialog.create(song).show(getParentFragmentManager(), SongShareDialog.TAG);
            return true;
        } else if (item.getItemId() == R.id.action_add_to_playlist) {
            AddToPlaylistDialog.create(song).show(getParentFragmentManager(), "ADD_PLAYLIST");
            return true;
        } else if (item.getItemId() == R.id.action_clear_queue) {
            MusicPlayerRemote.clearQueue();
            return true;
        } else if (item.getItemId() == R.id.action_save_queue) {
            CreatePlaylistDialog.create(MusicPlayerRemote.getPlayingQueue()).show(getParentFragmentManager(), "ADD_TO_PLAYLIST");
            return true;
        } else if (item.getItemId() == R.id.action_details) {
            SongDetailDialog.create(song).show(getParentFragmentManager(), SongDetailDialog.TAG);
            return true;
        } else if (item.getItemId() == R.id.action_go_to_album) {
            NavigationUtil.startAlbum(requireActivity(), new Album(song), null);
            return true;
        } else if (item.getItemId() == R.id.action_go_to_artist) {
            NavigationUtil.startArtist(requireActivity(), new Artist(song), null);
            return true;
        }
        return false;
    }

    protected void toggleFavorite(Song song) {
        MusicUtil.toggleFavorite(song);
    }

    protected boolean isToolbarShown() {
        return isToolbarShown;
    }

    protected void setToolbarShown(boolean toolbarShown) {
        isToolbarShown = toolbarShown;
    }

    protected void showToolbar(@Nullable final View toolbar) {
        if (toolbar == null) return;

        setToolbarShown(true);

        toolbar.setVisibility(View.VISIBLE);
        toolbar.animate().alpha(1f).setDuration(PlayerAlbumCoverFragment.VISIBILITY_ANIM_DURATION);
    }

    protected void hideToolbar(@Nullable final View toolbar) {
        if (toolbar == null) return;

        setToolbarShown(false);

        toolbar.animate().alpha(0f).setDuration(PlayerAlbumCoverFragment.VISIBILITY_ANIM_DURATION).withEndAction(() -> toolbar.setVisibility(View.GONE));
    }

    protected void toggleToolbar(@Nullable final View toolbar) {
        if (isToolbarShown()) {
            hideToolbar(toolbar);
        } else {
            showToolbar(toolbar);
        }
    }

    protected void checkToggleToolbar(@Nullable final View toolbar) {
        if (toolbar != null && !isToolbarShown() && toolbar.getVisibility() != View.GONE) {
            hideToolbar(toolbar);
        } else if (toolbar != null && isToolbarShown() && toolbar.getVisibility() != View.VISIBLE) {
            showToolbar(toolbar);
        }
    }

    protected String getUpNextAndQueueTime() {
        final long duration = MusicPlayerRemote.getQueueDurationMillis(MusicPlayerRemote.getPosition());

        return MusicUtil.buildInfoString(
            getResources().getString(R.string.up_next),
            MusicUtil.getReadableDurationString(duration)
        );
    }

    public abstract void onShow();

    public abstract void onHide();

    public abstract boolean onBackPressed();

    public Callbacks getCallbacks() {
        return callbacks;
    }

    public interface Callbacks {
        void onPaletteColorChanged();
    }
}
