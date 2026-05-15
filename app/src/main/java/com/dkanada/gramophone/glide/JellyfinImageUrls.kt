package com.dkanada.gramophone.glide

import com.dkanada.gramophone.util.JellyfinSdkSession
import com.dkanada.gramophone.util.QueryUtil
import org.jellyfin.sdk.api.client.util.UrlBuilder
import org.jellyfin.sdk.model.api.ImageType

object JellyfinImageUrls {

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
