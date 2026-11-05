package com.dkanada.gramophone.fragments.player;

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
import com.dkanada.gramophone.adapter.song.PlayingQueueAdapter;
import com.dkanada.gramophone.fragments.AbsMusicServiceFragment;
import com.dkanada.gramophone.helper.MusicPlayerRemote;
import com.h6ah4i.android.widget.advrecyclerview.animator.RefactoredDefaultItemAnimator;
import com.h6ah4i.android.widget.advrecyclerview.draggable.RecyclerViewDragDropManager;
import com.h6ah4i.android.widget.advrecyclerview.utils.WrapperAdapterUtils;

/**
 * The whole playing queue as a plain list with drag-to-reorder, for the queue sheet and the landscape player.
 * Played songs stay above the current one, dimmed, and the list scrolls so the current song is at the top.
 * The list inflates with its container's context, so a themed parent (the dark landscape player) styles the rows.
 */
public class PlayingQueueListFragment extends AbsMusicServiceFragment {
    private RecyclerView recyclerView;
    private LinearLayoutManager layoutManager;
    private PlayingQueueAdapter playingQueueAdapter;
    private RecyclerView.Adapter<?> wrappedAdapter;
    private RecyclerViewDragDropManager recyclerViewDragDropManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        LayoutInflater themedInflater = container != null ? inflater.cloneInContext(container.getContext()) : inflater;
        recyclerView = (RecyclerView) themedInflater.inflate(R.layout.fragment_playing_queue_list, container, false);

        return recyclerView;
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerViewDragDropManager = new RecyclerViewDragDropManager();
        playingQueueAdapter = new PlayingQueueAdapter(
                (AppCompatActivity) requireActivity(),
                MusicPlayerRemote.getPlayingQueue(),
                MusicPlayerRemote.getPosition(),
                R.layout.item_list,
                false,
                null);
        // there is no separate current song row, so the current song stays at full strength
        playingQueueAdapter.setDimCurrent(false);
        wrappedAdapter = recyclerViewDragDropManager.createWrappedAdapter(playingQueueAdapter);

        layoutManager = new LinearLayoutManager(requireContext());
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(wrappedAdapter);
        recyclerView.setItemAnimator(new RefactoredDefaultItemAnimator());

        recyclerViewDragDropManager.attachRecyclerView(recyclerView);
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

        recyclerView.setItemAnimator(null);
        recyclerView.setAdapter(null);

        if (wrappedAdapter != null) {
            WrapperAdapterUtils.releaseAll(wrappedAdapter);
            wrappedAdapter = null;
        }

        playingQueueAdapter = null;
        layoutManager = null;
        recyclerView = null;

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
        if (playingQueueAdapter == null) return;

        playingQueueAdapter.setCurrent(MusicPlayerRemote.getPosition());
        scrollToCurrent();
    }

    private void updateQueue() {
        if (playingQueueAdapter == null) return;

        playingQueueAdapter.swapDataSet(MusicPlayerRemote.getPlayingQueue(), MusicPlayerRemote.getPosition());
    }

    public void scrollToCurrent() {
        if (recyclerView == null) return;
        // leave a drag in progress alone, it would jump under the finger
        if (recyclerViewDragDropManager != null && recyclerViewDragDropManager.isDragging()) return;

        recyclerView.stopScroll();
        layoutManager.scrollToPositionWithOffset(MusicPlayerRemote.getPosition(), 0);
    }
}
