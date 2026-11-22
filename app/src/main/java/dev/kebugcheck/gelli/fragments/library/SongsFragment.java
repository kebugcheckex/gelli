package dev.kebugcheck.gelli.fragments.library;

import androidx.annotation.NonNull;

import dev.kebugcheck.gelli.App;
import dev.kebugcheck.gelli.R;
import dev.kebugcheck.gelli.adapter.song.ShuffleButtonSongAdapter;
import dev.kebugcheck.gelli.adapter.song.SongAdapter;
import dev.kebugcheck.gelli.model.LibraryLayout;
import dev.kebugcheck.gelli.model.Song;
import dev.kebugcheck.gelli.model.SortMethod;
import dev.kebugcheck.gelli.model.SortOrder;
import dev.kebugcheck.gelli.util.PreferenceUtil;
import dev.kebugcheck.gelli.util.QueryUtil;

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
