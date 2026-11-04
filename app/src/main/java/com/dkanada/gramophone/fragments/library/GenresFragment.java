package com.dkanada.gramophone.fragments.library;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.dkanada.gramophone.App;
import com.dkanada.gramophone.R;
import com.dkanada.gramophone.adapter.GenreAdapter;
import com.dkanada.gramophone.model.Genre;
import com.dkanada.gramophone.util.PreferenceUtil;
import com.dkanada.gramophone.util.QueryUtil;
import com.dkanada.gramophone.util.ViewUtil;

import java.util.ArrayList;
import java.util.List;

public class GenresFragment extends AbsLibraryPagerRecyclerViewFragment<GenreAdapter, LinearLayoutManager> {
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
    protected GenreAdapter createAdapter() {
        List<Genre> dataSet = getAdapter() == null ? new ArrayList<>() : getAdapter().getDataSet();
        return new GenreAdapter(getLibraryFragment().getMainActivity(), dataSet);
    }

    @Override
    protected void loadItems(int index) {
        QueryUtil.getGenres(index, media -> {
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
