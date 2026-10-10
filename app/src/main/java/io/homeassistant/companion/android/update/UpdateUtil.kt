package io.homeassistant.companion.android.update

import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.StrictMode
import android.os.StrictMode.VmPolicy
import android.view.View
import android.widget.Toast
import androidx.core.content.FileProvider
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import timber.log.Timber
import java.io.File
import java.io.IOException
import kotlin.time.Clock

object UpdateUtil {
    private const val REPO_URL = "https://github.com/sevenclockseven/Home-Assistant-Companion-for-Android"
    private const val RELEASES_LATEST_API_URL =
        "https://api.github.com/repos/sevenclockseven/Home-Assistant-Companion-for-Android/releases/latest"
    private const val FLAVOR_MINIMAL = "minimal"
    private const val APK_NAME_FULL = "app-full-release.apk"
    private const val APK_NAME_MINIMAL = "app-minimal-release.apk"

    @Serializable
    private data class LatestRelease(val tagName: String)

    /** Minimum delay after app launch before checking for updates, so startup is not slowed down. */
    const val UPDATE_CHECK_DELAY_MILLIS = 10_000L

    private const val CHECK_INTERVAL_MILLIS = 60 * 60 * 1000L

    private var mDownloadId: Long = 0

    /**
     * Checks the fork's GitHub releases for a newer version, throttled to once per hour.
     * Shows an update dialog when a newer version exists.
     */
    fun checkNew(context: Context, okHttpClient: OkHttpClient) {
        val checkTime = context.getSharedPreferences("config", Context.MODE_PRIVATE).getLong(
            UpdateActivity.CHECK_TIME,
            0
        )
        val now = Clock.System.now().toEpochMilliseconds()
        if (now - checkTime < CHECK_INTERVAL_MILLIS) {
            return
        }

        githubCheckNew(context, okHttpClient)
    }

    private fun githubCheckNew(context: Context, okHttpClient: OkHttpClient) {
        try {
            val request = Request.Builder().apply {
                url(RELEASES_LATEST_API_URL)
                header("Accept", "application/vnd.github+json")
            }.build()
            okHttpClient.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Timber.w(e, "Update check failed")
                    // Do not throttle on failure so the next launch retries.
                    runBlocking(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            context.getString(commonR.string.update_check_failed),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    val ver = readLatestTag(response) ?: return
                    Timber.d("Update check latest tag: %s", ver)
                    context.getSharedPreferences("config", Context.MODE_PRIVATE).edit()
                        .putLong(UpdateActivity.CHECK_TIME, Clock.System.now().toEpochMilliseconds())
                        .apply()
                    if (!ver.startsWith("v")) {
                        // Defense in depth: fork release tags always start with "v".
                        return
                    }
                    if (!BuildConfig.VERSION_NAME.contains(ver)) {
                        val apkName =
                            if (BuildConfig.FLAVOR == FLAVOR_MINIMAL) APK_NAME_MINIMAL else APK_NAME_FULL
                        val apkUrl = "$REPO_URL/releases/download/$ver/$apkName"
                        Timber.d("Update found, apk url: %s", apkUrl)
                        val updateInfo = UpdateInfo(
                            ver, context.getString(commonR.string.update_download_hint), apkUrl
                        )
                        val intent = Intent(context, UpdateActivity::class.java)
                        intent.putExtra(UpdateActivity.UPDATE_INFO, updateInfo)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    }
                }
            })
        } catch (e: Exception) {
            Timber.w(e, "Update check could not be started")
        }
    }

    private fun readLatestTag(response: Response): String? {
        if (!response.isSuccessful) {
            Timber.w("Update check returned HTTP %s", response.code)
            return null
        }
        return runCatching {
            kotlinJsonMapper.decodeFromString<LatestRelease>(response.body.string()).tagName
        }.getOrElse { e ->
            Timber.w(e, "Update check could not parse the latest release")
            null
        }
    }

    fun getActivityFromView(view: View): Activity? {
        var context: Context = view.context
        while (context is ContextWrapper) {
            if (context is Activity) {
                return context
            }
            context = context.baseContext
        }
        return null
    }

    fun downLoadApk(context: Context, url: String, describeStr: String) {
        clearCurrentTask(context)
        val saveFile = apkFile(context)
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri = Uri.parse(url)
        // From Android 7.0 installing a file:// URI is blocked; a permissive VM policy keeps
        // the legacy direct-file install working without routing through a FileProvider.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val localBuilder = VmPolicy.Builder()
            StrictMode.setVmPolicy(localBuilder.build())
        }
        val requestApk = DownloadManager.Request(uri)
        requestApk.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_MOBILE or DownloadManager.Request.NETWORK_WIFI)
        requestApk.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        if (saveFile.exists()) {
            saveFile.delete()
        }
        requestApk.setDestinationUri(Uri.fromFile(saveFile))
        requestApk.setTitle(describeStr)
        requestApk.setDescription(context.getString(commonR.string.update_notification_downloading))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            requestApk.setRequiresDeviceIdle(false)
            requestApk.setRequiresCharging(false)
        }
        mDownloadId = downloadManager.enqueue(requestApk)
    }

    private fun clearCurrentTask(mContext: Context) {
        if (mDownloadId == 0L) return
        val dm = mContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        try {
            dm.remove(mDownloadId)
        } catch (ex: IllegalArgumentException) {
            Timber.w(ex, "Could not clear previous download task")
        }
    }

    @SuppressLint("QueryPermissionsNeeded")
    fun installApk(context: Context) {
        mDownloadId = 0
        val saveFile: File = apkFile(context)
        val intent = Intent(Intent.ACTION_VIEW)
        if (saveFile.exists()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                val contentUri = FileProvider.getUriForFile(
                    context,
                    context.packageName + ".provider",
                    saveFile
                )
                intent.setDataAndType(contentUri, "application/vnd.android.package-archive")
            } else {
                intent.setDataAndType(
                    Uri.fromFile(saveFile),
                    "application/vnd.android.package-archive"
                )
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            if (context.packageManager.queryIntentActivities(intent, 0).size > 0) {
                context.startActivity(intent)
            }
        }
    }

    private fun apkFile(context: Context): File {
        val dir = File(context.externalCacheDir, "download")
        if (!dir.exists()) {
            dir.mkdir()
        }
        return File(dir, "temp.apk")
    }

    fun getDownloadId(): Long {
        return mDownloadId
    }
}
