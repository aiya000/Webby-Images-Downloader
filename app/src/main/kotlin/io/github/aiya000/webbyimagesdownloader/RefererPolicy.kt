package io.github.aiya000.webbyimagesdownloader

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Decides the `Referer` header sent along with an image request, the way browsers do by default
 * (`strict-origin-when-cross-origin`).
 *
 * Some image hosts refuse hotlinks without a Referer, so one is sent, but a page URL can carry a
 * token (a private gallery, a share link) that must not reach every image host on the page:
 * - same origin: the full page URL, without the fragment and user info
 * - another origin: only the origin of the page, e.g. `https://en.wikipedia.org/`
 * - from an https page to an http image: nothing
 */
object RefererPolicy {
    fun refererFor(pageUrl: String, imageUrl: String): String? {
        val page = pageUrl.toHttpUrlOrNull() ?: return null
        val image = imageUrl.toHttpUrlOrNull() ?: return null
        if (page.isHttps && !image.isHttps) return null

        return if (page.origin() == image.origin()) {
            page.newBuilder().username("").password("").fragment(null).build().toString()
        } else {
            page.origin() + "/"
        }
    }

    // HttpUrl already lowercases the host and drops a default port
    private fun HttpUrl.origin(): String {
        val defaultPort = HttpUrl.defaultPort(scheme)
        val hostPart = if (host.contains(':')) "[$host]" else host
        return "$scheme://$hostPart${if (port == defaultPort) "" else ":$port"}"
    }
}
