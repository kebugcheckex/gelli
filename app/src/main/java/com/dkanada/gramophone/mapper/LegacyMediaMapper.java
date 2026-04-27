package com.dkanada.gramophone.mapper;

import com.dkanada.gramophone.model.Album;
import com.dkanada.gramophone.model.Artist;
import com.dkanada.gramophone.model.Genre;
import com.dkanada.gramophone.model.Playlist;

import org.jellyfin.apiclient.model.dto.BaseItemDto;
import org.jellyfin.apiclient.model.dto.GenreDto;
import org.jellyfin.apiclient.model.entities.ImageType;

import java.util.Map;

public final class LegacyMediaMapper {
    private LegacyMediaMapper() {
    }

    public static Album toAlbum(BaseItemDto itemDto) {
        String artistId = null;
        String artistName = null;
        if (itemDto.getAlbumArtists() != null && itemDto.getAlbumArtists().size() != 0) {
            artistId = itemDto.getAlbumArtists().get(0).getId();
            artistName = itemDto.getAlbumArtists().get(0).getName();
        } else if (itemDto.getArtistItems() != null && itemDto.getArtistItems().size() != 0) {
            artistId = itemDto.getArtistItems().get(0).getId();
            artistName = itemDto.getArtistItems().get(0).getName();
        }

        return new Album(
                itemDto.getId(),
                itemDto.getName(),
                itemDto.getProductionYear() != null ? itemDto.getProductionYear() : 0,
                artistId,
                artistName,
                hasPrimary(itemDto) ? itemDto.getId() : null,
                firstBlurHash(itemDto)
        );
    }

    public static Artist toArtist(BaseItemDto itemDto) {
        Artist artist = new Artist(
                itemDto.getId(),
                itemDto.getName(),
                hasPrimary(itemDto) ? itemDto.getId() : null,
                firstBlurHash(itemDto)
        );

        if (itemDto.getGenreItems() != null) {
            for (GenreDto genreDto : itemDto.getGenreItems()) {
                artist.genres.add(toGenre(genreDto));
            }
        }
        return artist;
    }

    public static Genre toGenre(BaseItemDto itemDto) {
        return new Genre(
                itemDto.getId(),
                itemDto.getName(),
                itemDto.getSongCount() != null ? itemDto.getSongCount() : 0,
                hasPrimary(itemDto) ? itemDto.getId() : null,
                firstBlurHash(itemDto)
        );
    }

    public static Genre toGenre(GenreDto genreDto) {
        return new Genre(genreDto.getId(), genreDto.getName());
    }

    public static Playlist toPlaylist(BaseItemDto itemDto) {
        return new Playlist(
                itemDto.getId(),
                itemDto.getName(),
                hasPrimary(itemDto) ? itemDto.getId() : null,
                firstBlurHash(itemDto)
        );
    }

    private static boolean hasPrimary(BaseItemDto itemDto) {
        return itemDto.getImageTags() != null && itemDto.getImageTags().containsKey(ImageType.Primary);
    }

    private static String firstBlurHash(BaseItemDto itemDto) {
        if (itemDto.getImageBlurHashes() == null || itemDto.getImageBlurHashes().get(ImageType.Primary) == null) {
            return null;
        }
        Map<?, ?> map = itemDto.getImageBlurHashes().get(ImageType.Primary);
        if (map.isEmpty()) {
            return null;
        }
        Object first = map.values().toArray()[0];
        return first != null ? first.toString() : null;
    }
}
