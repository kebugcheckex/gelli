package com.dkanada.gramophone.util;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.content.Context;
import android.content.res.TypedArray;
import android.view.ContextThemeWrapper;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.core.content.ContextCompat;
import androidx.palette.graphics.Palette;

import com.dkanada.gramophone.R;
import com.dkanada.gramophone.model.ColorPreset;
import com.dkanada.gramophone.model.Theme;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.MaterialColors;

import java.util.Collections;
import java.util.Comparator;

public class ThemeUtil {
    public static void applyTheme(Activity activity, Theme theme, ColorPreset preset) {
        activity.setTheme(theme.style);

        // the dynamic overlay comes from the base theme, so it follows light or dark without any help
        if (preset == ColorPreset.WALLPAPER) DynamicColors.applyToActivityIfAvailable(activity);
        if (preset != ColorPreset.NEUTRAL) activity.getTheme().applyStyle(preset.overlay(theme), true);
        if (theme == Theme.BLACK) activity.getTheme().applyStyle(blackOverlay(preset), true);
    }

    // for code without an activity to read the colors from
    public static Context themedContext(Context context) {
        PreferenceUtil preferences = PreferenceUtil.getInstance(context);

        return themedContext(context, preferences.getTheme(), preferences.getColorPreset());
    }

    public static Context themedContext(Context context, Theme theme, ColorPreset preset) {
        Context themed = new ContextThemeWrapper(context, theme.style);

        if (preset == ColorPreset.WALLPAPER) themed = DynamicColors.wrapContextIfAvailable(themed);
        if (preset != ColorPreset.NEUTRAL) themed.getTheme().applyStyle(preset.overlay(theme), true);
        if (theme == Theme.BLACK) themed.getTheme().applyStyle(blackOverlay(preset), true);

        return themed;
    }

    @StyleRes
    private static int blackOverlay(ColorPreset preset) {
        return preset == ColorPreset.NEUTRAL ? R.style.ThemeOverlay_Gelli_Black_Neutral : R.style.ThemeOverlay_Gelli_Black;
    }

    public static ColorStateList getColorStateList(int normal, int active) {
        int[][] states = new int[][]{
            new int[]{-android.R.attr.state_checked},
            new int[]{android.R.attr.state_checked}
        };

        int[] colors = new int[]{
            normal,
            active
        };

        return new ColorStateList(states, colors);
    }

    @ColorInt
    public static int getColor(@Nullable Palette palette, int fallback) {
        if (palette != null) {
            if (palette.getVibrantSwatch() != null) {
                return palette.getVibrantSwatch().getRgb();
            } else if (palette.getMutedSwatch() != null) {
                return palette.getMutedSwatch().getRgb();
            } else if (palette.getDarkVibrantSwatch() != null) {
                return palette.getDarkVibrantSwatch().getRgb();
            } else if (palette.getDarkMutedSwatch() != null) {
                return palette.getDarkMutedSwatch().getRgb();
            } else if (palette.getLightVibrantSwatch() != null) {
                return palette.getLightVibrantSwatch().getRgb();
            } else if (palette.getLightMutedSwatch() != null) {
                return palette.getLightMutedSwatch().getRgb();
            } else if (!palette.getSwatches().isEmpty()) {
                return Collections.max(palette.getSwatches(), SwatchComparator.getInstance()).getRgb();
            }
        }

        return fallback;
    }

    @ColorInt
    public static int getPrimaryTextColor(Context context, boolean light) {
        return light
            ? ContextCompat.getColor(context, R.color.color_text_primary_light)
            : ContextCompat.getColor(context, R.color.color_text_primary_dark);
    }

    @ColorInt
    public static int getSecondaryTextColor(Context context, boolean light) {
        return light
            ? ContextCompat.getColor(context, R.color.color_text_secondary_light)
            : ContextCompat.getColor(context, R.color.color_text_secondary_dark);
    }

    @ColorInt
    public static int getPrimaryTextColor(Context context, int color) {
        return getPrimaryTextColor(context, MaterialColors.isColorLight(color));
    }

    @ColorInt
    public static int getSecondaryTextColor(Context context, int color) {
        return getSecondaryTextColor(context, MaterialColors.isColorLight(color));
    }

    @ColorInt
    public static int getColorResource(Context context, int resource) {
        return getColorResource(context, resource, 255);
    }

    @ColorInt
    public static int getColorResource(Context context, int resource, int alpha) {
        TypedArray array = context.obtainStyledAttributes(new int[]{resource});
        int color = array.getColor(0, ContextCompat.getColor(context, android.R.color.white));

        array.recycle();

        return MaterialColors.compositeARGBWithAlpha(color, alpha);
    }

    @ColorInt
    public static int getColorAlpha(Context context, int color, int alpha) {
        return MaterialColors.compositeARGBWithAlpha(ContextCompat.getColor(context, color), alpha);
    }

    private static class SwatchComparator implements Comparator<Palette.Swatch> {
        private static SwatchComparator sInstance;

        private static SwatchComparator getInstance() {
            if (sInstance == null) {
                sInstance = new SwatchComparator();
            }

            return sInstance;
        }

        @Override
        public int compare(Palette.Swatch lhs, Palette.Swatch rhs) {
            return lhs.getPopulation() - rhs.getPopulation();
        }
    }
}
