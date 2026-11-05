package com.dkanada.gramophone.activities.base;

import android.animation.ValueAnimator;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.PathInterpolator;

import androidx.annotation.FloatRange;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.graphics.ColorUtils;
import androidx.fragment.app.Fragment;

import com.dkanada.gramophone.R;
import com.dkanada.gramophone.databinding.SlidingMusicPanelLayoutBinding;
import com.dkanada.gramophone.helper.MusicPlayerRemote;
import com.dkanada.gramophone.fragments.player.AbsPlayerFragment;
import com.dkanada.gramophone.fragments.player.MiniPlayerFragment;
import com.dkanada.gramophone.fragments.player.NowPlayingScreen;
import com.dkanada.gramophone.fragments.player.card.CardPlayerFragment;
import com.dkanada.gramophone.fragments.player.flat.FlatPlayerFragment;
import com.dkanada.gramophone.util.NavigationUtil;
import com.dkanada.gramophone.util.PreferenceUtil;
import com.dkanada.gramophone.util.ViewUtil;
import com.sothree.slidinguppanel.SlidingUpPanelLayout;

public abstract class AbsMusicPanelActivity extends AbsMusicServiceActivity implements SlidingUpPanelLayout.PanelSlideListener, CardPlayerFragment.Callbacks {
    private SlidingMusicPanelLayoutBinding binding;
    // the binding root below expanded width, null in pane mode
    @Nullable
    private SlidingUpPanelLayout slidingLayout;

    private int navigationBarColor;
    private int taskDescriptionColor;
    private boolean lightStatusBar;

    private NowPlayingScreen currentNowPlayingScreen;
    private AbsPlayerFragment playerFragment;
    private MiniPlayerFragment miniPlayerFragment;
    private ValueAnimator navigationBarColorAnimator;

    // pane mode only: null until the queue is known
    private Boolean paneHidden;
    private boolean paneLaidOut;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(createContentView());

        currentNowPlayingScreen = PreferenceUtil.getInstance(this).getNowPlayingScreen();

        // must implement AbsPlayerFragment, and the fixed pane only has the flat arrangement
        Fragment fragment;
        switch (isPaneMode() ? NowPlayingScreen.FLAT : currentNowPlayingScreen) {
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
        if (isPaneMode()) {
            setUpPane();
        } else {
            setUpSlidingPanel();
        }

        // TODO remove this once createContentView works with inheritance
        // any AbsMusicPanelActivity child without setColor has status bar issues
        // setDrawUnderStatusBar only works after content view has been set
        setColor(PreferenceUtil.getInstance(this).getPrimaryColor());
    }

    /**
     * At expanded width the shell has no sliding panel: the player sits in a fixed pane beside the content.
     */
    public boolean isPaneMode() {
        return binding != null && slidingLayout == null;
    }

    private void setUpPane() {
        playerFragment.setMenuVisibility(true);
        playerFragment.setUserVisibleHint(true);

        // the player fragment sizes its views in its own first layout pass, which runs before this one
        binding.getRoot().getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                binding.getRoot().getViewTreeObserver().removeOnGlobalLayoutListener(this);
                paneLaidOut = true;
                applyPaneVisibility();
            }
        });
    }

    private void applyPaneVisibility() {
        if (!paneLaidOut || paneHidden == null) return;

        // noinspection ConstantConditions
        binding.nowPlayingPane.setVisibility(paneHidden ? View.GONE : View.VISIBLE);
        if (!paneHidden) playerFragment.onShow();
    }

    private void setUpSlidingPanel() {
        miniPlayerFragment = (MiniPlayerFragment) getSupportFragmentManager().findFragmentById(R.id.mini_player_fragment);

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
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (currentNowPlayingScreen != PreferenceUtil.getInstance(this).getNowPlayingScreen()) {
            NavigationUtil.recreateMain(this);
        }
    }

    public void setAntiDragView(View antiDragView) {
        if (isPaneMode()) return;
        slidingLayout.setAntiDragView(antiDragView);
    }

    protected abstract View createContentView();

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();

        if (isPaneMode()) {
            hideBottomBar(MusicPlayerRemote.getPlayingQueue().isEmpty());
            return;
        }

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

    /**
     * In pane mode the player is always showing, so this reports expanded.
     */
    public SlidingUpPanelLayout.PanelState getPanelState() {
        if (binding == null) return null;
        if (isPaneMode()) return SlidingUpPanelLayout.PanelState.EXPANDED;
        return slidingLayout.getPanelState();
    }

    public void collapsePanel() {
        if (isPaneMode()) return;
        slidingLayout.setPanelState(SlidingUpPanelLayout.PanelState.COLLAPSED);
    }

    public void expandPanel() {
        if (isPaneMode()) return;
        slidingLayout.setPanelState(SlidingUpPanelLayout.PanelState.EXPANDED);
    }

    /**
     * Hides the mini player, or in pane mode the whole now-playing pane so the content takes the full width.
     */
    public void hideBottomBar(final boolean hide) {
        if (isPaneMode()) {
            paneHidden = hide;
            applyPaneVisibility();
        } else if (hide) {
            slidingLayout.setPanelHeight(0);
            collapsePanel();
        } else {
            slidingLayout.setPanelHeight(getResources().getDimensionPixelSize(R.dimen.mini_player_height));
        }
    }

    protected View wrapSlidingMusicPanel(View view) {
        binding = SlidingMusicPanelLayoutBinding.inflate(getLayoutInflater());
        slidingLayout = binding.getRoot() instanceof SlidingUpPanelLayout ? (SlidingUpPanelLayout) binding.getRoot() : null;

        ViewGroup contentContainer = binding.contentContainer;
        contentContainer.addView(view);

        return binding.getRoot();
    }

    @Override
    public void onPaletteColorChanged() {
        if (!isPaneMode() && getPanelState() == SlidingUpPanelLayout.PanelState.EXPANDED) {
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

        if (contentOwnsSystemBars()) {
            super.setLightStatusBar(enabled);
        }
    }

    @Override
    public void setNavigationBarColor(int color) {
        navigationBarColor = color;

        if (navigationBarColorAnimator != null) {
            navigationBarColorAnimator.cancel();
        }

        if (contentOwnsSystemBars()) {
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
        // the pane is part of the screen, so Back leaves the screen rather than closing the player or its queue
        if (isPaneMode()) {
            super.onBackPressed();
            return;
        }

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

        if (binding == null || contentOwnsSystemBars()) {
            super.setTaskDescriptionColor(color);
        }
    }

    /**
     * Whether the status bar, navigation bar, and task colors follow the content rather than the player.
     * The player only takes them over when it is expanded over the content; the pane leaves them to the content.
     */
    private boolean contentOwnsSystemBars() {
        return isPaneMode() || getPanelState() == SlidingUpPanelLayout.PanelState.COLLAPSED;
    }
}
