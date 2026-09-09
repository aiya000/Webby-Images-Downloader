package io.github.aiya000.webbyimagesdownloader

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

data class WebImage(
    val url: String,
    val alt: String?,
)

data class PageImages(
    val pageUrl: String,
    val title: String,
    val images: List<WebImage>,
)

/** Fetches a web page and collects the URLs of the images it references. */
object ImageCollector {
    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

    private const val TIMEOUT_MILLIS = 20_000

    fun collect(url: String): PageImages {
        val document = Jsoup.connect(url)
            .userAgent(USER_AGENT)
            .timeout(TIMEOUT_MILLIS)
            .followRedirects(true)
            .get()
        return PageImages(
            pageUrl = document.location().ifBlank { url },
            title = document.title(),
            images = collectImages(document),
        )
    }

    private fun collectImages(document: Document): List<WebImage> {
        val found = LinkedHashMap<String, WebImage>()

        fun add(url: String?, alt: String?) {
            val normalized = url?.trim().orEmpty()
            if (!isHttpUrl(normalized)) return
            found.putIfAbsent(normalized, WebImage(normalized, alt?.takeIf { it.isNotBlank() }))
        }

        for (source in document.select("picture source[srcset]")) {
            add(largestFromSrcset(source), null)
        }
        for (img in document.select("img")) {
            val alt = img.attr("alt")
            add(largestFromSrcset(img), alt)
            for (attribute in IMAGE_ATTRIBUTES) {
                if (img.hasAttr(attribute)) add(img.absUrl(attribute), alt)
            }
        }
        for (meta in document.select("meta[property=og:image], meta[name=twitter:image]")) {
            add(meta.absUrl("content"), null)
        }
        return found.values.toList()
    }

    private val IMAGE_ATTRIBUTES = listOf("src", "data-src", "data-original", "data-lazy-src")

    private fun largestFromSrcset(element: Element): String? {
        if (!element.hasAttr("srcset")) return null
        val candidates = element.attr("srcset")
            .split(',')
            .mapNotNull { candidate ->
                val parts = candidate.trim().split(Regex("\\s+"))
                val src = parts.firstOrNull()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val descriptor = parts.getOrNull(1)
                val size = descriptor?.dropLast(1)?.toDoubleOrNull() ?: 0.0
                src to size
            }
        val best = candidates.maxByOrNull { it.second } ?: return null
        return resolveAgainst(element, best.first)
    }

    private fun resolveAgainst(element: Element, relative: String): String {
        // absUrl only works on attributes, so temporarily store the candidate in a scratch attribute
        element.attr("data-webby-scratch", relative)
        val absolute = element.absUrl("data-webby-scratch")
        element.removeAttr("data-webby-scratch")
        return absolute
    }

    private fun isHttpUrl(url: String): Boolean =
        url.startsWith("http://") || url.startsWith("https://")
}
