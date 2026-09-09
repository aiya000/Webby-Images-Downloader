package io.github.aiya000.webbyimagesdownloader.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.crossfade
import io.github.aiya000.webbyimagesdownloader.Downloader
import io.github.aiya000.webbyimagesdownloader.ImageCollector
import io.github.aiya000.webbyimagesdownloader.MainViewModel
import io.github.aiya000.webbyimagesdownloader.R
import io.github.aiya000.webbyimagesdownloader.WebImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val page = state.page

    fun startDownload() {
        val pageUrl = page?.pageUrl ?: return
        val count = Downloader.enqueue(context, pageUrl, state.selected)
        Toast.makeText(context, context.getString(R.string.download_started, count), Toast.LENGTH_SHORT).show()
        viewModel.clearSelection()
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            startDownload()
        } else {
            Toast.makeText(context, R.string.storage_permission_denied, Toast.LENGTH_SHORT).show()
        }
    }

    fun onDownloadClick() {
        val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            startDownload()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (state.selected.isEmpty()) stringResource(R.string.app_name)
                        else stringResource(R.string.selected_count, state.selected.size),
                    )
                },
                actions = {
                    if (page != null && page.images.isNotEmpty()) {
                        IconButton(onClick = viewModel::selectAll) {
                            Icon(Icons.Default.SelectAll, contentDescription = stringResource(R.string.select_all))
                        }
                        IconButton(onClick = viewModel::clearSelection, enabled = state.selected.isNotEmpty()) {
                            Icon(Icons.Default.Deselect, contentDescription = stringResource(R.string.clear_selection))
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.selected.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = ::onDownloadClick,
                    icon = { Icon(Icons.Default.Download, contentDescription = null) },
                    text = { Text("${stringResource(R.string.download)} (${state.selected.size})") },
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            UrlBar(
                url = state.urlInput,
                enabled = !state.isLoading,
                onUrlChange = viewModel::onUrlInputChange,
                onFetch = viewModel::fetch,
            )

            StatusLine(state.isLoading, state.errorMessage, page)

            if (page != null) {
                ImageGrid(
                    images = page.images,
                    pageUrl = page.pageUrl,
                    selected = state.selected,
                    onToggle = viewModel::toggleSelection,
                )
            }
        }
    }
}

@Composable
private fun UrlBar(
    url: String,
    enabled: Boolean,
    onUrlChange: (String) -> Unit,
    onFetch: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            modifier = Modifier.weight(1f),
            label = { Text(stringResource(R.string.url_label)) },
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onFetch() }),
        )
        Spacer(Modifier.width(8.dp))
        Button(onClick = onFetch, enabled = enabled && url.isNotBlank()) {
            Text(stringResource(R.string.fetch))
        }
    }
}

@Composable
private fun StatusLine(
    isLoading: Boolean,
    errorMessage: String?,
    page: io.github.aiya000.webbyimagesdownloader.PageImages?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            isLoading -> {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.fetching), style = MaterialTheme.typography.bodyMedium)
            }
            errorMessage != null -> Text(
                stringResource(R.string.fetch_failed, errorMessage),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
            page != null -> Text(
                if (page.images.isEmpty()) stringResource(R.string.no_images)
                else stringResource(R.string.found_images, page.images.size),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ImageGrid(
    images: List<WebImage>,
    pageUrl: String,
    selected: List<String>,
    onToggle: (String) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 110.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 96.dp),
    ) {
        items(images, key = { it.url }) { image ->
            val order = selected.indexOf(image.url)
            ImageCell(
                image = image,
                pageUrl = pageUrl,
                selectionOrder = if (order >= 0) order + 1 else null,
                onClick = { onToggle(image.url) },
            )
        }
    }
}

@Composable
private fun ImageCell(
    image: WebImage,
    pageUrl: String,
    selectionOrder: Int?,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val isSelected = selectionOrder != null
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = Modifier
            .padding(4.dp)
            .aspectRatio(1f)
            .clip(shape)
            .then(
                if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape) else Modifier,
            )
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
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
                .crossfade(true)
                .build(),
            contentDescription = image.alt,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (selectionOrder != null) {
            Box(
                modifier = Modifier
                    .padding(6.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .align(Alignment.TopStart),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = selectionOrder.toString(),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.15f)),
            )
        }
    }
}
