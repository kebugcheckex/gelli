package dev.kebugcheck.gelli.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.drawerlayout.widget.DrawerLayout;

import dev.kebugcheck.gelli.activities.base.AbsMusicContentActivity;
import dev.kebugcheck.gelli.util.LocalLibrary;
import dev.kebugcheck.gelli.util.NavigationUtil;
import dev.kebugcheck.gelli.databinding.ActivityMainContentBinding;
import dev.kebugcheck.gelli.databinding.ActivityMainDrawerLayoutBinding;
import dev.kebugcheck.gelli.databinding.NavigationDrawerHeaderBinding;
import dev.kebugcheck.gelli.dialogs.ConfirmLogoutDialog;
import dev.kebugcheck.gelli.R;
import dev.kebugcheck.gelli.glide.CustomGlideRequest;
import dev.kebugcheck.gelli.helper.MusicPlayerRemote;
import dev.kebugcheck.gelli.model.Song;
import dev.kebugcheck.gelli.fragments.main.LibraryFragment;
import dev.kebugcheck.gelli.util.MusicUtil;
import dev.kebugcheck.gelli.util.ConnectionState;
import dev.kebugcheck.gelli.util.PreferenceUtil;
import dev.kebugcheck.gelli.util.QueryUtil;
import dev.kebugcheck.gelli.util.ServerConnection;
import com.sothree.slidinguppanel.SlidingUpPanelLayout;

import java.util.List;

public class MainActivity extends AbsMusicContentActivity {
    private ActivityMainDrawerLayoutBinding binding;
    private ActivityMainContentBinding contentBinding;
    private NavigationDrawerHeaderBinding navigationBinding;
    private boolean onLogout;

    @Nullable
    private List<QueryUtil.Library> libraries;

    @Nullable
    private Fragment pendingFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LocalLibrary.verifyAsync();
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

            menu.add(R.id.navigation_drawer_menu_category_server, R.id.nav_server, menu.size(), R.string.server_connecting);
            menu.getItem(menu.size() - 1).setActionView(R.layout.navigation_server_latency);
            updateServerItem();

            // the last library may belong to another account, so fall back to the first
            int selected = 0;
            String lastLibrary = PreferenceUtil.getInstance(this).getLastLibrary();
            for (int i = 0; i < libraries.size(); i++) {
                if (libraries.get(i).getId().equals(lastLibrary)) selected = i;
            }

            // queries are scoped to this library, so an account without one can't keep the previous account's
            QueryUtil.currentLibrary = libraries.isEmpty() ? null : libraries.get(selected);

            for (QueryUtil.Library itemDto : libraries) {
                menu.add(R.id.navigation_drawer_menu_category_sections, itemDto.getId().hashCode(), menu.size(), itemDto.getName());
                menu.getItem(menu.size() - 1).setIcon(R.drawable.ic_album_white_24dp).setCheckable(true);
            }

            menu.add(R.id.navigation_drawer_menu_category_other, R.id.nav_settings, menu.size(), R.string.action_settings);
            menu.getItem(menu.size() - 1).setIcon(R.drawable.ic_settings_white_24dp);
            menu.add(R.id.navigation_drawer_menu_category_other, R.id.nav_about, menu.size(), R.string.action_about);
            menu.getItem(menu.size() - 1).setIcon(R.drawable.ic_info_outline_white_24dp);
            menu.add(R.id.navigation_drawer_menu_category_other, R.id.nav_logout, menu.size(), R.string.logout);
            menu.getItem(menu.size() - 1).setIcon(R.drawable.ic_exit_to_app_white_48dp);

            setUpDrawerLayout();

