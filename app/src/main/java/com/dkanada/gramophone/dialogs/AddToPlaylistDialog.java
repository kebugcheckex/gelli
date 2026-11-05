package com.dkanada.gramophone.dialogs;

import android.app.Dialog;
import android.os.Bundle;

import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import com.dkanada.gramophone.R;
import com.dkanada.gramophone.model.Playlist;
import com.dkanada.gramophone.model.Song;
import com.dkanada.gramophone.util.DialogUtil;
import com.dkanada.gramophone.util.PlaylistUtil;
import com.dkanada.gramophone.util.QueryUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

public class AddToPlaylistDialog extends DialogFragment {
    @NonNull
    public static AddToPlaylistDialog create(Song song) {
        List<Song> list = new ArrayList<>();
        list.add(song);
        return create(list);
    }

    @NonNull
    public static AddToPlaylistDialog create(List<Song> songs) {
        AddToPlaylistDialog dialog = new AddToPlaylistDialog();

        Bundle args = new Bundle();
        args.putParcelableArrayList("songs", new ArrayList<>(songs));

        dialog.setArguments(args);
        return dialog;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        List<Playlist> playlists = new ArrayList<>();
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireActivity());

        // the first item creates a playlist, and the existing playlists follow once they load
        ArrayAdapter<CharSequence> adapter = DialogUtil.createListAdapter(builder.getContext());
        adapter.add(getString(R.string.action_new_playlist));

        QueryUtil.getPlaylists(media -> {
            playlists.addAll(media);
            for (Playlist playlist : media) {
                adapter.add(playlist.name);
            }
        });

        return builder
                .setTitle(R.string.action_add_to_playlist)
                .setAdapter(adapter, (dialog, i) -> {
                    final List<Song> songs = requireArguments().getParcelableArrayList("songs");
                    if (songs == null) return;

                    if (i == 0) {
                        CreatePlaylistDialog.create(songs).show(requireActivity().getSupportFragmentManager(), "ADD_TO_PLAYLIST");
                    } else {
                        PlaylistUtil.addItems(songs, playlists.get(i - 1).id);
                    }
                })
                .create();
    }
}
