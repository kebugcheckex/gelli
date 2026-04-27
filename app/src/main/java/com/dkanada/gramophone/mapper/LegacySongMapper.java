package com.dkanada.gramophone.mapper;

import com.dkanada.gramophone.model.PlaylistSong;
import com.dkanada.gramophone.model.Song;

import org.jellyfin.apiclient.model.dto.BaseItemDto;
import org.jellyfin.apiclient.model.dto.MediaSourceInfo;
import org.jellyfin.apiclient.model.entities.ImageType;
import org.jellyfin.apiclient.model.entities.MediaStream;

import java.util.Map;

public final class LegacySongMapper {
    private LegacySongMapper() {
    }

    public static Song fromItem(BaseItemDto itemDto) {
        Song song = new Song();
        song.id = itemDto.getId();
        song.title = itemDto.getName();
        song.trackNumber = itemDto.getIndexNumber() != null ? itemDto.getIndexNumber() : 0;
        song.discNumber = itemDto.getParentIndexNumber() != null ? itemDto.getParentIndexNumber() : 0;
        song.year = itemDto.getProductionYear() != null ? itemDto.getProductionYear() : 0;
        song.duration = itemDto.getRunTimeTicks() != null ? itemDto.getRunTimeTicks() / 10000 : 0;

        song.albumId = itemDto.getAlbumId();
        song.albumName = itemDto.getAlbum();

        if (itemDto.getArtistItems() != null && itemDto.getArtistItems().size() != 0) {
            song.artistId = itemDto.getArtistItems().get(0).getId();
            song.artistName = itemDto.getArtistItems().get(0).getName();
        } else if (itemDto.getAlbumArtists() != null && itemDto.getAlbumArtists().size() != 0) {
            song.artistId = itemDto.getAlbumArtists().get(0).getId();
            song.artistName = itemDto.getAlbumArtists().get(0).getName();
        }

        song.primary = itemDto.getAlbumPrimaryImageTag() != null ? song.albumId : null;
        if (itemDto.getImageBlurHashes() != null && itemDto.getImageBlurHashes().get(ImageType.Primary) != null) {
            song.blurHash = firstMapValue(itemDto.getImageBlurHashes().get(ImageType.Primary));
        }

        song.favorite = itemDto.getUserData() != null && itemDto.getUserData().getIsFavorite();

        if (itemDto.getMediaSources() != null && itemDto.getMediaSources().size() != 0 && itemDto.getMediaSources().get(0) != null) {
            MediaSourceInfo source = itemDto.getMediaSources().get(0);

            song.path = source.getPath();
            song.size = source.getSize() != null ? source.getSize() : 0;

            song.container = source.getContainer();
            song.bitRate = source.getBitrate() != null ? source.getBitrate() : 0;
            song.supportsTranscoding = source.getSupportsTranscoding();

            if (source.getMediaStreams() != null && source.getMediaStreams().size() != 0) {
                MediaStream stream = source.getMediaStreams().get(0);
                song.codec = stream.getCodec();
                song.sampleRate = stream.getSampleRate() != null ? stream.getSampleRate() : 0;
                song.bitDepth = stream.getBitDepth() != null ? stream.getBitDepth() : 0;
                song.channels = stream.getChannels() != null ? stream.getChannels() : 0;
            }
        }

        return song;
    }

    public static PlaylistSong fromPlaylistItem(BaseItemDto itemDto, String playlistId) {
        return new PlaylistSong(fromItem(itemDto), playlistId, itemDto.getPlaylistItemId());
    }

    private static String firstMapValue(Map<?, ?> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        Object first = map.values().toArray()[0];
        return first != null ? first.toString() : null;
    }
}
