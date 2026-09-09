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
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.IntSize
import kotlin.math.exp
import kotlinx.coroutines.withTimeoutOrNull

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

    /** Called when a zoom gesture ends: a scale that is only nominally above 1x snaps back to exactly 1x. */
    fun settle() {
        if (scale < SNAP_TO_ONE_BELOW) reset()
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
        const val SNAP_TO_ONE_BELOW = 1.1f
    }
}

private enum class GestureMode { Undecided, Pinch, Pan, QuickScale, PassThrough }

/** Dragging the full view height after a double tap multiplies the scale by e^QUICK_SCALE_SENSITIVITY. */
private const val QUICK_SCALE_SENSITIVITY = 2.5f

/**
 * Pinch to zoom, one-finger pan while zoomed, double tap to zoom in / reset,
 * double tap + hold + drag (down = zoom in, up = zoom out), and [onTap] for a confirmed single tap.
 *
 * A one-finger drag at 1x is deliberately left unconsumed so a parent (e.g. a pager) can handle it.
 */
fun Modifier.zoomGestures(state: ZoomState, onTap: () -> Unit = {}): Modifier = pointerInput(state) {
    state.viewSize = size
    detectZoomGestures(state, onTap)
}

private suspend fun PointerInputScope.detectZoomGestures(state: ZoomState, onTap: () -> Unit) {
    val doubleTapTimeout = viewConfiguration.doubleTapTimeoutMillis
    val doubleTapSlop = viewConfiguration.touchSlop * 4

    awaitEachGesture {
        state.viewSize = size
        var down = awaitFirstDown(requireUnconsumed = false)
        var isSecondTap = false
        while (true) {
            val endedAsTap = trackPointers(state, down.position, isSecondTap)
            if (!endedAsTap) break
            if (isSecondTap) {
                state.toggleDoubleTap(down.position)
                break
            }
            // A plain tap so far: it is a single tap unless a second one lands in time
            val second = withTimeoutOrNull(doubleTapTimeout) { awaitFirstDown(requireUnconsumed = false) }
            if (second == null) {
                onTap()
                break
            }
            isSecondTap = (second.position - down.position).getDistance() <= doubleTapSlop
            down = second
        }
    }
}

/**
 * Follows one pointer sequence (from a down to all pointers being up) and applies the gesture it turns out to be.
 * Returns true when it ended as a plain tap (no drag, no pinch).
 */
private suspend fun AwaitPointerEventScope.trackPointers(
    state: ZoomState,
    downPosition: Offset,
    isSecondTap: Boolean,
): Boolean {
    val touchSlop = viewConfiguration.touchSlop
    var mode = GestureMode.Undecided
    var accumulatedDrag = Offset.Zero

    while (true) {
        val event = awaitPointerEvent()
        val pressed = event.changes.filter { it.pressed }

        if (pressed.isEmpty()) {
            if (mode == GestureMode.Pinch || mode == GestureMode.QuickScale) state.settle()
            return mode == GestureMode.Undecided
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

private fun AwaitPointerEventScope.applyDrag(mode: GestureMode, state: ZoomState, delta: Offset, focal: Offset) {
    when (mode) {
        GestureMode.QuickScale -> state.zoomBy(exp(delta.y * QUICK_SCALE_SENSITIVITY / size.height), focal)
        GestureMode.Pan -> state.panBy(delta)
        else -> Unit
    }
}
