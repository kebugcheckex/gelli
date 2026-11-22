package com.dkanada.gramophone.glide

import com.bumptech.glide.load.model.GlideUrl
import com.dkanada.gramophone.util.JellyfinSdkSession
import com.dkanada.gramophone.util.QueryUtil
import org.jellyfin.sdk.api.client.util.UrlBuilder
import org.jellyfin.sdk.model.api.ImageType

object JellyfinImageUrls {

    /**
     * The primary image as a Glide model. It is cached by item rather than by url, so a cover fetched through
     * one address of the server is not fetched again through another. Item ids are GUIDs, so the key can't
     * mix up the covers of two servers either.
     */
    @JvmStatic
    @JvmOverloads
    fun primaryImage(itemId: String, maxHeight: Int = 800): GlideUrl =
        CachedGlideUrl(buildPrimaryImageUrl(itemId, maxHeight, JellyfinSdkSession.getBaseUrl()), primaryImageKey(itemId, maxHeight))

    internal fun primaryImageKey(itemId: String, maxHeight: Int): String {
        val id = QueryUtil.toUuidOrNull(itemId)?.toString() ?: itemId
        return "jellyfin/items/$id/primary?maxHeight=$maxHeight"
    }

    /** Fetches the url but caches under the key. */
    private class CachedGlideUrl(url: String, private val key: String) : GlideUrl(url) {
        override fun getCacheKey(): String = key
    }

    @JvmStatic
    @JvmOverloads
    fun buildPrimaryImageUrl(itemId: String, maxHeight: Int = 800): String =
        buildPrimaryImageUrl(itemId, maxHeight, JellyfinSdkSession.getBaseUrl())

    internal fun buildPrimaryImageUrl(
        itemId: String,
        maxHeight: Int = 800,
        baseUrl: String?,
    ): String {
        val normalizedBase = baseUrl?.trimEnd('/')?.takeIf { it.isNotBlank() }
        val itemUuid = QueryUtil.toUuidOrNull(itemId)

        if (normalizedBase == null || itemUuid == null) {
            return "/Items/$itemId/Images/Primary?maxHeight=$maxHeight"
        }

        // Delegate to the same UrlBuilder the SDK's ImageApi uses, so the URL shape
        // matches SDK-issued URLs exactly.
        return UrlBuilder.buildUrl(
            baseUrl = normalizedBase,
            pathTemplate = "/Items/{itemId}/Images/{imageType}",
            pathParameters = mapOf(
                "itemId" to itemUuid,
                "imageType" to ImageType.PRIMARY,
            ),
            queryParameters = mapOf("maxHeight" to maxHeight),
        )
    }
}
