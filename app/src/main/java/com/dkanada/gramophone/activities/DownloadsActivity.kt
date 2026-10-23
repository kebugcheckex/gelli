package com.dkanada.gramophone.activities

import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.format.Formatter
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.dkanada.gramophone.R
import com.dkanada.gramophone.activities.base.AbsBaseActivity
import com.dkanada.gramophone.databinding.ActivityDownloadsBinding
import com.dkanada.gramophone.model.NetworkPolicy
import com.dkanada.gramophone.util.LocalLibrary
import com.dkanada.gramophone.util.NetworkUtil
import com.dkanada.gramophone.util.OfflineCoverage
import com.dkanada.gramophone.util.PreferenceUtil
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date

class DownloadsActivity : AbsBaseActivity(), LocalLibrary.Listener, NetworkUtil.Listener,
    SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var binding: ActivityDownloadsBinding
    private val overview by lazy { ViewModelProvider(this)[DownloadsViewModel::class.java].overview }
    private val preferences get() = PreferenceUtil.getInstance(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDownloadsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setBackgroundColor(preferences.primaryColor)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        val accent = ColorStateList.valueOf(preferences.accentColor)
        binding.coverageProgress.progressTintList = accent
        binding.loading.indeterminateTintList = accent
        ViewCompat.setScreenReaderFocusable(binding.coverageSummary, true)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                overview.state.collect(::render)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        LocalLibrary.addListener(this)
        NetworkUtil.addListener(this)
        preferences.registerOnSharedPreferenceChangedListener(this)
        openAccount()
    }

    override fun onStop() {
        LocalLibrary.removeListener(this)
        NetworkUtil.removeListener(this)
        preferences.unregisterOnSharedPreferenceChangedListener(this)
        super.onStop()
    }

    private fun openAccount() = overview.open(preferences.user, connected())

    // Inventory requests only transfer metadata; streaming/saving policies don't gate this check.
    private fun connected() = NetworkUtil.isAllowed(this, NetworkPolicy.ANY)

    override fun onLibraryChanged() = overview.localChanged()
    override fun onNetworkChanged() = overview.networkChanged(connected())

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == PreferenceUtil.USER) openAccount()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_downloads, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val state = overview.state.value
        menu.findItem(R.id.refresh_downloads)?.isEnabled = state.accountId != null && !state.refreshing
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.refresh_downloads) {
            overview.refresh()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun render(state: DownloadsState) {
        val hasAccount = state.accountId != null
        binding.noAccount.isVisible = !hasAccount
        binding.scope.isVisible = hasAccount
        binding.coverageCard.isVisible = hasAccount
        binding.storageCard.isVisible = hasAccount
        val busy = state.refreshing || state.loadingInventory || (state.loadingLocal && state.local == null)
        binding.loading.visibility = if (hasAccount && busy) View.VISIBLE else View.INVISIBLE
        invalidateOptionsMenu()
        if (!hasAccount) return

        val local = state.local.takeUnless { state.localFailed }
        val coverage = local?.let { OfflineCoverage.from(it, state.inventory) }
        val numbers = NumberFormat.getIntegerInstance()
        val headline = when {
            state.localFailed -> getString(R.string.downloads_local_failed)
            local == null -> getString(R.string.downloads_checking_local)
            coverage?.totalSongCount == 0 -> getString(R.string.downloads_empty_library)
            coverage?.isBelowOnePercent == true -> getString(R.string.downloads_below_one)
            coverage?.percentage != null -> getString(R.string.downloads_percentage, numbers.format(coverage.percentage))
            else -> getString(R.string.downloads_available)
        }
        binding.coverageTitle.text = headline
        val count = when {
            coverage?.totalSongCount != null && coverage.totalSongCount > 0 -> resources.getQuantityString(
                R.plurals.downloads_coverage_count, coverage.totalSongCount,
                numbers.format(coverage.availableSongCount), numbers.format(coverage.totalSongCount))
            local != null -> resources.getQuantityString(R.plurals.downloads_saved_count,
                local.availableSongCount, numbers.format(local.availableSongCount))
            else -> null
        }
        binding.coverageCount.showText(count)
        val accessibleHeadline = if (coverage?.isBelowOnePercent == true) {
            getString(R.string.downloads_below_one_accessibility)
        } else headline
        binding.coverageSummary.contentDescription = listOfNotNull(count, accessibleHeadline).joinToString(". ")
        binding.coverageProgress.isVisible = coverage?.percentage != null
        if (coverage?.percentage != null) {
            // Preserve precision for small libraries and never display a full bar before completion.
            binding.coverageProgress.progress = (coverage.availableSongCount.toLong() * 10000 /
                coverage.totalSongCount!!).toInt()
        }
        binding.libraryChecked.showText(state.inventory?.let {
            getString(R.string.downloads_checked, DateFormat.getDateTimeInstance(
                DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it.checkedAt)))
        })
        binding.libraryStatus.showText(when {
            !state.connected -> getString(if (state.inventory == null) R.string.downloads_offline_unknown
                else R.string.downloads_offline_snapshot)
            state.refreshing || state.loadingInventory -> getString(R.string.downloads_checking_server)
            state.refreshFailed || state.inventoryFailed -> getString(if (state.inventory == null)
                R.string.downloads_refresh_failed_unknown else R.string.downloads_refresh_failed_snapshot)
            state.inventory == null -> getString(R.string.downloads_inventory_unknown)
            else -> null
        })
        binding.outsideInventory.showText(coverage?.savedOutsideInventoryCount?.takeIf { it > 0 }?.let {
            resources.getQuantityString(R.plurals.downloads_outside_inventory, it, numbers.format(it))
        })
        binding.audioUsage.text = when {
            state.localFailed -> getString(R.string.downloads_storage_unknown)
            local == null -> getString(R.string.downloads_checking_storage)
            local.audioBytes == null -> getString(R.string.downloads_storage_unknown)
            else -> getString(R.string.downloads_audio_usage, Formatter.formatFileSize(this, local.audioBytes))
        }
        binding.storageWarning.isVisible = local != null && local.audioBytes == null
    }

    private fun TextView.showText(value: String?) {
        text = value
        isVisible = !value.isNullOrEmpty()
    }
}
