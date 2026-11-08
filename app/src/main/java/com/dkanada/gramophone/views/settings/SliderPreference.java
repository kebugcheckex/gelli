package com.dkanada.gramophone.views.settings;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.dkanada.gramophone.R;
import com.google.android.material.slider.Slider;

/**
 * A percentage on a stepped slider. SeekBarPreference has no steps for touch, so a drag could land between them.
 */
public class SliderPreference extends Preference {
    private final int valueFrom;
    private final int valueTo;
    private final int stepSize;

    private int value;

    public SliderPreference(@NonNull Context context, AttributeSet attrs) {
        super(context, attrs);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SliderPreference);
        valueFrom = (int) a.getFloat(R.styleable.SliderPreference_android_valueFrom, 0);
        valueTo = (int) a.getFloat(R.styleable.SliderPreference_android_valueTo, 100);
        stepSize = (int) a.getFloat(R.styleable.SliderPreference_android_stepSize, 1);
        a.recycle();

        setLayoutResource(R.layout.preference_row_slider);
        // the slider takes the touches, a tap on the row has nothing to do
        setSelectable(false);
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        Slider slider = (Slider) holder.findViewById(R.id.slider);
        TextView label = (TextView) holder.findViewById(R.id.slider_value);

        slider.clearOnChangeListeners();
        slider.setValueFrom(valueFrom);
        slider.setValueTo(valueTo);
        slider.setStepSize(stepSize);
        slider.setValue(value);
        label.setText(format(value));

        slider.addOnChangeListener((s, v, fromUser) -> {
            int newValue = (int) v;
            label.setText(format(newValue));
            if (fromUser && newValue != value && callChangeListener(newValue)) {
                setValue(newValue);
            }
        });
    }

    @Override
    protected Object onGetDefaultValue(@NonNull TypedArray a, int index) {
        return a.getInt(index, valueFrom);
    }

    @Override
    protected void onSetInitialValue(Object defaultValue) {
        setValue(getPersistedInt(defaultValue == null ? valueFrom : (Integer) defaultValue));
    }

    public void setValue(int value) {
        // the slider throws on a value off its range or between its steps
        int clamped = Math.max(valueFrom, Math.min(valueTo, value));
        this.value = valueFrom + Math.round((clamped - valueFrom) / (float) stepSize) * stepSize;
        persistInt(this.value);
    }

    private static String format(int value) {
        return value + "%";
    }
}
