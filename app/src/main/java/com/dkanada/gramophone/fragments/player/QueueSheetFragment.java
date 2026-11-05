package com.dkanada.gramophone.fragments.player;

import android.graphics.PorterDuff;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.dkanada.gramophone.R;
import com.dkanada.gramophone.activities.base.AbsMusicPanelActivity;
import com.dkanada.gramophone.adapter.song.PlayingQueueAdapter;
import com.dkanada.gramophone.databinding.FragmentQueueSheetBinding;
import com.dkanada.gramophone.fragments.AbsMusicServiceFragment;
import com.dkanada.gramophone.helper.MusicPlayerRemote;
import com.dkanada.gramophone.util.MusicUtil;
import com.dkanada.gramophone.util.ThemeUtil;
import com.h6ah4i.android.widget.advrecyclerview.animator.RefactoredDefaultItemAnimator;
import com.h6ah4i.android.widget.advrecyclerview.draggable.RecyclerViewDragDropManager;
import com.h6ah4i.android.widget.advrecyclerview.utils.WrapperAdapterUtils;

/**
 * The playing queue as a side sheet at the expanded width. Played songs stay above the current one,
 * dimmed, and the list scrolls so the current song is at the top.
 */
public class QueueSheetFragment extends AbsMusicServiceFragment {
    private FragmentQueueSheetBinding binding;

    private LinearLayoutManager layoutManager;
    private PlayingQueueAdapter playingQueueAdapter;
    private RecyclerView.Adapter<?> wrappedAdapter;
    private RecyclerViewDragDropManager recyclerViewDragDropManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentQueueSheetBinding.inflate(inflater, container, false);

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.queueSheetCloseButton.setColorFilter(ThemeUtil.getColorResource(requireContext(), R.attr.colorOnSurfaceVariant), PorterDuff.Mode.SRC_IN);
        binding.queueSheetCloseButton.setOnClickListener(v -> ((AbsMusicPanelActivity) requireActivity()).setQueueSheetOpen(false));

        recyclerViewDragDropManager = new RecyclerViewDragDropManager();
        playingQueueAdapter = new PlayingQueueAdapter(
                (AppCompatActivity) requireActivity(),
                MusicPlayerRemote.getPlayingQueue(),
                MusicPlayerRemote.getPosition(),
                R.layout.item_list,
                false,
                null);
        // the sheet has no separate current song row, so the current song stays at full strength
        playingQueueAdapter.setDimCurrent(false);
        wrappedAdapter = recyclerViewDragDropManager.createWrappedAdapter(playingQueueAdapter);

        layoutManager = new LinearLayoutManager(requireContext());
        binding.queueSheetRecyclerView.setLayoutManager(layoutManager);
        binding.queueSheetRecyclerView.setAdapter(wrappedAdapter);
        binding.queueSheetRecyclerView.setItemAnimator(new RefactoredDefaultItemAnimator());

        recyclerViewDragDropManager.attachRecyclerView(binding.queueSheetRecyclerView);
        scrollToCurrent();
    }

    @Override
    public void onPause() {
        if (recyclerViewDragDropManager != null) {
            recyclerViewDragDropManager.cancelDrag();
        }

        super.onPause();
    }

    @Override
    public void onDestroyView() {
        if (recyclerViewDragDropManager != null) {
            recyclerViewDragDropManager.release();
            recyclerViewDragDropManager = null;
        }

        binding.queueSheetRecyclerView.setItemAnimator(null);
        binding.queueSheetRecyclerView.setAdapter(null);

        if (wrappedAdapter != null) {
            WrapperAdapterUtils.releaseAll(wrappedAdapter);
            wrappedAdapter = null;
        }

        playingQueueAdapter = null;
        layoutManager = null;
        binding = null;

        super.onDestroyView();
    }

    @Override
    public void onServiceConnected() {
        updateQueue();
        scrollToCurrent();
    }

    // a reorder or removal changes the queue too, so this keeps the scroll position
    @Override
    public void onQueueChanged() {
        updateQueue();
    }

    @Override
    public void onPlayMetadataChanged() {
        playingQueueAdapter.setCurrent(MusicPlayerRemote.getPosition());
        updateSubtitle();
        scrollToCurrent();
    }

    private void updateQueue() {
        playingQueueAdapter.swapDataSet(MusicPlayerRemote.getPlayingQueue(), MusicPlayerRemote.getPosition());
        updateSubtitle();
    }

    private void updateSubtitle() {
        long duration = MusicPlayerRemote.getQueueDurationMillis(MusicPlayerRemote.getPosition());
        binding.queueSheetSubtitle.setText(MusicUtil.buildInfoString(getString(R.string.up_next), MusicUtil.getReadableDurationString(duration)));
    }

    private void scrollToCurrent() {
        // leave a drag in progress alone, it would jump under the finger
        if (recyclerViewDragDropManager != null && recyclerViewDragDropManager.isDragging()) return;

        binding.queueSheetRecyclerView.stopScroll();
        layoutManager.scrollToPositionWithOffset(MusicPlayerRemote.getPosition(), 0);
    }
}
