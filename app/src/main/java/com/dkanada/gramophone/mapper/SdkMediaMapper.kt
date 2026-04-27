package com.dkanada.gramophone.mapper

import com.dkanada.gramophone.model.Album
import com.dkanada.gramophone.model.Artist
import com.dkanada.gramophone.model.Genre
import com.dkanada.gramophone.model.Playlist
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.NameGuidPair

object SdkMediaMapper {
    @JvmStatic
    fun toAlbum(item: BaseItemDto): Album {
        val artistItem = item.albumArtists?.firstOrNull() ?: item.artistItems?.firstOrNull()
        return Album(
            SdkMapperUtil.uuidToId(item.id),
            item.name ?: "",
            item.productionYear ?: 0,
            SdkMapperUtil.uuidToId(artistItem?.id),
            artistItem?.name,
            if (SdkMapperUtil.hasPrimary(item)) SdkMapperUtil.uuidToId(item.id) else null,
            SdkMapperUtil.firstPrimaryBlurHash(item)
        )
    }

    @JvmStatic
    fun toArtist(item: BaseItemDto): Artist {
        val artist = Artist(
            SdkMapperUtil.uuidToId(item.id),
            item.name ?: "",
            if (SdkMapperUtil.hasPrimary(item)) SdkMapperUtil.uuidToId(item.id) else null,
            SdkMapperUtil.firstPrimaryBlurHash(item)
        )
        item.genreItems?.forEach { genre ->
            artist.genres.add(toGenre(genre))
        }
        return artist
    }

    @JvmStatic
    fun toGenre(item: BaseItemDto): Genre {
        return Genre(
            SdkMapperUtil.uuidToId(item.id),
            item.name ?: "",
            item.songCount ?: 0,
            if (SdkMapperUtil.hasPrimary(item)) SdkMapperUtil.uuidToId(item.id) else null,
            SdkMapperUtil.firstPrimaryBlurHash(item)
        )
    }

    @JvmStatic
    fun toGenre(item: NameGuidPair): Genre {
        return Genre(
            SdkMapperUtil.uuidToId(item.id),
            item.name ?: ""
        )
    }

    @JvmStatic
    fun toPlaylist(item: BaseItemDto): Playlist {
        return Playlist(
            SdkMapperUtil.uuidToId(item.id),
            item.name ?: "",
            if (SdkMapperUtil.hasPrimary(item)) SdkMapperUtil.uuidToId(item.id) else null,
            SdkMapperUtil.firstPrimaryBlurHash(item)
        )
    }
}
