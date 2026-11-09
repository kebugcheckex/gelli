package com.dkanada.gramophone.fragments.library;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.dkanada.gramophone.R;
import com.dkanada.gramophone.model.LibraryLayout;
import com.dkanada.gramophone.model.SortMethod;
import com.dkanada.gramophone.model.SortOrder;
import com.dkanada.gramophone.util.SectionedList;
import com.dkanada.gramophone.util.Util;
import com.dkanada.gramophone.views.SectionIndexBar;

/**
 * A library tab that can show its items as a list or as a grid. The column count is derived from the
 * width the list actually has, never from the orientation: a grid fits as many cells of the chosen size
 * as the width allows, but always at least one more column than the next larger size, and a list uses
 * two columns once the width reaches the expanded size class.
 */
public abstract class AbsLibraryPagerRecyclerViewCustomGridSizeFragment<A extends RecyclerView.Adapter> extends AbsLibraryPagerRecyclerViewFragment<A, GridLayoutManager> {
    private LibraryLayout libraryLayout;
    private SortMethod sortMethod;
    private SortOrder sortOrder;

    private int currentLayoutRes;

    @Nullable
    private SectionedList<?> sections;

    public final LibraryLayout getLibraryLayout() {
        if (libraryLayout == null) {
            libraryLayout = loadLibraryLayout();
        }

        return libraryLayout;
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

    /**
     * Whether the current sort files the items under letters. Such a tab loads every item at once and
     * shows the letter bar in place of the drag thumb; any other sort pages through the items.
     */
    protected boolean isSectioned() {
        return false;
    }

    /**
     * Shows the letter bar for items sorted into sections, or hides it and brings back the drag thumb
     * for null. The adapter must hold exactly the items of the sections.
     */
    protected final void setSections(@Nullable SectionedList<?> sections) {
        if (getView() == null) return;

        this.sections = sections;

        SectionIndexBar bar = getSectionIndexBar();
        if (sections == null) {
            bar.setVisibility(View.GONE);
            bar.setOnSectionSelectedListener(null);
        } else {
            boolean[] hasItems = new boolean[sections.getSections().size()];
            for (int i = 0; i < hasItems.length; i++) {
                hasItems[i] = sections.hasItems(i);
            }

            bar.setSections(sections.getSections(), hasItems);
            bar.setOnSectionSelectedListener(this::scrollToSection);
            bar.setVisibility(View.VISIBLE);
        }

        getRecyclerView().setFastScrollEnabled(sections == null);
        applyRecyclerViewPaddingForLayoutRes(getRecyclerView(), currentLayoutRes);
        // the padding changes the width the grid has for its columns
        getRecyclerView().post(this::updateSpanCount);
    }

    private void scrollToSection(int sectionIndex) {
        if (sections == null || getLayoutManager() == null) return;

        int position = sections.positionForSection(sectionIndex);
        if (position >= 0) {
            getRecyclerView().stopScroll();
            getLayoutManager().scrollToPositionWithOffset(position, 0);
        }
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

        return layout.gridSpanCount(availableWidth, grid -> getResources().getDimensionPixelSize(grid.getCellWidth()));
    }

    protected void applyRecyclerViewPaddingForLayoutRes(@NonNull RecyclerView recyclerView, @LayoutRes int res) {
        int padding = res == R.layout.item_grid ? getResources().getDimensionPixelSize(R.dimen.grid_padding) : 0;

        // the letter bar sits over the end edge, so keep item content such as menu buttons out from under it
        int end = padding + (sections != null ? getSectionIndexBar().getColumnWidth() : 0);

        recyclerView.setPaddingRelative(padding, padding, end, padding);
    }

    protected abstract LibraryLayout loadLibraryLayout();

    protected abstract void saveLibraryLayout(LibraryLayout libraryLayout);

    protected abstract SortMethod loadSortMethod();

    protected abstract void saveSortMethod(SortMethod sortMethod);

    protected abstract void setSortMethod(SortMethod sortMethod);

    protected abstract SortOrder loadSortOrder();

    protected abstract void saveSortOrder(SortOrder sortOrder);

    protected abstract void setSortOrder(SortOrder sortOrder);
}
