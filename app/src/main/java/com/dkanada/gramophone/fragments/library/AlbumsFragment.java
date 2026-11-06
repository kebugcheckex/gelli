package com.dkanada.gramophone.fragments.library;

import androidx.annotation.NonNull;

import com.dkanada.gramophone.App;
import com.dkanada.gramophone.R;
import com.dkanada.gramophone.adapter.album.AlbumAdapter;
import com.dkanada.gramophone.model.Album;
import com.dkanada.gramophone.model.LibraryLayout;
import com.dkanada.gramophone.model.SortMethod;
import com.dkanada.gramophone.model.SortOrder;
import com.dkanada.gramophone.util.PreferenceUtil;
import com.dkanada.gramophone.util.QueryUtil;

import java.util.ArrayList;
import java.util.List;

public class AlbumsFragment extends AbsLibraryPagerRecyclerViewCustomGridSizeFragment<AlbumAdapter> {
    @NonNull
    @Override
    protected AlbumAdapter createAdapter() {
        int itemLayoutRes = getItemLayoutRes();
        notifyLayoutResChanged(itemLayoutRes);

        List<Album> dataSet = getAdapter() == null ? new ArrayList<>() : getAdapter().getDataSet();
        return new AlbumAdapter(getLibraryFragment().getMainActivity(), dataSet, itemLayoutRes, loadUsePalette());
    }

    @Override
    protected void loadItems(int index) {
        QueryUtil.getAlbums(getSortMethod(), getSortOrder(), index, media -> {
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
        return R.string.no_albums;
    }

    @Override
    protected SortMethod loadSortMethod() {
        return PreferenceUtil.getInstance(getActivity()).getAlbumSortMethod();
    }

    @Override
    protected void saveSortMethod(SortMethod sortMethod) {
        PreferenceUtil.getInstance(getActivity()).setAlbumSortMethod(sortMethod);
    }

    @Override
    protected void setSortMethod(SortMethod sortMethod) {
    }

    @Override
    protected SortOrder loadSortOrder() {
        return PreferenceUtil.getInstance(getActivity()).getAlbumSortOrder();
    }

    @Override
    protected void saveSortOrder(SortOrder sortOrder) {
        PreferenceUtil.getInstance(getActivity()).setAlbumSortOrder(sortOrder);
    }

    @Override
    protected void setSortOrder(SortOrder sortOrder) {
    }

    @Override
    public boolean loadUsePalette() {
        return PreferenceUtil.getInstance(getActivity()).getAlbumColoredFooters();
    }

    @Override
    protected void setUsePalette(boolean usePalette) {
        getAdapter().usePalette(usePalette);
    }

    @Override
    protected LibraryLayout loadLibraryLayout() {
        return PreferenceUtil.getInstance(getActivity()).getAlbumLayout();
    }

    @Override
    protected void saveLibraryLayout(LibraryLayout libraryLayout) {
        PreferenceUtil.getInstance(getActivity()).setAlbumLayout(libraryLayout);
    }

    @Override
    protected void saveUsePalette(boolean usePalette) {
        PreferenceUtil.getInstance(getActivity()).setAlbumColoredFooters(usePalette);
    }
}
