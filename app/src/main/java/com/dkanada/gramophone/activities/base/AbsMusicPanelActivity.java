package com.dkanada.gramophone.activities.base;

import android.animation.ValueAnimator;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.PathInterpolator;

import androidx.annotation.FloatRange;
import androidx.annotation.RequiresApi;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.dkanada.gramophone.R;
import com.dkanada.gramophone.databinding.SlidingMusicPanelLayoutBinding;
import com.dkanada.gramophone.helper.MusicPlayerRemote;
import com.dkanada.gramophone.fragments.player.AbsPlayerFragment;
import com.dkanada.gramophone.fragments.player.LandscapePlayerFragment;
import com.dkanada.gramophone.fragments.player.NowPlayingScreen;
import com.dkanada.gramophone.fragments.player.PlayerBarFragment;
import com.dkanada.gramophone.fragments.player.card.CardPlayerFragment;
import com.dkanada.gramophone.fragments.player.flat.FlatPlayerFragment;
import com.dkanada.gramophone.util.NavigationUtil;
import com.dkanada.gramophone.util.PreferenceUtil;
import com.dkanada.gramophone.util.ViewUtil;
import com.sothree.slidinguppanel.SlidingUpPanelLayout;

/**
 * Wraps the content in a sliding panel that holds the player. The collapsed panel is the mini player,
 * or at the expanded width a full-width player bar, which also has a queue sheet beside the content.
 */
public abstract class AbsMusicPanelActivity extends AbsMusicServiceActivity implements SlidingUpPanelLayout.PanelSlideListener, CardPlayerFragment.Callbacks {
    private SlidingMusicPanelLayoutBinding binding;
    private SlidingUpPanelLayout slidingLayout;

    private int navigationBarColor;
    private int taskDescriptionColor;
    private boolean lightStatusBar;

    private NowPlayingScreen currentNowPlayingScreen;
    private AbsPlayerFragment playerFragment;
    // the mini player, or the player bar at the expanded width
    private Fragment miniPlayerFragment;
    private ValueAnimator navigationBarColorAnimator;

    // the collapsed panel and the queue sheet stay hidden until the queue is known to have songs
    private boolean bottomBarHidden = true;
    // the collapsed panel reaches under the navigation bar, so it grows by this much and pads its content up
    private int bottomInset;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(createContentView());

        currentNowPlayingScreen = PreferenceUtil.getInstance(this).getNowPlayingScreen();

        // must implement AbsPlayerFragment, and a window wider than it is tall gets the landscape player whatever the preference
        Fragment fragment;
        if (getResources().getBoolean(R.bool.landscape_player)) {
            fragment = new LandscapePlayerFragment();
        } else switch (currentNowPlayingScreen) {
            case FLAT:
                fragment = new FlatPlayerFragment();
                break;
            case CARD:
            default:
                fragment = new CardPlayerFragment();
                break;
        }

        getSupportFragmentManager().beginTransaction().replace(R.id.player_fragment_container, fragment).commit();
        getSupportFragmentManager().executePendingTransactions();

        playerFragment = (AbsPlayerFragment) getSupportFragmentManager().findFragmentById(R.id.player_fragment_container);
        miniPlayerFragment = getSupportFragmentManager().findFragmentById(R.id.mini_player_fragment);

        // noinspection ConstantConditions
        miniPlayerFragment.getView().setOnClickListener(v -> expandPanel());

