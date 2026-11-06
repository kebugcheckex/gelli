package com.dkanada.gramophone.fragments.library;

import androidx.annotation.NonNull;

import com.dkanada.gramophone.App;
import com.dkanada.gramophone.R;
import com.dkanada.gramophone.adapter.song.ShuffleButtonSongAdapter;
import com.dkanada.gramophone.adapter.song.SongAdapter;
import com.dkanada.gramophone.model.LibraryLayout;
import com.dkanada.gramophone.model.Song;
import com.dkanada.gramophone.model.SortMethod;
import com.dkanada.gramophone.model.SortOrder;
import com.dkanada.gramophone.util.PreferenceUtil;
import com.dkanada.gramophone.util.QueryUtil;

import java.util.ArrayList;
import java.util.List;

public class SongsFragment extends AbsLibraryPagerRecyclerViewCustomGridSizeFragment<SongAdapter> {
    @NonNull
    @Override
    protected SongAdapter createAdapter() {
        int itemLayoutRes = getItemLayoutRes();
        notifyLayoutResChanged(itemLayoutRes);

        List<Song> dataSet = getAdapter() == null ? new ArrayList<>() : getAdapter().getDataSet();
        SongAdapter adapter;

        if (!getLibraryLayout().isGrid()) {
            adapter = new ShuffleButtonSongAdapter(
                    getLibraryFragment().getMainActivity(),
                    dataSet,
                    itemLayoutRes);
        } else {
            adapter = new SongAdapter(
                    getLibraryFragment().getMainActivity(),
                    dataSet,
                    itemLayoutRes);
        }

        return adapter;
    }

    @Override
    protected boolean isFullSpanItem(int position) {
        // the shuffle button stays a single full-width row when the list uses two columns
        return position == 0 && getAdapter() instanceof ShuffleButtonSongAdapter;
    }

    @Override
    protected void loadItems(int index) {
        QueryUtil.getSongs(getSortMethod(), getSortOrder(), index, isOnlyFavorites(), media -> {
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

    protected boolean isOnlyFavorites() {
        return false;
    }

    @Override
    protected int getEmptyMessage() {
        return R.string.no_songs;
    }

    @Override
    protected SortMethod loadSortMethod() {
        return PreferenceUtil.getInstance(getActivity()).getSongSortMethod();
    }

    @Override
    protected void saveSortMethod(SortMethod sortMethod) {
        PreferenceUtil.getInstance(getActivity()).setSongSortMethod(sortMethod);
    }

    @Override
    protected void setSortMethod(SortMethod sortMethod) {
    }

    @Override
    protected SortOrder loadSortOrder() {
        return PreferenceUtil.getInstance(getActivity()).getSongSortOrder();
    }

    @Override
    protected void saveSortOrder(SortOrder sortOrder) {
        PreferenceUtil.getInstance(getActivity()).setSongSortOrder(sortOrder);
    }

    @Override
    protected void setSortOrder(SortOrder sortOrder) {
    }

    @Override
    protected LibraryLayout loadLibraryLayout() {
        return PreferenceUtil.getInstance(getActivity()).getSongLayout();
    }

    @Override
    protected void saveLibraryLayout(LibraryLayout libraryLayout) {
        PreferenceUtil.getInstance(getActivity()).setSongLayout(libraryLayout);
    }

}
