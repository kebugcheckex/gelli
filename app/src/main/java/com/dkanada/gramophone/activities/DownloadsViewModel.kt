package com.dkanada.gramophone.activities

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dkanada.gramophone.util.LocalLibrary
import com.dkanada.gramophone.util.ServerSongInventory

internal class DownloadsViewModel : ViewModel() {
    val overview = DownloadsOverview(viewModelScope, object : DownloadsRepository {
        override suspend fun local(accountId: String) = LocalLibrary.getSummary(accountId)
        override suspend fun inventory(accountId: String) = ServerSongInventory.read(accountId)
        override suspend fun refresh(accountId: String) = ServerSongInventory.refresh(accountId)
    })
}
