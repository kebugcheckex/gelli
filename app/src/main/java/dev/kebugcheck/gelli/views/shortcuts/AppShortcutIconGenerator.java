package dev.kebugcheck.gelli.views.shortcuts;

import android.content.Context;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;

import androidx.core.graphics.drawable.IconCompat;

import dev.kebugcheck.gelli.R;
import dev.kebugcheck.gelli.util.ImageUtil;
import dev.kebugcheck.gelli.util.PreferenceUtil;
import dev.kebugcheck.gelli.util.ThemeUtil;

public final class AppShortcutIconGenerator {
    public static Icon generateThemedIcon(Context context, int iconId) {
        if (PreferenceUtil.getInstance(context).getColoredShortcuts()) {
            return generateUserThemedIcon(context, iconId).toIcon(context);
        } else {
            return generateDefaultThemedIcon(context, iconId).toIcon(context);
        }
    }

    private static IconCompat generateDefaultThemedIcon(Context context, int iconId) {
        // return icon of iconId with default colors
        return generateThemedIcon(context, iconId,
                context.getColor(R.color.color_shortcut_foreground),
                context.getColor(R.color.color_shortcut_background)
        );
    }

    private static IconCompat generateUserThemedIcon(Context context, int iconId) {
        // the shortcut manager runs from the application context too, which doesn't have the theme
        Context themed = ThemeUtil.themedContext(context);

        return generateThemedIcon(context, iconId,
                ThemeUtil.getColorResource(themed, R.attr.colorOnPrimaryContainer),
                ThemeUtil.getColorResource(themed, R.attr.colorPrimaryContainer)
        );
    }

    private static IconCompat generateThemedIcon(Context context, int iconId, int foregroundColor, int backgroundColor) {
        // Get and tint foreground and background drawables
        Drawable vectorDrawable = ImageUtil.getTintedVectorDrawable(context, iconId, foregroundColor);
        Drawable backgroundDrawable = ImageUtil.getTintedVectorDrawable(context, R.drawable.ic_app_shortcut_background, backgroundColor);

        AdaptiveIconDrawable adaptiveIconDrawable = new AdaptiveIconDrawable(backgroundDrawable, vectorDrawable);
        return IconCompat.createWithAdaptiveBitmap(ImageUtil.createBitmap(adaptiveIconDrawable));
    }

}
