package com.beta.auraos.util

import android.graphics.Paint
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.AttrRes
import com.beta.auraos.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors

/** Small view helpers for the redesigned screens (LRGutierrez design system, BCV Tracker family). */
object PcUi {

    fun color(view: android.view.View, @AttrRes attr: Int): Int =
        MaterialColors.getColor(view, attr)

    /** Wordmark ("Aura·OS") with the middle dot in amber, as BCV Tracker's BrandMark. */
    fun applyWordmark(textView: TextView) {
        val text = textView.text.toString()
        val dot = text.indexOf('·')
        if (dot < 0) return
        textView.text = SpannableString(text).apply {
            setSpan(
                ForegroundColorSpan(color(textView, R.attr.accentCustom)),
                dot, dot + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
    }

    fun underline(textView: TextView) {
        textView.paintFlags = textView.paintFlags or Paint.UNDERLINE_TEXT_FLAG
    }

    /**
     * Adds a toggle chip to [parent]. Chips in a horizontal row get an 8dp gap;
     * [fill] stretches them to equal widths.
     */
    fun addChip(
        parent: ViewGroup,
        label: CharSequence,
        checked: Boolean,
        fill: Boolean = false,
        onClick: () -> Unit,
    ): MaterialButton {
        val chip = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pc_chip, parent, false) as MaterialButton
        chip.text = label
        chip.isChecked = checked
        chip.setOnClickListener {
            // Selection is driven by state, not by the button's own toggle.
            chip.isChecked = checked
            onClick()
        }
        val gap = (8 * parent.resources.displayMetrics.density).toInt()
        val lp = if (fill) {
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        } else {
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        if (parent.childCount > 0) {
            if ((parent as? LinearLayout)?.orientation == LinearLayout.VERTICAL) lp.topMargin = gap
            else lp.marginStart = gap
        }
        parent.addView(chip, lp)
        return chip
    }
}
