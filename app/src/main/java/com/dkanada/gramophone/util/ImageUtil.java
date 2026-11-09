package com.dkanada.gramophone.util;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;

import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.content.res.ResourcesCompat;

import java.io.InputStream;

public class ImageUtil {

    public static Bitmap resizeBitmap(@NonNull Bitmap src, int maxForSmallerSize) {
        int width = src.getWidth();
        int height = src.getHeight();

        final int dstWidth;
        final int dstHeight;

        if (width < height) {
            if (maxForSmallerSize >= width) {
                return src;
            }

            float ratio = (float) height / width;
            dstWidth = maxForSmallerSize;
            dstHeight = Math.round(maxForSmallerSize * ratio);
        } else {
            if (maxForSmallerSize >= height) {
                return src;
            }

            float ratio = (float) width / height;
            dstWidth = Math.round(maxForSmallerSize * ratio);
            dstHeight = maxForSmallerSize;
        }

        return Bitmap.createScaledBitmap(src, dstWidth, dstHeight, false);
    }

    public static Bitmap createBitmap(Drawable drawable) {
        return createBitmap(drawable, 1f);
    }

    public static Bitmap createBitmap(Drawable drawable, float sizeMultiplier) {
        Bitmap bitmap = Bitmap.createBitmap((int) (drawable.getIntrinsicWidth() * sizeMultiplier), (int) (drawable.getIntrinsicHeight() * sizeMultiplier), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bitmap);
        drawable.setBounds(0, 0, c.getWidth(), c.getHeight());
        drawable.draw(c);
        return bitmap;
    }

    public static Drawable getVectorDrawable(@NonNull Resources res, @DrawableRes int resId, @Nullable Resources.Theme theme) {
        return ResourcesCompat.getDrawable(res, resId, theme);
    }

    public static Drawable getTintedVectorDrawable(@NonNull Context context, @DrawableRes int resId, @ColorInt int color) {
        Drawable drawable = getVectorDrawable(context.getResources(), resId, context.getTheme());

        DrawableCompat.setTintMode(drawable, PorterDuff.Mode.SRC_IN);
        DrawableCompat.setTint(drawable, color);

        return drawable;
    }

    public static Drawable getVectorDrawable(@NonNull Context context, @DrawableRes int resId) {
        return getVectorDrawable(context.getResources(), resId, context.getTheme());
    }

    public static Drawable resolveDrawable(@NonNull Context context, @AttrRes int drawableAttr) {
        TypedArray a = context.obtainStyledAttributes(new int[]{drawableAttr});
        Drawable drawable = a.getDrawable(0);
        a.recycle();
        return drawable;
    }

    public static Bitmap resize(InputStream stream, int scaledWidth, int scaledHeight) {
        final Bitmap bitmap = BitmapFactory.decodeStream(stream);

        return Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true);
    }
}
