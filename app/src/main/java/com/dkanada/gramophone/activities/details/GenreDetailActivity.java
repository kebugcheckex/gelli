package com.dkanada.gramophone.activities.details;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.dkanada.gramophone.BuildConfig;
import com.dkanada.gramophone.activities.base.AbsMusicContentActivity;
import com.dkanada.gramophone.databinding.ActivityGenreDetailBinding;
import com.dkanada.gramophone.util.NavigationUtil;
import com.dkanada.gramophone.R;
import com.dkanada.gramophone.adapter.song.SongAdapter;
import com.dkanada.gramophone.helper.MusicPlayerRemote;
import com.dkanada.gramophone.model.Genre;
import com.dkanada.gramophone.util.QueryUtil;
import com.dkanada.gramophone.util.ViewUtil;

import java.util.ArrayList;

public class GenreDetailActivity extends AbsMusicContentActivity {
    public static final String EXTRA_GENRE = BuildConfig.APPLICATION_ID + ".extra.genre";

    private ActivityGenreDetailBinding binding;

    private Genre genre;

    private SongAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        genre = getIntent().getParcelableExtra(EXTRA_GENRE);

        super.onCreate(savedInstanceState);

        setUpRecyclerView();
        setUpToolBar();
    }

    @Override
    public void onStateOnline() {
        QueryUtil.getSongsByGenreId(genre.id, media -> {
            adapter.getDataSet().addAll(media);
            adapter.notifyDataSetChanged();
        });
    }

    @Override
    protected View createContentView() {
        binding = ActivityGenreDetailBinding.inflate(getLayoutInflater());

        return wrapSlidingMusicPanel(binding.getRoot());
    }

    private void setUpRecyclerView() {
        ViewUtil.setUpFastScrollRecyclerViewColor(this, binding.recyclerView);
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        ViewUtil.capContentWidth(binding.recyclerView);

        adapter = new SongAdapter(this, new ArrayList<>(), R.layout.item_list);
        binding.recyclerView.setAdapter(adapter);

        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                super.onChanged();
                checkIsEmpty();
            }
        });
    }

    private void setUpToolBar() {
        binding.toolbar.setTitle(genre.name);
        setSupportActionBar(binding.toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_detail_genre, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_shuffle_genre) {
            MusicPlayerRemote.openAndShuffleQueue(adapter.getDataSet(), true);
            return true;
        } else if (id == R.id.action_download) {
            NavigationUtil.startDownload(this, adapter.getDataSet());
            return true;
        } else if (id == R.id.action_remove_from_device) {
            NavigationUtil.removeFromDevice(this, adapter.getDataSet());
            return true;
        }

        return super.onOptionsItemSelected(item);
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
    protected void onDestroy() {
        binding.recyclerView.setAdapter(null);

        adapter = null;
        super.onDestroy();
    }
}
