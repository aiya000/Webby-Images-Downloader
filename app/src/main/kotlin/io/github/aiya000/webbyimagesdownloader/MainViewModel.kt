package io.github.aiya000.webbyimagesdownloader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MainUiState(
    val urlInput: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val page: PageImages? = null,
    /** Selected image URLs, in selection order. */
    val selected: List<String> = emptyList(),
)

class MainViewModel : ViewModel() {
    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    fun onUrlInputChange(value: String) {
        _state.update { it.copy(urlInput = value) }
    }

    /** Called when a URL arrives from outside (share sheet). */
    fun openSharedUrl(url: String) {
        _state.update { it.copy(urlInput = url) }
        fetch()
    }

    fun fetch() {
        val url = _state.value.urlInput.trim()
        if (url.isEmpty()) return
        val normalized = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
        _state.update { it.copy(isLoading = true, errorMessage = null, page = null, selected = emptyList()) }
        viewModelScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { ImageCollector.collect(normalized) } }
            _state.update { current ->
                result.fold(
                    onSuccess = { current.copy(isLoading = false, page = it) },
                    onFailure = { current.copy(isLoading = false, errorMessage = it.message ?: it.javaClass.simpleName) },
                )
            }
        }
    }

    fun toggleSelection(url: String) {
        _state.update { current ->
            val selected = if (url in current.selected) current.selected - url else current.selected + url
            current.copy(selected = selected)
        }
    }

    fun selectAll() {
        _state.update { current ->
            current.copy(selected = current.page?.images?.map { it.url }.orEmpty())
        }
    }

    fun clearSelection() {
        _state.update { it.copy(selected = emptyList()) }
    }
}
