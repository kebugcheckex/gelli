package com.dkanada.gramophone.views;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.dkanada.gramophone.util.ViewUtil;
import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView;

/**
 * A fast scroll list whose drag thumb can be switched off, for when a {@link SectionIndexBar} takes
 * its place on the same edge. The library has no switch of its own, so a disabled thumb is drawn
 * transparent and ignores touches.
 */
public class LibraryRecyclerView extends FastScrollRecyclerView {
    private boolean fastScrollEnabled = true;

    public LibraryRecyclerView(@NonNull Context context) {
        super(context);
    }

    public LibraryRecyclerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public LibraryRecyclerView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setFastScrollEnabled(boolean enabled) {
        if (enabled == fastScrollEnabled) return;

        fastScrollEnabled = enabled;
        if (enabled) {
            ViewUtil.setUpFastScrollRecyclerViewColor(getContext(), this);
        } else {
            setThumbColor(Color.TRANSPARENT);
            setTrackColor(Color.TRANSPARENT);
        }

        invalidate();
    }

    @Override
    public boolean onInterceptTouchEvent(@NonNull RecyclerView recyclerView, @NonNull MotionEvent event) {
        return fastScrollEnabled && super.onInterceptTouchEvent(recyclerView, event);
    }

    @Override
    public void onTouchEvent(@NonNull RecyclerView recyclerView, @NonNull MotionEvent event) {
        if (fastScrollEnabled) super.onTouchEvent(recyclerView, event);
    }
}
