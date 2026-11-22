package dev.kebugcheck.gelli.views.shortcuts.type;

import android.content.Context;
import android.content.pm.ShortcutInfo;

import dev.kebugcheck.gelli.R;
import dev.kebugcheck.gelli.views.shortcuts.AppShortcutIconGenerator;
import dev.kebugcheck.gelli.views.shortcuts.AppShortcutLauncherActivity;

public final class LatestShortcutType extends BaseShortcutType {
    public LatestShortcutType(Context context) {
        super(context);
    }

    public static String getId() {
        return PREFIX + ".latest";
    }

    @Override
    public ShortcutInfo getShortcutInfo() {
        return new ShortcutInfo.Builder(context, getId())
            .setShortLabel(context.getString(R.string.last_added))
            .setIcon(AppShortcutIconGenerator.generateThemedIcon(context, R.drawable.ic_app_shortcut_last_added))
            .setIntent(getPlaySongsIntent(AppShortcutLauncherActivity.SHORTCUT_TYPE_LATEST))
            .build();
    }
}
