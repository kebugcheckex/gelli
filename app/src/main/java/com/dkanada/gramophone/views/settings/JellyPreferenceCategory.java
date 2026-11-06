package com.dkanada.gramophone.views.settings;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.PreferenceCategory;

import com.dkanada.gramophone.R;

public class JellyPreferenceCategory extends PreferenceCategory {
    public JellyPreferenceCategory(Context context, AttributeSet attrs) {
        super(context, attrs);

        setLayoutResource(R.layout.preference_category);
    }
}
