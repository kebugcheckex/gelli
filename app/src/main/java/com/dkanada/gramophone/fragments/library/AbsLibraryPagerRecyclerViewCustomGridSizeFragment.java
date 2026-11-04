package com.dkanada.gramophone.fragments.library;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.dkanada.gramophone.R;
import com.dkanada.gramophone.model.LibraryLayout;
import com.dkanada.gramophone.model.SortMethod;
import com.dkanada.gramophone.model.SortOrder;
import com.dkanada.gramophone.util.Util;

/**
 * A library tab that can show its items as a list or as a grid. The column count is derived from the
 * width the list actually has, never from the orientation: a grid fits as many cells of the chosen size
 * as the width allows, and a list uses two columns once the width reaches the expanded size class.
 */
public abstract class AbsLibraryPagerRecyclerViewCustomGridSizeFragment<A extends RecyclerView.Adapter> extends AbsLibraryPagerRecyclerViewFragment<A, GridLayoutManager> {
    private LibraryLayout libraryLayout;
    private SortMethod sortMethod;
    private SortOrder sortOrder;

    private boolean usePaletteInitialized;
    private boolean usePalette;
    private int currentLayoutRes;

    public final LibraryLayout getLibraryLayout() {
        if (libraryLayout == null) {
            libraryLayout = loadLibraryLayout();
        }

        return libraryLayout;
    }

    public final boolean usePalette() {
        if (!usePaletteInitialized) {
            usePalette = loadUsePalette();
            usePaletteInitialized = true;
        }

        return usePalette;
    }

    public final SortMethod getSortMethod() {
        if (sortMethod == null) {
            sortMethod = loadSortMethod();
        }

        return sortMethod;
    }

    public final SortOrder getSortOrder() {
        if (sortOrder == null) {
            sortOrder = loadSortOrder();
        }

        return sortOrder;
    }

    public void setAndSaveLibraryLayout(@NonNull final LibraryLayout libraryLayout) {
        int oldLayoutRes = getItemLayoutRes();
        this.libraryLayout = libraryLayout;
        saveLibraryLayout(libraryLayout);

        // only recreate the adapter and layout manager if the item layout has changed
        if (oldLayoutRes != getItemLayoutRes()) {
            invalidateAdapter();
            invalidateLayoutManager();
        } else {
            updateSpanCount();
        }
    }

    public void setAndSaveUsePalette(final boolean usePalette) {
        this.usePalette = usePalette;
        saveUsePalette(usePalette);
        setUsePalette(usePalette);
    }

    public void setAndSaveSortMethod(final SortMethod sortMethod) {
        this.sortMethod = sortMethod;
        saveSortMethod(sortMethod);
        setSortMethod(sortMethod);
        invalidateAdapter();
    }

    public void setAndSaveSortOrder(final SortOrder sortOrder) {
        this.sortOrder = sortOrder;
        saveSortOrder(sortOrder);
        setSortOrder(sortOrder);
        invalidateAdapter();
    }

    public boolean canUsePalette() {
        return getItemLayoutRes() == R.layout.item_grid;
    }

    @LayoutRes
    protected final int getItemLayoutRes() {
        return getLibraryLayout().isGrid() ? R.layout.item_grid : getListItemLayoutRes();
    }

    @LayoutRes
    protected int getListItemLayoutRes() {
        return R.layout.item_list;
    }

    /**
     * Items that span the whole row regardless of the column count, such as a header.
     */
    protected boolean isFullSpanItem(int position) {
        return false;
    }

    @NonNull
    @Override
    protected GridLayoutManager createLayoutManager() {
        final GridLayoutManager layoutManager = new GridLayoutManager(getActivity(), getSpanCount(getAvailableWidth()));
        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return isFullSpanItem(position) ? layoutManager.getSpanCount() : 1;
            }
        });

        return layoutManager;
    }

    protected final void notifyLayoutResChanged(@LayoutRes int res) {
        this.currentLayoutRes = res;

        RecyclerView recyclerView = getRecyclerView();
        if (recyclerView != null) {
            applyRecyclerViewPaddingForLayoutRes(recyclerView, currentLayoutRes);
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        applyRecyclerViewPaddingForLayoutRes(getRecyclerView(), currentLayoutRes);

        // the window or the space around the list can change size without recreating the fragment
        getRecyclerView().addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (right - left != oldRight - oldLeft) {
                v.post(this::updateSpanCount);
            }
        });
    }

    private void updateSpanCount() {
        if (getView() == null || getLayoutManager() == null) return;

        int spanCount = getSpanCount(getAvailableWidth());
        if (spanCount != getLayoutManager().getSpanCount()) {
            getLayoutManager().setSpanCount(spanCount);
            getAdapter().notifyDataSetChanged();
        }
    }

    private int getAvailableWidth() {
        RecyclerView recyclerView = getView() != null ? getRecyclerView() : null;
        if (recyclerView != null && recyclerView.getWidth() > 0) {
            return recyclerView.getWidth() - recyclerView.getPaddingLeft() - recyclerView.getPaddingRight();
        }

        // not laid out yet, so estimate from the window width
        return Math.round(getResources().getConfiguration().screenWidthDp * getResources().getDisplayMetrics().density);
    }

    private int getSpanCount(int availableWidth) {
        LibraryLayout layout = getLibraryLayout();
        if (!layout.isGrid()) {
            float density = getResources().getDisplayMetrics().density;
            return availableWidth / density >= Util.EXPANDED_WIDTH_DP ? 2 : 1;
        }

        int cellWidth = getResources().getDimensionPixelSize(layout.getCellWidth());
        return Math.max(1, availableWidth / cellWidth);
    }

    protected void applyRecyclerViewPaddingForLayoutRes(@NonNull RecyclerView recyclerView, @LayoutRes int res) {
        int padding;
        if (res == R.layout.item_grid) {
            padding = (int) (getResources().getDisplayMetrics().density * 2);
        } else {
            padding = 0;
        }

        recyclerView.setPadding(padding, padding, padding, padding);
    }

    protected abstract LibraryLayout loadLibraryLayout();

    protected abstract void saveLibraryLayout(LibraryLayout libraryLayout);

    protected abstract void saveUsePalette(boolean usePalette);

    protected abstract boolean loadUsePalette();

    protected abstract void setUsePalette(boolean usePalette);

    protected abstract SortMethod loadSortMethod();

    protected abstract void saveSortMethod(SortMethod sortMethod);

    protected abstract void setSortMethod(SortMethod sortMethod);

    protected abstract SortOrder loadSortOrder();

    protected abstract void saveSortOrder(SortOrder sortOrder);

    protected abstract void setSortOrder(SortOrder sortOrder);
}
