package dev.kebugcheck.gelli.util

import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import dev.kebugcheck.gelli.App
import dev.kebugcheck.gelli.database.LocalSong
import dev.kebugcheck.gelli.model.User
import dev.kebugcheck.gelli.service.SaveWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Accounts are identified by the server's own id and the Jellyfin user id, never by an address. A server
 * reached through several addresses (e.g. on the LAN and through a tunnel) is one account, with one set of
 * stored songs, and signing in through another address only adds that address to it.
 *
 * Accounts saved before the server id was recorded are keyed by their address. They are folded into the
 * account for the same user when one is signed in or when they next connect. Jellyfin user ids are random
 * GUIDs, so a matching user id is the same user on the same server.
 */
object Accounts {
    private const val TAG = "Accounts"

    private val lock = Any()

    @JvmStatic
    fun accountId(serverId: String, jellyfinUserId: String): String = "$serverId:$jellyfinUserId"

    /** Server ids compare without dashes or case, the shape the server reports them in. */
    @JvmStatic
    fun normalizeServerId(id: String): String = id.replace("-", "").lowercase(Locale.ROOT)

    /** Adds the address to the end of the list unless the list has it already. */
    @JvmStatic
    @VisibleForTesting
    fun withAddress(addresses: List<String>, address: String): List<String> =
        if (address in addresses) addresses else addresses + address

    /** The list with the address at [from] moved to [to], the way a drag in the list moves it. */
    @JvmStatic
    @VisibleForTesting
    fun movedAddress(addresses: List<String>, from: Int, to: Int): List<String> =
        addresses.toMutableList().apply { add(to, removeAt(from)) }

    /** The list without the address, or null if the address is the only one, as an account needs one. */
    @JvmStatic
    @VisibleForTesting
    fun withoutAddress(addresses: List<String>, address: String): List<String>? {
        val remaining = addresses - address
        return remaining.takeIf { it.isNotEmpty() }
    }

    enum class AddResult { ADDED, KNOWN, INVALID, UNREACHABLE, OTHER_SERVER, UNIDENTIFIED }

    /**
     * Adds an address to the end of the account's list, after checking that it reaches the account's server.
     * No sign in is needed, as the token works through every address of the server.
     */
    suspend fun addAddress(accountId: String, raw: String): AddResult = withContext(Dispatchers.IO) {
        addAddressBlocking(accountId, raw)
    }

    private suspend fun addAddressBlocking(accountId: String, raw: String): AddResult {
        val address = JellyfinSdkSession.normalizeAddress(raw) ?: return AddResult.INVALID
        val user = App.getDatabase().userDao().getUser(accountId) ?: return AddResult.INVALID
        // an account saved before server ids were recorded has nothing to check the address against
        val serverId = user.serverId ?: return AddResult.UNIDENTIFIED
        if (address in user.addresses) return AddResult.KNOWN

        val id = ServerConnection.probe(address) ?: return AddResult.UNREACHABLE
        if (normalizeServerId(id) != normalizeServerId(serverId)) return AddResult.OTHER_SERVER

        val added = updateAddresses(accountId) { withAddress(it, address) }
        return if (added) AddResult.ADDED else AddResult.INVALID
    }

    /** Removes an address from the account, unless it is the last one. Returns whether it was removed. */
    @WorkerThread
    @JvmStatic
    fun removeAddress(accountId: String, address: String): Boolean =
        updateAddresses(accountId) { withoutAddress(it, address) }

    /** Sets the priority order of the account's addresses, which must be the addresses it has. */
    @WorkerThread
    @JvmStatic
    fun reorderAddresses(accountId: String, addresses: List<String>): Boolean =
        updateAddresses(accountId) { current -> addresses.takeIf { it.size == current.size && it.toSet() == current.toSet() } }

    /**
     * Replaces the account's addresses with what [change] returns for them, or leaves them if it returns
     * null. The last used address stays if it is still on the list, so the next start begins with it.
     */
    private fun updateAddresses(accountId: String, change: (List<String>) -> List<String>?): Boolean = synchronized(lock) {
        val users = App.getDatabase().userDao()
        val user = users.getUser(accountId) ?: return false
        val addresses = change(user.addresses.ifEmpty { listOfNotNull(user.server) }) ?: return false

        user.addresses = addresses
        if (user.server !in addresses) user.server = addresses.first()
        users.insertUser(user)
        return true
    }

    /** Saves a sign in through the address and returns the account it belongs to. */
    @WorkerThread
    @JvmStatic
    fun signIn(serverId: String, jellyfinUserId: String, name: String, token: String, address: String): User =
        merge(normalizeServerId(serverId), jellyfinUserId) { account ->
            account.name = name
            account.token = token
            account.server = address
            account.addresses = withAddress(account.addresses, address)
        }

    /**
     * Records the server id of an account saved without one, once its token has been checked against the
     * server, and returns the account it now belongs to.
     */
    @WorkerThread
    @JvmStatic
    fun identify(user: User, serverId: String): User {
        val jellyfinUserId = user.jellyfinUserId ?: return user

        return merge(normalizeServerId(serverId), jellyfinUserId) { account ->
            account.name = user.name
            account.token = user.token
            account.server = user.server
        }
    }

