package io.github.aiya000.webbyimagesdownloader

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment

/** Enqueues image downloads to the public Download directory via DownloadManager. */
object Downloader {
    private const val SUBDIRECTORY = "WebbyImagesDownloader"

    /** Returns the number of downloads enqueued. */
    fun enqueue(context: Context, pageUrl: String, imageUrls: List<String>): Int {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        var count = 0
        imageUrls.forEachIndexed { index, url ->
            val fileName = fileNameFor(url, index)
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle(fileName)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "$SUBDIRECTORY/$fileName")
                .addRequestHeader("User-Agent", ImageCollector.USER_AGENT)
                .addRequestHeader("Referer", pageUrl)
            manager.enqueue(request)
            count++
        }
        return count
    }

    private fun fileNameFor(url: String, index: Int): String {
        val lastSegment = Uri.parse(url).lastPathSegment.orEmpty()
        val cleaned = lastSegment
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .trim('_', '.')
        val base = cleaned.ifBlank { "image_${index + 1}" }
        return if (base.contains('.')) base else "$base.jpg"
    }
}
