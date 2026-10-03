package com.v2ray.ang.handler

import android.content.Context
import android.os.Build
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.dto.AppUpdateNotice
import com.v2ray.ang.dto.CheckUpdateResult
import com.v2ray.ang.dto.GitHubRelease
import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.extension.concatUrl
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object UpdateCheckerManager {

    /** File the panel publishes at the repository root for the customer app. */
    private const val PANEL_UPDATE_FILE = "update.json"

    /**
     * Update notice the owner published through the panel, or null when the panel
     * offers none, the file cannot be read, or it is not newer than this build.
     *
     * A failure is logged and treated as "no update" so an unreachable repository
     * never blocks the app from starting.
     */
    suspend fun checkPanelUpdate(): AppUpdateNotice? = withContext(Dispatchers.IO) {
        if (!ActivationManager.isActivated()) return@withContext null
        val url = ActivationManager.repoRootUrl() + PANEL_UPDATE_FILE
        val response = try {
            HttpUtil.getUrlContent(UrlContentRequest(url = url, timeout = 5000))
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Panel update check failed", e)
            return@withContext null
        }
        if (response.isNullOrEmpty()) return@withContext null
        val notice = JsonUtil.fromJsonSafe(response, AppUpdateNotice::class.java)
        if (notice == null || !notice.isNewerThan(BuildConfig.VERSION_NAME)) {
            return@withContext null
        }
        LogUtil.i(AppConfig.TAG, "Panel published version: ${notice.version}")
        notice
    }

    /**
     * Downloads [notice] into the app's own cache directory and returns the file, or
     * null when the download failed. The cache path is what the app's FileProvider
     * already exposes, so the downloaded APK can be handed to the system installer
     * without granting any world-readable location.
     *
     * Must run off the main thread.
     */
    fun downloadPanelUpdate(notice: AppUpdateNotice, context: Context): File? {
        val target = File(context.cacheDir, "app-update.apk")
        val ok = HttpUtil.downloadToFile(
            UrlContentRequest(url = notice.url, timeout = 60_000),
            target
        )
        if (!ok) {
            target.delete()
            return null
        }
        return target
    }

    suspend fun checkForUpdate(includePreRelease: Boolean = false): CheckUpdateResult = withContext(Dispatchers.IO) {
        val url = if (includePreRelease) {
            AppConfig.APP_API_URL
        } else {
            AppConfig.APP_API_URL.concatUrl("latest")
        }

        val proxyUsername = SettingsManager.getSocksUsername()
        val proxyPassword = SettingsManager.getSocksPassword()

        var response = HttpUtil.getUrlContent(
            UrlContentRequest(
                url = url,
                timeout = 5000
            )
        )
        if (response.isNullOrEmpty()) {
            val httpPort = SettingsManager.getHttpPort()
            response = HttpUtil.getUrlContent(
                UrlContentRequest(
                    url = url,
                    timeout = 5000,
                    httpPort = httpPort,
                    proxyUsername = proxyUsername,
                    proxyPassword = proxyPassword
                )
            )
                ?: throw IllegalStateException("Failed to get response")
        }

        val latestRelease = if (includePreRelease) {
            JsonUtil.fromJsonSafe(response, Array<GitHubRelease>::class.java)
                ?.firstOrNull()
                ?: throw IllegalStateException("No pre-release found")
        } else {
            JsonUtil.fromJsonSafe(response, GitHubRelease::class.java)
        }
        if (latestRelease == null) {
            return@withContext CheckUpdateResult(hasUpdate = false)
        }

        val latestVersion = latestRelease.tagName.removePrefix("v")
        LogUtil.i(
            AppConfig.TAG,
            "Found new version: $latestVersion (current: ${BuildConfig.VERSION_NAME})"
        )

        return@withContext if (AppUpdateNotice.compareVersions(latestVersion, BuildConfig.VERSION_NAME) > 0) {
            val downloadUrl = getDownloadUrl(latestRelease, Build.SUPPORTED_ABIS[0])
            CheckUpdateResult(
                hasUpdate = true,
                latestVersion = latestVersion,
                releaseNotes = latestRelease.body,
                downloadUrl = downloadUrl,
                isPreRelease = latestRelease.prerelease
            )
        } else {
            CheckUpdateResult(hasUpdate = false)
        }
    }

    private fun getDownloadUrl(release: GitHubRelease, abi: String): String {
        val fDroid = "fdroid"

        val assetsByAbi = release.assets.filter {
            (it.name.contains(abi, true))
        }

        val asset = if (BuildConfig.APPLICATION_ID.contains(fDroid, ignoreCase = true)) {
            assetsByAbi.firstOrNull { it.name.contains(fDroid) }
        } else {
            assetsByAbi.firstOrNull { !it.name.contains(fDroid) }
        }

        return asset?.browserDownloadUrl
            ?: throw IllegalStateException("No compatible APK found")
    }
}