            if (!libraries.isEmpty()) menu.getItem(selected).setChecked(true);
            setCurrentFragment(LibraryFragment.newInstance());
        });
    }

    @Override
    protected void onConnectionStateChanged(@NonNull ConnectionState state) {
        boolean unreachable = state == ConnectionState.Unreachable.INSTANCE;
        contentBinding.unreachable.setVisibility(unreachable ? View.VISIBLE : View.GONE);
        contentBinding.retry.setEnabled(true);
        updateServerItem();
    }

    /** Shows the address in use at the top of the drawer, with how long its last probe took. */
    private void updateServerItem() {
        MenuItem item = binding.navigationView.getMenu().findItem(R.id.nav_server);
        if (item == null) return;

        ConnectionState state = ServerConnection.getState();
        Long latency = null;
        if (state instanceof ConnectionState.Online) {
            String address = ((ConnectionState.Online) state).getAddress();
            item.setTitle(displayAddress(address));
            latency = ServerConnection.getLatency(address);
        } else if (state == ConnectionState.Unreachable.INSTANCE) {
            item.setTitle(R.string.server_no_address);
        } else {
            item.setTitle(R.string.server_connecting);
        }

        item.setIcon(state == ConnectionState.Unreachable.INSTANCE ? R.drawable.ic_cloud_off_24dp : R.drawable.ic_cloud_queue_24dp);
        if (item.getActionView() instanceof TextView) {
            ((TextView) item.getActionView()).setText(latency == null ? null : getString(R.string.latency_ms, latency));
        }
    }

    /** The address without its scheme, which the host and port tell apart well enough in a narrow drawer. */
    private static String displayAddress(String address) {
        return address.replaceFirst("^https?://", "").replaceFirst("/+$", "");
    }

    @Override
    protected void onContentBottomInsetChanged(int inset) {
        // the banner is the bottom of the screen when no mini player sits under it
        View banner = contentBinding.unreachable;
        banner.setPadding(banner.getPaddingLeft(), banner.getPaddingTop(), banner.getPaddingRight(), inset);
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
        contentBinding = ActivityMainContentBinding.inflate(getLayoutInflater());
        contentBinding.retry.setOnClickListener(v -> {
            // the next connection state turns it back on
            v.setEnabled(false);
            ServerConnection.retry();
        });

        ViewGroup drawerContent = binding.drawerContentContainer;
        drawerContent.addView(wrapSlidingMusicPanel(contentBinding.getRoot()));

        binding.drawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerOpened(@NonNull View drawerView) {
                // the latency from when the address was picked may be hours old
                ServerConnection.refreshLatency(MainActivity.this::updateServerItem);
            }
        });

        return binding.getRoot();
    }

    private void setUpNavigationView() {
        binding.navigationView.setNavigationItemSelectedListener(menuItem -> {
            binding.drawerLayout.closeDrawers();
            if (menuItem.getItemId() == R.id.nav_server) {
                String user = PreferenceUtil.getInstance(this).getUser();
                if (user != null) new Handler().postDelayed(() -> AddressesActivity.start(MainActivity.this, user), 200);
            } else if (menuItem.getItemId() == R.id.nav_settings) {
                new Handler().postDelayed(() -> startActivity(new Intent(MainActivity.this, SettingsActivity.class)), 200);
            } else if (menuItem.getItemId() == R.id.nav_about) {
                new Handler().postDelayed(() -> startActivity(new Intent(MainActivity.this, AboutActivity.class)), 200);
            } else if (menuItem.getItemId() == R.id.nav_switch_account) {
                new Handler().postDelayed(() -> NavigationUtil.startSelect(MainActivity.this), 200);
            } else if (menuItem.getItemId() == R.id.nav_logout) {
                onLogout = true;
                ConfirmLogoutDialog.create().show(getSupportFragmentManager(), ConfirmLogoutDialog.TAG);
            }

            // only run the following code when a new library has been selected
            if (QueryUtil.currentLibrary != null && menuItem.getItemId() == QueryUtil.currentLibrary.getId().hashCode()) return true;

            for (QueryUtil.Library itemDto : libraries) {
                if (menuItem.getItemId() == itemDto.getId().hashCode()) {
                    QueryUtil.currentLibrary = itemDto;
                    PreferenceUtil.getInstance(this).setLastLibrary(itemDto.getId());
                    setCurrentFragment(LibraryFragment.newInstance());
                    break;
                }
            }

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
