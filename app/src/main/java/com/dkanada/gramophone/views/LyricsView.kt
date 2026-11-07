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
 * The lyrics shown in a player, or a short message in their place. The player supplies the colors.
 *
 * Synced lyrics fill the current line as it plays, either in a list that keeps the current line centered, or as a
 * single focused line with optional neighbors. Unsynced lyrics are a plain list either way.
 */
class LyricsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {
    private val list: RecyclerView
    private val focus: View
    private val focusPrevious: LyricLineView
    private val focusCurrent: LyricLineView
    private val focusNext: LyricLineView
    private val message: TextView
    private val layoutManager = LinearLayoutManager(context)
    private val adapter = LineAdapter()

    private var lyrics: Lyrics? = null
    private var currentLine = -1
    private var currentFill = 0f

    private var focused = false

    private var userScrolling = false
    private var followPausedUntil = 0L

    init {
        LayoutInflater.from(context).inflate(R.layout.view_lyrics, this, true)
        list = findViewById(R.id.lyrics_list)
        focus = findViewById(R.id.lyrics_focus)
        focusPrevious = findViewById(R.id.lyrics_focus_previous)
        focusCurrent = findViewById(R.id.lyrics_focus_current)
        focusNext = findViewById(R.id.lyrics_focus_next)
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

    /**
     * Shows a synced lyric as one large line instead of a list. The lines before and after it are shown smaller
     * only with [showNeighbors], which is for hosts tall enough to fit them.
     */
    fun setFocusedLine(focused: Boolean, showNeighbors: Boolean) {
        this.focused = focused
        val neighbors = if (focused && showNeighbors) VISIBLE else GONE
        focusPrevious.visibility = neighbors
        focusNext.visibility = neighbors

        updatePresentation()
    }

    fun setTextColor(@ColorInt color: Int) {
        val dimmed = dim(color)

        message.setTextColor(color)
        adapter.setTextColor(color, dimmed)

        for (line in listOf(focusPrevious, focusCurrent, focusNext)) {
            line.setTextColor(dimmed)
            line.fillColor = color
        }
    }

    fun setLyrics(lyrics: Lyrics?) {
        message.visibility = GONE

        if (this.lyrics === lyrics) {
            updatePresentation()
            return
        }

        this.lyrics = lyrics
        currentLine = -1
        currentFill = 0f
        userScrolling = false
        followPausedUntil = 0L

        list.stopScroll()
        adapter.setLyrics(lyrics)
        list.invalidateItemDecorations()
        layoutManager.scrollToPositionWithOffset(0, 0)

        updatePresentation()
        updateFocus()
    }

    /** Replaces the lyrics with a message, or with nothing at all when it is null. */
    fun setMessage(text: CharSequence?) {
        setLyrics(null)
        list.visibility = GONE
        focus.visibility = GONE
        message.text = text
        message.visibility = if (text == null) GONE else VISIBLE
    }

    /** The line is -1 before the first line, and the fill is how far the line has played, from 0 to 1. */
    fun setPosition(line: Int, fill: Float) {
        if (line != currentLine) {
            currentLine = line
            currentFill = fill
            adapter.setCurrent(line, fill)
            updateFocus()
            follow(line)
            return
        }

        if (fill == currentFill) return
        currentFill = fill

        // only the current row redraws, and nothing is rebound or laid out again
        adapter.setCurrentFill(fill)
        (list.findViewHolderForAdapterPosition(line) as? LineHolder)?.line?.fill = fill
        if (line >= 0) focusCurrent.fill = fill
    }

    private fun isFocusShown() = focused && lyrics?.isSynced == true

    private fun updatePresentation() {
        val focusShown = isFocusShown()
        list.visibility = if (focusShown) GONE else VISIBLE
        focus.visibility = if (focusShown) VISIBLE else GONE
    }

    /** Before the first line, the first line waits unfilled in the current line's place. */
    private fun updateFocus() {
        val lines = lyrics?.lines.orEmpty()
        val shown = max(currentLine, 0)

        // the text only changes with the line, so TalkBack reads each line once rather than on every frame
        focusPrevious.text = lines.getOrNull(shown - 1)?.takeIf { currentLine >= 0 }?.text.orEmpty()
        focusCurrent.text = lines.getOrNull(shown)?.text.orEmpty()
        focusNext.text = lines.getOrNull(shown + 1)?.text.orEmpty()
        focusCurrent.fill = if (currentLine >= 0) currentFill else 0f
    }

    private fun follow(line: Int) {
        if (isFocusShown() || userScrolling || SystemClock.uptimeMillis() < followPausedUntil) return

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

    private class LineHolder(val line: LyricLineView) : RecyclerView.ViewHolder(line)

    private class LineAdapter : RecyclerView.Adapter<LineHolder>() {
        private var lines: List<Lyrics.Line> = emptyList()
        private var synced = false
        private var current = -1
        private var currentFill = 0f

        @ColorInt
        private var textColor = 0

        @ColorInt
        private var dimmedColor = 0

        fun setLyrics(lyrics: Lyrics?) {
            lines = lyrics?.lines.orEmpty()
            synced = lyrics?.isSynced == true
            current = -1
            currentFill = 0f
            notifyDataSetChanged()
        }

        fun setTextColor(@ColorInt color: Int, @ColorInt dimmed: Int) {
            if (color == textColor && dimmed == dimmedColor) return

            textColor = color
            dimmedColor = dimmed
            notifyDataSetChanged()
        }

        fun setCurrent(line: Int, fill: Float) {
            val previous = current
            current = line
            currentFill = fill
            if (previous >= 0) notifyItemChanged(previous)
            if (line >= 0) notifyItemChanged(line)
        }

        /** Kept for a rebind, while the view on screen is filled directly. */
        fun setCurrentFill(fill: Float) {
            currentFill = fill
        }

        override fun getItemCount() = lines.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            LineHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_lyrics_line, parent, false) as LyricLineView)

        override fun onBindViewHolder(holder: LineHolder, position: Int) {
            holder.line.text = lines[position].text
            // only the colors change, since a different text size or weight could rewrap the line and move the list
            holder.line.setTextColor(if (synced) dimmedColor else textColor)
            holder.line.fillColor = textColor
            holder.line.fill = if (synced && position == current) currentFill else 0f
        }
    }

    private companion object {
        // about 3:1 on a mid-tone palette, which is the worst case for the player's text color rule
        const val DIMMED_ALPHA = 0xA0
        const val FOLLOW_PAUSE = 4000L
        const val JUMP_DISTANCE = 6
        const val SCROLL_MS_PER_INCH = 300f

        @ColorInt
        fun dim(@ColorInt color: Int) = ColorUtils.setAlphaComponent(color, DIMMED_ALPHA)
    }
}
