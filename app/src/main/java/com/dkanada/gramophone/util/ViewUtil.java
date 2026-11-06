package com.dkanada.gramophone.util;

import android.animation.Animator;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.animation.PathInterpolator;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

import com.dkanada.gramophone.R;
import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView;

public class ViewUtil {
    public final static int PHONOGRAPH_ANIM_TIME = 1000;

    public static Animator createBackgroundColorTransition(final View v, @ColorInt final int startColor, @ColorInt final int endColor) {
        return createColorAnimator(v, "backgroundColor", startColor, endColor);
    }

    public static Animator createTextColorTransition(final TextView v, @ColorInt final int startColor, @ColorInt final int endColor) {
        return createColorAnimator(v, "textColor", startColor, endColor);
    }

    private static Animator createColorAnimator(Object target, String propertyName, @ColorInt int startColor, @ColorInt int endColor) {
        ObjectAnimator animator;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            animator = ObjectAnimator.ofArgb(target, propertyName, startColor, endColor);
        } else {
            animator = ObjectAnimator.ofInt(target, propertyName, startColor, endColor);
            animator.setEvaluator(new ArgbEvaluator());
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            animator.setInterpolator(new PathInterpolator(0.4f, 0f, 1f, 1f));
        }

        animator.setDuration(PHONOGRAPH_ANIM_TIME);
        return animator;
    }

    public static boolean hitTest(View v, int x, int y) {
        final int tx = (int) (v.getTranslationX() + 0.5f);
        final int ty = (int) (v.getTranslationY() + 0.5f);
        final int left = v.getLeft() + tx;
        final int right = v.getRight() + tx;
        final int top = v.getTop() + ty;
        final int bottom = v.getBottom() + ty;

        return (x >= left) && (x <= right) && (y >= top) && (y <= bottom);
    }

    public static void setUpFastScrollRecyclerViewColor(Context context, FastScrollRecyclerView recyclerView) {
        int primaryColor = ThemeUtil.getColorResource(context, R.attr.colorPrimary);

        recyclerView.setPopupBgColor(primaryColor);
        recyclerView.setPopupTextColor(ThemeUtil.getColorResource(context, R.attr.colorOnPrimary));
        recyclerView.setThumbColor(primaryColor);
        recyclerView.setTrackColor(ThemeUtil.getColorResource(context, R.attr.colorControlNormal, 40));
    }

    public static float convertDpToPixel(float dp, Resources resources) {
        DisplayMetrics metrics = resources.getDisplayMetrics();
        return dp * metrics.density;
    }

    public static float convertPixelsToDp(float px, Resources resources) {
        DisplayMetrics metrics = resources.getDisplayMetrics();
        return px / metrics.density;
    }

    /**
     * Centers the content of a full-width scrolling view in a column no wider than
     * {@code max_content_width} by growing its horizontal padding. The view keeps its full-width
     * background and can still be scrolled from the margins. Works for any width the view gets,
     * including split-screen and a content area that is narrower than the window.
     */
    public static void capContentWidth(@NonNull View view) {
        final int maxWidth = view.getResources().getDimensionPixelSize(R.dimen.max_content_width);
        final int baseLeft = view.getPaddingLeft();
        final int baseRight = view.getPaddingRight();

        view.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            int inset = Math.max(0, (right - left - baseLeft - baseRight - maxWidth) / 2);
            if (v.getPaddingLeft() != baseLeft + inset || v.getPaddingRight() != baseRight + inset) {
                v.setPadding(baseLeft + inset, v.getPaddingTop(), baseRight + inset, v.getPaddingBottom());
            }
        });
    }
}
