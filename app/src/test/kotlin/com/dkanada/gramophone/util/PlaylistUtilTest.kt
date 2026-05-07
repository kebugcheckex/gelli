package com.dkanada.gramophone.util

import com.dkanada.gramophone.mapper.SdkSongMapper
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class PlaylistUtilTest {

    private val userId = "22222222222222222222222222222222"
    private val playlistId = UUID.fromString("11111111-1111-1111-1111-111111111111")

    // --- getPlaylistItemsRequest ---

    @Test
    fun getPlaylistItemsRequest_setsPlaylistId() {
        val request = PlaylistUtil.getPlaylistItemsRequest(playlistId, userId)

        assertEquals(playlistId, request.playlistId)
    }

    @Test
    fun getPlaylistItemsRequest_convertsUserId() {
        val request = PlaylistUtil.getPlaylistItemsRequest(playlistId, userId)

        assertEquals(UUID.fromString("22222222-2222-2222-2222-222222222222"), request.userId)
    }

    @Test
    fun getPlaylistItemsRequest_includesMediaSources() {
        val request = PlaylistUtil.getPlaylistItemsRequest(playlistId, userId)

        assertTrue(request.fields?.contains(ItemFields.MEDIA_SOURCES) == true)
    }

    @Test
    fun getPlaylistItemsRequest_hasNullUserId_whenUserIdIsNull() {
        val request = PlaylistUtil.getPlaylistItemsRequest(playlistId, null)

        assertNull(request.userId)
    }

    @Test
    fun getPlaylistItemsRequest_hasNullUserId_whenUserIdIsBlank() {
        val request = PlaylistUtil.getPlaylistItemsRequest(playlistId, "  ")

        assertNull(request.userId)
    }

    // --- SdkSongMapper.fromPlaylistItem ---

    @Test
    fun fromPlaylistItem_passesPlaylistId() {
        val item = BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.AUDIO)
        val song = SdkSongMapper.fromPlaylistItem(item, "playlist123")

        assertEquals("playlist123", song.playlistId)
    }

    @Test
    fun fromPlaylistItem_usesPlaylistItemIdAsIndexId() {
        val item = BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.AUDIO, playlistItemId = "entry-abc-456")
        val song = SdkSongMapper.fromPlaylistItem(item, "playlist123")

        assertEquals("entry-abc-456", song.indexId)
    }

    @Test
    fun fromPlaylistItem_hasNullIndexId_whenPlaylistItemIdAbsent() {
        val item = BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.AUDIO, playlistItemId = null)
        val song = SdkSongMapper.fromPlaylistItem(item, "playlist123")

        assertNull(song.indexId)
    }

    @Test
    fun fromPlaylistItem_songFieldsMatchFromItem() {
        val itemId = UUID.randomUUID()
        val item = BaseItemDto(
            id = itemId,
            type = BaseItemKind.AUDIO,
            name = "Test Song",
            indexNumber = 3,
            parentIndexNumber = 2,
            playlistItemId = "entry-xyz"
        )
        val base = SdkSongMapper.fromItem(item)
        val playlist = SdkSongMapper.fromPlaylistItem(item, "pl1")

        assertEquals(base.id, playlist.id)
        assertEquals(base.title, playlist.title)
        assertEquals(base.trackNumber, playlist.trackNumber)
        assertEquals(base.discNumber, playlist.discNumber)
    }
}
