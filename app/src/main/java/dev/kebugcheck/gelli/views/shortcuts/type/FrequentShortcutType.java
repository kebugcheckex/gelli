package dev.kebugcheck.gelli.views.shortcuts.type;

import android.content.Context;
import android.content.pm.ShortcutInfo;

import dev.kebugcheck.gelli.R;
import dev.kebugcheck.gelli.views.shortcuts.AppShortcutIconGenerator;
import dev.kebugcheck.gelli.views.shortcuts.AppShortcutLauncherActivity;

public final class FrequentShortcutType extends BaseShortcutType {
    public FrequentShortcutType(Context context) {
        super(context);
    }

    public static String getId() {
        return PREFIX + ".frequent";
    }

    @Override
    public ShortcutInfo getShortcutInfo() {
        return new ShortcutInfo.Builder(context, getId())
            .setShortLabel(context.getString(R.string.my_top_tracks))
            .setIcon(AppShortcutIconGenerator.generateThemedIcon(context, R.drawable.ic_app_shortcut_top_tracks))
            .setIntent(getPlaySongsIntent(AppShortcutLauncherActivity.SHORTCUT_TYPE_FREQUENT))
            .build();
    }
}
