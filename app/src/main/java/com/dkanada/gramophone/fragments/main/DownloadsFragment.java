package com.dkanada.gramophone.fragments.main;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.dkanada.gramophone.R;
import com.dkanada.gramophone.adapter.song.SongAdapter;
import com.dkanada.gramophone.databinding.FragmentDownloadsBinding;
import com.dkanada.gramophone.helper.MusicPlayerRemote;
import com.dkanada.gramophone.model.Song;
import com.dkanada.gramophone.util.DownloadUtil;
import com.dkanada.gramophone.util.PreferenceUtil;
import com.dkanada.gramophone.util.ViewUtil;

import java.util.ArrayList;
import java.util.List;

// the only library screen in offline mode, listing the songs downloaded by the current account
public class DownloadsFragment extends AbsMainActivityFragment {
    private FragmentDownloadsBinding binding;

    private SongAdapter adapter;

    public static DownloadsFragment newInstance() {
        return new DownloadsFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        setHasOptionsMenu(true);
        binding = FragmentDownloadsBinding.inflate(inflater);

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        setUpToolbar();
        setUpRecyclerView();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadSongs();
    }

    private void setUpToolbar() {
        int primaryColor = PreferenceUtil.getInstance(requireActivity()).getPrimaryColor();
        binding.appbar.setBackgroundColor(primaryColor);
        binding.toolbar.setBackgroundColor(primaryColor);
        binding.toolbar.setNavigationIcon(R.drawable.ic_menu_white_24dp);
        binding.toolbar.setTitle(R.string.downloads);
        binding.toolbar.setSubtitle(R.string.offline);
        getMainActivity().setSupportActionBar(binding.toolbar);
    }

    private void setUpRecyclerView() {
        adapter = new SongAdapter(getMainActivity(), new ArrayList<>(), R.layout.item_list, false, getMainActivity(), false);
        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                binding.empty.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
            }
        });

        ViewUtil.setUpFastScrollRecyclerViewColor(getActivity(), binding.recyclerView, PreferenceUtil.getInstance(requireActivity()).getAccentColor());
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        binding.recyclerView.setAdapter(adapter);
    }

    private void loadSongs() {
        RecyclerView recyclerView = binding.recyclerView;

        // checking every file can be slow with a large collection
        new Thread(() -> {
            List<Song> songs = DownloadUtil.getDownloadedSongs();
            recyclerView.post(() -> {
                if (binding == null) return;
                adapter.swapDataSet(songs);
            });
        }).start();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_downloads, menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_shuffle_all && !adapter.getDataSet().isEmpty()) {
            MusicPlayerRemote.openAndShuffleQueue(adapter.getDataSet(), true);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
