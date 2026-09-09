package io.github.aiya000.webbyimagesdownloader

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import io.github.aiya000.webbyimagesdownloader.ui.MainScreen
import io.github.aiya000.webbyimagesdownloader.ui.theme.WebbyImagesDownloaderTheme

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        if (savedInstanceState == null) {
            handleIntent(intent)
        }
        setContent {
            WebbyImagesDownloaderTheme {
                MainScreen(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val url = extractSharedUrl(intent) ?: return
        viewModel.openSharedUrl(url)
    }

    private fun extractSharedUrl(intent: Intent?): String? {
        if (intent == null || intent.action != Intent.ACTION_SEND) return null
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
        return URL_PATTERN.find(text)?.value
    }

    private companion object {
        val URL_PATTERN = Regex("""https?://\S+""")
    }
}
