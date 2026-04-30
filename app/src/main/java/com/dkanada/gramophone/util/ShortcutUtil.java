package com.dkanada.gramophone.util;

import com.dkanada.gramophone.interfaces.MediaCallback;
import com.dkanada.gramophone.model.Song;
import com.dkanada.gramophone.model.SortMethod;
import com.dkanada.gramophone.model.SortOrder;

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
