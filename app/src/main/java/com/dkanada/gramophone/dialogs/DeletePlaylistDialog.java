package com.dkanada.gramophone.dialogs;

import android.app.Dialog;
import android.os.Bundle;
import android.text.Html;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import com.dkanada.gramophone.R;
import com.dkanada.gramophone.model.Playlist;
import com.dkanada.gramophone.util.PlaylistUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

public class DeletePlaylistDialog extends DialogFragment {
    public static final String TAG = DeletePlaylistDialog.class.getSimpleName();

    @NonNull
    public static DeletePlaylistDialog create(Playlist playlist) {
        List<Playlist> list = new ArrayList<>();
        list.add(playlist);
        return create(list);
    }

    @NonNull
    public static DeletePlaylistDialog create(List<Playlist> playlists) {
        DeletePlaylistDialog dialog = new DeletePlaylistDialog();
        Bundle args = new Bundle();
        args.putParcelableArrayList("playlists", new ArrayList<>(playlists));
        dialog.setArguments(args);
        return dialog;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final List<Playlist> playlists = getArguments().getParcelableArrayList("playlists");
        int title;
        CharSequence content;
        if (playlists.size() > 1) {
            title = R.string.delete_playlists_title;
            content = Html.fromHtml(getString(R.string.delete_x_playlists, playlists.size()));
        } else {
            title = R.string.delete_playlist_title;
            content = Html.fromHtml(getString(R.string.delete_playlist_x, playlists.get(0).name));
        }

        return new MaterialAlertDialogBuilder(requireActivity())
                .setTitle(title)
                .setMessage(content)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.delete_action, (dialog, which) -> PlaylistUtil.deletePlaylist(playlists))
                .create();
    }
}
