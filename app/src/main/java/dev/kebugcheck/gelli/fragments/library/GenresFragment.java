package dev.kebugcheck.gelli.fragments.library;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;

import dev.kebugcheck.gelli.App;
import dev.kebugcheck.gelli.R;
import dev.kebugcheck.gelli.adapter.GenreAdapter;
import dev.kebugcheck.gelli.model.Genre;
import dev.kebugcheck.gelli.util.PreferenceUtil;
import dev.kebugcheck.gelli.util.QueryUtil;
import dev.kebugcheck.gelli.util.ViewUtil;

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
