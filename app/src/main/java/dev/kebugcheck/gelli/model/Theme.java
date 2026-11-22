package dev.kebugcheck.gelli.model;

import androidx.annotation.StyleRes;

import dev.kebugcheck.gelli.R;

public enum Theme {
    LIGHT(R.style.Theme_Phonograph_Light),
    DARK(R.style.Theme_Phonograph),
    // ThemeUtil puts the black surfaces on top of the dark theme, after the color preset
    BLACK(R.style.Theme_Phonograph);

    @StyleRes
    public final int style;

    Theme(int style) {
        this.style = style;
    }
}
