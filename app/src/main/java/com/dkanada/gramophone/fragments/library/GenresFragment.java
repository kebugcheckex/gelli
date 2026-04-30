package com.dkanada.gramophone.fragments.library;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.dkanada.gramophone.App;
import com.dkanada.gramophone.R;
import com.dkanada.gramophone.adapter.GenreAdapter;
import com.dkanada.gramophone.model.Genre;
import com.dkanada.gramophone.util.PreferenceUtil;
import com.dkanada.gramophone.util.QueryUtil;

import org.jellyfin.apiclient.model.querying.ItemsByNameQuery;

import java.util.ArrayList;
import java.util.List;

public class GenresFragment extends AbsLibraryPagerRecyclerViewFragment<GenreAdapter, LinearLayoutManager, ItemsByNameQuery> {
    @NonNull
    @Override
    protected LinearLayoutManager createLayoutManager() {
        return new LinearLayoutManager(getActivity());
    }

    @NonNull
    @Override
    protected GenreAdapter createAdapter() {
        List<Genre> dataSet = getAdapter() == null ? new ArrayList<>() : getAdapter().getDataSet();
        return new GenreAdapter(getLibraryFragment().getMainActivity(), dataSet);
    }

    @NonNull
    @Override
    protected ItemsByNameQuery createQuery() {
        ItemsByNameQuery query = new ItemsByNameQuery();

        query.setUserId(App.getApiClient().getCurrentUserId());
        query.setRecursive(true);
        query.setLimit(PreferenceUtil.getInstance(App.getInstance()).getPageSize());
        query.setStartIndex(getAdapter().getItemCount());
        query.setParentId(QueryUtil.currentLibrary.getId());

        return query;
    }

    @Override
    protected void loadItems(int index) {
        ItemsByNameQuery query = getQuery();
        query.setStartIndex(index);

        QueryUtil.getGenres(query, media -> {
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
        return R.string.no_genres;
    }
}
