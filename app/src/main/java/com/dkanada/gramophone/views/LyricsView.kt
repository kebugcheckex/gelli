package com.dkanada.gramophone.views

import android.content.Context
import android.graphics.Rect
import android.os.SystemClock
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import com.dkanada.gramophone.R
import com.dkanada.gramophone.model.Lyrics
import kotlin.math.max

/**
 * The lyrics shown in a player, or a short message in their place. Synced lyrics keep the current line centered
 * and dim the others, and unsynced ones are a plain list. The player supplies the colors.
 */
class LyricsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {
    private val list: RecyclerView
    private val message: TextView
    private val layoutManager = LinearLayoutManager(context)
    private val adapter = LineAdapter()

    private var lyrics: Lyrics? = null
    private var currentLine = -1

    private var userScrolling = false
    private var followPausedUntil = 0L

    init {
        LayoutInflater.from(context).inflate(R.layout.view_lyrics, this, true)
        list = findViewById(R.id.lyrics_list)
        message = findViewById(R.id.lyrics_message)

        list.layoutManager = layoutManager
        list.adapter = adapter
        // a change of line recolors two rows, and the default animation would make them blink
        list.itemAnimator = null
        list.addItemDecoration(EdgeSpace())

        list.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                // the player's panel would otherwise take a vertical drag and close the player
                if (e.actionMasked == MotionEvent.ACTION_DOWN && (rv.canScrollVertically(1) || rv.canScrollVertically(-1))) {
                    rv.parent.requestDisallowInterceptTouchEvent(true)
                }

                return false
            }
        })

        list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    userScrolling = true
                } else if (newState == RecyclerView.SCROLL_STATE_IDLE && userScrolling) {
                    userScrolling = false
                    followPausedUntil = SystemClock.uptimeMillis() + FOLLOW_PAUSE
                }
            }
        })

        list.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop) {
                list.post {
                    list.invalidateItemDecorations()
                    if (currentLine >= 0) jumpTo(currentLine)
                }
            }
        }
    }

    fun setTextColor(@ColorInt color: Int) {
        message.setTextColor(color)
        adapter.setTextColor(color)
    }

    fun setLyrics(lyrics: Lyrics?) {
        message.visibility = GONE
        list.visibility = VISIBLE

        if (this.lyrics === lyrics) return

        this.lyrics = lyrics
        currentLine = -1
        userScrolling = false
        followPausedUntil = 0L

        list.stopScroll()
        adapter.setLyrics(lyrics)
        list.invalidateItemDecorations()
        layoutManager.scrollToPositionWithOffset(0, 0)
    }

    /** Replaces the lyrics with a message, or with nothing at all when it is null. */
    fun setMessage(text: CharSequence?) {
        setLyrics(null)
        list.visibility = GONE
        message.text = text
        message.visibility = if (text == null) GONE else VISIBLE
    }

    fun setCurrentLine(line: Int) {
        if (line == currentLine) return

        currentLine = line
        adapter.setCurrent(line)

        if (userScrolling || SystemClock.uptimeMillis() < followPausedUntil) return

        // before the first line, stay at the first line
        val target = max(line, 0)
        val first = layoutManager.findFirstVisibleItemPosition()
        val last = layoutManager.findLastVisibleItemPosition()
        if (first == RecyclerView.NO_POSITION || target < first - JUMP_DISTANCE || target > last + JUMP_DISTANCE) {
            jumpTo(target)
        } else {
            smoothCenter(target)
        }
    }

    /** Far away, as after a seek, scrolling there would be a blur. The row's height isn't known until it is laid out. */
    private fun jumpTo(position: Int) {
        list.stopScroll()
        layoutManager.scrollToPositionWithOffset(position, list.height / 2)
        list.post { smoothCenter(position) }
    }

    private fun smoothCenter(position: Int) {
        if (position >= adapter.itemCount) return

        layoutManager.startSmoothScroll(CenterScroller(context).apply { targetPosition = position })
    }

    private class CenterScroller(context: Context) : LinearSmoothScroller(context) {
        override fun calculateDtToFit(viewStart: Int, viewEnd: Int, boxStart: Int, boxEnd: Int, snapPreference: Int): Int =
            (boxStart + boxEnd) / 2 - (viewStart + viewEnd) / 2

        override fun calculateSpeedPerPixel(displayMetrics: DisplayMetrics): Float =
            SCROLL_MS_PER_INCH / displayMetrics.densityDpi
    }

    /** Lets the first and last lines of a synced lyric reach the center. */
    private inner class EdgeSpace : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
            if (lyrics?.isSynced != true) return

            val position = parent.getChildAdapterPosition(view)
            if (position == 0) outRect.top = parent.height / 2
            if (position == adapter.itemCount - 1) outRect.bottom = parent.height / 2
        }
    }

    private class LineHolder(val text: TextView) : RecyclerView.ViewHolder(text)

    private class LineAdapter : RecyclerView.Adapter<LineHolder>() {
        private var lines: List<Lyrics.Line> = emptyList()
        private var synced = false
        private var current = -1

        @ColorInt
        private var textColor = 0

        fun setLyrics(lyrics: Lyrics?) {
            lines = lyrics?.lines.orEmpty()
            synced = lyrics?.isSynced == true
            current = -1
            notifyDataSetChanged()
        }

        fun setTextColor(@ColorInt color: Int) {
            if (color == textColor) return

            textColor = color
            notifyDataSetChanged()
        }

        fun setCurrent(line: Int) {
            val previous = current
            current = line
            if (previous >= 0) notifyItemChanged(previous)
            if (line >= 0) notifyItemChanged(line)
        }

        override fun getItemCount() = lines.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            LineHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_lyrics_line, parent, false) as TextView)

        override fun onBindViewHolder(holder: LineHolder, position: Int) {
            holder.text.text = lines[position].text
            // only the color changes, since a different text size or weight could rewrap the line and move the list
            holder.text.setTextColor(if (!synced || position == current) textColor else ColorUtils.setAlphaComponent(textColor, DIMMED_ALPHA))
        }
    }

    private companion object {
        // about 3:1 on a mid-tone palette, which is the worst case for the player's text color rule
        const val DIMMED_ALPHA = 0xA0
        const val FOLLOW_PAUSE = 4000L
        const val JUMP_DISTANCE = 6
        const val SCROLL_MS_PER_INCH = 300f
    }
}
