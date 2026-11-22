package dev.kebugcheck.gelli.activities

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.kebugcheck.gelli.util.LocalLibrary
import dev.kebugcheck.gelli.util.ServerSongInventory

internal class DownloadsViewModel : ViewModel() {
    val overview = DownloadsOverview(viewModelScope, object : DownloadsRepository {
        override suspend fun local(accountId: String) = LocalLibrary.getSummary(accountId)
        override suspend fun inventory(accountId: String) = ServerSongInventory.read(accountId)
        override suspend fun refresh(accountId: String) = ServerSongInventory.refresh(accountId)
    })
}
