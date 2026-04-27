package com.dkanada.gramophone.mapper

import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ImageType
import java.util.UUID

internal object SdkMapperUtil {
    fun uuidToId(value: UUID?): String {
        return value?.toString()?.replace("-", "") ?: ""
    }

    fun hasPrimary(item: BaseItemDto): Boolean {
        return item.imageTags?.containsKey(ImageType.PRIMARY) == true
    }

    fun firstPrimaryBlurHash(item: BaseItemDto): String? {
        return item.imageBlurHashes
            ?.get(ImageType.PRIMARY)
            ?.values
            ?.firstOrNull()
    }
}
