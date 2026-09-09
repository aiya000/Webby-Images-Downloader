package io.github.aiya000.webbyimagesdownloader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import io.github.aiya000.webbyimagesdownloader.ImageCollector
import io.github.aiya000.webbyimagesdownloader.R
import io.github.aiya000.webbyimagesdownloader.WebImage

private const val MIN_SCALE = 1f
private const val MAX_SCALE = 6f
private const val DOUBLE_TAP_SCALE = 2.5f

/** Full-screen viewer for checking an image before downloading: pinch to zoom, drag to pan, arrows to move on. */
@Composable
fun ImageViewerDialog(
    images: List<WebImage>,
    initialIndex: Int,
    pageUrl: String,
    onDismiss: () -> Unit,
) {
    var index by remember { mutableIntStateOf(initialIndex.coerceIn(0, images.lastIndex)) }
    var scale by remember { mutableFloatStateOf(MIN_SCALE) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val context = LocalContext.current
    val image = images[index]

    fun resetZoom() {
        scale = MIN_SCALE
        offset = Offset.Zero
    }

    fun show(newIndex: Int) {
        if (newIndex !in images.indices) return
        index = newIndex
        resetZoom()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(image.url)
                    .httpHeaders(
                        NetworkHeaders.Builder()
                            .set("Referer", pageUrl)
                            .set("User-Agent", ImageCollector.USER_AGENT)
                            .build(),
                    )
                    .build(),
                contentDescription = image.alt,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(index) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                            offset = if (scale > MIN_SCALE) offset + pan else Offset.Zero
                        }
                    }
                    .pointerInput(index) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (scale > MIN_SCALE) resetZoom() else scale = DOUBLE_TAP_SCALE
                            },
                        )
                    }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )

            // Header: position + close
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(start = 16.dp)
                    .align(Alignment.TopCenter),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.viewer_position, index + 1, images.size),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close), tint = Color.White)
                }
            }

            // Footer: previous / next and the image URL
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .navigationBarsPadding()
                    .align(Alignment.BottomCenter),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { show(index - 1) }, enabled = index > 0) {
                    Icon(
                        Icons.Default.ChevronLeft,
                        contentDescription = stringResource(R.string.previous_image),
                        tint = if (index > 0) Color.White else Color.White.copy(alpha = 0.3f),
                    )
                }
                Text(
                    text = image.url,
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                )
                IconButton(onClick = { show(index + 1) }, enabled = index < images.lastIndex) {
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = stringResource(R.string.next_image),
                        tint = if (index < images.lastIndex) Color.White else Color.White.copy(alpha = 0.3f),
                    )
                }
            }
        }
    }
}
