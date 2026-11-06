package com.dkanada.gramophone.adapter.base

import android.content.Context
import android.content.res.ColorStateList
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.core.view.MenuItemCompat
import androidx.recyclerview.widget.RecyclerView
import com.dkanada.gramophone.R
import com.google.android.material.color.MaterialColors
import java.util.*

/**
 * Keeps the checked items and shows them in an action mode over the toolbar. Starting a mode finishes any
 * other one in the activity, so only one adapter has a selection at a time.
 */
abstract class AbsMultiSelectAdapter<VH : RecyclerView.ViewHolder, I>(
    private val context: Context,
    private var menuRes: Int,
) : RecyclerView.Adapter<VH>() {
    private var checked: MutableList<I> = ArrayList()
    private var actionMode: ActionMode? = null

    private val actionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.menuInflater.inflate(menuRes, menu)
            tintIcons(menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean = onSelection(item)

        override fun onDestroyActionMode(mode: ActionMode) {
            actionMode = null
            checkNone()
        }
    }

    protected fun isChecked(identifier: I): Boolean {
        return checked.contains(identifier)
    }

    protected fun isActive(): Boolean {
        return actionMode != null
    }

    protected fun toggleChecked(position: Int): Boolean {
        val identifier = getIdentifier(position)

        if (identifier != null && !checked.remove(identifier)) {
            checked.add(identifier)
        }

        notifyItemChanged(position)
        updateActionMode()

        return true
    }

    protected fun setMenu(menuRes: Int) {
        this.menuRes = menuRes
    }

    private fun updateActionMode() {
        if (checked.isEmpty()) {
            actionMode?.finish()
            return
        }

        if (actionMode == null) {
            actionMode = (context as AppCompatActivity).startSupportActionMode(actionModeCallback)
        }

        actionMode?.title = if (checked.size == 1) {
            getName(checked[0])
        } else {
            context.getString(R.string.x_selected, checked.size)
        }
    }

    // the menu icons are white for the old colored bar, and the mode now sits on the surface
    private fun tintIcons(menu: Menu) {
        val color = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, 0)
        val tint = ColorStateList.valueOf(color)

        for (i in 0 until menu.size()) {
            MenuItemCompat.setIconTintList(menu.getItem(i), tint)
        }
    }

    private fun checkAll() {
        checked.clear()

        for (i in 0 until itemCount) {
            val identifier = getIdentifier(i)

            if (identifier != null) {
                checked.add(identifier)
            }
        }

        notifyDataSetChanged()
        updateActionMode()
    }

    private fun checkNone() {
        checked.clear()
        notifyDataSetChanged()
    }

    private fun onSelection(menuItem: MenuItem): Boolean {
        if (menuItem.itemId == R.id.action_select_all) {
            checkAll()
        } else {
            onMultipleItemAction(menuItem, ArrayList(checked))
            actionMode?.finish()
        }

        return true
    }

    protected abstract fun getName(identifier: I): String?

    protected abstract fun getIdentifier(position: Int): I?

    @JvmSuppressWildcards
    protected abstract fun onMultipleItemAction(menuItem: MenuItem, selection: List<I>)
}
