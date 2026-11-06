package com.dkanada.gramophone.dialogs.preferences;

import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.preference.ListPreferenceDialogFragmentCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

// the library builds its dialog with a plain AlertDialog.Builder, which has none of the Material shape or colors
public class ListPreferenceDialog extends ListPreferenceDialogFragmentCompat {
    public static final String TAG = ListPreferenceDialog.class.getSimpleName();

    public static ListPreferenceDialog create(String key) {
        Bundle bundle = new Bundle(1);
        bundle.putString(ARG_KEY, key);

        ListPreferenceDialog dialog = new ListPreferenceDialog();
        dialog.setArguments(bundle);

        return dialog;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
            .setTitle(getPreference().getDialogTitle());

        onPrepareDialogBuilder(builder);

        return builder.create();
    }
}
