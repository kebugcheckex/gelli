package com.dkanada.gramophone.fragments.library;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.dkanada.gramophone.App;
import com.dkanada.gramophone.R;
import com.dkanada.gramophone.adapter.PlaylistAdapter;
import com.dkanada.gramophone.model.Playlist;
import com.dkanada.gramophone.util.PreferenceUtil;
import com.dkanada.gramophone.util.QueryUtil;
import com.dkanada.gramophone.util.ViewUtil;

import java.util.ArrayList;
import java.util.List;

public class PlaylistsFragment extends AbsLibraryPagerRecyclerViewFragment<PlaylistAdapter, LinearLayoutManager> {
    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ViewUtil.capContentWidth(getRecyclerView());
    }

    @NonNull
    @Override
    protected LinearLayoutManager createLayoutManager() {
        return new LinearLayoutManager(getActivity());
    }

    @NonNull
    @Override
    protected PlaylistAdapter createAdapter() {
        List<Playlist> dataSet = getAdapter() == null ? new ArrayList<>() : getAdapter().getDataSet();
        return new PlaylistAdapter(getLibraryFragment().getMainActivity(), dataSet, R.layout.item_list_single_row);
    }

    @Override
    protected void loadItems(int index) {
        QueryUtil.getPlaylists(index, media -> {
            if (index == 0) getAdapter().getDataSet().clear();
            getAdapter().getDataSet().addAll(media);
            if (media.size() < PreferenceUtil.getInstance(App.getInstance()).getPageSize()) {
                size = getAdapter().getDataSet().size();
            } else {
                size = getAdapter().getDataSet().size() + 1;
            }
            getAdapter().notifyDataSetChanged();
            loading = false;
        });
    }

    @Override
    protected int getEmptyMessage() {
        return R.string.no_playlists;
    }
}
