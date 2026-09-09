package io.github.aiya000.webbyimagesdownloader.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.IntSize
import kotlin.math.exp

/**
 * Zoom / pan state of one image. Transforms are applied with `graphicsLayer` around the view centre,
 * so [offset] is the translation in pixels and [scale] the factor.
 */
class ZoomState(
    private val maxScale: Float = MAX_SCALE,
    private val onZoomedChange: (Boolean) -> Unit = {},
) {
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set
    var viewSize: IntSize = IntSize.Zero

    val isZoomed: Boolean get() = scale > 1f

    fun reset() {
        setScale(1f, Offset.Zero)
    }

    /** Multiplies the scale by [factor], keeping the image point under [focal] where it is. */
    fun zoomBy(factor: Float, focal: Offset) = setScale(scale * factor, focal)

    fun setScale(target: Float, focal: Offset) {
        val wasZoomed = isZoomed
        val newScale = target.coerceIn(1f, maxScale)
        val center = Offset(viewSize.width / 2f, viewSize.height / 2f)
        val focalFromCenter = focal - center
        offset = focalFromCenter - (focalFromCenter - offset) * (newScale / scale)
        scale = newScale
        clampOffset()
        if (wasZoomed != isZoomed) onZoomedChange(isZoomed)
    }

    fun panBy(delta: Offset) {
        offset += delta
        clampOffset()
    }

    fun toggleDoubleTap(focal: Offset) {
        if (isZoomed) reset() else setScale(DOUBLE_TAP_SCALE, focal)
    }

    private fun clampOffset() {
        if (!isZoomed) {
            offset = Offset.Zero
            return
        }
        val boundX = viewSize.width * (scale - 1f) / 2f
        val boundY = viewSize.height * (scale - 1f) / 2f
        offset = Offset(offset.x.coerceIn(-boundX, boundX), offset.y.coerceIn(-boundY, boundY))
    }

    companion object {
        const val MAX_SCALE = 6f
        const val DOUBLE_TAP_SCALE = 2.5f
    }
}

private enum class GestureMode { Undecided, Pinch, Pan, QuickScale, PassThrough }

/** Dragging the full view height after a double tap multiplies the scale by e^QUICK_SCALE_SENSITIVITY. */
private const val QUICK_SCALE_SENSITIVITY = 2f

/**
 * Pinch to zoom, one-finger pan while zoomed, double tap to zoom in / reset, and
 * double tap + hold + drag (down = zoom in, up = zoom out).
 *
 * A one-finger drag at 1x is deliberately left unconsumed so a parent (e.g. a pager) can handle it.
 */
fun Modifier.zoomGestures(state: ZoomState): Modifier = pointerInput(state) {
    state.viewSize = size
    detectZoomGestures(state)
}

private suspend fun PointerInputScope.detectZoomGestures(state: ZoomState) {
    val doubleTapTimeout = viewConfiguration.doubleTapTimeoutMillis
    val touchSlop = viewConfiguration.touchSlop
    val doubleTapSlop = touchSlop * 4
    var lastTapUpTime = 0L
    var lastTapPosition = Offset.Zero

    awaitEachGesture {
        state.viewSize = size
        val down = awaitFirstDown(requireUnconsumed = false)
        val downPosition = down.position
        val isSecondTap = down.uptimeMillis - lastTapUpTime <= doubleTapTimeout &&
            (downPosition - lastTapPosition).getDistance() <= doubleTapSlop
        lastTapUpTime = 0L

        var mode = GestureMode.Undecided
        var accumulatedDrag = Offset.Zero

        while (true) {
            val event = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }

            if (pressed.isEmpty()) {
                val up = event.changes.firstOrNull() ?: break
                if (mode == GestureMode.Undecided) {
                    if (isSecondTap) {
                        state.toggleDoubleTap(downPosition)
                    } else {
                        lastTapUpTime = up.uptimeMillis
                        lastTapPosition = downPosition
                    }
                }
                break
            }

            if (pressed.size >= 2) {
                mode = GestureMode.Pinch
                val zoom = event.calculateZoom()
                if (zoom != 1f) state.zoomBy(zoom, event.calculateCentroid())
                if (state.isZoomed) state.panBy(event.calculatePan())
                event.changes.forEach { if (it.positionChanged()) it.consume() }
                continue
            }

            val change = pressed.first()
            val delta = change.positionChange()
            when (mode) {
                GestureMode.Undecided -> {
                    accumulatedDrag += delta
                    if (accumulatedDrag.getDistance() > touchSlop) {
                        mode = when {
                            isSecondTap -> GestureMode.QuickScale
                            state.isZoomed -> GestureMode.Pan
                            else -> GestureMode.PassThrough
                        }
                        applyDrag(mode, state, accumulatedDrag, downPosition)
                        if (mode != GestureMode.PassThrough) change.consume()
                    }
                }
                GestureMode.Pinch -> {
                    // A finger was lifted mid-pinch: keep panning with the remaining one
                    if (state.isZoomed) state.panBy(delta)
                    change.consume()
                }
                GestureMode.PassThrough -> Unit
                else -> {
                    applyDrag(mode, state, delta, downPosition)
                    change.consume()
                }
            }
        }
    }
}

private fun PointerInputScope.applyDrag(mode: GestureMode, state: ZoomState, delta: Offset, focal: Offset) {
    when (mode) {
        GestureMode.QuickScale -> state.zoomBy(exp(delta.y * QUICK_SCALE_SENSITIVITY / size.height), focal)
        GestureMode.Pan -> state.panBy(delta)
        else -> Unit
    }
}
