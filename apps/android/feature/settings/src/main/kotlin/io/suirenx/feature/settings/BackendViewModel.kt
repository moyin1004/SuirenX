package io.suirenx.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.suirenx.core.domain.BackendRepository
import io.suirenx.core.model.BackendSettings
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BackendUiState(
    val settings: BackendSettings? = null,
    val address: String = "",
    val name: String = "",
    val busy: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class BackendViewModel @Inject constructor(private val repository: BackendRepository) : ViewModel() {
    val uiState: StateFlow<BackendUiState>
        field = MutableStateFlow(BackendUiState())

    init {
        viewModelScope.launch {
            repository.settings.collect { settings -> uiState.update { it.copy(settings = settings) } }
        }
        load()
    }

    fun load() = perform { repository.initialize() }
    fun onAddressChanged(value: String) { uiState.update { it.copy(address = value, error = null) } }
    fun onNameChanged(value: String) { uiState.update { it.copy(name = value, error = null) } }
    fun save() {
        val state = uiState.value
        perform { repository.saveAndSelect(state.address, state.name) }
    }
    fun select(url: String) = perform { repository.select(url) }

    private fun perform(action: suspend () -> Result<Unit>) {
        if (uiState.value.busy) return
        uiState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            action().fold(
                onSuccess = {
                    uiState.update { it.copy(busy = false, address = "", name = "") }
                },
                onFailure = { error ->
                    uiState.update { it.copy(busy = false, error = error.message ?: "无法读取或保存地址，请重试") }
                },
            )
        }
    }
}
