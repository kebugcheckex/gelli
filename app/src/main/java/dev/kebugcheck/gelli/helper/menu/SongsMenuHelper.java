package dev.kebugcheck.gelli.helper.menu;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;

import dev.kebugcheck.gelli.R;
import dev.kebugcheck.gelli.dialogs.AddToPlaylistDialog;
import dev.kebugcheck.gelli.helper.MusicPlayerRemote;
import dev.kebugcheck.gelli.model.Song;
import dev.kebugcheck.gelli.util.NavigationUtil;

import java.util.List;

public class SongsMenuHelper {
    public static boolean handleMenuClick(@NonNull FragmentActivity activity, @NonNull List<Song> songs, int menuItemId) {
        if (menuItemId == R.id.action_play) {
            MusicPlayerRemote.openQueue(songs, 0, true);
            return true;
        } else if (menuItemId == R.id.action_play_next) {
            MusicPlayerRemote.playNext(songs);
            return true;
        } else if (menuItemId == R.id.action_add_to_queue) {
            MusicPlayerRemote.enqueue(songs);
            return true;
        } else if (menuItemId == R.id.action_add_to_playlist) {
            AddToPlaylistDialog.create(songs).show(activity.getSupportFragmentManager(), "ADD_PLAYLIST");
            return true;
        } else if (menuItemId == R.id.action_download) {
            NavigationUtil.startDownload(activity, songs);
            return true;
        } else if (menuItemId == R.id.action_remove_from_device) {
            NavigationUtil.removeFromDevice(activity, songs);
            return true;
        }

        return false;
    }
}
