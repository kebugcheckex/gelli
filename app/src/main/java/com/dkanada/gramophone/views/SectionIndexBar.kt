package com.dkanada.gramophone.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.dkanada.gramophone.R
import com.dkanada.gramophone.util.ThemeUtil
import kotlin.math.min

/**
 * A column of section letters along the end edge of a list. Touching or dragging along it picks a
 * section, and a bubble beside the finger shows which one. The view covers the whole list so the
 * bubble has room, but it only takes touches that start on the column; the rest reach the list.
 */
class SectionIndexBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    fun interface OnSectionSelectedListener {
        fun onSectionSelected(sectionIndex: Int)
    }

    var onSectionSelectedListener: OnSectionSelectedListener? = null

    /** The width of the letter column, which the list keeps clear of its content. */
    val columnWidth: Int = dp(28f).toInt()

    private var sections: List<String> = emptyList()
    private var hasItems: BooleanArray = BooleanArray(0)

    private var selected = -1
    private var touching = false

    private val maxLetterHeight = dp(20f)
    private val maxLetterSize = sp(11f)
    private val bubbleRadius = dp(32f)
    private val bubbleGap = dp(16f)

    private val letterColor = ThemeUtil.getColorResource(context, android.R.attr.textColorSecondary)
    private val accentColor = ThemeUtil.getColorResource(context, R.attr.colorPrimary)

    private val letterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ThemeUtil.getColorResource(context, R.attr.colorControlNormal, 30)
    }
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accentColor
    }
    private val bubbleTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        textSize = sp(28f)
        color = ThemeUtil.getColorResource(context, R.attr.colorOnPrimary)
    }
    private val trackRect = RectF()

    init {
        // TalkBack users scroll the list itself
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /** The sections in the order the list runs, and which of them have items. */
    fun setSections(sections: List<String>, hasItems: BooleanArray) {
        require(sections.size == hasItems.size) { "every section needs to say whether it has items" }

        this.sections = sections
        this.hasItems = hasItems
        selected = -1
        touching = false
        invalidate()
    }

    private val isRtl: Boolean
        get() = layoutDirection == LAYOUT_DIRECTION_RTL

    private val columnLeft: Float
        get() = if (isRtl) 0f else (width - columnWidth).toFloat()

    private val availableHeight: Float
        get() = (height - paddingTop - paddingBottom).toFloat()

    private val letterHeight: Float
        get() = if (sections.isEmpty()) 0f else min(maxLetterHeight, availableHeight / sections.size)

    private val lettersTop: Float
        get() = paddingTop + (availableHeight - letterHeight * sections.size) / 2

    private fun letterCenterY(index: Int): Float = lettersTop + letterHeight * (index + 0.5f)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (sections.isEmpty()) return

        val centerX = columnLeft + columnWidth / 2f
        if (touching) {
            val inset = dp(4f)
            trackRect.set(columnLeft + inset, lettersTop - inset, columnLeft + columnWidth - inset, lettersTop + letterHeight * sections.size + inset)
            canvas.drawRoundRect(trackRect, columnWidth / 2f, columnWidth / 2f, trackPaint)
        }

        letterPaint.textSize = min(maxLetterSize, letterHeight * 0.8f)
        val baseline = -(letterPaint.ascent() + letterPaint.descent()) / 2
        sections.forEachIndexed { index, section ->
            letterPaint.color = if (touching && index == selected) accentColor else letterColor
            // dim the empty sections, on top of whatever transparency the theme color has
            if (!hasItems[index]) letterPaint.alpha = letterPaint.alpha * 3 / 10
            canvas.drawText(section, centerX, letterCenterY(index) + baseline, letterPaint)
        }

        if (touching && selected >= 0) {
            val bubbleX = if (isRtl) columnLeft + columnWidth + bubbleGap + bubbleRadius else columnLeft - bubbleGap - bubbleRadius
            val bubbleY = letterCenterY(selected).coerceIn(bubbleRadius, (height - bubbleRadius).coerceAtLeast(bubbleRadius))
            canvas.drawCircle(bubbleX, bubbleY, bubbleRadius, bubblePaint)

            val textBaseline = -(bubbleTextPaint.ascent() + bubbleTextPaint.descent()) / 2
            canvas.drawText(sections[selected], bubbleX, bubbleY + textBaseline, bubbleTextPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (sections.isEmpty()) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (event.x < columnLeft || event.x > columnLeft + columnWidth) return false

                touching = true
                parent?.requestDisallowInterceptTouchEvent(true)
                select(event.y)
            }
            MotionEvent.ACTION_MOVE -> select(event.y)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                touching = false
                selected = -1
                invalidate()
            }
        }

        return true
    }

    private fun select(y: Float) {
        if (letterHeight <= 0f) return

        val index = ((y - lettersTop) / letterHeight).toInt().coerceIn(0, sections.size - 1)
        if (index == selected) return

        selected = index
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        onSectionSelectedListener?.onSectionSelected(index)
        invalidate()
    }

    private fun dp(value: Float): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    private fun sp(value: Float): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)
}
