package com.dkanada.gramophone.fragments.player;

import android.graphics.PorterDuff;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.dkanada.gramophone.R;
import com.dkanada.gramophone.activities.base.AbsMusicPanelActivity;
import com.dkanada.gramophone.databinding.FragmentQueueSheetBinding;
import com.dkanada.gramophone.fragments.AbsMusicServiceFragment;
import com.dkanada.gramophone.helper.MusicPlayerRemote;
import com.dkanada.gramophone.util.MusicUtil;
import com.dkanada.gramophone.util.ThemeUtil;

/**
 * The playing queue as a side sheet at the expanded width: a header with the remaining time over the queue list.
 */
public class QueueSheetFragment extends AbsMusicServiceFragment {
    private FragmentQueueSheetBinding binding;

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
    }

    @Override
    public void onDestroyView() {
        binding = null;

        super.onDestroyView();
    }

    @Override
    public void onServiceConnected() {
        updateSubtitle();
    }

    @Override
    public void onQueueChanged() {
        updateSubtitle();
    }

    @Override
    public void onPlayMetadataChanged() {
        updateSubtitle();
    }

    private void updateSubtitle() {
        if (binding == null) return;

        long duration = MusicPlayerRemote.getQueueDurationMillis(MusicPlayerRemote.getPosition());
        binding.queueSheetSubtitle.setText(MusicUtil.buildInfoString(getString(R.string.up_next), MusicUtil.getReadableDurationString(duration)));
    }
}
