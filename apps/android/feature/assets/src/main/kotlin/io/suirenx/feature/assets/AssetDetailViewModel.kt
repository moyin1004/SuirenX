package io.suirenx.feature.assets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.suirenx.core.domain.GetAssetUseCase
import io.suirenx.core.model.Asset
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AssetDetailUiState(
    val isLoading: Boolean = true,
    val asset: Asset? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class AssetDetailViewModel @Inject constructor(
    private val getAsset: GetAssetUseCase,
    private val changeNotifier: AssetChangeNotifier,
) : ViewModel() {
    val uiState: StateFlow<AssetDetailUiState>
        field = MutableStateFlow(AssetDetailUiState())

    private var loadJob: Job? = null
    private var assetId: String? = null

    init {
        // The edit page slides up over this screen; when a save lands there we
        // silently refresh so the revealed detail already shows the new values.
        viewModelScope.launch {
            changeNotifier.events.collect {
                assetId?.let { fetch(it, silent = true) }
            }
        }
    }

    fun load(id: String) {
        if (id.isBlank() || (id == assetId && uiState.value.asset != null)) return
        assetId = id
        fetch(id)
    }

    fun retry() {
        val id = assetId ?: return
        assetId = null
        load(id)
    }

    private fun fetch(id: String, silent: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (!silent) {
                uiState.update { it.copy(isLoading = true, errorMessage = null) }
            }
            getAsset(id).fold(
                onSuccess = { asset ->
                    uiState.update {
                        it.copy(isLoading = false, asset = asset, errorMessage = null)
                    }
                },
                onFailure = {
                    // Keep the already-visible asset during a background refresh.
                    if (!silent || uiState.value.asset == null) {
                        uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = "无法加载资产，请检查网络或后端地址后重试",
                            )
                        }
                    }
                },
            )
        }
    }
}
