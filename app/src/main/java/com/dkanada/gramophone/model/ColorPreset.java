package com.dkanada.gramophone.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.StyleRes;

import com.dkanada.gramophone.R;

import java.util.ArrayList;
import java.util.List;

public enum ColorPreset {
    // the grey roles of the base themes, with nothing on top
    NEUTRAL(R.string.color_preset_neutral, 0, 0),
    INDIGO(R.string.color_preset_indigo, R.style.ThemeOverlay_Gelli_Color_Indigo_Light, R.style.ThemeOverlay_Gelli_Color_Indigo_Dark),
    BLUE(R.string.color_preset_blue, R.style.ThemeOverlay_Gelli_Color_Blue_Light, R.style.ThemeOverlay_Gelli_Color_Blue_Dark),
    TEAL(R.string.color_preset_teal, R.style.ThemeOverlay_Gelli_Color_Teal_Light, R.style.ThemeOverlay_Gelli_Color_Teal_Dark),
    GREEN(R.string.color_preset_green, R.style.ThemeOverlay_Gelli_Color_Green_Light, R.style.ThemeOverlay_Gelli_Color_Green_Dark),
    AMBER(R.string.color_preset_amber, R.style.ThemeOverlay_Gelli_Color_Amber_Light, R.style.ThemeOverlay_Gelli_Color_Amber_Dark),
    ORANGE(R.string.color_preset_orange, R.style.ThemeOverlay_Gelli_Color_Orange_Light, R.style.ThemeOverlay_Gelli_Color_Orange_Dark),
    RED(R.string.color_preset_red, R.style.ThemeOverlay_Gelli_Color_Red_Light, R.style.ThemeOverlay_Gelli_Color_Red_Dark),
    PINK(R.string.color_preset_pink, R.style.ThemeOverlay_Gelli_Color_Pink_Light, R.style.ThemeOverlay_Gelli_Color_Pink_Dark),
    PURPLE(R.string.color_preset_purple, R.style.ThemeOverlay_Gelli_Color_Purple_Light, R.style.ThemeOverlay_Gelli_Color_Purple_Dark),
    BROWN(R.string.color_preset_brown, R.style.ThemeOverlay_Gelli_Color_Brown_Light, R.style.ThemeOverlay_Gelli_Color_Brown_Dark),
    // the system's dynamic colors, which ThemeUtil applies in place of an overlay
    WALLPAPER(R.string.color_preset_wallpaper, 0, 0);

    public static final ColorPreset DEFAULT = INDIGO;

    @StringRes
    public final int title;

    @StyleRes
    private final int lightOverlay;

    @StyleRes
    private final int darkOverlay;

    ColorPreset(int title, int lightOverlay, int darkOverlay) {
        this.title = title;
        this.lightOverlay = lightOverlay;
        this.darkOverlay = darkOverlay;
    }

    @StyleRes
    public int overlay(@NonNull Theme theme) {
        return theme == Theme.LIGHT ? lightOverlay : darkOverlay;
    }

    // a stored value can outlive its preset or come from a device with dynamic color, and this runs on every launch
    @NonNull
    public static ColorPreset parse(@Nullable String name, boolean dynamicColorAvailable) {
        for (ColorPreset preset : available(dynamicColorAvailable)) {
            if (preset.name().equals(name)) return preset;
        }

        return DEFAULT;
    }

    @NonNull
    public static List<ColorPreset> available(boolean dynamicColorAvailable) {
        List<ColorPreset> presets = new ArrayList<>();
        for (ColorPreset preset : values()) {
            if (preset != WALLPAPER || dynamicColorAvailable) presets.add(preset);
        }

        return presets;
    }
}
