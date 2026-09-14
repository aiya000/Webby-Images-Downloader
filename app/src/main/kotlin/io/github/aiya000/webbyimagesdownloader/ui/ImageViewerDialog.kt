package io.github.aiya000.webbyimagesdownloader.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import io.github.aiya000.webbyimagesdownloader.ImageCollector
import io.github.aiya000.webbyimagesdownloader.R
import io.github.aiya000.webbyimagesdownloader.WebImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Full-screen viewer for checking images before downloading.
 * Swipe between images at 1x; pinch, double tap, or double-tap-and-drag to zoom.
 * The header / footer start hidden and toggle with a single tap; the back gesture closes the viewer.
 */
@Composable
fun ImageViewerDialog(
    images: List<WebImage>,
    initialIndex: Int,
    pageUrl: String,
    onDismiss: () -> Unit,
) {
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, images.lastIndex),
        pageCount = { images.size },
    )
    val zoomedPages = remember { mutableStateMapOf<Int, Boolean>() }
    // Keyed by URL so a resolution stays known while the pager recycles the page it was loaded on
    val pixelSizes = remember { mutableStateMapOf<String, IntSize>() }
    var showChrome by remember { mutableStateOf(false) }
    val currentImage = images[pagerState.currentPage]

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
                userScrollEnabled = zoomedPages[pagerState.currentPage] != true,
            ) { page ->
                ZoomableImage(
                    image = images[page],
                    pageUrl = pageUrl,
                    isSettled = pagerState.settledPage == page,
                    onZoomedChange = { zoomedPages[page] = it },
                    onPixelSizeChange = { pixelSizes[images[page].url] = it },
                    onTap = { showChrome = !showChrome },
                )
            }

            AnimatedVisibility(
                visible = showChrome,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Background first so it also covers the area behind the status bar
                        .background(Color.Black.copy(alpha = 0.4f))
                        .statusBarsPadding()
                        .padding(start = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.viewer_position, pagerState.currentPage + 1, images.size),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    pixelSizes[currentImage.url]?.let { size ->
                        Text(
                            text = stringResource(R.string.viewer_resolution, size.width, size.height),
                            color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close), tint = Color.White)
                    }
                }
            }

            AnimatedVisibility(
                visible = showChrome,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Text(
                    text = currentImage.url,
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ZoomableImage(
    image: WebImage,
    pageUrl: String,
    isSettled: Boolean,
    onZoomedChange: (Boolean) -> Unit,
    onPixelSizeChange: (IntSize) -> Unit,
    onTap: () -> Unit,
) {
    val context = LocalContext.current
    val zoomState = remember { ZoomState(onZoomedChange = onZoomedChange) }
    var loaded by remember { mutableStateOf<SuccessResult?>(null) }

    // Once swiped away, come back at 1x
    LaunchedEffect(isSettled) {
        if (!isSettled) zoomState.reset()
    }

    LaunchedEffect(loaded) {
        loaded?.let { onPixelSizeChange(originalPixelSize(context, it)) }
    }

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
        onSuccess = { loaded = it.result },
        modifier = Modifier
            .fillMaxSize()
            .zoomGestures(zoomState, onTap = onTap)
            .graphicsLayer {
                scaleX = zoomState.scale
                scaleY = zoomState.scale
                translationX = zoomState.offset.x
                translationY = zoomState.offset.y
            },
    )
}

/**
 * The size of the image as it is served on the web, in pixels.
 *
 * Coil may downsample a large image to the size of the view it is drawn in, so the decoded bitmap is
 * only a fallback; the header of the original file in the disk cache is what actually gets read.
 */
private suspend fun originalPixelSize(context: Context, result: SuccessResult): IntSize =
    withContext(Dispatchers.IO) {
        val cachedSize = result.diskCacheKey?.let { key ->
            SingletonImageLoader.get(context).diskCache?.openSnapshot(key)?.use { snapshot ->
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(snapshot.data.toString(), bounds)
                if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                    IntSize(bounds.outWidth, bounds.outHeight)
                } else {
                    null
                }
            }
        }
        cachedSize ?: IntSize(result.image.width, result.image.height)
    }
