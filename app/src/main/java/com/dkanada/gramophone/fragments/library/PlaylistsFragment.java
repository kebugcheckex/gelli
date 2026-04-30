package com.dkanada.gramophone.fragments.library;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.dkanada.gramophone.App;
import com.dkanada.gramophone.R;
import com.dkanada.gramophone.adapter.PlaylistAdapter;
import com.dkanada.gramophone.model.Playlist;
import com.dkanada.gramophone.util.PreferenceUtil;
import com.dkanada.gramophone.util.QueryUtil;

import org.jellyfin.apiclient.model.querying.ItemQuery;

import java.util.ArrayList;
import java.util.List;

public class PlaylistsFragment extends AbsLibraryPagerRecyclerViewFragment<PlaylistAdapter, LinearLayoutManager, ItemQuery> {
    @NonNull
    @Override
    protected LinearLayoutManager createLayoutManager() {
        return new LinearLayoutManager(getActivity());
    }

    @NonNull
    @Override
    protected PlaylistAdapter createAdapter() {
        List<Playlist> dataSet = getAdapter() == null ? new ArrayList<>() : getAdapter().getDataSet();
        return new PlaylistAdapter(getLibraryFragment().getMainActivity(), dataSet, R.layout.item_list_single_row, getLibraryFragment().getMainActivity());
    }

    @NonNull
    @Override
    protected ItemQuery createQuery() {
        ItemQuery query = new ItemQuery();

        query.setIncludeItemTypes(new String[]{"Playlist"});
        query.setUserId(App.getApiClient().getCurrentUserId());
        query.setRecursive(true);
        query.setLimit(PreferenceUtil.getInstance(App.getInstance()).getPageSize());
        query.setStartIndex(getAdapter().getItemCount());

        return query;
    }

    @Override
    protected void loadItems(int index) {
        ItemQuery query = getQuery();
        query.setStartIndex(index);

        QueryUtil.getPlaylists(query, media -> {
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
