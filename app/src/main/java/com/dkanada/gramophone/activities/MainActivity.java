package com.dkanada.gramophone.activities;

import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.drawerlayout.widget.DrawerLayout;

import com.dkanada.gramophone.activities.base.AbsMusicContentActivity;
import com.dkanada.gramophone.util.LocalLibrary;
import com.dkanada.gramophone.util.NavigationUtil;
import com.dkanada.gramophone.util.PreferenceUtil;
import com.dkanada.gramophone.util.ThemeUtil;
import com.dkanada.gramophone.databinding.ActivityMainContentBinding;
import com.dkanada.gramophone.databinding.ActivityMainDrawerLayoutBinding;
import com.dkanada.gramophone.databinding.NavigationDrawerHeaderBinding;
import com.dkanada.gramophone.dialogs.ConfirmLogoutDialog;
import com.dkanada.gramophone.R;
import com.dkanada.gramophone.glide.CustomGlideRequest;
import com.dkanada.gramophone.helper.MusicPlayerRemote;
import com.dkanada.gramophone.model.Song;
import com.dkanada.gramophone.fragments.main.LibraryFragment;
import com.dkanada.gramophone.util.MusicUtil;
import com.dkanada.gramophone.util.QueryUtil;
import com.sothree.slidinguppanel.SlidingUpPanelLayout;

import java.util.List;

public class MainActivity extends AbsMusicContentActivity {
    private ActivityMainDrawerLayoutBinding binding;
    private NavigationDrawerHeaderBinding navigationBinding;
    private boolean onLogout;

    @Nullable
    private List<QueryUtil.Library> libraries;

