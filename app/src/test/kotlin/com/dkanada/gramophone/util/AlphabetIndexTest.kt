package com.dkanada.gramophone.util

import com.dkanada.gramophone.model.SortMethod
import com.ibm.icu.text.Transliterator
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.NameGuidPair
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

class AlphabetIndexTest {
    private lateinit var original: (String, Boolean) -> String

    // android.icu is only a stub here, and icu4j carries the same rules
    @Before
    fun useIcu4j() {
        original = AlphabetIndex.transliterate
        val names = Transliterator.getInstance("Han-Latin/Names; Latin-ASCII")
        val titles = Transliterator.getInstance("Han-Latin; Latin-ASCII")
        AlphabetIndex.transliterate = { text, personName -> (if (personName) names else titles).transliterate(text) }
    }

    @After
    fun restore() {
        AlphabetIndex.transliterate = original
    }

    private val names: AlphabetIndex.Key<String> = AlphabetIndex.Key(personName = true) { it }

    @Test
    fun sectionOf_filesHanUnderPinyinInitial() {
        assertEquals("Z", AlphabetIndex.sectionOf("周杰伦", personName = true))
        assertEquals("C", AlphabetIndex.sectionOf("陈奕迅", personName = true))
        assertEquals("A", AlphabetIndex.sectionOf("阿杜", personName = true))
        assertEquals("W", AlphabetIndex.sectionOf("五月天"))
    }

    @Test
    fun sectionOf_skipsLeadingArticleAndAccents() {
        assertEquals("B", AlphabetIndex.sectionOf("The Beatles"))
        assertEquals("N", AlphabetIndex.sectionOf("A Night at the Opera"))
        assertEquals("B", AlphabetIndex.sectionOf("Björk"))
        assertEquals("A", AlphabetIndex.sectionOf("A"))
    }

    @Test
    fun sectionOf_filesEverythingElseUnderOther() {
        assertEquals(AlphabetIndex.OTHER, AlphabetIndex.sectionOf("2Pac"))
        assertEquals(AlphabetIndex.OTHER, AlphabetIndex.sectionOf("あいみょん"))
        assertEquals(AlphabetIndex.OTHER, AlphabetIndex.sectionOf("아이유"))
        assertEquals(AlphabetIndex.OTHER, AlphabetIndex.sectionOf(""))
        assertEquals(AlphabetIndex.OTHER, AlphabetIndex.sectionOf(null))
    }

    @Test
    fun sortKey_readsLeadingHanAsSurnameOnlyForPersonNames() {
        assertTrue(AlphabetIndex.sortKey("曾轶可", personName = true).startsWith("zeng"))
        assertTrue(AlphabetIndex.sortKey("单依纯", personName = true).startsWith("shan"))
        assertTrue(AlphabetIndex.sortKey("单曲", personName = false).startsWith("dan"))
    }

    @Test
    fun sort_interleavesHanWithLatinAndPutsOtherLast() {
        val sorted = AlphabetIndex.sort(
            listOf("周杰伦", "10cc", "Coldplay", "陈奕迅", "あいみょん", "Beyond", "Adele"),
            descending = false,
            keys = listOf(names)
        )

        assertEquals(listOf("Adele", "Beyond", "陈奕迅", "Coldplay", "周杰伦", "10cc", "あいみょん"), sorted.items)
        assertEquals(AlphabetIndex.SECTIONS, sorted.sections)
    }

    @Test
    fun sort_descendingReversesItemsAndSections() {
        val sorted = AlphabetIndex.sort(listOf("Beyond", "10cc", "周杰伦", "Adele"), descending = true, keys = listOf(names))

        assertEquals(listOf("10cc", "周杰伦", "Beyond", "Adele"), sorted.items)
        assertEquals(AlphabetIndex.OTHER, sorted.sections.first())
        assertEquals("A", sorted.sections.last())
        assertEquals(1, sorted.positionForSection(sorted.sections.indexOf("Z")))
    }

    @Test
    fun positionForSection_fallsForwardThenBack() {
        val sorted = AlphabetIndex.sort(listOf("Adele", "Coldplay", "周杰伦"), descending = false, keys = listOf(names))
        val sections = sorted.sections

        assertTrue(sorted.hasItems(sections.indexOf("C")))
        assertFalse(sorted.hasItems(sections.indexOf("B")))
        assertEquals(1, sorted.positionForSection(sections.indexOf("B")))
        assertEquals(2, sorted.positionForSection(sections.indexOf("Z")))
        // nothing after Z, so the empty # section lands on the last section that has items
        assertEquals(2, sorted.positionForSection(sections.indexOf(AlphabetIndex.OTHER)))
        assertEquals(-1, AlphabetIndex.sort(emptyList(), false, listOf(names)).positionForSection(0))
    }

    @Test
    fun sectionedAlbumKeys_sortsByArtistThenTitle() {
        fun album(id: Long, title: String, artist: String) = BaseItemDto(
            id = UUID(0, id),
            name = title,
            type = BaseItemKind.MUSIC_ALBUM,
            albumArtists = listOf(NameGuidPair(name = artist, id = UUID(1, id)))
        )

        val albums = listOf(album(1, "叶惠美", "周杰伦"), album(2, "Parachutes", "Coldplay"), album(3, "范特西", "周杰伦"))

        val byArtist = AlphabetIndex.sort(albums, false, QueryUtil.sectionedAlbumKeys(SortMethod.ARTIST))
        assertEquals(listOf("Parachutes", "范特西", "叶惠美"), byArtist.items.map { it.name })

        val byTitle = AlphabetIndex.sort(albums, false, QueryUtil.sectionedAlbumKeys(SortMethod.NAME))
        assertEquals(listOf("范特西", "Parachutes", "叶惠美"), byTitle.items.map { it.name })
    }
}
