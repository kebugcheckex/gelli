package dev.kebugcheck.gelli.activities

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isInvisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.kebugcheck.gelli.App
import dev.kebugcheck.gelli.R
import dev.kebugcheck.gelli.activities.base.AbsBaseActivity
import dev.kebugcheck.gelli.databinding.ActivityAddressesBinding
import dev.kebugcheck.gelli.databinding.ItemAddressBinding
import dev.kebugcheck.gelli.model.User
import dev.kebugcheck.gelli.util.Accounts
import dev.kebugcheck.gelli.util.DialogUtil
import dev.kebugcheck.gelli.util.JellyfinSdkSession
import dev.kebugcheck.gelli.util.PreferenceUtil
import dev.kebugcheck.gelli.util.ServerConnection
import dev.kebugcheck.gelli.util.ViewUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The addresses of one account in priority order: the app uses the first one that answers. Addresses can be
 * added, removed and dragged into another order. Each one is probed when the screen opens, to show which
 * work on the current network.
 */
class AddressesActivity : AbsBaseActivity() {
    private lateinit var binding: ActivityAddressesBinding
    private lateinit var accountId: String

    private val adapter = AddressAdapter()
    private val touchHelper = ItemTouchHelper(DragCallback())

    private enum class Status { CHECKING, REACHABLE, UNREACHABLE, OTHER_SERVER }

    private val statuses = HashMap<String, Status>()

    private var user: User? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        accountId = intent.getStringExtra(EXTRA_ACCOUNT_ID) ?: run {
            finish()
            return
        }

        binding = ActivityAddressesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        ViewUtil.capContentWidth(binding.recyclerView)
        touchHelper.attachToRecyclerView(binding.recyclerView)

