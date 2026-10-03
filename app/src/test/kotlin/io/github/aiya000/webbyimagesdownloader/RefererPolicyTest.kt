package io.github.aiya000.webbyimagesdownloader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Images are fetched with the page as the Referer, since some hosts refuse hotlinks without one.
// A page URL can carry a token, though (a private gallery, a share link), and it must not reach
// every image host on the page. Browsers settle this with strict-origin-when-cross-origin, and
// that is enough for the hotlink checks too, so the app does the same
class RefererPolicyTest {
    private val page = "https://en.wikipedia.org/wiki/Cat?wbmark=r8z#History"

    @Test
    fun `an image on another host gets only the origin of the page`() {
        assertEquals(
            "https://en.wikipedia.org/",
            RefererPolicy.refererFor(page, "https://upload.wikimedia.org/a/b/Cat.jpg"),
        )
    }

    @Test
    fun `an image on another port or scheme of the same host is cross-origin too`() {
        assertEquals(
            "https://example.com/",
            RefererPolicy.refererFor("https://example.com/share?token=secret", "https://example.com:8443/a.png"),
        )
        assertEquals(
            "http://example.com:8080/",
            RefererPolicy.refererFor("http://example.com:8080/share?token=secret", "http://example.com/a.png"),
        )
    }

    @Test
    fun `an image on the same origin gets the full page URL without the fragment`() {
        assertEquals(
            "https://en.wikipedia.org/wiki/Cat?wbmark=r8z",
            RefererPolicy.refererFor(page, "https://en.wikipedia.org/static/cat.png"),
        )
    }

    @Test
    fun `the host is compared without regard to case, and a default port is the same origin`() {
        assertEquals(
            "https://example.com/gallery?id=1",
            RefererPolicy.refererFor("https://Example.com/gallery?id=1", "https://example.com:443/a.png"),
        )
    }

    @Test
    fun `nothing is sent from an https page to an http image`() {
        assertNull(RefererPolicy.refererFor(page, "http://upload.wikimedia.org/a.jpg"))
        assertNull(RefererPolicy.refererFor("https://example.com/p?token=secret", "http://example.com/a.jpg"))
    }

    @Test
    fun `user info in the page URL is never sent`() {
        assertEquals(
            "https://example.com/p",
            RefererPolicy.refererFor("https://user:pass@example.com/p", "https://example.com/a.png"),
        )
        assertEquals(
            "https://example.com/",
            RefererPolicy.refererFor("https://user:pass@example.com/p", "https://cdn.example.net/a.png"),
        )
    }

    @Test
    fun `nothing is sent when the page URL cannot be read`() {
        assertNull(RefererPolicy.refererFor("not a url", "https://example.com/a.png"))
        assertNull(RefererPolicy.refererFor("ftp://example.com/p", "https://example.com/a.png"))
    }
}
