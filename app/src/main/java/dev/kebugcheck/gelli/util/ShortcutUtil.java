package dev.kebugcheck.gelli.util;

import dev.kebugcheck.gelli.interfaces.MediaCallback;
import dev.kebugcheck.gelli.model.Song;
import dev.kebugcheck.gelli.model.SortMethod;
import dev.kebugcheck.gelli.model.SortOrder;

public class ShortcutUtil {
    public static void getFrequent(MediaCallback<Song> callback) {
        QueryUtil.getSongsBySort(SortMethod.COUNT, SortOrder.DESCENDING, 200, false, callback);
    }

    public static void getLatest(MediaCallback<Song> callback) {
        QueryUtil.getSongsBySort(SortMethod.ADDED, SortOrder.DESCENDING, 200, false, callback);
    }

    public static void getShuffle(MediaCallback<Song> callback, boolean onlyFavorites) {
        QueryUtil.getSongsBySort(SortMethod.RANDOM, SortOrder.DESCENDING, 200, onlyFavorites, callback);
    }
}
