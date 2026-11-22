package dev.kebugcheck.gelli.fragments.library;

import androidx.annotation.NonNull;

import dev.kebugcheck.gelli.App;
import dev.kebugcheck.gelli.R;
import dev.kebugcheck.gelli.adapter.album.AlbumAdapter;
import dev.kebugcheck.gelli.model.Album;
import dev.kebugcheck.gelli.model.LibraryLayout;
import dev.kebugcheck.gelli.model.SortMethod;
import dev.kebugcheck.gelli.model.SortOrder;
import dev.kebugcheck.gelli.util.PreferenceUtil;
import dev.kebugcheck.gelli.util.QueryUtil;

import java.util.ArrayList;
import java.util.List;

public class AlbumsFragment extends AbsLibraryPagerRecyclerViewCustomGridSizeFragment<AlbumAdapter> {
    @NonNull
    @Override
    protected AlbumAdapter createAdapter() {
        int itemLayoutRes = getItemLayoutRes();
        notifyLayoutResChanged(itemLayoutRes);

        List<Album> dataSet = getAdapter() == null ? new ArrayList<>() : getAdapter().getDataSet();
        return new AlbumAdapter(getLibraryFragment().getMainActivity(), dataSet, itemLayoutRes);
    }

    @Override
    protected boolean isSectioned() {
        return getSortMethod() == SortMethod.NAME || getSortMethod() == SortMethod.ARTIST;
    }

    @Override
    protected void loadItems(int index) {
        if (isSectioned()) {
            QueryUtil.getSectionedAlbums(getSortMethod(), getSortOrder(), sections -> {
                getAdapter().getDataSet().clear();
                getAdapter().getDataSet().addAll(sections.getItems());
                size = getAdapter().getDataSet().size();
                getAdapter().notifyDataSetChanged();
                setSections(sections);
                loading = false;
            });

            return;
        }

        if (index == 0) setSections(null);

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
    protected LibraryLayout loadLibraryLayout() {
        return PreferenceUtil.getInstance(getActivity()).getAlbumLayout();
    }

    @Override
    protected void saveLibraryLayout(LibraryLayout libraryLayout) {
        PreferenceUtil.getInstance(getActivity()).setAlbumLayout(libraryLayout);
    }

}
