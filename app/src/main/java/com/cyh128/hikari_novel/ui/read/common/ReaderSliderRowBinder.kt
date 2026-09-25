package com.cyh128.hikari_novel.ui.read.common

import android.view.inputmethod.EditorInfo
import com.cyh128.hikari_novel.databinding.ViewReaderSliderRowBinding
import com.cyh128.hikari_novel.util.ReaderSliderValue

/**
 * Wires one reader slider row: label, editable value box, slider and the
 * min/max range labels.
 *
 * Both reader panels used to carry their own copy of this logic, which is how
 * the horizontal and vertical panels drifted apart. There is one implementation
 * now. See docs/reader-settings-round4-plan.md R4-3.
 *
 * Behaviour follows Koodo Reader's `sliderList`:
 * - dragging applies live, so the page previews the change;
 * - the value box commits on Enter *and* on focus loss;
 * - a typed value is clamped into the row's range;
 * - the min/max labels are derived from the bounds rather than hand-written.
 */
object ReaderSliderRowBinder {

    fun bind(
        binding: ViewReaderSliderRowBinding,
        label: String,
        valueFrom: Float,
        valueTo: Float,
        stepSize: Float,
        initialValue: Float,
        onValueChange: (Float) -> Unit
    ) {
        binding.sliderRowLabel.text = label
        binding.sliderRowMin.text = ReaderSliderValue.minLabel(valueFrom)
        binding.sliderRowMax.text = ReaderSliderValue.maxLabel(valueTo)

        binding.sliderRowSlider.valueFrom = valueFrom
        binding.sliderRowSlider.valueTo = valueTo
        binding.sliderRowSlider.stepSize = stepSize

        // A restored preference can sit off the step grid (an older build wrote
        // it, or the step changed); Material's Slider throws on such a value.
        val start = ReaderSliderValue.snapToStep(initialValue, valueFrom, stepSize)
            .coerceIn(valueFrom, valueTo)
        binding.sliderRowSlider.value = start
        binding.sliderRowValue.setText(ReaderSliderValue.format(start))

        // Live preview while dragging.
        binding.sliderRowSlider.addOnChangeListener { _, value, _ ->
            onValueChange(value)
            binding.sliderRowValue.setText(ReaderSliderValue.format(value))
        }

        binding.sliderRowValue.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) commitTypedValue(binding, valueFrom, valueTo, stepSize, onValueChange)
        }

        binding.sliderRowValue.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                commitTypedValue(binding, valueFrom, valueTo, stepSize, onValueChange)
                binding.sliderRowValue.clearFocus()
                true
            } else {
                false
            }
        }
    }

    /**
     * Applies whatever is in the value box, clamped and snapped.
     *
     * Non-numeric input restores the slider's own value rather than being
     * rejected silently, so the box never keeps a value that is not applied.
     */
    private fun commitTypedValue(
        binding: ViewReaderSliderRowBinding,
        valueFrom: Float,
        valueTo: Float,
        stepSize: Float,
        onValueChange: (Float) -> Unit
    ) {
        val typed = ReaderSliderValue.parseAndClamp(
            binding.sliderRowValue.text?.toString().orEmpty(),
            valueFrom,
            valueTo
        )
        if (typed == null) {
            binding.sliderRowValue.setText(
                ReaderSliderValue.format(binding.sliderRowSlider.value)
            )
            return
        }
        val snapped = ReaderSliderValue.snapToStep(typed, valueFrom, stepSize)
            .coerceIn(valueFrom, valueTo)
        // Assigning the same value to a Material Slider is a no-op, so the
        // change listener would not fire; apply it explicitly.
        if (binding.sliderRowSlider.value != snapped) {
            binding.sliderRowSlider.value = snapped
        }
        binding.sliderRowValue.setText(ReaderSliderValue.format(snapped))
        onValueChange(snapped)
    }
}
