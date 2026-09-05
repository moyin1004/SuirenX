package io.suirenx.feature.assets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.suirenx.core.domain.GetAssetUseCase
import io.suirenx.core.domain.UpdateAssetUseCase
import io.suirenx.core.model.Asset
import java.math.BigDecimal
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
    val form: AssetFormState? = null,
)

@HiltViewModel
class AssetDetailViewModel @Inject constructor(
    private val getAsset: GetAssetUseCase,
    private val updateAsset: UpdateAssetUseCase,
    private val changeNotifier: AssetChangeNotifier,
) : ViewModel() {
    val uiState: StateFlow<AssetDetailUiState>
        field = MutableStateFlow(AssetDetailUiState())

    private var loadJob: Job? = null
    private var saveJob: Job? = null
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

    fun openEdit() {
        val asset = uiState.value.asset ?: return
        if (uiState.value.form != null) return
        uiState.update {
            it.copy(
                form = AssetFormState(
                    name = asset.name,
                    price = BigDecimal(asset.priceCents).movePointLeft(2).toPlainString(),
                    purchaseDate = asset.purchaseDate.toString(),
                ),
            )
        }
    }

    fun dismissEdit() {
        if (uiState.value.form?.isSaving != true) uiState.update { it.copy(form = null) }
    }

    fun onEditNameChanged(value: String) = updateForm { copy(name = value, errorMessage = null) }
    fun onEditPriceChanged(value: String) = updateForm { copy(price = value, errorMessage = null) }
    fun onEditPurchaseDateChanged(value: String) =
        updateForm { copy(purchaseDate = value, errorMessage = null) }

    private fun updateForm(transform: AssetFormState.() -> AssetFormState) {
        uiState.update { state ->
            val form = state.form
            if (form == null || form.isSaving) state else state.copy(form = form.transform())
        }
    }

    fun saveEdit() {
        val form = uiState.value.form ?: return
        if (form.isSaving) return
        val id = uiState.value.asset?.id ?: return
        val draft = try {
            form.toNewAsset()
        } catch (error: IllegalArgumentException) {
            updateForm { copy(errorMessage = error.message) }
            return
        }
        updateForm { copy(isSaving = true, errorMessage = null) }
        saveJob = viewModelScope.launch {
            val result = updateAsset(id, draft)
            result.fold(
                onSuccess = { asset ->
                    changeNotifier.notifyAssetChanged()
                    uiState.update { it.copy(asset = asset, form = null) }
                },
                onFailure = {
                    uiState.update { state ->
                        state.copy(
                            form = state.form?.copy(
                                isSaving = false,
                                errorMessage = "保存失败，请检查网络和服务后重试",
                            ),
                        )
                    }
                },
            )
        }
    }
}
