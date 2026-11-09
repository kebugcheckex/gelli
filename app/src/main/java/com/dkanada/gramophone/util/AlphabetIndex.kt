package com.dkanada.gramophone.util

import android.icu.text.Transliterator
import java.text.CollationKey
import java.text.Collator
import java.util.Locale

/**
 * Sorts names into the A to Z sections of the library letter bar. Han characters sort and section by
 * their Pinyin, so 周杰伦 sits among the Z names rather than after all of them, which is where the
 * server puts it when it sorts by code point. Whatever does not start with a Latin letter once it is
 * transliterated (digits, symbols, kana, Hangul, Cyrillic) goes into [OTHER].
 */
object AlphabetIndex {
    const val OTHER = "#"

    /** Every section, in ascending order. */
    @JvmField
    val SECTIONS: List<String> = ('A'..'Z').map(Char::toString) + OTHER

    private val ARTICLES = listOf("the ", "an ", "a ")

    /**
     * Turns text into lowercase ASCII where it can. Person names read a leading Han character as a
     * surname, so 曾 is Zeng and 单 is Shan, while a title reads 曾经 as Ceng Jing.
     * Replaced in unit tests, where android.icu only has stubs.
     */
    internal var transliterate: (text: String, personName: Boolean) -> String = HanToLatin::apply

    /** One name to sort by, picked from an item. A person name reads a leading Han character as a surname. */
    class Key<T>(val personName: Boolean, val select: (T) -> String?)

    /** The text items are compared by: lowercase, without a leading article, with Han written in Pinyin. */
    @JvmStatic
    @JvmOverloads
    fun sortKey(text: String?, personName: Boolean = false): String {
        var value = text?.trim()?.lowercase(Locale.ROOT).orEmpty()
        ARTICLES.firstOrNull { value.startsWith(it) && value.length > it.length }?.let {
            value = value.substring(it.length).trimStart()
        }

        if (value.all { it.code < 0x80 }) return value

        return transliterate(value, personName).trim().lowercase(Locale.ROOT)
    }

    /** The section of a [sortKey]. */
    @JvmStatic
    fun sectionOfKey(key: String): String {
        val first = key.firstOrNull() ?: return OTHER
        return if (first in 'a'..'z') first.uppercaseChar().toString() else OTHER
    }

    @JvmStatic
    @JvmOverloads
    fun sectionOf(text: String?, personName: Boolean = false): String = sectionOfKey(sortKey(text, personName))

    /**
     * Sorts items by section and then by each key in turn. A descending list is the ascending one
     * reversed, so its sections run from [OTHER] back to A.
     */
    fun <T> sort(items: List<T>, descending: Boolean, keys: List<Key<T>>): SectionedList<T> {
        require(keys.isNotEmpty()) { "sorting needs at least one key" }

        val collator = Collator.getInstance(Locale.ROOT)
        val sorted = items
            .map { item ->
                val texts = keys.map { sortKey(it.select(item), it.personName) }
                Sortable(item, sectionOfKey(texts[0]), texts.map(collator::getCollationKey))
            }
            .sortedWith(SORTABLE_ORDER)
            .let { if (descending) it.asReversed() else it }

        return SectionedList(sorted.map { it.item }, sorted.map { it.section }, descending)
    }

    private class Sortable<T>(val item: T, val section: String, val keys: List<CollationKey>)

    private val SORTABLE_ORDER = Comparator<Sortable<*>> { a, b ->
        val bySection = SECTIONS.indexOf(a.section).compareTo(SECTIONS.indexOf(b.section))
        if (bySection != 0) return@Comparator bySection

        for (i in a.keys.indices) {
            val byKey = a.keys[i].compareTo(b.keys[i])
            if (byKey != 0) return@Comparator byKey
        }

        0
    }

    private object HanToLatin {
        private val names: Transliterator? by lazy { create("Han-Latin/Names; Latin-ASCII") ?: titles }
        private val titles: Transliterator? by lazy { create("Han-Latin; Latin-ASCII") }

        // every Android build is expected to carry these rules, so a missing one only leaves text as it is
        private fun create(id: String): Transliterator? = try {
            Transliterator.getInstance(id)
        } catch (_: IllegalArgumentException) {
            null
        }

        // a transliterator is not safe to share between threads
        fun apply(text: String, personName: Boolean): String {
            val transliterator = (if (personName) names else titles) ?: return text
            return synchronized(transliterator) { transliterator.transliterate(text) }
        }
    }
}

/** Items sorted into the sections of [AlphabetIndex], with the position each section starts at. */
class SectionedList<T> internal constructor(
    val items: List<T>,
    private val itemSections: List<String>,
    private val descending: Boolean
) {
    /** Every section, in the order the list runs, including those with no items. */
    val sections: List<String> = if (descending) AlphabetIndex.SECTIONS.asReversed() else AlphabetIndex.SECTIONS

    private val firstPositions: Map<String, Int> = buildMap {
        itemSections.forEachIndexed { position, section -> putIfAbsent(section, position) }
    }

    fun hasItems(sectionIndex: Int): Boolean = sections[sectionIndex] in firstPositions

    /**
     * Where to jump for a section: its first item, or for an empty section the first item of the next
     * section that has one, or of the closest one before it at the end of the list. -1 for an empty list.
     */
    fun positionForSection(sectionIndex: Int): Int {
        for (i in sectionIndex until sections.size) firstPositions[sections[i]]?.let { return it }
        for (i in sectionIndex - 1 downTo 0) firstPositions[sections[i]]?.let { return it }
        return -1
    }

    fun <R> map(transform: (T) -> R): SectionedList<R> = SectionedList(items.map(transform), itemSections, descending)

    companion object {
        @JvmStatic
        fun <T> empty(): SectionedList<T> = SectionedList(emptyList(), emptyList(), false)
    }
}
