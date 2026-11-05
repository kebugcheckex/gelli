package com.dkanada.gramophone.util;

import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;

import com.dkanada.gramophone.R;
import com.dkanada.gramophone.databinding.DialogTextInputBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public final class DialogUtil {
    private DialogUtil() {
    }

    public interface TextCallback {
        void onText(@NonNull String text);
    }

    /**
     * A dialog with a single name field. The positive button stays disabled while the trimmed text is empty,
     * and the callback receives the trimmed text.
     */
    @NonNull
    public static AlertDialog createNameInputDialog(@NonNull Context context, @StringRes int title, @StringRes int positiveText, @NonNull TextCallback callback) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        DialogTextInputBinding binding = DialogTextInputBinding.inflate(LayoutInflater.from(builder.getContext()));
        binding.inputLayout.setHint(R.string.name);

        AlertDialog dialog = builder
            .setTitle(title)
            .setView(binding.getRoot())
            .setPositiveButton(positiveText, (d, which) -> callback.onText(getTrimmedText(binding)))
            .setNegativeButton(android.R.string.cancel, null)
            .create();

        binding.input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                Button positive = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
                if (positive != null) positive.setEnabled(!getTrimmedText(binding).isEmpty());
            }
        });

        binding.input.setOnEditorActionListener((v, actionId, event) -> {
            Button positive = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
            if (actionId != EditorInfo.IME_ACTION_DONE || positive == null || !positive.isEnabled()) return false;

            positive.performClick();
            return true;
        });

        dialog.setOnShowListener(d -> {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(!getTrimmedText(binding).isEmpty());
            binding.input.requestFocus();
        });

        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }

        return dialog;
    }

    /**
     * An adapter for a dialog list that can change after the dialog is created, laid out like the items
     * from {@link MaterialAlertDialogBuilder#setItems}. Pass the builder's context.
     */
    @NonNull
    public static ArrayAdapter<CharSequence> createListAdapter(@NonNull Context dialogContext) {
        TypedArray attributes = dialogContext.obtainStyledAttributes(null, androidx.appcompat.R.styleable.AlertDialog, androidx.appcompat.R.attr.alertDialogStyle, 0);
        int layout = attributes.getResourceId(androidx.appcompat.R.styleable.AlertDialog_listItemLayout, android.R.layout.simple_list_item_1);
        attributes.recycle();

        return new ArrayAdapter<>(dialogContext, layout, android.R.id.text1);
    }

    @NonNull
    private static String getTrimmedText(@NonNull DialogTextInputBinding binding) {
        return binding.input.getText() == null ? "" : binding.input.getText().toString().trim();
    }
}
