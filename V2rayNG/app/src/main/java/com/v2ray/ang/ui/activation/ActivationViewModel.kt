package com.v2ray.ang.ui.activation

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import com.v2ray.ang.AngApplication
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.handler.ActivationErrorKind
import com.v2ray.ang.handler.ActivationManager
import com.v2ray.ang.handler.ActivationOutcome
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.ui.base.BaseViewModel
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * State of the one-time activation screen. Durable activation state lives in
 * [ActivationManager]; this state is transient UI progress.
 */
data class ActivationUiState(
    val isLoading: Boolean = false,
    val errorResId: Int? = null,
)

class ActivationViewModel(application: Application) : BaseViewModel(application) {

    private val _uiState = MutableStateFlow(ActivationUiState())
    val uiState: StateFlow<ActivationUiState> = _uiState.asStateFlow()

    fun activate(code: String, configCode: String?) {
        if (_uiState.value.isLoading) return
        if (code.isBlank()) {
            toastError(R.string.activation_error_denied)
            return
        }
        viewModelScope.launch {
            _uiState.value = ActivationUiState(isLoading = true)
            when (val outcome = ActivationManager.activate(code, configCode)) {
                is ActivationOutcome.Master -> complete(ActivationManager.MODE_MASTER, code.trim())
                is ActivationOutcome.Subscription ->
                    importSubscription(outcome.url, outcome.row, code.trim())
                is ActivationOutcome.Error -> {
                    _uiState.value = ActivationUiState(errorResId = outcome.kind.toResId())
                }
            }
        }
    }

    private suspend fun importSubscription(url: String, rowId: String, code: String) {
        val importedCount = withContext(Dispatchers.IO) {
            val subId = rowId.ifBlank { Utils.getUuid() }
            val subItem = SubscriptionItem(remarks = code, url = url, autoUpdate = true)
            MmkvManager.encodeSubscription(subId, subItem)
            val (count, _) = AngConfigManager.importBatchConfig(url, subId, append = false)
            count
        }
        if (importedCount > 0) {
            SettingsChangeManager.makeSetupGroupTab()
            complete(ActivationManager.MODE_CODE, code)
        } else {
            _uiState.value = ActivationUiState(errorResId = R.string.activation_error_generic)
        }
    }

    private fun complete(mode: String, code: String) {
        ActivationManager.markActivated(mode, code)
        _uiState.value = ActivationUiState()
        toastSuccess(R.string.activation_success)
        finishActivity()
    }
}

internal fun ActivationErrorKind.toResId(): Int = when (this) {
    ActivationErrorKind.NETWORK -> R.string.activation_error_network
    ActivationErrorKind.LIMIT -> R.string.activation_error_limit
    ActivationErrorKind.DENIED -> R.string.activation_error_denied
    else -> R.string.activation_error_generic
}

class ActivationViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        return ActivationViewModel(application) as T
    }
}