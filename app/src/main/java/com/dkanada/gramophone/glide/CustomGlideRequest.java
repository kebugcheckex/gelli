package com.dkanada.gramophone.glide;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.bitmap.BitmapTransitionOptions;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.signature.ObjectKey;
import com.dkanada.gramophone.R;
import com.dkanada.gramophone.glide.palette.BitmapPaletteCrossFadeFactory;
import com.dkanada.gramophone.glide.palette.BitmapPaletteWrapper;
import com.dkanada.gramophone.util.JellyfinSdkSession;
import com.wolt.blurhashkt.BlurHashDecoder;

import static com.bumptech.glide.GenericTransitionOptions.with;

public class CustomGlideRequest {
    public static final DiskCacheStrategy DEFAULT_DISK_CACHE_STRATEGY = DiskCacheStrategy.ALL;
    public static final int DEFAULT_IMAGE = R.drawable.default_album_art;
    public static final int THEMED_IMAGE = R.drawable.album_art_placeholder;
    public static final int DEFAULT_DURATION = 200;

    public static class Builder {
        private final RequestManager requestManager;
        private final Object item;
        private final Context context;

        private Builder(Context context, String item, String placeholder) {
            // only an activity has the colors for the themed image, and a service can't decode a vector to a bitmap
            boolean themed = context.getTheme().resolveAttribute(R.attr.colorSurfaceContainerHighest, new TypedValue(), true);
            int image = themed ? THEMED_IMAGE : DEFAULT_IMAGE;

            this.requestManager = Glide.with(context);
            this.context = context;

            if (item != null) {
                this.item = createUrl(item);
            } else {
                // a null model goes straight to the fallback, which is drawn with the theme below
                this.item = themed ? null : DEFAULT_IMAGE;
            }

            RequestOptions options = new RequestOptions()
                    .error(image)
                    .fallback(image)
                    .theme(context.getTheme())
                    .diskCacheStrategy(DEFAULT_DISK_CACHE_STRATEGY)
                    .signature(new ObjectKey(item != null ? item : 0));

            if (placeholder != null) {
                Bitmap bitmap = BlurHashDecoder.INSTANCE.decode(placeholder, 40, 40, 1, true);
                options = options.placeholder(new BitmapDrawable(context.getResources(), bitmap));
            } else {
                options = options.placeholder(image);
            }

            requestManager.applyDefaultRequestOptions(options);
        }

        public static Builder from(Context context, String item, String placeholder) {
            return new Builder(context, item, placeholder);
        }

        public PaletteBuilder palette() {
            return new PaletteBuilder(this, this.context);
        }

        public BitmapBuilder bitmap() {
            return new BitmapBuilder(this);
        }

        public RequestBuilder<Drawable> build() {
            return requestManager.load(item)
                    .transition(DrawableTransitionOptions.withCrossFade(DEFAULT_DURATION));
        }
    }

    public static class BitmapBuilder {
        private final Builder builder;

        public BitmapBuilder(Builder builder) {
            this.builder = builder;
        }

        public RequestBuilder<Bitmap> build() {
            return builder.requestManager.asBitmap().load(builder.item)
                    .transition(BitmapTransitionOptions.withCrossFade(DEFAULT_DURATION));
        }
    }

    public static class PaletteBuilder {
        final Context context;
        private final Builder builder;

        public PaletteBuilder(Builder builder, Context context) {
            this.builder = builder;
            this.context = context;
        }

        public RequestBuilder<BitmapPaletteWrapper> build() {
            return builder.requestManager.as(BitmapPaletteWrapper.class).load(builder.item)
                    .transition(with(new BitmapPaletteCrossFadeFactory()));
        }
    }

    public static String createUrl(String item) {
        return JellyfinImageUrls.buildPrimaryImageUrl(item);
    }
}
