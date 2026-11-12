package com.hormonelog.app.platform

import android.app.Application
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.hormonelog.app.ai.ModelDownloader
import com.hormonelog.app.ai.ModelManager
import com.hormonelog.app.ai.ModelSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

sealed interface UpdateUi {
    data object Idle : UpdateUi
    data object Checking : UpdateUi
    data object Latest : UpdateUi
    /** A newer release for this package and signing key; the download is kept only if it matches [size] and [sha256]. */
    data class Available(val versionName: String, val asset: String, val url: String, val size: Long, val sha256: String) : UpdateUi
    data class Downloading(val done: Long, val total: Long) : UpdateUi
    /** Android's own install prompt. Tapping again reopens it when it could not open by itself. */
    data class Confirm(val prompt: Intent) : UpdateUi
    data class Failed(val message: String, val retry: Available? = null) : UpdateUi
}

private const val LATEST = "https://api.github.com/repos/AIProject-k/HRT_Application/releases/latest"
private const val MANIFEST = "store-manifest.json"

/** Each asset's download address by file name, from GitHub's latest-release answer (drafts and pre-releases are never "latest"). */
internal fun releaseAssets(json: String): Map<String, String> {
    val assets = JSONObject(json).getJSONArray("assets")
    return (0 until assets.length()).associate { i -> assets.getJSONObject(i).let { it.getString("name") to it.getString("browser_download_url") } }
}

/** What the release's store-manifest.json means for the app installed now. */
internal fun readUpdate(manifest: String, assets: Map<String, String>, packageName: String, installedCode: Long, installedSigner: String): UpdateUi {
    val m = JSONObject(manifest)
    if (m.getString("packageName") != packageName) return UpdateUi.Failed("최신 릴리스가 이 앱의 것이 아니에요.")
    if (m.getLong("versionCode") <= installedCode) return UpdateUi.Latest
    if (!m.getString("signerSha256").equals(installedSigner, ignoreCase = true)) {
        return UpdateUi.Failed("설치된 앱과 서명 키가 달라서 앱 안에서는 업데이트할 수 없어요.")
    }
    val asset = m.getString("asset")
    val url = assets[asset] ?: return UpdateUi.Failed("최신 릴리스에 설치 파일이 없어요.")
    return UpdateUi.Available(m.getString("versionName"), asset, url, m.getLong("size"), m.getString("sha256"))
}

/**
 * 내 정보 › 업데이트 확인. The network is used only when the user taps: once to read the newest GitHub release, and once more,
 * on a second tap, to download its APK. Android's installer then asks the user before anything is replaced.
 */
class AppUpdater(private val app: Application, private val scope: CoroutineScope) {
    var ui by mutableStateOf<UpdateUi>(UpdateUi.Idle)
        private set
    private val dir = File(app.cacheDir, "update")
    private val statusAction = "${app.packageName}.UPDATE_STATUS"
    private var installing: UpdateUi.Available? = null
    /** A finished update leaves its APK behind; a partial download resumes only within one run of the app. */
    private val cleanup = scope.launch(Dispatchers.IO) { dir.deleteRecursively() }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = onStatus(intent)
    }

    init {
        ContextCompat.registerReceiver(app, receiver, IntentFilter(statusAction), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    fun dispose() = app.unregisterReceiver(receiver)

    fun tap() {
        when (val u = ui) {
            UpdateUi.Idle, UpdateUi.Latest -> check()
            is UpdateUi.Failed -> u.retry?.let(::download) ?: check()
            is UpdateUi.Available -> download(u)
            is UpdateUi.Confirm -> runCatching { app.startActivity(u.prompt.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            UpdateUi.Checking, is UpdateUi.Downloading -> Unit
        }
    }

    private fun check() {
        ui = UpdateUi.Checking
        scope.launch {
            ui = try {
                withContext(Dispatchers.IO) {
                    val assets = releaseAssets(get(LATEST))
                    val manifest = assets[MANIFEST] ?: return@withContext UpdateUi.Failed("최신 릴리스에 업데이트 정보가 없어요.")
                    val info = app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                    val signer = sha256(info.signingInfo!!.apkContentsSigners.first().toByteArray())
                    readUpdate(get(manifest), assets, app.packageName, info.longVersionCode, signer)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UpdateUi.Failed("업데이트를 확인하지 못했어요. 인터넷 연결을 확인하고 다시 눌러 주세요.")
            }
        }
    }

    private fun download(a: UpdateUi.Available) {
        ui = UpdateUi.Downloading(0, a.size)
        installing = a
        scope.launch {
            try {
                cleanup.join()
                withContext(Dispatchers.IO) {
                    // The model downloader already resumes over HTTPS and keeps the file only when its size and SHA-256 match.
                    val apk = ModelManager(dir, ModelSpec(a.asset, a.url, a.size, a.sha256))
                    if (!apk.isInstalled()) ModelDownloader(apk).download { n -> scope.launch { if (ui is UpdateUi.Downloading) ui = UpdateUi.Downloading(n, a.size) } }
                    install(apk.model)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ui = UpdateUi.Failed("업데이트 파일을 받지 못했어요. 다시 누르면 받은 부분부터 이어받아요.", a)
            }
        }
    }

    private fun install(apk: File) {
        val installer = app.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply { setAppPackageName(app.packageName) }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("base.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val status = Intent(statusAction).setPackage(app.packageName)
            session.commit(PendingIntent.getBroadcast(app, id, status, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT).intentSender)
        }
    }

    private fun onStatus(intent: Intent) {
        val retry = installing
        ui = when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val prompt = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                if (prompt == null) {
                    UpdateUi.Failed("설치 창을 열지 못했어요. 다시 눌러 주세요.", retry)
                } else {
                    runCatching { app.startActivity(prompt.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    UpdateUi.Confirm(prompt)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> UpdateUi.Latest
            PackageInstaller.STATUS_FAILURE_ABORTED -> UpdateUi.Failed("설치를 취소했어요. 다시 누르면 설치 창을 열어요.", retry)
            else -> UpdateUi.Failed("설치하지 못했어요. ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()}".trim(), retry)
        }
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 15_000
            c.readTimeout = 20_000
            check(c.responseCode == 200) { "HTTP ${c.responseCode}" }
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
