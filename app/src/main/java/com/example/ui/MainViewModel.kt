package com.example.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.api.DecompilationResult
import com.example.api.DogBoltClient
import com.example.util.FileUtil
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AppState {
    object Idle : AppState()
    data class PickingFile(val uri: Uri?) : AppState() // uri is chosen but not uploaded yet
    data class Uploading(val progress: Float = 0f) : AppState()
    data class Polling(val decompilationsUrl: String, val results: List<DecompilationResult>) : AppState()
    data class Error(val message: String) : AppState()
    data class ViewingCode(val decompilerName: String, val code: String, val previousUrl: String, val results: List<DecompilationResult>) : AppState()
}

class MainViewModel : ViewModel() {
    private val _state = MutableStateFlow<AppState>(AppState.Idle)
    val state: StateFlow<AppState> = _state.asStateFlow()

    private var pollJob: Job? = null

    fun filePicked(uri: Uri) {
        _state.value = AppState.PickingFile(uri)
    }

    fun uploadFile(context: Context, uri: Uri) {
        viewModelScope.launch {
            _state.value = AppState.Uploading()
            val (file, error) = FileUtil.getFileFromUri(context, uri, maxSizeMb = 50)
            if (error != null || file == null) {
                _state.value = AppState.Error(error ?: "Unknown error")
                return@launch
            }

            val (response, uploadError) = DogBoltClient.uploadFile(file)
            if (uploadError != null || response == null) {
                _state.value = AppState.Error(uploadError ?: "Upload failed")
                return@launch
            }

            startPolling(response.decompilations_url)
        }
    }

    private fun startPolling(url: String) {
        _state.value = AppState.Polling(url, emptyList())
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (true) {
                try {
                    val response = DogBoltClient.api.getDecompilations(url)
                    val results = response.results
                    _state.value = AppState.Polling(url, results)
                    // If all finished (have error or download_url), we could stop polling.
                    // But maybe simpler to just poll a few times or continuously if they want.
                    // For now, poll every 5 seconds.
                    val allDone = results.isNotEmpty() && results.all { it.error != null || it.download_url != null }
                    if (allDone) {
                        break
                    }
                } catch (e: Exception) {
                    // Ignored during polling
                    e.printStackTrace()
                }
                delay(5000)
            }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
        _state.value = AppState.Idle
    }

    fun viewCode(result: DecompilationResult, currentUrl: String, currentResults: List<DecompilationResult>) {
        val downloadUrl = result.download_url ?: return
        viewModelScope.launch {
            try {
                val okHttpClient = okhttp3.OkHttpClient()
                val request = okhttp3.Request.Builder().url(downloadUrl).build()
                val response = kotlinx.coroutines.Dispatchers.IO.let {
                    kotlinx.coroutines.withContext(it) {
                        okHttpClient.newCall(request).execute()
                    }
                }
                val code = response.body?.string() ?: "Failed to read code"
                _state.value = AppState.ViewingCode(result.decompiler.name, code, currentUrl, currentResults)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun closeCode(previousUrl: String, results: List<DecompilationResult>) {
        _state.value = AppState.Polling(previousUrl, results)
        // Resume polling if needed
        startPolling(previousUrl)
    }
}
