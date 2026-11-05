package com.dkanada.gramophone.dialogs;

import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import com.dkanada.gramophone.App;
import com.dkanada.gramophone.R;
import com.dkanada.gramophone.helper.EventListener;
import com.dkanada.gramophone.helper.MusicPlayerRemote;
import com.dkanada.gramophone.model.User;
import com.dkanada.gramophone.util.NavigationUtil;
import com.dkanada.gramophone.util.PreferenceUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

public class ConfirmLogoutDialog extends DialogFragment {
    public static final String TAG = ConfirmLogoutDialog.class.getSimpleName();

    @NonNull
    public static ConfirmLogoutDialog create() {
        return new ConfirmLogoutDialog();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return new MaterialAlertDialogBuilder(requireActivity())
                .setTitle(R.string.logout)
                .setMessage(R.string.confirm_logout)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.logout, (dialog, which) -> {
                    EventListener.stop();
                    MusicPlayerRemote.clearQueue();

                    PreferenceUtil.getInstance(requireContext()).setServer(null);
                    PreferenceUtil.getInstance(requireContext()).setUser(null);
                    List<User> users = App.getDatabase().userDao().getUsers();
                    if (users.size() > 0) {
                        NavigationUtil.startSelect(requireContext());
                    } else {
                        NavigationUtil.startLogin(requireContext());
                    }
                })
                .create();
    }
}
