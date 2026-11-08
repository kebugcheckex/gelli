package com.dkanada.gramophone.fragments.main;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;
import androidx.viewpager.widget.ViewPager;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.dkanada.gramophone.databinding.FragmentLibraryBinding;
import com.dkanada.gramophone.helper.MusicPlayerRemote;
import com.dkanada.gramophone.util.ShortcutUtil;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.navigationrail.NavigationRailView;
import com.dkanada.gramophone.model.Category;
import com.dkanada.gramophone.R;
import com.dkanada.gramophone.adapter.MusicLibraryPagerAdapter;
import com.dkanada.gramophone.dialogs.CreatePlaylistDialog;
import com.dkanada.gramophone.model.LibraryLayout;
import com.dkanada.gramophone.model.SortMethod;
import com.dkanada.gramophone.model.SortOrder;
import com.dkanada.gramophone.activities.SearchActivity;
import com.dkanada.gramophone.fragments.library.AbsLibraryPagerRecyclerViewCustomGridSizeFragment;
import com.dkanada.gramophone.fragments.library.AlbumsFragment;
import com.dkanada.gramophone.fragments.library.PlaylistsFragment;
import com.dkanada.gramophone.fragments.library.SongsFragment;
import com.dkanada.gramophone.util.PreferenceUtil;

public class LibraryFragment extends AbsMainActivityFragment implements ViewPager.OnPageChangeListener, SharedPreferences.OnSharedPreferenceChangeListener {
    private FragmentLibraryBinding binding;

    private MusicLibraryPagerAdapter pagerAdapter;

    public static LibraryFragment newInstance() {
        return new LibraryFragment();
    }

    public LibraryFragment() {
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        setHasOptionsMenu(true);
        binding = FragmentLibraryBinding.inflate(inflater);

        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        PreferenceUtil.getInstance(getActivity()).unregisterOnSharedPreferenceChangedListener(this);
        super.onDestroyView();
        binding.pager.removeOnPageChangeListener(this);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        PreferenceUtil.getInstance(getActivity()).registerOnSharedPreferenceChangedListener(this);

        setUpToolbar();
        setUpViewPager();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
        if (PreferenceUtil.CATEGORIES.equals(key)) {
            Fragment current = getCurrentFragment();
            pagerAdapter.setCategories(PreferenceUtil.getInstance(getActivity()).getCategories());
            binding.pager.setOffscreenPageLimit(pagerAdapter.getCount() - 1);
            int position = pagerAdapter.getItemPosition(current);
            if (position < 0) position = 0;
            binding.pager.setCurrentItem(position);
            PreferenceUtil.getInstance(getContext()).setLastTab(position);

            updateTabVisibility();
            updateRailItems();
        }
    }

    /**
     * The expanded width layout has a navigation rail in place of the tabs and the drawer button.
     */
    private boolean hasRail() {
        return binding.navigationRail != null;
    }

    private void setUpToolbar() {
        if (!hasRail()) {
            binding.toolbar.setNavigationIcon(R.drawable.ic_menu_white_24dp);
            binding.toolbar.setTitle(R.string.app_name);
        }

        getMainActivity().setSupportActionBar(binding.toolbar);
    }

    private void setUpRail() {
        NavigationRailView rail = binding.navigationRail;

        View header = rail.getHeaderView();
        if (header != null) {
            header.findViewById(R.id.rail_menu).setOnClickListener(v -> getMainActivity().openDrawer());
            header.findViewById(R.id.rail_shuffle).setOnClickListener(v -> shuffleAll());
        }

        rail.setOnItemSelectedListener(item -> {
            binding.pager.setCurrentItem(item.getItemId(), false);
            return true;
        });

        updateRailItems();
    }

    private void updateRailItems() {
        if (!hasRail()) return;

        Menu menu = binding.navigationRail.getMenu();
        menu.clear();
        for (int i = 0; i < pagerAdapter.getCount(); i++) {
            Category category = pagerAdapter.getCategory(i);
            menu.add(Menu.NONE, i, i, category.title).setIcon(category.icon);
        }

        updateRailSelection(binding.pager.getCurrentItem());
    }

    private void updateRailSelection(int position) {
        if (!hasRail() || position >= pagerAdapter.getCount()) return;

        binding.navigationRail.getMenu().getItem(position).setChecked(true);
        binding.toolbar.setTitle(pagerAdapter.getCategory(position).title);
    }

    private void shuffleAll() {
        ShortcutUtil.getShuffle((media) -> MusicPlayerRemote.openAndShuffleQueue(media, true), false);
    }

