package com.dkanada.gramophone.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.Html;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import com.afollestad.materialdialogs.MaterialDialog;
import com.dkanada.gramophone.R;
import com.dkanada.gramophone.model.Song;
import com.dkanada.gramophone.util.LocalLibrary;

import java.util.ArrayList;
import java.util.List;

public class RemoveFromDeviceDialog extends DialogFragment {
    public static final String TAG = RemoveFromDeviceDialog.class.getSimpleName();

    // only the ids are kept, since a whole artist or playlist would not fit in the arguments as songs
    @NonNull
    public static RemoveFromDeviceDialog create(List<Song> songs) {
        RemoveFromDeviceDialog dialog = new RemoveFromDeviceDialog();

        ArrayList<String> ids = new ArrayList<>();
        for (Song song : songs) {
            ids.add(song.id);
        }

        Bundle args = new Bundle();
        args.putStringArrayList("ids", ids);

        dialog.setArguments(args);
        return dialog;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final List<String> ids = requireArguments().getStringArrayList("ids");
        final Context context = requireContext().getApplicationContext();

        return new MaterialDialog.Builder(requireActivity())
                .title(R.string.action_remove_from_device)
                .content(Html.fromHtml(getString(R.string.remove_x_songs_from_device, ids.size())))
                .positiveText(R.string.remove_action)
                .negativeText(android.R.string.cancel)
                .onPositive((dialog, which) -> remove(context, ids))
                .build();
    }

    /** Removes the songs without asking and reports how many were stored. */
    public static void remove(Context context, List<String> ids) {
        LocalLibrary.delete(ids, count -> {
            String message;
            if (count == 0) {
                message = context.getString(R.string.nothing_to_remove_from_device);
            } else if (count == 1) {
                message = context.getString(R.string.removed_song_from_device);
            } else {
                message = context.getString(R.string.removed_x_songs_from_device, count);
            }

            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        });
    }
}
