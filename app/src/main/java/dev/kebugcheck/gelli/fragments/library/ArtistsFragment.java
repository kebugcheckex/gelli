package dev.kebugcheck.gelli.fragments.library;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;

import dev.kebugcheck.gelli.App;
import dev.kebugcheck.gelli.R;
import dev.kebugcheck.gelli.adapter.artist.ArtistAdapter;
import dev.kebugcheck.gelli.model.Artist;
import dev.kebugcheck.gelli.model.LibraryLayout;
import dev.kebugcheck.gelli.model.SortMethod;
import dev.kebugcheck.gelli.model.SortOrder;
import dev.kebugcheck.gelli.interfaces.MediaCallback;
import dev.kebugcheck.gelli.util.PreferenceUtil;
import dev.kebugcheck.gelli.util.QueryUtil;

import java.util.ArrayList;
import java.util.List;

public class ArtistsFragment extends AbsLibraryPagerRecyclerViewCustomGridSizeFragment<ArtistAdapter> {
    protected boolean isAlbumArtists() {
        return false;
    }

    @NonNull
    @Override
    protected ArtistAdapter createAdapter() {
        int itemLayoutRes = getItemLayoutRes();
        notifyLayoutResChanged(itemLayoutRes);

        List<Artist> dataSet = getAdapter() == null ? new ArrayList<>() : getAdapter().getDataSet();
        return new ArtistAdapter(getLibraryFragment().getMainActivity(), dataSet, itemLayoutRes, isAlbumArtists());
    }

    @Override
    protected boolean isSectioned() {
        return getSortMethod() == SortMethod.NAME;
    }

    @Override
    protected void loadItems(int index) {
        if (isSectioned()) {
            QueryUtil.getSectionedArtists(isAlbumArtists(), getSortOrder(), sections -> {
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

        MediaCallback<Artist> callback = media -> {
            if (index == 0) getAdapter().getDataSet().clear();
            getAdapter().getDataSet().addAll(media);
            if (media.size() < PreferenceUtil.getInstance(App.getInstance()).getPageSize()) {
                size = getAdapter().getDataSet().size();
            } else {
                size = getAdapter().getDataSet().size() + 1;
            }
            getAdapter().notifyDataSetChanged();
            loading = false;
        };

        if (isAlbumArtists()) {
            QueryUtil.getAlbumArtists(getSortMethod(), getSortOrder(), index, callback);
        } else {
            QueryUtil.getArtists(getSortMethod(), getSortOrder(), index, callback);
        }
    }

    @Override
    protected int getEmptyMessage() {
        return isAlbumArtists() ? R.string.no_album_artists : R.string.no_artists;
    }

    @LayoutRes
    @Override
    protected int getListItemLayoutRes() {
        return R.layout.item_list_single_row;
    }

    @Override
    protected SortMethod loadSortMethod() {
        PreferenceUtil preferences = PreferenceUtil.getInstance(getActivity());
        return isAlbumArtists() ? preferences.getAlbumArtistsSortMethod() : preferences.getArtistSortMethod();
    }

    @Override
    protected void saveSortMethod(SortMethod sortMethod) {
        PreferenceUtil preferences = PreferenceUtil.getInstance(getActivity());
        if (isAlbumArtists()) {
            preferences.setAlbumArtistsSortMethod(sortMethod);
        } else {
            preferences.setArtistSortMethod(sortMethod);
        }
    }

    @Override
    protected void setSortMethod(SortMethod sortMethod) {
    }

    @Override
    protected SortOrder loadSortOrder() {
        PreferenceUtil preferences = PreferenceUtil.getInstance(getActivity());
        return isAlbumArtists() ? preferences.getAlbumArtistsSortOrder() : preferences.getArtistSortOrder();
    }

    @Override
    protected void saveSortOrder(SortOrder sortOrder) {
        PreferenceUtil preferences = PreferenceUtil.getInstance(getActivity());
        if (isAlbumArtists()) {
            preferences.setAlbumArtistsSortOrder(sortOrder);
        } else {
            preferences.setArtistSortOrder(sortOrder);
        }
    }

    @Override
    protected void setSortOrder(SortOrder sortOrder) {
    }

    @Override
    protected LibraryLayout loadLibraryLayout() {
        PreferenceUtil preferences = PreferenceUtil.getInstance(getActivity());
        return isAlbumArtists() ? preferences.getAlbumArtistsLayout() : preferences.getArtistLayout();
    }

    @Override
    protected void saveLibraryLayout(LibraryLayout libraryLayout) {
        PreferenceUtil preferences = PreferenceUtil.getInstance(getActivity());
        if (isAlbumArtists()) {
            preferences.setAlbumArtistsLayout(libraryLayout);
        } else {
            preferences.setArtistLayout(libraryLayout);
        }
    }

}
