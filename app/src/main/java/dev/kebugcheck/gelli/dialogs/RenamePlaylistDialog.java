package dev.kebugcheck.gelli.dialogs;

import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import dev.kebugcheck.gelli.R;
import dev.kebugcheck.gelli.model.Playlist;
import dev.kebugcheck.gelli.util.DialogUtil;
import dev.kebugcheck.gelli.util.PlaylistUtil;

public class RenamePlaylistDialog extends DialogFragment {
    public static final String TAG = RenamePlaylistDialog.class.getSimpleName();

    private static final String PLAYLIST = "playlist";

    @NonNull
    public static RenamePlaylistDialog create(Playlist playlist) {
        RenamePlaylistDialog dialog = new RenamePlaylistDialog();

        Bundle args = new Bundle();
        args.putString(PLAYLIST, playlist.id);

        dialog.setArguments(args);
        return dialog;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return DialogUtil.createNameInputDialog(requireActivity(), R.string.rename_playlist_title, R.string.rename_action, name -> {
            String id = requireArguments().getString(PLAYLIST);
            PlaylistUtil.renamePlaylist(id, name);
        });
    }
}
