package com.dkanada.gramophone.activities.details;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.dkanada.gramophone.BuildConfig;
import com.dkanada.gramophone.activities.base.AbsMusicContentActivity;
import com.dkanada.gramophone.adapter.song.SongAdapter;
import com.dkanada.gramophone.databinding.ActivityArtistDetailBinding;
import com.dkanada.gramophone.util.NavigationUtil;
import com.google.android.material.appbar.AppBarLayout;
import com.dkanada.gramophone.R;
import com.dkanada.gramophone.adapter.album.HorizontalAlbumAdapter;
import com.dkanada.gramophone.dialogs.AddToPlaylistDialog;
import com.dkanada.gramophone.glide.CustomGlideRequest;
import com.dkanada.gramophone.helper.MusicPlayerRemote;
import com.dkanada.gramophone.model.Album;
import com.dkanada.gramophone.model.Artist;
import com.dkanada.gramophone.model.Song;
import com.dkanada.gramophone.util.MusicUtil;
import com.dkanada.gramophone.util.QueryUtil;
import com.dkanada.gramophone.util.ViewUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class ArtistDetailActivity extends AbsMusicContentActivity implements AppBarLayout.OnOffsetChangedListener {
    public static final String EXTRA_ARTIST = BuildConfig.APPLICATION_ID + ".extra.artist";
    public static final String EXTRA_ALBUM_ARTIST = BuildConfig.APPLICATION_ID + ".extra.album_artist";

    private ActivityArtistDetailBinding binding;

    private int headerViewHeight;

    private Artist artist;
    private boolean albumArtist;
    private HorizontalAlbumAdapter albumAdapter;
    private SongAdapter songAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        artist = getIntent().getParcelableExtra(EXTRA_ARTIST);
        albumArtist = getIntent().getBooleanExtra(EXTRA_ALBUM_ARTIST, false);

        super.onCreate(savedInstanceState);

        setUpObservableListViewParams();
        setUpToolbar();
        setUpViews();

        loadArtistImage(artist);
        setArtist(artist);
    }

    @Override
    public void onStateOnline() {
        QueryUtil.getAlbumsByArtistId(artist.id, media -> {
            // oldest first; albums without a year (0) go last, and ties fall back to the title
            List<Album> albums = new ArrayList<>(media);
            albums.sort(Comparator
                    .comparingInt((Album album) -> album.year > 0 ? album.year : Integer.MAX_VALUE)
                    .thenComparing(album -> album.title, String.CASE_INSENSITIVE_ORDER));
            artist.albums = albums;
            setArtist(artist);
        }, albumArtist);

        QueryUtil.getSongsByArtistId(artist.id, media -> {
            artist.songs = media;
            setArtist(artist);
        }, albumArtist);
    }

    @Override
    public void onOffsetChanged(AppBarLayout appBarLayout, int verticalOffset) {
        float headerAlpha = Math.max(0, Math.min(1, 1 + (2 * (float) verticalOffset / headerViewHeight)));
        binding.header.setAlpha(headerAlpha);
    }

    @Override
    protected View createContentView() {
        binding = ActivityArtistDetailBinding.inflate(getLayoutInflater());

        return wrapSlidingMusicPanel(binding.getRoot());
    }

    private void setUpObservableListViewParams() {
        headerViewHeight = getResources().getDimensionPixelSize(R.dimen.detail_header_height);
    }

    private void setUpViews() {
        setUpSongListView();
        setUpAlbumRecyclerView();
    }

    private void setUpSongListView() {
        // the w1200dp layout has a fixed side column instead of a collapsing header
        if (binding.appBarLayout != null) binding.appBarLayout.addOnOffsetChangedListener(this);

        songAdapter = new SongAdapter(this, artist.songs, R.layout.item_list);

        binding.songs.setLayoutManager(new GridLayoutManager(this, 1));
        binding.songs.setAdapter(songAdapter);
        ViewUtil.capContentWidth(binding.songs);

        binding.scrollView.setRecyclerView(binding.songs);
    }

    private void setUpAlbumRecyclerView() {
        binding.albums.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        albumAdapter = new HorizontalAlbumAdapter(this, artist.albums);
        binding.albums.setAdapter(albumAdapter);

        // NestedScrollView will ignore horizontal RecyclerView without this line
        binding.albums.setNestedScrollingEnabled(false);

        albumAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                super.onChanged();
                if (albumAdapter.getItemCount() == 0) finish();
            }
        });
    }

    private void loadArtistImage(Artist artist) {
        CustomGlideRequest.Builder
                .from(this, artist.primary, artist.blurHash)
                .build().dontAnimate()
                .into(binding.image);
    }

    private void setUpToolbar() {
        setSupportActionBar(binding.toolbar);
        binding.toolbar.setTitle(null);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_detail_artist, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        final List<Song> songs = songAdapter.getDataSet();
        if (id == R.id.action_shuffle_artist) {
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
        binding.albums.stopScroll();
        super.onBackPressed();
    }

    private void setArtist(Artist artist) {
        this.artist = artist;

        for (Album album : artist.albums) {
            album.songs.clear();
            for (Song song : artist.songs) {
                if (song.albumId.equals(album.id)) {
                    album.songs.add(song);
                }
            }
        }

        binding.toolbar.setTitle(artist.name);
        binding.songCountText.setText(MusicUtil.getSongCountString(this, artist.songs.size()));
        binding.albumCountText.setText(MusicUtil.getAlbumCountString(this, artist.albums.size()));
        binding.durationText.setText(MusicUtil.getReadableDurationString(MusicUtil.getTotalDuration(this, artist.songs)));

        if (!artist.songs.isEmpty()) songAdapter.swapDataSet(artist.songs);
        if (!artist.albums.isEmpty()) albumAdapter.swapDataSet(artist.albums);
    }
}
