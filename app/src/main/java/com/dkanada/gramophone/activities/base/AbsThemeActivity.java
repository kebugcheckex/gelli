package com.dkanada.gramophone.activities.base;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.dkanada.gramophone.util.NavigationUtil;
import com.dkanada.gramophone.util.ThemeUtil;
import com.dkanada.gramophone.R;
import com.dkanada.gramophone.util.PreferenceUtil;
import com.dkanada.gramophone.util.Util;
import com.google.android.material.color.MaterialColors;

public abstract class AbsThemeActivity extends AppCompatActivity {
    private int currentTheme;
    private int primaryColor;
    private int accentColor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTheme(PreferenceUtil.getInstance(this).getTheme().style);
        setColor(ThemeUtil.getColorResource(this, R.attr.colorSurface));

        primaryColor = PreferenceUtil.getInstance(this).getPrimaryColor();
        accentColor = PreferenceUtil.getInstance(this).getAccentColor();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (PreferenceUtil.getInstance(this).getTheme().style != currentTheme
                || PreferenceUtil.getInstance(this).getPrimaryColor() != primaryColor
                || PreferenceUtil.getInstance(this).getAccentColor() != accentColor) {
            NavigationUtil.recreateMain(this);
        }
    }

    @Override
    public void setTheme(int resId) {
        super.setTheme(resId);

        currentTheme = resId;
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Util.setAllowDrawUnderStatusBar(getWindow());
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            Util.setStatusBarTranslucent(getWindow());
        }
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setTaskDescription(new ActivityManager.TaskDescription(getTitle().toString(), null, solid));
        }
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
