package io.github.aiya000.webbyimagesdownloader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

// values/ is what every locale without its own directory falls back to, so it is English, and the
// Japanese UI lives in values-ja/. The two copies have to agree on what each string takes: a
// %1$d on one side and a %1$s on the other makes getString() throw, and only on the device set to
// the locale that got it wrong
class StringResourcesTest {
    private val formatSpecifier = Regex("""%(?:(\d+)\$)?[-#+ 0,(<]*\d*(?:\.\d+)?([a-zA-Z%])""")
    private val japanese = Regex("""[\p{IsHiragana}\p{IsKatakana}\p{IsHan}]""")

    @Test
    fun `the default strings are in English`() {
        readStrings("values").forEach { (name, value) ->
            assertFalse("\"$name\" in values/ is not English: $value", japanese.containsMatchIn(value))
        }
    }

    @Test
    fun `every default string is translated to Japanese`() {
        val japaneseStrings = readStrings("values-ja")
        // the app name is a name, the same in every language
        readStrings("values").keys
            .filter { it != "app_name" }
            .forEach { name ->
                assertTrue("\"$name\" is missing from values-ja/", name in japaneseStrings)
            }
    }

    @Test
    fun `every translated string takes the same format arguments as the original`() {
        val original = readStrings("values")
        readStrings("values-ja").forEach { (name, value) ->
            val english = original[name]
            assertTrue("\"$name\" is in values-ja/ but not in values/", english != null)
            assertEquals(
                "the format arguments of \"$name\" differ between values/ and values-ja/",
                formatArguments(english!!),
                formatArguments(value),
            )
        }
    }

    // Each argument as its position and conversion, e.g. %1$d -> 1 to 'd'. Both have to match:
    // the position decides which argument is read, the conversion how it is formatted
    private fun formatArguments(value: String): Map<Int, Char> {
        val arguments = mutableMapOf<Int, Char>()
        var nextPosition = 1
        formatSpecifier.findAll(value).forEach { match ->
            val (explicitPosition, conversion) = match.destructured
            if (conversion == "%" || conversion == "n") return@forEach
            val position = explicitPosition.toIntOrNull() ?: nextPosition++
            arguments[position] = conversion.single()
        }
        return arguments
    }

    private fun readStrings(valuesDir: String): Map<String, String> {
        val xml = File(resDirectory, "$valuesDir/strings.xml")
        assertTrue("$xml does not exist", xml.isFile)
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml)
            .documentElement
            .getElementsByTagName("string")
        return buildMap {
            for (i in 0 until nodes.length) {
                val node = nodes.item(i) as Element
                put(node.getAttribute("name"), node.textContent)
            }
        }
    }

    // gradle runs the unit tests with the module directory as the working directory
    private val resDirectory = File("src/main/res").also {
        assertTrue("no res directory in ${File("").absolutePath}", it.isDirectory)
    }
}
