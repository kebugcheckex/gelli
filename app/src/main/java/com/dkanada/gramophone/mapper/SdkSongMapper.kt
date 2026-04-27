package com.dkanada.gramophone.mapper

import com.dkanada.gramophone.model.Song
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ImageType

object SdkSongMapper {
    @JvmStatic
    fun fromItem(item: BaseItemDto): Song {
        val song = Song()
        song.id = uuidToId(item.id)
        song.title = item.name ?: ""
        song.trackNumber = item.indexNumber ?: 0
        song.discNumber = item.parentIndexNumber ?: 0
        song.year = item.productionYear ?: 0
        song.duration = (item.runTimeTicks ?: 0L) / 10000

        song.albumId = uuidToId(item.albumId)
        song.albumName = item.album

        val artistItem = item.artistItems?.firstOrNull() ?: item.albumArtists?.firstOrNull()
        song.artistId = uuidToId(artistItem?.id)
        song.artistName = artistItem?.name

        song.primary = if (item.albumPrimaryImageTag != null && song.albumId != null) song.albumId else null
        song.blurHash = item.imageBlurHashes
            ?.get(ImageType.PRIMARY)
            ?.values
            ?.firstOrNull()

        song.favorite = item.userData?.isFavorite == true

        val source = item.mediaSources?.firstOrNull()
        if (source != null) {
            song.path = source.path
            song.size = source.size ?: 0L
            song.container = source.container
            song.bitRate = source.bitrate ?: 0
            song.supportsTranscoding = source.supportsTranscoding

            val stream = source.mediaStreams?.firstOrNull()
            if (stream != null) {
                song.codec = stream.codec
                song.sampleRate = stream.sampleRate ?: 0
                song.bitDepth = stream.bitDepth ?: 0
                song.channels = stream.channels ?: 0
            }
        }

        return song
    }

    private fun uuidToId(value: java.util.UUID?): String {
        return value?.toString()?.replace("-", "") ?: ""
    }
}
