package dev.kebugcheck.gelli.views.shortcuts.type;

import android.content.Context;
import android.content.pm.ShortcutInfo;

import dev.kebugcheck.gelli.R;
import dev.kebugcheck.gelli.views.shortcuts.AppShortcutIconGenerator;
import dev.kebugcheck.gelli.views.shortcuts.AppShortcutLauncherActivity;

public final class ShuffleShortcutType extends BaseShortcutType {
    public ShuffleShortcutType(Context context) {
        super(context);
    }

    public static String getId() {
        return PREFIX + ".shuffle";
    }

    @Override
    public ShortcutInfo getShortcutInfo() {
        return new ShortcutInfo.Builder(context, getId())
            .setShortLabel(context.getString(R.string.action_shuffle))
            .setIcon(AppShortcutIconGenerator.generateThemedIcon(context, R.drawable.ic_app_shortcut_shuffle_all))
            .setIntent(getPlaySongsIntent(AppShortcutLauncherActivity.SHORTCUT_TYPE_SHUFFLE))
            .build();
    }
}
