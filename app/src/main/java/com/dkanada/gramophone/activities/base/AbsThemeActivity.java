package com.dkanada.gramophone.activities.base;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.dkanada.gramophone.model.ColorPreset;
import com.dkanada.gramophone.model.Theme;
import com.dkanada.gramophone.util.NavigationUtil;
import com.dkanada.gramophone.util.ThemeUtil;
import com.dkanada.gramophone.R;
import com.dkanada.gramophone.util.PreferenceUtil;
import com.dkanada.gramophone.util.Util;
import com.google.android.material.color.MaterialColors;

public abstract class AbsThemeActivity extends AppCompatActivity {
    private Theme currentTheme;
    private ColorPreset currentPreset;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        currentTheme = PreferenceUtil.getInstance(this).getTheme();
        currentPreset = PreferenceUtil.getInstance(this).getColorPreset();

        // the fragments restored by super.onCreate inflate with this theme
        ThemeUtil.applyTheme(this, currentTheme, currentPreset);

        super.onCreate(savedInstanceState);

        setColor(ThemeUtil.getColorResource(this, R.attr.colorSurface));
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (PreferenceUtil.getInstance(this).getTheme() != currentTheme
                || PreferenceUtil.getInstance(this).getColorPreset() != currentPreset) {
            NavigationUtil.recreateMain(this);
        }
    }

    public void setColor(int color) {
        boolean light = MaterialColors.isColorLight(color);

        // this replaces every system UI flag, so the light flags have to come after it
        setDrawUnderStatusBar();
        setLightStatusBar(light);
        setLightNavigationBar(light);

        setStatusBarColor(color);
        setTaskDescriptionColor(color);
        setNavigationBarColor(color);
    }

    private void setDrawUnderStatusBar() {
        Util.setAllowDrawUnderStatusBar(getWindow());
    }

    public void setStatusBarColor(int color) {
        View statusBar = getWindow().getDecorView().getRootView().findViewById(R.id.status_bar);

        if (statusBar != null) {
            statusBar.setBackgroundColor(color);
            getWindow().setStatusBarColor(Color.TRANSPARENT);
        } else {
            getWindow().setStatusBarColor(color);
        }
    }

    public void setTaskDescriptionColor(int color) {
        int solid = MaterialColors.compositeARGBWithAlpha(color, 255);

        setTaskDescription(new ActivityManager.TaskDescription(getTitle().toString(), null, solid));
    }

    public void setNavigationBarColor(int color) {
        getWindow().setNavigationBarColor(color);
    }

    @SuppressLint("InlinedApi")
    public void setLightStatusBar(boolean enabled) {
        View view = getWindow().getDecorView();

        int flags = enabled
            ? view.getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            : view.getSystemUiVisibility() & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;

        view.setSystemUiVisibility(flags);
    }

    @SuppressLint("InlinedApi")
    public void setLightNavigationBar(boolean enabled) {
        View view = getWindow().getDecorView();

        int flags = enabled
            ? view.getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            : view.getSystemUiVisibility() & ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;

        view.setSystemUiVisibility(flags);
    }
}
