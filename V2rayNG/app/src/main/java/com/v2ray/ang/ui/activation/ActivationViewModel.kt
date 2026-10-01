package com.v2ray.ang.ui.activation

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.handler.ActivationErrorKind
import com.v2ray.ang.handler.ActivationManager
import com.v2ray.ang.handler.ActivationOutcome
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.ui.base.BaseViewModel
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
                is ActivationOutcome.Master -> complete(ActivationManager.MODE_MASTER, code.trim(), "")
                is ActivationOutcome.Subscription ->
                    importSubscription(outcome.code, outcome.row)
                is ActivationOutcome.Error -> {
                    _uiState.value = ActivationUiState(errorResId = outcome.kind.toResId())
                }
            }
        }
    }

    /**
     * Imports the fetched row as raw locked-package text. The row is already read, so
     * no subscription URL is created: the local subscription id is the activated code,
     * which keeps the per-subscription usage lock that later reports traffic to the row.
     */
    private suspend fun importSubscription(code: String, row: String) {
        val importedCount = withContext(Dispatchers.IO) {
            MmkvManager.encodeSubscription(code, SubscriptionItem(remarks = code, url = "", autoUpdate = false))
            AngConfigManager.importBatchConfig(row, code, append = false).first
        }
        if (importedCount > 0) {
            SettingsChangeManager.makeSetupGroupTab()
            complete(ActivationManager.MODE_CODE, code, code)
        } else {
            _uiState.value = ActivationUiState(errorResId = R.string.activation_error_generic)
        }
    }

    private fun complete(mode: String, code: String, subscriptionId: String) {
        ActivationManager.markActivated(mode, code, subscriptionId)
        _uiState.value = ActivationUiState()
        if (subscriptionId.isNotBlank()) {
            ActivationManager.reportActivation(code, subscriptionId)
        }
        toastSuccess(R.string.activation_success)
        finishActivity()
    }
}

internal fun ActivationErrorKind.toResId(): Int = when (this) {
    ActivationErrorKind.NETWORK -> R.string.activation_error_network
    ActivationErrorKind.LIMIT -> R.string.activation_error_limit
    ActivationErrorKind.DENIED -> R.string.activation_error_denied
    ActivationErrorKind.NO_WRITE_TOKEN -> R.string.activation_error_no_write_token
    else -> R.string.activation_error_generic
}

class ActivationViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        return ActivationViewModel(application) as T
    }
}