        slidingLayout.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                slidingLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this);

                switch (getPanelState()) {
                    case EXPANDED:
                        onPanelSlide(slidingLayout, 1);
                        onPanelExpanded(slidingLayout);
                        break;
                    case COLLAPSED:
                        onPanelCollapsed(slidingLayout);
                        break;
                    default:
                        playerFragment.onHide();
                        break;
                }
            }
        });

        slidingLayout.addPanelSlideListener(this);

        // TODO remove this once createContentView works with inheritance
        // any AbsMusicPanelActivity child without setColor has status bar issues
        // setDrawUnderStatusBar only works after content view has been set
        setColor(PreferenceUtil.getInstance(this).getPrimaryColor());
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (currentNowPlayingScreen != PreferenceUtil.getInstance(this).getNowPlayingScreen()) {
            NavigationUtil.recreateMain(this);
        }

        // another screen may have opened or closed the sheet
        applyQueueSheetVisibility();
    }

    public void setAntiDragView(View antiDragView) {
        slidingLayout.setAntiDragView(antiDragView);
    }

    protected abstract View createContentView();

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();

        // don't call hideBottomBar(true) here as it causes a bug with the SlidingUpPanelLayout
        if (!MusicPlayerRemote.getPlayingQueue().isEmpty()) {
            slidingLayout.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    slidingLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    hideBottomBar(false);
                }
            });
        }
    }

    @Override
    public void onQueueChanged() {
        super.onQueueChanged();
        hideBottomBar(MusicPlayerRemote.getPlayingQueue().isEmpty());
    }

    @Override
    public void onPanelSlide(View panel, @FloatRange(from = 0, to = 1) float slideOffset) {
        setMiniPlayerAlphaProgress(slideOffset);
        if (navigationBarColorAnimator != null) navigationBarColorAnimator.cancel();
        super.setNavigationBarColor(ColorUtils.blendARGB(navigationBarColor, playerFragment.getPaletteColor(), slideOffset));
    }

    @Override
    public void onPanelStateChanged(View panel, SlidingUpPanelLayout.PanelState previousState, SlidingUpPanelLayout.PanelState newState) {
        switch (newState) {
            case COLLAPSED:
                onPanelCollapsed(panel);
                break;
            case EXPANDED:
                onPanelExpanded(panel);
                break;
            case ANCHORED:
                // this fixes a bug where the panel would get stuck for some reason
                collapsePanel();
                break;
        }
    }

    public void onPanelCollapsed(View panel) {
        super.setLightStatusBar(lightStatusBar);
        super.setTaskDescriptionColor(taskDescriptionColor);
        super.setNavigationBarColor(navigationBarColor);

        playerFragment.setMenuVisibility(false);
        playerFragment.setUserVisibleHint(false);
        playerFragment.onHide();

        // the expanded player has its own favorite button
        if (miniPlayerFragment instanceof PlayerBarFragment) {
            ((PlayerBarFragment) miniPlayerFragment).updateIsFavorite();
        }
    }

    public void onPanelExpanded(View panel) {
        int playerFragmentColor = playerFragment.getPaletteColor();

        super.setLightStatusBar(false);
        super.setTaskDescriptionColor(playerFragmentColor);
        super.setNavigationBarColor(playerFragmentColor);

        playerFragment.setMenuVisibility(true);
        playerFragment.setUserVisibleHint(true);
        playerFragment.onShow();
    }

    private void setMiniPlayerAlphaProgress(@FloatRange(from = 0, to = 1) float progress) {
        if (miniPlayerFragment.getView() == null) return;
        float alpha = 1 - progress;
        miniPlayerFragment.getView().setAlpha(alpha);
        // necessary to make the views below clickable
        miniPlayerFragment.getView().setVisibility(alpha == 0 ? View.GONE : View.VISIBLE);
    }

    public SlidingUpPanelLayout.PanelState getPanelState() {
        return binding == null ? null : slidingLayout.getPanelState();
    }

    public void collapsePanel() {
        slidingLayout.setPanelState(SlidingUpPanelLayout.PanelState.COLLAPSED);
    }

    public void expandPanel() {
        slidingLayout.setPanelState(SlidingUpPanelLayout.PanelState.EXPANDED);
    }

    public void hideBottomBar(final boolean hide) {
        bottomBarHidden = hide;
        applyQueueSheetVisibility();

        if (hide) {
            slidingLayout.setPanelHeight(0);
            collapsePanel();
        } else {
            slidingLayout.setPanelHeight(getCollapsedPanelHeight());
        }
    }

    private int getCollapsedPanelHeight() {
        return getResources().getDimensionPixelSize(R.dimen.mini_player_height) + bottomInset;
    }

    private void applyBottomInset() {
        if (miniPlayerFragment == null || miniPlayerFragment.getView() == null) return;

        View miniPlayer = miniPlayerFragment.getView();
        miniPlayer.setPadding(miniPlayer.getPaddingLeft(), miniPlayer.getPaddingTop(), miniPlayer.getPaddingRight(), bottomInset);
        miniPlayer.getLayoutParams().height = getCollapsedPanelHeight();
        miniPlayer.requestLayout();

        if (!bottomBarHidden) slidingLayout.setPanelHeight(getCollapsedPanelHeight());
    }

    /**
     * Whether the queue sheet is open. Only the expanded width has one, and it stays open across screens.
     */
    public boolean isQueueSheetOpen() {
        return binding != null && binding.queueSheet != null && PreferenceUtil.getInstance(this).getQueueSheetOpen();
    }

    public void setQueueSheetOpen(boolean open) {
        PreferenceUtil.getInstance(this).setQueueSheetOpen(open);
        applyQueueSheetVisibility();
    }

    public void toggleQueueSheet() {
        setQueueSheetOpen(!isQueueSheetOpen());
    }

    private void applyQueueSheetVisibility() {
        if (binding == null || binding.queueSheet == null) return;

        binding.queueSheet.setVisibility(isQueueSheetOpen() && !bottomBarHidden ? View.VISIBLE : View.GONE);
        if (miniPlayerFragment instanceof PlayerBarFragment) {
            ((PlayerBarFragment) miniPlayerFragment).updateQueueButton();
        }
    }

    protected View wrapSlidingMusicPanel(View view) {
        binding = SlidingMusicPanelLayoutBinding.inflate(getLayoutInflater());
        slidingLayout = binding.getRoot();
        ViewCompat.setOnApplyWindowInsetsListener(slidingLayout, (v, insets) -> {
            int inset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            if (inset != bottomInset) {
                bottomInset = inset;
                applyBottomInset();
            }

            return insets;
        });

        ViewGroup contentContainer = binding.contentContainer;
        contentContainer.addView(view);

        return binding.getRoot();
    }

    @Override
    public void onPaletteColorChanged() {
        if (getPanelState() == SlidingUpPanelLayout.PanelState.EXPANDED) {
            int playerFragmentColor = playerFragment.getPaletteColor();

            super.setTaskDescriptionColor(playerFragmentColor);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                animateNavigationBarColor(playerFragmentColor);
            }
        }
    }

    @Override
    public void setLightStatusBar(boolean enabled) {
        lightStatusBar = enabled;

        if (getPanelState() == SlidingUpPanelLayout.PanelState.COLLAPSED) {
            super.setLightStatusBar(enabled);
        }
    }

    @Override
    public void setNavigationBarColor(int color) {
        navigationBarColor = color;

        if (navigationBarColorAnimator != null) {
            navigationBarColorAnimator.cancel();
        }

        if (getPanelState() == SlidingUpPanelLayout.PanelState.COLLAPSED) {
            super.setNavigationBarColor(color);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void animateNavigationBarColor(int color) {
        if (navigationBarColorAnimator != null) {
            navigationBarColorAnimator.cancel();
        }

        navigationBarColorAnimator = ValueAnimator
            .ofArgb(getWindow().getNavigationBarColor(), color)
            .setDuration(ViewUtil.PHONOGRAPH_ANIM_TIME);

        navigationBarColorAnimator.setInterpolator(new PathInterpolator(0.4f, 0f, 1f, 1f));
        navigationBarColorAnimator.addUpdateListener(animation -> super.setNavigationBarColor((int) animation.getAnimatedValue()));
        navigationBarColorAnimator.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (navigationBarColorAnimator != null) {
            navigationBarColorAnimator.cancel();
        }
    }

    @Override
    public void onBackPressed() {
        boolean queuePanelCollapsed = playerFragment.onBackPressed();

        if (!queuePanelCollapsed && getPanelState() == SlidingUpPanelLayout.PanelState.EXPANDED) {
            collapsePanel();
        } else if (!queuePanelCollapsed) {
            super.onBackPressed();
        }
    }

    @Override
    public void setTaskDescriptionColor(int color) {
        taskDescriptionColor = color;

        if (getPanelState() == null || getPanelState() == SlidingUpPanelLayout.PanelState.COLLAPSED) {
            super.setTaskDescriptionColor(color);
        }
    }
}