    private void setUpViewPager() {
        pagerAdapter = new MusicLibraryPagerAdapter(requireActivity(), getChildFragmentManager());
        binding.pager.setAdapter(pagerAdapter);
        binding.pager.setOffscreenPageLimit(pagerAdapter.getCount() - 1);

        if (binding.tabs != null) {
            binding.tabs.setupWithViewPager(binding.pager);
        }

        updateTabVisibility();

        if (PreferenceUtil.getInstance(getContext()).getRememberLastTab()) {
            binding.pager.setCurrentItem(PreferenceUtil.getInstance(getContext()).getLastTab());
        }

        binding.pager.addOnPageChangeListener(this);
        if (hasRail()) setUpRail();
    }

    private void updateTabVisibility() {
        if (binding.tabs == null) return;

        // hide the tab bar when only a single tab is visible
        binding.tabs.setVisibility(pagerAdapter.getCount() == 1 ? View.GONE : View.VISIBLE);
    }

    public Fragment getCurrentFragment() {
        return pagerAdapter.getFragment(binding.pager.getCurrentItem());
    }

    private boolean isPlaylistPage() {
        return getCurrentFragment() instanceof PlaylistsFragment;
    }

    public void addOnAppBarOffsetChangedListener(AppBarLayout.OnOffsetChangedListener onOffsetChangedListener) {
        binding.appbar.addOnOffsetChangedListener(onOffsetChangedListener);
    }

    public void removeOnAppBarOffsetChangedListener(AppBarLayout.OnOffsetChangedListener onOffsetChangedListener) {
        binding.appbar.removeOnOffsetChangedListener(onOffsetChangedListener);
    }

    public int getTotalAppBarScrollingRange() {
        return binding.appbar.getTotalScrollRange();
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_main, menu);
        if (isPlaylistPage()) {
            menu.add(0, R.id.action_new_playlist, 0, R.string.action_new_playlist);
        }

        // the rail has its own shuffle button
        if (hasRail()) {
            menu.removeItem(R.id.action_shuffle_all);
        }

