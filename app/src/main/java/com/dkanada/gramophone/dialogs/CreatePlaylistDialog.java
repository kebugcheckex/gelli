package com.dkanada.gramophone.dialogs;

import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.dkanada.gramophone.R;
import com.dkanada.gramophone.model.Song;
import com.dkanada.gramophone.util.DialogUtil;
import com.dkanada.gramophone.util.PlaylistUtil;

import java.util.ArrayList;
import java.util.List;

public class CreatePlaylistDialog extends DialogFragment {
    private static final String SONGS = "songs";

    @NonNull
    public static CreatePlaylistDialog create() {
        return create((Song) null);
    }

    @NonNull
    public static CreatePlaylistDialog create(@Nullable Song song) {
        List<Song> list = new ArrayList<>();
        if (song != null) list.add(song);
        return create(list);
    }

    @NonNull
    public static CreatePlaylistDialog create(List<Song> songs) {
        CreatePlaylistDialog dialog = new CreatePlaylistDialog();
        Bundle args = new Bundle();
        args.putParcelableArrayList(SONGS, new ArrayList<>(songs));
        dialog.setArguments(args);
        return dialog;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return DialogUtil.createNameInputDialog(requireActivity(), R.string.action_new_playlist, R.string.create_action, name -> {
            List<Song> songs = requireArguments().getParcelableArrayList(SONGS);
            if (songs != null) {
                PlaylistUtil.createPlaylist(name, songs);
            }
        });
    }
}
