package com.dkanada.gramophone.helper;

import android.content.Context;
import android.view.ViewGroup;

import com.dkanada.gramophone.R;

public class HorizontalAdapterHelper {
    public static final int LAYOUT_RES = R.layout.item_grid_card_horizontal;

    public static final int TYPE_FIRST = 1;
    public static final int TYPE_MIDDLE = 2;
    public static final int TYPE_LAST = 3;

    public static void applyMarginToLayoutParams(Context context, ViewGroup.MarginLayoutParams layoutParams, int viewType) {
        int listMargin = context.getResources().getDimensionPixelSize(R.dimen.default_item_margin);

        // the tile pads its cover by half the margin, so this lines the first cover up with the rest of the screen
        layoutParams.leftMargin = viewType == TYPE_FIRST ? listMargin / 2 : 0;
        layoutParams.rightMargin = viewType == TYPE_LAST ? listMargin / 2 : 0;
    }

    public static int getItemViewType(int position, int itemCount) {
        if (position == 0) {
            return TYPE_FIRST;
        } else if (position == itemCount - 1) {
            return TYPE_LAST;
        } else {
            return TYPE_MIDDLE;
        }
    }
}