    @Nullable
    private Fragment pendingFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setColor(isExpandedLayout()
            ? ThemeUtil.getColorResource(this, R.attr.colorSurface)
            : PreferenceUtil.getInstance(this).getPrimaryColor());
        LocalLibrary.verifyAsync();
    }

    private boolean isExpandedLayout() {
        return getResources().getBoolean(R.bool.expanded_layout);
    }

    @Override
    public void setStatusBarColor(int color) {
        if (!isExpandedLayout()) {
            super.setStatusBarColor(color);
            return;
        }

        // the neutral app bar continues into the status bar without the darker band
        View statusBar = findViewById(R.id.status_bar);
        if (statusBar != null) statusBar.setBackgroundColor(color);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
    }

    public void openDrawer() {
        binding.drawerLayout.openDrawer(binding.navigationView);
    }

    @Override
    public void onStateOnline() {
        Menu menu = binding.navigationView.getMenu();
        QueryUtil.getLibraries(media -> {
            libraries = media;
            menu.clear();

            for (QueryUtil.Library itemDto : libraries) {
                if (menu.size() == 0) {
                    QueryUtil.currentLibrary = itemDto;
                }

                if (itemDto.getCollectionType() == null || !itemDto.getCollectionType().equals("music")) continue;
                menu.add(R.id.navigation_drawer_menu_category_sections, itemDto.getId().hashCode(), menu.size(), itemDto.getName());
                menu.getItem(menu.size() - 1).setIcon(R.drawable.ic_album_white_24dp);
            }

            menu.add(R.id.navigation_drawer_menu_category_other, R.id.nav_settings, menu.size(), R.string.action_settings);
            menu.getItem(menu.size() - 1).setIcon(R.drawable.ic_settings_white_24dp);
            menu.add(R.id.navigation_drawer_menu_category_other, R.id.nav_about, menu.size(), R.string.action_about);
            menu.getItem(menu.size() - 1).setIcon(R.drawable.ic_info_outline_white_24dp);
            menu.add(R.id.navigation_drawer_menu_category_other, R.id.nav_logout, menu.size(), R.string.logout);
            menu.getItem(menu.size() - 1).setIcon(R.drawable.ic_exit_to_app_white_48dp);

            setUpDrawerLayout();

            menu.getItem(0).setChecked(true);
            setCurrentFragment(LibraryFragment.newInstance());
        });
    }

    @Override
    public void onPause() {
        super.onPause();

        // only override when logout selected
        if (onLogout) {
            overridePendingTransition(0, R.anim.fade_quick);
            onLogout = false;
        }
    }

    private void setCurrentFragment(Fragment fragment) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        if (isFinishing() || isDestroyed()) return;

        if (fragmentManager.isStateSaved()) {
            pendingFragment = fragment;
            return;
        }

        // the selection belongs to the library being replaced
        finishActionMode();

        fragmentManager.beginTransaction().replace(R.id.fragment_container, fragment, null).commit();
        pendingFragment = null;
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();

        if (pendingFragment != null) {
            Fragment fragment = pendingFragment;
            pendingFragment = null;
            setCurrentFragment(fragment);
        }
    }

    @Override
    protected View createContentView() {
        binding = ActivityMainDrawerLayoutBinding.inflate(getLayoutInflater());
        ActivityMainContentBinding contentBinding = ActivityMainContentBinding.inflate(getLayoutInflater());

        ViewGroup drawerContent = binding.drawerContentContainer;
        drawerContent.addView(wrapSlidingMusicPanel(contentBinding.getRoot()));

        return binding.getRoot();
    }

    private void setUpNavigationView() {
        int normalColor = ThemeUtil.getColorResource(this, android.R.attr.textColorPrimary);
        int activeColor = PreferenceUtil.getInstance(this).getAccentColor();

        binding.navigationView.setItemIconTintList(ThemeUtil.getColorStateList(normalColor, activeColor));
        binding.navigationView.setItemTextColor(ThemeUtil.getColorStateList(normalColor, activeColor));

        binding.navigationView.setNavigationItemSelectedListener(menuItem -> {
            binding.drawerLayout.closeDrawers();
            switch (menuItem.getItemId()) {
                case R.id.nav_settings:
                    new Handler().postDelayed(() -> startActivity(new Intent(MainActivity.this, SettingsActivity.class)), 200);
                    break;
                case R.id.nav_about:
                    new Handler().postDelayed(() -> startActivity(new Intent(MainActivity.this, AboutActivity.class)), 200);
                    break;
                case R.id.nav_switch_account:
                    new Handler().postDelayed(() -> NavigationUtil.startSelect(MainActivity.this), 200);
                    break;
                case R.id.nav_logout:
                    onLogout = true;
                    ConfirmLogoutDialog.create().show(getSupportFragmentManager(), ConfirmLogoutDialog.TAG);
                    break;
            }

            // only run the following code when a new library has been selected
            if (QueryUtil.currentLibrary != null && menuItem.getItemId() == QueryUtil.currentLibrary.getId().hashCode()) return true;

            for (QueryUtil.Library itemDto : libraries) {
                if (menuItem.getItemId() == itemDto.getId().hashCode()) {
                    QueryUtil.currentLibrary = itemDto;
                    setCurrentFragment(LibraryFragment.newInstance());
                    break;
                }
            }

            // setCheckable must be applied to the items on creation
            // it also applies a tacky background color for the checked item
            // this is a hack to check the current item without that
            if (menuItem.getGroupId() != R.id.navigation_drawer_menu_category_sections) return true;

            for (int i = 0; i < binding.navigationView.getMenu().size(); i++) {
                MenuItem item = binding.navigationView.getMenu().getItem(i);

                // ignore items that open new activities since the navigation view is hidden
                item.setChecked(item == menuItem);
            }

            return true;
        });
    }

    private void setUpDrawerLayout() {
        setUpNavigationView();
    }

    private void updateNavigationDrawerHeader() {
        if (!MusicPlayerRemote.getPlayingQueue().isEmpty()) {
            Song song = MusicPlayerRemote.getCurrentSong();
            if (navigationBinding == null) {
                navigationBinding = NavigationDrawerHeaderBinding.inflate(getLayoutInflater());

                binding.navigationView.addHeaderView(navigationBinding.getRoot());
                navigationBinding.getRoot().setOnClickListener(v -> {
                    binding.drawerLayout.closeDrawers();
                    if (getPanelState() == SlidingUpPanelLayout.PanelState.COLLAPSED) {
                        expandPanel();
                    }
                });
            }

            navigationBinding.title.setText(song.title);
            navigationBinding.text.setText(MusicUtil.buildInfoString(song.artistName, song.albumName));

            CustomGlideRequest.Builder
                .from(this, song.primary, song.blurHash)
                .build().centerInside().into(navigationBinding.image);
        } else if (binding.navigationView.getHeaderCount() != 0) {
            binding.navigationView.removeHeaderView(navigationBinding.getRoot());
            navigationBinding = null;
        }
    }

    @Override
    public void onPlayMetadataChanged() {
        super.onPlayMetadataChanged();
        updateNavigationDrawerHeader();
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        updateNavigationDrawerHeader();
    }

    @Override
    public void onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(binding.navigationView)) {
            binding.drawerLayout.closeDrawers();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            if (binding.drawerLayout.isDrawerOpen(binding.navigationView)) {
                binding.drawerLayout.closeDrawer(binding.navigationView);
            } else {
                binding.drawerLayout.openDrawer(binding.navigationView);
            }

            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onPanelExpanded(View view) {
        super.onPanelExpanded(view);
        binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
    }

    @Override
    public void onPanelCollapsed(View view) {
        super.onPanelCollapsed(view);
        binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
    }
}
