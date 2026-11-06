package com.dkanada.gramophone.dialogs.preferences;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.dkanada.gramophone.R;
import com.dkanada.gramophone.databinding.ItemColorPresetBinding;
import com.dkanada.gramophone.model.ColorPreset;
import com.dkanada.gramophone.model.Theme;
import com.dkanada.gramophone.util.PreferenceUtil;
import com.dkanada.gramophone.util.ThemeUtil;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

public class ColorPresetDialog extends DialogFragment {
    public static final String TAG = ColorPresetDialog.class.getSimpleName();

    private static final int COLUMNS = 4;

    public static ColorPresetDialog create() {
        return new ColorPresetDialog();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireActivity());
        Context context = builder.getContext();
        View view = LayoutInflater.from(context).inflate(R.layout.preference_dialog_color_preset, null);
        RecyclerView recyclerView = view.findViewById(R.id.recycler_view);

        recyclerView.setLayoutManager(new GridLayoutManager(context, COLUMNS));
        recyclerView.setAdapter(new ColorPresetAdapter(ColorPreset.available(DynamicColors.isDynamicColorAvailable())));

        return builder
                .setTitle(R.string.pref_title_color_preset)
                .setView(view)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
    }

    private void select(ColorPreset preset) {
        PreferenceUtil preferences = PreferenceUtil.getInstance(requireContext());

        // saving recreates the activity, which would bring the dialog back if it was still open
        dismiss();
        preferences.setColorPreset(preset);
    }

    private class ColorPresetAdapter extends RecyclerView.Adapter<ColorPresetAdapter.ViewHolder> {
        private final List<ColorPreset> presets;
        private final Theme theme;
        private final ColorPreset current;

        ColorPresetAdapter(List<ColorPreset> presets) {
            PreferenceUtil preferences = PreferenceUtil.getInstance(requireContext());

            this.presets = presets;
            this.theme = preferences.getTheme();
            this.current = preferences.getColorPreset();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(ItemColorPresetBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ColorPreset preset = presets.get(position);
            ItemColorPresetBinding binding = holder.binding;
            Context themed = ThemeUtil.themedContext(binding.getRoot().getContext(), theme, preset);

            tint(binding.container, ThemeUtil.getColorResource(themed, R.attr.colorPrimaryContainer));
            tint(binding.primary, ThemeUtil.getColorResource(themed, R.attr.colorPrimary));
            binding.check.setImageTintList(ColorStateList.valueOf(ThemeUtil.getColorResource(themed, R.attr.colorOnPrimary)));
            binding.check.setVisibility(preset == current ? View.VISIBLE : View.GONE);
            binding.title.setText(preset.title);

            binding.getRoot().setSelected(preset == current);
            binding.getRoot().setOnClickListener(v -> select(preset));
        }

        private void tint(View view, int color) {
            view.setBackgroundTintList(ColorStateList.valueOf(color));
        }

        @Override
        public int getItemCount() {
            return presets.size();
        }

        private class ViewHolder extends RecyclerView.ViewHolder {
            final ItemColorPresetBinding binding;

            ViewHolder(ItemColorPresetBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}
