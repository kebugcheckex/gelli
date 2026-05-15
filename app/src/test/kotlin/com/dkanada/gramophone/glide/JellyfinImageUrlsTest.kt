package com.dkanada.gramophone.glide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JellyfinImageUrlsTest {

    // --- Expected URL shape ---

    @Test
    fun buildPrimaryImageUrl_producesExpectedUrlForDashlessId() {
        val result = JellyfinImageUrls.buildPrimaryImageUrl(
            itemId = "aabbccddeeff00112233445566778899",
            maxHeight = 800,
            baseUrl = "https://example.com"
        )

        // SDK UrlBuilder appends the path to the base URL and adds query params.
        assertEquals(
            "https://example.com/Items/aabbccdd-eeff-0011-2233-445566778899/Images/Primary?maxHeight=800",
            result
        )
    }

    @Test
    fun buildPrimaryImageUrl_producesExpectedUrlForDashedId() {
        val result = JellyfinImageUrls.buildPrimaryImageUrl(
            itemId = "aabbccdd-eeff-0011-2233-445566778899",
            maxHeight = 800,
            baseUrl = "https://example.com"
        )

        assertEquals(
            "https://example.com/Items/aabbccdd-eeff-0011-2233-445566778899/Images/Primary?maxHeight=800",
            result
        )
    }

    // --- maxHeight is honoured ---

    @Test
    fun buildPrimaryImageUrl_honorsCustomMaxHeight() {
        val result = JellyfinImageUrls.buildPrimaryImageUrl(
            itemId = "aabbccddeeff00112233445566778899",
            maxHeight = 400,
            baseUrl = "https://example.com"
        )

        assertTrue("URL must contain maxHeight=400", result.contains("maxHeight=400"))
    }

    @Test
    fun buildPrimaryImageUrl_defaultMaxHeightIs800() {
        // Call the public overload without a session — it falls back to the path-only
        // variant, but we can verify the default through the internal overload too.
        val result = JellyfinImageUrls.buildPrimaryImageUrl(
            itemId = "aabbccddeeff00112233445566778899",
            baseUrl = "https://example.com"
        )

        assertTrue("URL must contain maxHeight=800 by default", result.contains("maxHeight=800"))
    }

    // --- Null / blank baseUrl returns path-only URL ---

    @Test
    fun buildPrimaryImageUrl_returnsPathOnly_whenBaseUrlIsNull() {
        val result = JellyfinImageUrls.buildPrimaryImageUrl(
            itemId = "aabbccddeeff00112233445566778899",
            maxHeight = 800,
            baseUrl = null
        )

        // Must start with a slash, no scheme or host.
        assertTrue("path-only URL must start with /", result.startsWith("/"))
        assertTrue("path-only URL must contain item ID segment", result.contains("aabbccddeeff00112233445566778899"))
        assertTrue("path-only URL must contain maxHeight", result.contains("maxHeight=800"))
    }

    @Test
    fun buildPrimaryImageUrl_returnsPathOnly_whenBaseUrlIsBlank() {
        val result = JellyfinImageUrls.buildPrimaryImageUrl(
            itemId = "aabbccddeeff00112233445566778899",
            maxHeight = 800,
            baseUrl = "   "
        )

        assertTrue("path-only URL must start with /", result.startsWith("/"))
    }

    // --- Trailing slash on baseUrl is normalised ---

    @Test
    fun buildPrimaryImageUrl_normalizesTrailingSlash() {
        val withSlash = JellyfinImageUrls.buildPrimaryImageUrl(
            itemId = "aabbccddeeff00112233445566778899",
            maxHeight = 800,
            baseUrl = "https://example.com/"
        )
        val withoutSlash = JellyfinImageUrls.buildPrimaryImageUrl(
            itemId = "aabbccddeeff00112233445566778899",
            maxHeight = 800,
            baseUrl = "https://example.com"
        )

        // Both must produce the same URL with no double-slash.
        assertEquals(withoutSlash, withSlash)
        assertTrue("URL must not contain double slash", !withSlash.contains("//Items"))
    }

    // --- URL structure: path segments present ---

    @Test
    fun buildPrimaryImageUrl_containsExpectedPathSegments() {
        val result = JellyfinImageUrls.buildPrimaryImageUrl(
            itemId = "aabbccddeeff00112233445566778899",
            maxHeight = 800,
            baseUrl = "https://example.com"
        )

        assertTrue(result.contains("/Items/"))
        assertTrue(result.contains("/Images/Primary"))
    }

    // --- Invalid item ID falls back to path-only ---

    @Test
    fun buildPrimaryImageUrl_returnsPathOnly_whenItemIdIsInvalidUuid() {
        val result = JellyfinImageUrls.buildPrimaryImageUrl(
            itemId = "not-a-valid-uuid",
            maxHeight = 800,
            baseUrl = "https://example.com"
        )

        // Can't build a proper URL without a valid UUID — fall back to path-only.
        assertTrue("must start with /", result.startsWith("/"))
    }
}