        Fragment currentFragment = getCurrentFragment();
        if (currentFragment instanceof AbsLibraryPagerRecyclerViewCustomGridSizeFragment && currentFragment.isAdded()) {
            AbsLibraryPagerRecyclerViewCustomGridSizeFragment absLibraryRecyclerViewCustomGridSizeFragment = (AbsLibraryPagerRecyclerViewCustomGridSizeFragment) currentFragment;

            setUpLayoutMenu(absLibraryRecyclerViewCustomGridSizeFragment, menu.findItem(R.id.action_layout).getSubMenu());
            setUpSortMethodMenu(absLibraryRecyclerViewCustomGridSizeFragment, menu.findItem(R.id.action_sort_method).getSubMenu());
            setUpSortOrderMenu(absLibraryRecyclerViewCustomGridSizeFragment, menu.findItem(R.id.action_sort_order).getSubMenu());
        } else {
            menu.removeItem(R.id.action_layout);
            menu.removeItem(R.id.action_sort_method);
            menu.removeItem(R.id.action_sort_order);
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        Fragment currentFragment = getCurrentFragment();
        if (currentFragment instanceof AbsLibraryPagerRecyclerViewCustomGridSizeFragment) {
            AbsLibraryPagerRecyclerViewCustomGridSizeFragment absLibraryRecyclerViewCustomGridSizeFragment = (AbsLibraryPagerRecyclerViewCustomGridSizeFragment) currentFragment;
            if (handleLayoutMenuItem(absLibraryRecyclerViewCustomGridSizeFragment, item)) {
                return true;
            }

            if (handleSortMethodMenuItem(absLibraryRecyclerViewCustomGridSizeFragment, item)) {
                return true;
            }

            if (handleSortOrderMenuItem(absLibraryRecyclerViewCustomGridSizeFragment, item)) {
                return true;
            }
        }

        int id = item.getItemId();
        if (id == R.id.action_shuffle_all) {
            shuffleAll();
            return true;
        } else if (id == R.id.action_new_playlist) {
            CreatePlaylistDialog.create().show(getChildFragmentManager(), "CREATE_PLAYLIST");
            return true;
        } else if (id == R.id.action_search) {
            startActivity(new Intent(getActivity(), SearchActivity.class));
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void setUpLayoutMenu(@NonNull AbsLibraryPagerRecyclerViewCustomGridSizeFragment fragment, @NonNull SubMenu layoutMenu) {
        layoutMenu.findItem(getLayoutMenuItemId(fragment.getLibraryLayout())).setChecked(true);
    }

    private boolean handleLayoutMenuItem(@NonNull AbsLibraryPagerRecyclerViewCustomGridSizeFragment fragment, @NonNull MenuItem item) {
        for (LibraryLayout layout : LibraryLayout.values()) {
            if (getLayoutMenuItemId(layout) == item.getItemId()) {
                item.setChecked(true);
                fragment.setAndSaveLibraryLayout(layout);
                return true;
            }
        }

        return false;
    }

    @IdRes
    private static int getLayoutMenuItemId(@NonNull LibraryLayout layout) {
        switch (layout) {
            case GRID_SMALL:
                return R.id.action_layout_grid_small;
            case GRID_MEDIUM:
                return R.id.action_layout_grid_medium;
            case GRID_LARGE:
                return R.id.action_layout_grid_large;
            default:
                return R.id.action_layout_list;
        }
    }

    private void setUpSortMethodMenu(@NonNull AbsLibraryPagerRecyclerViewCustomGridSizeFragment fragment, @NonNull SubMenu sortMethodMenu) {
        SortMethod currentSortMethod = fragment.getSortMethod();
        sortMethodMenu.clear();

        sortMethodMenu.add(0, R.id.action_sort_method_name, 0, R.string.sort_method_name)
            .setChecked(currentSortMethod.equals(SortMethod.NAME));

        if (fragment instanceof AlbumsFragment) {
            sortMethodMenu.add(0, R.id.action_sort_method_artist, 1, R.string.sort_method_artist)
                    .setChecked(currentSortMethod.equals(SortMethod.ARTIST));
            sortMethodMenu.add(0, R.id.action_sort_method_year, 2, R.string.sort_method_year)
                    .setChecked(currentSortMethod.equals(SortMethod.YEAR));
        } else if (fragment instanceof SongsFragment) {
            sortMethodMenu.add(0, R.id.action_sort_method_album, 1, R.string.sort_method_album)
                    .setChecked(currentSortMethod.equals(SortMethod.ALBUM));
            sortMethodMenu.add(0, R.id.action_sort_method_artist, 2, R.string.sort_method_artist)
                    .setChecked(currentSortMethod.equals(SortMethod.ARTIST));
            sortMethodMenu.add(0, R.id.action_sort_method_year, 3, R.string.sort_method_year)
                    .setChecked(currentSortMethod.equals(SortMethod.YEAR));
        }

        sortMethodMenu.add(0, R.id.action_sort_method_added, 4, R.string.sort_method_added)
            .setChecked(currentSortMethod.equals(SortMethod.ADDED));
        sortMethodMenu.add(0, R.id.action_sort_method_random, 5, R.string.sort_method_random)
            .setChecked(currentSortMethod.equals(SortMethod.RANDOM));

        sortMethodMenu.setGroupCheckable(0, true, true);
    }

    private void setUpSortOrderMenu(@NonNull AbsLibraryPagerRecyclerViewCustomGridSizeFragment fragment, @NonNull SubMenu sortOrderMenu) {
        SortOrder currentSortOrder = fragment.getSortOrder();
        sortOrderMenu.clear();

        sortOrderMenu.add(0, R.id.action_sort_order_ascending, 0, R.string.sort_order_ascending)
                .setChecked(currentSortOrder.equals(SortOrder.ASCENDING));
        sortOrderMenu.add(0, R.id.action_sort_order_descending, 1, R.string.sort_order_descending)
                .setChecked(currentSortOrder.equals(SortOrder.DESCENDING));

        sortOrderMenu.setGroupCheckable(0, true, true);
    }

    private boolean handleSortMethodMenuItem(@NonNull AbsLibraryPagerRecyclerViewCustomGridSizeFragment fragment, @NonNull MenuItem item) {
        SortMethod sortMethod = null;
        if (item.getItemId() == R.id.action_sort_method_name) {
            sortMethod = SortMethod.NAME;
        } else if (item.getItemId() == R.id.action_sort_method_album) {
            sortMethod = SortMethod.ALBUM;
        } else if (item.getItemId() == R.id.action_sort_method_artist) {
            sortMethod = SortMethod.ARTIST;
        } else if (item.getItemId() == R.id.action_sort_method_year) {
            sortMethod = SortMethod.YEAR;
        } else if (item.getItemId() == R.id.action_sort_method_added) {
            sortMethod = SortMethod.ADDED;
        } else if (item.getItemId() == R.id.action_sort_method_random) {
            sortMethod = SortMethod.RANDOM;
        }

        if (sortMethod != null) {
            item.setChecked(true);
            fragment.setAndSaveSortMethod(sortMethod);
            return true;
        }

        return false;
    }

    private boolean handleSortOrderMenuItem(@NonNull AbsLibraryPagerRecyclerViewCustomGridSizeFragment fragment, @NonNull MenuItem item) {
        SortOrder sortOrder = null;
        if (item.getItemId() == R.id.action_sort_order_ascending) {
            sortOrder = SortOrder.ASCENDING;
        } else if (item.getItemId() == R.id.action_sort_order_descending) {
            sortOrder = SortOrder.DESCENDING;
        }

        if (sortOrder != null) {
            item.setChecked(true);
            fragment.setAndSaveSortOrder(sortOrder);
            return true;
        }

        return false;
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
    }

    @Override
    public void onPageSelected(int position) {
        PreferenceUtil.getInstance(getActivity()).setLastTab(position);
        updateRailSelection(position);

        // the selection belongs to the page that started it
        getMainActivity().finishActionMode();
    }

    @Override
    public void onPageScrollStateChanged(int state) {
    }
}
