package dev.kebugcheck.gelli.fragments.library;

import androidx.annotation.NonNull;

import dev.kebugcheck.gelli.adapter.song.ShuffleButtonSongAdapter;
import dev.kebugcheck.gelli.adapter.song.SongAdapter;

public class FavoritesFragment extends SongsFragment {
    @Override
    protected boolean isOnlyFavorites() {
        return true;
    }

    @NonNull
    @Override
    protected SongAdapter createAdapter() {
        SongAdapter adapter = super.createAdapter();

        // set the shuffle button adapter to only shuffle favorites
        if (adapter instanceof ShuffleButtonSongAdapter) {
            ((ShuffleButtonSongAdapter) adapter).setFavorite(true);
        }

        return adapter;
    }
}
