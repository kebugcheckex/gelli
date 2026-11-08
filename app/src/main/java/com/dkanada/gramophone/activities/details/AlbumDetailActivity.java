package com.dkanada.gramophone.activities.details;

import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.dkanada.gramophone.BuildConfig;
import com.dkanada.gramophone.activities.base.AbsMusicContentActivity;
import com.dkanada.gramophone.databinding.ActivityAlbumDetailBinding;
import com.google.android.material.appbar.AppBarLayout;
import com.dkanada.gramophone.R;
import com.dkanada.gramophone.adapter.song.AlbumSongAdapter;
import com.dkanada.gramophone.dialogs.AddToPlaylistDialog;
import com.dkanada.gramophone.glide.CustomGlideRequest;
import com.dkanada.gramophone.helper.MusicPlayerRemote;
import com.dkanada.gramophone.model.Album;
import com.dkanada.gramophone.model.Artist;
import com.dkanada.gramophone.model.Song;
import com.dkanada.gramophone.util.MusicUtil;
import com.dkanada.gramophone.util.NavigationUtil;
import com.dkanada.gramophone.util.QueryUtil;
import com.dkanada.gramophone.util.ViewUtil;

import java.util.List;
import java.util.Objects;

public class AlbumDetailActivity extends AbsMusicContentActivity implements AppBarLayout.OnOffsetChangedListener {
    public static final String EXTRA_ALBUM = BuildConfig.APPLICATION_ID + ".extra.album";

    private ActivityAlbumDetailBinding binding;

    private int headerViewHeight;

    private Album album;
    private AlbumSongAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        album = getIntent().getParcelableExtra(EXTRA_ALBUM);
        Log.d("AlbumDetailActivity", "onCreate: album id = " + (album != null ? album.id : "null"));

        super.onCreate(savedInstanceState);

        setUpObservableListViewParams();
        setUpToolbar();
        setUpViews();

        loadAlbumCover(album);
        setAlbum(album);
    }

    @Override
    public void onStateOnline() {
        if (album == null) {
            Log.w("AlbumDetailActivity", "onStateOnline: album is null, skipping fetch");
            return;
        }
        if (album.songs != null && !album.songs.isEmpty()) {
            Log.d("AlbumDetailActivity", "onStateOnline: songs already populated, skipping fetch");
            return;
        }
        Log.d("AlbumDetailActivity", "onStateOnline: starting fetch for album id = " + album.id);
        QueryUtil.getAlbumSongs(album.id, media -> {
            Log.d("AlbumDetailActivity", "onStateOnline: fetch finished, songs size = " + (media != null ? media.size() : "null"));
            if (media != null && !media.isEmpty()) {
                album.songs = media;
            } else {
                Log.w("AlbumDetailActivity", "onStateOnline: empty result, preserving existing songs list");
            }
            setAlbum(album);
        });
    }

    @Override
    public void onOffsetChanged (AppBarLayout appBarLayout, int verticalOffset) {
        float headerAlpha = Math.max(0, Math.min(1, 1 + (2 * (float) verticalOffset / headerViewHeight)));
        binding.header.setAlpha(headerAlpha);
    }

    @Override
    protected View createContentView() {
        binding = ActivityAlbumDetailBinding.inflate(getLayoutInflater());

        return wrapSlidingMusicPanel(binding.getRoot());
    }

    private void setUpObservableListViewParams() {
        headerViewHeight = getResources().getDimensionPixelSize(R.dimen.detail_header_height);
    }

    private void setUpViews() {
        setUpRecyclerView();
        setUpSongsAdapter();
        binding.artistText.setOnClickListener(v -> {
            NavigationUtil.startArtist(AlbumDetailActivity.this, new Artist(album), null);
        });
    }

    private void loadAlbumCover(Album album) {
        CustomGlideRequest.Builder
                .from(this, album.primary, album.blurHash)
                .build().dontAnimate()
                .into(binding.image);
    }

    private void setUpRecyclerView() {
        // the w1200dp layout has a fixed side column instead of a collapsing header
        if (binding.appBarLayout != null) binding.appBarLayout.addOnOffsetChangedListener(this);
    }

    private void setUpToolbar() {
        setSupportActionBar(binding.toolbar);
        binding.toolbar.setTitle(null);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
    }

    private void setUpSongsAdapter() {
        adapter = new AlbumSongAdapter(this, album.songs, R.layout.item_list);
        binding.list.setLayoutManager(new GridLayoutManager(this, 1));
        binding.list.setAdapter(adapter);
        ViewUtil.capContentWidth(binding.list);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_detail_album, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        final List<Song> songs = adapter.getDataSet();
        if (id == R.id.action_shuffle_album) {
            MusicPlayerRemote.openAndShuffleQueue(songs, true);
            return true;
        } else if (id == R.id.action_play_next) {
            MusicPlayerRemote.playNext(songs);
            return true;
        } else if (id == R.id.action_add_to_queue) {
            MusicPlayerRemote.enqueue(songs);
            return true;
        } else if (id == R.id.action_add_to_playlist) {
            AddToPlaylistDialog.create(songs).show(getSupportFragmentManager(), "ADD_PLAYLIST");
            return true;
        } else if (id == R.id.action_go_to_artist) {
            NavigationUtil.startArtist(this, new Artist(album), null);
            return true;
        } else if (id == R.id.action_download) {
            NavigationUtil.startDownload(this, songs);
            return true;
        } else if (id == R.id.action_remove_from_device) {
            NavigationUtil.removeFromDevice(this, songs);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        binding.list.stopScroll();
        super.onBackPressed();
    }

    private void setAlbum(Album album) {
        this.album = album;

        binding.toolbar.setTitle(album.title);
        binding.artistText.setText(album.artistName);
        binding.songCountText.setText(MusicUtil.getSongCountString(this, album.songs.size()));
        binding.durationText.setText(MusicUtil.getReadableDurationString(MusicUtil.getTotalDuration(this, album.songs)));
        binding.albumYearText.setText(MusicUtil.getYearString(album.year));

        if (adapter != null) {
            adapter.swapDataSet(album.songs);
        }
    }
}
