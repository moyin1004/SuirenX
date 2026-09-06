package io.suirenx.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.suirenx.core.domain.ThemeRepository
import io.suirenx.core.model.ThemeMode
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ThemeSettingsViewModel @Inject constructor(private val repository: ThemeRepository) : ViewModel() {
    val theme: StateFlow<ThemeMode> = repository.theme
    init { viewModelScope.launch { repository.initialize() } }
    fun select(theme: ThemeMode) { viewModelScope.launch { repository.select(theme) } }
}
