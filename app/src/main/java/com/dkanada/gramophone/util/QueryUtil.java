package com.dkanada.gramophone.util;

import android.util.Log;

import com.dkanada.gramophone.App;
import com.dkanada.gramophone.interfaces.MediaCallback;
import com.dkanada.gramophone.mapper.LegacyMediaMapper;
import com.dkanada.gramophone.mapper.LegacySongMapper;
import com.dkanada.gramophone.model.Album;
import com.dkanada.gramophone.model.Artist;
import com.dkanada.gramophone.model.Genre;
import com.dkanada.gramophone.model.Playlist;
import com.dkanada.gramophone.model.Song;

import org.jellyfin.apiclient.interaction.Response;
import org.jellyfin.apiclient.model.dto.BaseItemDto;
import org.jellyfin.apiclient.model.dto.BaseItemType;
import org.jellyfin.apiclient.model.querying.ArtistsQuery;
import org.jellyfin.apiclient.model.querying.ItemFields;
import org.jellyfin.apiclient.model.querying.ItemQuery;
import org.jellyfin.apiclient.model.querying.ItemsByNameQuery;
import org.jellyfin.apiclient.model.querying.ItemsResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class QueryUtil {
    public static BaseItemDto currentLibrary;
    private static final String TAG = "QueryUtil";

    // TODO return BaseItemDto everywhere
    // will simplify the code for the getPlaylists method
    public static void getLibraries(MediaCallback<BaseItemDto> callback) {
        String id = App.getApiClient().getCurrentUserId();
        App.getApiClient().GetUserViews(id, new Response<ItemsResult>() {
            @Override
            public void onResponse(ItemsResult result) {
                List<BaseItemDto> libraries = new ArrayList<>(Arrays.asList(result.getItems()));

                callback.onLoadMedia(libraries);
            }

            @Override
            public void onError(Exception exception) {
                exception.printStackTrace();
            }
        });
    }

    public static void getPlaylists(MediaCallback<Playlist> callback) {
        ItemQuery query = new ItemQuery();
        query.setIncludeItemTypes(new String[]{"Playlist"});
        applyProperties(query);
        App.getApiClient().GetItemsAsync(query, new Response<ItemsResult>() {
            @Override
            public void onResponse(ItemsResult result) {
                List<Playlist> playlists = new ArrayList<>();
                for (BaseItemDto itemDto : result.getItems()) {
                    playlists.add(LegacyMediaMapper.toPlaylist(itemDto));
                }

                callback.onLoadMedia(playlists);
            }

            @Override
            public void onError(Exception exception) {
                exception.printStackTrace();
            }
        });
    }

    public static void getGenres(MediaCallback<Genre> callback) {
        ItemsByNameQuery query = new ItemsByNameQuery();
        applyProperties(query);
        App.getApiClient().GetGenresAsync(query, new Response<ItemsResult>() {
            @Override
            public void onResponse(ItemsResult result) {
                List<Genre> genres = new ArrayList<>();
                for (BaseItemDto itemDto : result.getItems()) {
                    genres.add(LegacyMediaMapper.toGenre(itemDto));
                }

                callback.onLoadMedia(genres);
            }

            @Override
            public void onError(Exception exception) {
                exception.printStackTrace();
            }
        });
    }

    public static void getItems(ItemQuery query, MediaCallback<Object> callback) {
        query.setIncludeItemTypes(new String[]{"MusicArtist", "MusicAlbum", "Audio"});
        query.setFields(new ItemFields[]{ItemFields.MediaSources});
        query.setUserId(App.getApiClient().getCurrentUserId());
        query.setLimit(40);
        query.setRecursive(true);
        App.getApiClient().GetItemsAsync(query, new Response<ItemsResult>() {
            @Override
            public void onResponse(ItemsResult result) {
                List<Object> items = new ArrayList<>();
                for (BaseItemDto itemDto : result.getItems()) {
                    if (itemDto.getBaseItemType() == BaseItemType.MusicArtist) {
                        items.add(LegacyMediaMapper.toArtist(itemDto));
                    } else if (itemDto.getBaseItemType() == BaseItemType.MusicAlbum) {
                        items.add(LegacyMediaMapper.toAlbum(itemDto));
                    } else {
                        items.add(LegacySongMapper.fromItem(itemDto));
                    }
                }

                callback.onLoadMedia(items);
            }

            @Override
            public void onError(Exception exception) {
                exception.printStackTrace();
            }
        });
    }

    public static void getAlbums(ItemQuery query, MediaCallback<Album> callback) {
        query.setIncludeItemTypes(new String[]{"MusicAlbum"});
        applyProperties(query);
        App.getApiClient().GetItemsAsync(query, new Response<ItemsResult>() {
            @Override
            public void onResponse(ItemsResult result) {
                List<Album> albums = new ArrayList<>();
                for (BaseItemDto itemDto : result.getItems()) {
                    albums.add(LegacyMediaMapper.toAlbum(itemDto));
                }

                callback.onLoadMedia(albums);
            }

            @Override
            public void onError(Exception exception) {
                exception.printStackTrace();
            }
        });
    }

    public static void getArtists(ArtistsQuery query, MediaCallback<Artist> callback) {
        query.setFields(new ItemFields[]{ItemFields.Genres});
        applyProperties(query);
        App.getApiClient().GetAlbumArtistsAsync(query, new Response<ItemsResult>() {
            @Override
            public void onResponse(ItemsResult result) {
                List<Artist> artists = new ArrayList<>();
                for (BaseItemDto itemDto : result.getItems()) {
                    artists.add(LegacyMediaMapper.toArtist(itemDto));
                }

                callback.onLoadMedia(artists);
            }

            @Override
            public void onError(Exception exception) {
                exception.printStackTrace();
            }
        });
    }

    public static void getSongs(ItemQuery query, MediaCallback<Song> callback) {
        query.setIncludeItemTypes(new String[]{"Audio"});
        query.setFields(new ItemFields[]{ItemFields.MediaSources});
        applyProperties(query);
        App.getApiClient().GetItemsAsync(query, new Response<ItemsResult>() {
            @Override
            public void onResponse(ItemsResult result) {
                List<Song> songs = new ArrayList<>();
                for (BaseItemDto itemDto : result.getItems()) {
                    songs.add(LegacySongMapper.fromItem(itemDto));
                }

                callback.onLoadMedia(songs);
            }

            @Override
            public void onError(Exception exception) {
                exception.printStackTrace();
            }
        });
    }

    public static void applyAlbumIdFilter(ItemQuery query, String albumId) {
        if (albumId == null) {
            Log.w(TAG, "applyAlbumIdFilter: albumId is null");
            return;
        }
        try {
            // Newer Jellyfin API supports AlbumIds for audio queries.
            ItemQuery.class
                    .getMethod("setAlbumIds", String[].class)
                    .invoke(query, (Object) new String[]{albumId});
            return;
        } catch (NoSuchMethodException e) {
            Log.w(TAG, "applyAlbumIdFilter: setAlbumIds not available, using ParentId");
        } catch (Exception e) {
            Log.w(TAG, "applyAlbumIdFilter: failed to set AlbumIds, using ParentId", e);
        }

        query.setParentId(albumId);
    }

    public static void applyProperties(ItemQuery query) {
        query.setUserId(App.getApiClient().getCurrentUserId());
        query.setRecursive(true);
        if (query.getParentId() == null && query.getArtistIds().length == 0) {
            query.setLimit(PreferenceUtil.getInstance(App.getInstance()).getPageSize());
        }

        if (currentLibrary == null || query.getParentId() != null) return;
        if (query.getArtistIds().length == 0) query.setParentId(currentLibrary.getId());
    }

    public static void applyProperties(ItemsByNameQuery query) {
        query.setUserId(App.getApiClient().getCurrentUserId());
        query.setRecursive(true);
        if (query.getParentId() == null) {
            query.setLimit(PreferenceUtil.getInstance(App.getInstance()).getPageSize());
        }

        if (currentLibrary == null || query.getParentId() != null) return;
        query.setParentId(currentLibrary.getId());
    }
}
