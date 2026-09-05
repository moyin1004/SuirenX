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
) : ViewModel() {
    val uiState: StateFlow<AssetDetailUiState>
        field = MutableStateFlow(AssetDetailUiState())

    private var loadJob: Job? = null
    private var assetId: String? = null

    fun load(id: String) {
        if (id.isBlank() || (id == assetId && uiState.value.asset != null)) return
        assetId = id
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            uiState.value = AssetDetailUiState(isLoading = true)
            val result = getAsset(id)
            result.fold(
                onSuccess = { asset ->
                    uiState.value = AssetDetailUiState(isLoading = false, asset = asset)
                },
                onFailure = {
                    uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "无法加载资产，请检查网络或后端地址后重试",
                        )
                    }
                },
            )
        }
    }

    fun retry() {
        val id = assetId ?: return
        assetId = null
        load(id)
    }
}
