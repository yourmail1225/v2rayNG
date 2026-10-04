import androidx.lifecycle.compose.collectAsStateWithLifecycle
package com.v2ray.ang.ui.backup

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.WebDavConfig
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.base.HelperBaseComponentActivity
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.DeleteConfirmDialog
import com.v2ray.ang.ui.compose.InputDialog
import com.v2ray.ang.ui.compose.InputField
import com.v2ray.ang.ui.compose.NavigationBarsSpacer
import com.v2ray.ang.ui.compose.SelectListDialog
import com.v2ray.ang.ui.compose.PasswordVerifyDialog
import com.v2ray.ang.ui.compose.SettingsMenuItem
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

enum class BackupLocation(@StringRes val labelRes: Int) {
    Local(R.string.backup_location_local),
    WebDav(R.string.backup_location_webdav)
}

class BackupActivity : HelperBaseComponentActivity() {

    private val viewModel: BackupViewModel by viewModels()



    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.viewModelEvent.collect { event ->
                    when (event) {
                        is BackupViewModel.BackupViewModelEvent.ShareFile -> {
                            handleShareFile(event.filePath)
                        }

                        is BackupViewModel.BackupViewModelEvent.ExportLocal -> {
                            handleExportLocal(event.cachePath, event.targetUri)
                        }

                        is BackupViewModel.BackupViewModelEvent.RestoreSuccess -> {
                            SettingsManager.initApp(this@BackupActivity)
                        }

                        else -> {}
                    }
                }
            }
        }
    }

    @Composable
    override fun ScreenContent() {
        val backupPasswordPrompt by viewModel.backupPasswordPrompt.collectAsStateWithLifecycle()
        BackupScreen(
            isLoadingState = viewModel.isLoading,
            webDavConfigState = viewModel.webDavConfig,
            onBackupOptionSelected = { location -> viewModel.requestBackup(location) },
            onShareClick = { viewModel.requestShareBackup() },
            onRestoreOptionSelected = { location -> viewModel.requestRestore(location) },
            onCleanupProfiles = viewModel::cleanupProfileStorage,
            onWebDavSave = { config -> viewModel.saveWebDavConfig(config) },
            onBackClick = { finish() }
        )
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        observeViewModel()
    }
}