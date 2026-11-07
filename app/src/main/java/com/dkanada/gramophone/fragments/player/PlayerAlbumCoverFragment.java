package com.dkanada.gramophone.fragments.player;

import android.animation.Animator;
import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;

import com.dkanada.gramophone.databinding.FragmentPlayerAlbumCoverBinding;
import com.dkanada.gramophone.fragments.AbsMusicServiceFragment;
import com.dkanada.gramophone.glide.CustomGlideRequest;
import com.dkanada.gramophone.glide.CustomPaletteTarget;
import com.dkanada.gramophone.helper.MusicPlayerRemote;
import com.dkanada.gramophone.interfaces.base.SimpleAnimatorListener;
import com.dkanada.gramophone.model.Song;
import com.dkanada.gramophone.util.ViewUtil;

import java.util.Objects;

/**
 * The current song's cover. It doesn't page through the queue, so a swipe never changes the song.
 */
public class PlayerAlbumCoverFragment extends AbsMusicServiceFragment {

    public static final int VISIBILITY_ANIM_DURATION = 300;

    private FragmentPlayerAlbumCoverBinding binding;

    private Callbacks callbacks;

    // the metadata callback also fires for a favorite toggle, which shouldn't reload the cover
    private String loadedId;
    private String loadedImage;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentPlayerAlbumCoverBinding.inflate(inflater);

        return binding.getRoot();
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // a click listener would also take a drag that ends on the cover as a tap
        GestureDetector gestureDetector = new GestureDetector(requireContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(@NonNull MotionEvent e) {
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
                if (callbacks != null) callbacks.onToolbarToggled();
                return true;
            }
        });

        binding.playerImage.setOnTouchListener((v, event) -> gestureDetector.onTouchEvent(event));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        loadedId = null;
        loadedImage = null;
    }

    @Override
    public void onServiceConnected() {
        updateCover();
    }

    @Override
    public void onPlayMetadataChanged() {
        updateCover();
    }

    @Override
    public void onQueueChanged() {
        updateCover();
    }

    private void updateCover() {
        Song song = MusicPlayerRemote.getCurrentSong();
        if (song == null) return;
        if (Objects.equals(song.id, loadedId) && Objects.equals(song.primary, loadedImage)) return;

        loadedId = song.id;
        loadedImage = song.primary;

        // a new load into the same view cancels the last one, so a late color can't come from an older song
        CustomGlideRequest.Builder
                .from(requireContext(), song.primary, song.blurHash)
                .palette().build()
                .into(new CustomPaletteTarget(binding.playerImage) {
                    @Override
                    public void onColorReady(int color) {
                        if (callbacks != null) callbacks.onColorChanged(color);
                    }
                });
    }

    public void showHeartAnimation() {
        binding.playerFavoriteIcon.clearAnimation();

        binding.playerFavoriteIcon.setAlpha(0f);
        binding.playerFavoriteIcon.setScaleX(0f);
        binding.playerFavoriteIcon.setScaleY(0f);
        binding.playerFavoriteIcon.setVisibility(View.VISIBLE);
        binding.playerFavoriteIcon.setPivotX(binding.playerFavoriteIcon.getWidth() / 2f);
        binding.playerFavoriteIcon.setPivotY(binding.playerFavoriteIcon.getHeight() / 2f);

        binding.playerFavoriteIcon.animate()
                .setDuration(ViewUtil.PHONOGRAPH_ANIM_TIME / 2)
                .setInterpolator(new DecelerateInterpolator())
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setListener(new SimpleAnimatorListener() {
                    @Override
                    public void onAnimationCancel(Animator animation) {
                        binding.playerFavoriteIcon.setVisibility(View.INVISIBLE);
                    }
                })
                .withEndAction(() -> binding.playerFavoriteIcon.animate()
                        .setDuration(ViewUtil.PHONOGRAPH_ANIM_TIME / 2)
                        .setInterpolator(new AccelerateInterpolator())
                        .scaleX(0f)
                        .scaleY(0f)
                        .alpha(0f)
                        .start())
                .start();
    }

    public void setCallbacks(Callbacks listener) {
        callbacks = listener;
    }

    public interface Callbacks {
        void onColorChanged(int color);

        void onFavoriteToggled();

        void onToolbarToggled();
    }
}