        binding.add.setOnClickListener { showAddDialog() }
    }

    // a change of order moves the session to another address a moment later
    @SuppressLint("NotifyDataSetChanged")
    private val connectionListener = ServerConnection.Listener { adapter.notifyDataSetChanged() }

    override fun onStart() {
        super.onStart()
        ServerConnection.addListener(connectionListener)
        load(probe = true)
    }

    override fun onStop() {
        ServerConnection.removeListener(connectionListener)
        super.onStop()
    }

    private val isCurrentAccount get() = PreferenceUtil.getInstance(this).user == accountId

    private fun load(probe: Boolean) = lifecycleScope.launch {
        val loaded = withContext(Dispatchers.IO) { App.getDatabase().userDao().getUser(accountId) }
        if (loaded == null) {
            finish()
            return@launch
        }

        user = loaded
        supportActionBar?.subtitle = loaded.name
        adapter.submit(loaded.addresses.ifEmpty { listOfNotNull(loaded.server) })
        if (probe) probeAll()
    }

    private fun probeAll() {
        val expected = user?.serverId?.let(Accounts::normalizeServerId)

        for (address in adapter.addresses) {
            statuses[address] = Status.CHECKING
            lifecycleScope.launch {
                val id = withContext(Dispatchers.IO) { ServerConnection.probe(address) }
                statuses[address] = when {
                    id == null -> Status.UNREACHABLE
                    expected != null && Accounts.normalizeServerId(id) != expected -> Status.OTHER_SERVER
                    else -> Status.REACHABLE
                }
                adapter.refresh(address)
            }
        }
        adapter.notifyDataSetChanged()
    }

    /** The address the app talks to for this account: the one in use now, or the one used last. */
    private fun activeAddress(): String? =
        if (isCurrentAccount) JellyfinSdkSession.getBaseUrl() else user?.server

    private fun showAddDialog() {
        DialogUtil.createTextInputDialog(
            this,
            R.string.add_address,
            R.string.address_hint,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI,
            R.string.add
        ) { text -> add(text) }.show()
    }

    private fun add(text: String) = lifecycleScope.launch {
        binding.add.isEnabled = false
        val result = Accounts.addAddress(accountId, text)
        binding.add.isEnabled = true

        val message = when (result) {
            Accounts.AddResult.ADDED -> R.string.address_added
            Accounts.AddResult.KNOWN -> R.string.address_known
            Accounts.AddResult.INVALID -> R.string.error_empty_server
            Accounts.AddResult.UNREACHABLE -> R.string.error_unreachable_server
            Accounts.AddResult.OTHER_SERVER -> R.string.address_other_server_add
            Accounts.AddResult.UNIDENTIFIED -> R.string.address_unidentified
        }
        Toast.makeText(this@AddressesActivity, message, Toast.LENGTH_SHORT).show()

        if (result == Accounts.AddResult.ADDED) {
            // the probe that let it in found it reachable
            JellyfinSdkSession.normalizeAddress(text)?.let { statuses[it] = Status.REACHABLE }
            changed()
        }
    }

    private fun remove(address: String) = lifecycleScope.launch {
        val removed = withContext(Dispatchers.IO) { Accounts.removeAddress(accountId, address) }
        if (removed) changed()
    }

    private fun reorder(addresses: List<String>) = lifecycleScope.launch {
        val saved = withContext(Dispatchers.IO) { Accounts.reorderAddresses(accountId, addresses) }
        if (saved) changed() else load(probe = false)
    }

    private fun changed() {
        if (isCurrentAccount) ServerConnection.addressesChanged()
        load(probe = false)
    }

    private inner class AddressAdapter : RecyclerView.Adapter<AddressAdapter.ViewHolder>() {
        val addresses = ArrayList<String>()

        @SuppressLint("NotifyDataSetChanged")
        fun submit(list: List<String>) {
            addresses.clear()
            addresses.addAll(list)
            notifyDataSetChanged()
        }

        fun refresh(address: String) {
            val position = addresses.indexOf(address)
            if (position >= 0) notifyItemChanged(position)
        }

        fun move(from: Int, to: Int) {
            val moved = Accounts.movedAddress(addresses, from, to)
            addresses.clear()
            addresses.addAll(moved)
            notifyItemMoved(from, to)
        }

        override fun getItemCount() = addresses.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            ViewHolder(ItemAddressBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(addresses[position])

        @SuppressLint("ClickableViewAccessibility")
        inner class ViewHolder(private val item: ItemAddressBinding) : RecyclerView.ViewHolder(item.root) {
            init {
                item.dragView.setOnTouchListener { _, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) touchHelper.startDrag(this)
                    false
                }
                item.delete.setOnClickListener {
                    val position = bindingAdapterPosition
                    if (position != RecyclerView.NO_POSITION) remove(addresses[position])
                }
            }

            fun bind(address: String) {
                item.address.text = address

                val status = getString(
                    when (statuses[address]) {
                        Status.REACHABLE -> R.string.address_reachable
                        Status.UNREACHABLE -> R.string.address_unreachable
                        Status.OTHER_SERVER -> R.string.address_other_server
                        Status.CHECKING, null -> R.string.address_checking
                    }
                )
                item.status.text = when (address) {
                    activeAddress() -> getString(
                        if (isCurrentAccount) R.string.address_in_use else R.string.address_last_used,
                        status
                    )
                    else -> status
                }

                // an account needs an address, and one address leaves nothing to order
                item.delete.isInvisible = addresses.size < 2
                item.dragView.isInvisible = addresses.size < 2
            }
        }
    }

    private inner class DragCallback : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
        private var moved = false

        override fun isLongPressDragEnabled() = false

        override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
            val from = viewHolder.bindingAdapterPosition
            val to = target.bindingAdapterPosition
            if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false

            adapter.move(from, to)
            moved = true
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

        // saves once the item is dropped, rather than on every step of the drag
        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            if (!moved) return

            moved = false
            reorder(adapter.addresses.toList())
        }
    }

    companion object {
        private const val EXTRA_ACCOUNT_ID = "account_id"

        @JvmStatic
        fun start(context: Context, accountId: String) {
            context.startActivity(Intent(context, AddressesActivity::class.java).putExtra(EXTRA_ACCOUNT_ID, accountId))
        }
    }
}
