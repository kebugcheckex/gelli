package com.dkanada.gramophone.mapper;

import com.dkanada.gramophone.model.SortOrder;

public final class LegacySortMapper {
    private LegacySortMapper() {
    }

    public static org.jellyfin.apiclient.model.entities.SortOrder toApi(SortOrder sortOrder) {
        if (sortOrder == SortOrder.ASCENDING) {
            return org.jellyfin.apiclient.model.entities.SortOrder.Ascending;
        }
        return org.jellyfin.apiclient.model.entities.SortOrder.Descending;
    }
}