    /** Remembers the address the account last connected through, so the next start begins with it. */
    @JvmStatic
    fun useAddress(accountId: String, address: String) {
        App.getDatabase().userDao().setServer(accountId, address)
    }

    private fun merge(serverId: String, jellyfinUserId: String, update: (User) -> Unit): User = synchronized(lock) {
        val users = App.getDatabase().userDao()
        val id = accountId(serverId, jellyfinUserId)

        val account = users.getUser(id) ?: User(id, serverId, jellyfinUserId)
        val unidentified = users.getUnidentifiedUsers(jellyfinUserId).filter { it.id != id }
        for (user in unidentified) {
            account.addresses = user.addresses.fold(account.addresses, ::withAddress)
            if (account.name == null) account.name = user.name
        }

        update(account)
        users.insertUser(account)
        if (unidentified.isEmpty()) return account

        // a running save would keep writing into the folder that is being emptied
        val context = App.getInstance()
        SaveWorker.cancel(context)
        for (user in unidentified) {
            fold(user, id)
        }

        val preferences = PreferenceUtil.getInstance(context)
        if (unidentified.any { it.id == preferences.user }) preferences.user = id

        LocalLibrary.notifyChanged()
        LocalLibrary.verifyAsync()
        return account
    }

    /**
     * Moves the stored songs, covers, lyrics and inventory of an account into another and deletes it. Where
     * both have a song, the stored copy wins, and the account being kept wins a tie.
     *
     * Files move before rows, and a file found already moved is picked up, so an interrupted fold leaves the
     * old account in place to be folded again rather than rows pointing at moved files.
     */
    private fun fold(user: User, to: String) {
        val from = user.id
        val database = App.getDatabase()
        val songs = database.localSongDao()

        val existing = songs.getAllSongs(to).associateBy { it.itemId }
        val moved = ArrayList<LocalSong>()
        val dropped = ArrayList<LocalSong>()

        for (song in songs.getAllSongs(from)) {
            val current = existing[song.itemId]
            if (current != null && !replaces(song.state, current.state)) {
                dropped.add(song)
                continue
            }

            current?.let(dropped::add)
            song.filePath = song.filePath?.let { moveFile(it, from, to) }
            song.accountId = to
            // nothing is saving it any more
            if (song.state == LocalSong.State.DOWNLOADING) song.state = LocalSong.State.QUEUED
            moved.add(song)
        }

        database.runInTransaction {
            for (song in moved) songs.insertSong(song)
            songs.deleteAllSongs(from)
            database.songInventoryDao().move(from, to)
            database.userDao().deleteUser(user)
        }

        val kept = moved.mapNotNullTo(HashSet()) { it.filePath }
        for (path in dropped.mapNotNull { it.filePath }) {
            if (path !in kept) File(path).delete()
        }

        foldFolders(from, to, kept)
        Log.i(TAG, "fold: moved ${moved.size} songs into $to")
    }

    /** Whether a song from the account being folded in replaces the kept account's row for the same item. */
    @JvmStatic
    @VisibleForTesting
    fun replaces(incoming: LocalSong.State, current: LocalSong.State): Boolean =
        incoming == LocalSong.State.COMPLETE && current != LocalSong.State.COMPLETE

    /** Returns the path the file has after the move, which is the old path if it could not be moved. */
    private fun moveFile(path: String, from: String, to: String): String {
        val target = movedPath(path, LocalLibrary.accountDirName(from), LocalLibrary.accountDirName(to)) ?: return path
        val source = File(path)
        val destination = File(target)

        if (!source.exists()) return if (destination.exists()) target else path

        destination.parentFile?.mkdirs()
        if (source.renameTo(destination)) return target

        Log.w(TAG, "moveFile: could not move $path")
        return path
    }

    /**
     * The same file under the other account folder, for a file in a folder of the first one such as
     * <root>/<account>/audio/<file>. Returns null for a file outside the account folders.
     */
    @JvmStatic
    @VisibleForTesting
    fun movedPath(path: String, fromDir: String, toDir: String): String? {
        val file = File(path)
        val folder = file.parentFile ?: return null
        val account = folder.parentFile ?: return null
        val root = account.parentFile ?: return null
        if (account.name != fromDir) return null

        return File(File(File(root, toDir), folder.name), file.name).path
    }

    /** Moves the covers and lyrics across, then removes what is left of the old account folders. */
    private fun foldFolders(from: String, to: String, kept: Set<String>) {
        for (root in LocalLibrary.getRoots()) {
            val source = File(root, LocalLibrary.accountDirName(from))
            if (!source.isDirectory) continue
            val target = File(root, LocalLibrary.accountDirName(to))

            for (name in listOf("images", "lyrics")) {
                val files = File(source, name).listFiles() ?: continue
                for (file in files) {
                    val destination = File(File(target, name), file.name)
                    destination.parentFile?.mkdirs()
                    // the kept account's own copy of a cover or lyrics is as good as this one
                    if (destination.exists() || !file.renameTo(destination)) file.delete()
                }
            }

            File(source, "tmp").deleteRecursively()
            // only songs that could not be moved are left in use here
            File(source, "audio").listFiles()?.forEach { file ->
                if (file.absolutePath !in kept) file.delete()
            }

            for (name in listOf("images", "lyrics", "audio")) File(source, name).delete()
            source.delete()
        }
    }
}
