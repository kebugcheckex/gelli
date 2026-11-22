package dev.kebugcheck.gelli.activities.details;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import dev.kebugcheck.gelli.BuildConfig;
import dev.kebugcheck.gelli.activities.base.AbsMusicContentActivity;
import dev.kebugcheck.gelli.databinding.ActivityPlaylistDetailBinding;
import dev.kebugcheck.gelli.dialogs.DeletePlaylistDialog;
import dev.kebugcheck.gelli.dialogs.RenamePlaylistDialog;
import dev.kebugcheck.gelli.helper.menu.SongsMenuHelper;
import com.h6ah4i.android.widget.advrecyclerview.animator.GeneralItemAnimator;
import com.h6ah4i.android.widget.advrecyclerview.animator.RefactoredDefaultItemAnimator;
import com.h6ah4i.android.widget.advrecyclerview.draggable.RecyclerViewDragDropManager;
import com.h6ah4i.android.widget.advrecyclerview.utils.WrapperAdapterUtils;
import dev.kebugcheck.gelli.R;
import dev.kebugcheck.gelli.adapter.song.OrderablePlaylistSongAdapter;
import dev.kebugcheck.gelli.adapter.song.SongAdapter;
import dev.kebugcheck.gelli.helper.MusicPlayerRemote;
import dev.kebugcheck.gelli.model.Playlist;
import dev.kebugcheck.gelli.model.PlaylistSong;
import dev.kebugcheck.gelli.model.Song;
import dev.kebugcheck.gelli.util.PlaylistUtil;
import dev.kebugcheck.gelli.util.ViewUtil;

import java.util.ArrayList;

public class PlaylistDetailActivity extends AbsMusicContentActivity {
    public static String EXTRA_PLAYLIST = BuildConfig.APPLICATION_ID + ".extra.playlist";

    private ActivityPlaylistDetailBinding binding;

    private Playlist playlist;

    private SongAdapter adapter;

    private RecyclerView.Adapter wrappedAdapter;
    private RecyclerViewDragDropManager recyclerViewDragDropManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        playlist = getIntent().getParcelableExtra(EXTRA_PLAYLIST);

        super.onCreate(savedInstanceState);

        setUpRecyclerView();
        setUpToolbar();
    }

    @Override
    public void onStateOnline() {
        PlaylistUtil.getPlaylist(playlist.id, media -> {
            adapter.getDataSet().addAll(media);
            adapter.notifyDataSetChanged();
        });
    }

    @Override
    protected View createContentView() {
        binding = ActivityPlaylistDetailBinding.inflate(getLayoutInflater());

        return wrapSlidingMusicPanel(binding.getRoot());
    }

    private void setUpRecyclerView() {
        ViewUtil.setUpFastScrollRecyclerViewColor(this, binding.recyclerView);
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        ViewUtil.capContentWidth(binding.recyclerView);

        recyclerViewDragDropManager = new RecyclerViewDragDropManager();
        final GeneralItemAnimator animator = new RefactoredDefaultItemAnimator();
        adapter = new OrderablePlaylistSongAdapter(this, new ArrayList<>(), R.layout.item_list, (fromPosition, toPosition) -> {
            PlaylistUtil.moveItem(playlist.id, (PlaylistSong) adapter.getDataSet().get(fromPosition), toPosition);
            Song song = adapter.getDataSet().remove(fromPosition);
            adapter.getDataSet().add(toPosition, song);
            adapter.notifyItemMoved(fromPosition, toPosition);
        });

        wrappedAdapter = recyclerViewDragDropManager.createWrappedAdapter(adapter);

        binding.recyclerView.setAdapter(wrappedAdapter);
        binding.recyclerView.setItemAnimator(animator);

        recyclerViewDragDropManager.attachRecyclerView(binding.recyclerView);

        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                super.onChanged();
                checkIsEmpty();
            }
        });
    }

    private void setUpToolbar() {
        binding.toolbar.setTitle(playlist.name);
        setSupportActionBar(binding.toolbar);
        // noinspection ConstantConditions
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_detail_playlist, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_shuffle_playlist) {
            MusicPlayerRemote.openAndShuffleQueue(adapter.getDataSet(), true);
        } else if (item.getItemId() == R.id.action_rename_playlist) {
            RenamePlaylistDialog.create(playlist).show(getSupportFragmentManager(), RenamePlaylistDialog.TAG);
        } else if (item.getItemId() == R.id.action_delete_playlist) {
            DeletePlaylistDialog.create(playlist).show(getSupportFragmentManager(), DeletePlaylistDialog.TAG);
        }

        // TODO move SongsMenuHelper into AbsMediaDetailActivity
        return SongsMenuHelper.handleMenuClick(this, adapter.getDataSet(), item.getItemId())
            || super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        binding.recyclerView.stopScroll();
        super.onBackPressed();
    }

    private void checkIsEmpty() {
        binding.empty.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onPause() {
        if (recyclerViewDragDropManager != null) {
            recyclerViewDragDropManager.cancelDrag();
        }

        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (recyclerViewDragDropManager != null) {
            recyclerViewDragDropManager.release();
            recyclerViewDragDropManager = null;
        }

        binding.recyclerView.setItemAnimator(null);
        binding.recyclerView.setAdapter(null);

        if (wrappedAdapter != null) {
            WrapperAdapterUtils.releaseAll(wrappedAdapter);
            wrappedAdapter = null;
        }

        adapter = null;
        super.onDestroy();
    }
}
