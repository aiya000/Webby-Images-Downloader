package io.github.aiya000.webbyimagesdownloader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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

/**
 * Full-screen viewer for checking images before downloading.
 * Swipe between images at 1x; pinch, double tap, or double-tap-and-drag to zoom.
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
                )
            }

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
                    text = stringResource(R.string.viewer_position, pagerState.currentPage + 1, images.size),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close), tint = Color.White)
                }
            }

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
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun ZoomableImage(
    image: WebImage,
    pageUrl: String,
    isSettled: Boolean,
    onZoomedChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val zoomState = remember { ZoomState(onZoomedChange = onZoomedChange) }

    // Once swiped away, come back at 1x
    LaunchedEffect(isSettled) {
        if (!isSettled) zoomState.reset()
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
        modifier = Modifier
            .fillMaxSize()
            .zoomGestures(zoomState)
            .graphicsLayer {
                scaleX = zoomState.scale
                scaleY = zoomState.scale
                translationX = zoomState.offset.x
                translationY = zoomState.offset.y
            },
    )
}
