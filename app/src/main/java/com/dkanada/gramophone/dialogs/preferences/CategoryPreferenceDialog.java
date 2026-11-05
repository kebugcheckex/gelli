package com.dkanada.gramophone.dialogs.preferences;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.dkanada.gramophone.R;
import com.dkanada.gramophone.adapter.CategoryAdapter;
import com.dkanada.gramophone.model.Category;
import com.dkanada.gramophone.util.PreferenceUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class CategoryPreferenceDialog extends DialogFragment {
    public static final String TAG = CategoryPreferenceDialog.class.getSimpleName();

    public static CategoryPreferenceDialog create() {
        return new CategoryPreferenceDialog();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireActivity());
        View view = LayoutInflater.from(builder.getContext()).inflate(R.layout.preference_dialog_category, null);
        List<Category> categories = PreferenceUtil.getInstance(getContext()).getCategories();
        CategoryAdapter adapter = new CategoryAdapter(categories);
        RecyclerView recyclerView = view.findViewById(R.id.recycler_view);

        recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        recyclerView.setAdapter(adapter);
        adapter.attachToRecyclerView(recyclerView);

        AlertDialog dialog = builder
                .setTitle(R.string.pref_title_categories)
                .setView(view)
                .setPositiveButton(android.R.string.ok, (d, which) -> PreferenceUtil.getInstance(getContext()).setCategories(adapter.getCategories()))
                .setNeutralButton(R.string.reset_action, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        // the dialog would dismiss on any button, so reset is attached here to keep it open
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            adapter.setCategories(Arrays.stream(Category.values())
                .peek(category -> category.select = true)
                .collect(Collectors.toList()));
        }));

        return dialog;
    }
}
