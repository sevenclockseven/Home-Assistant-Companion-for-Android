package io.homeassistant.companion.android.update

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.os.Parcelable
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.update.UpdateUtil.downLoadApk
import io.homeassistant.companion.android.update.UpdateUtil.getDownloadId
import kotlinx.parcelize.Parcelize
import timber.log.Timber
import java.lang.ref.WeakReference
import kotlin.time.Clock

@Parcelize
data class UpdateInfo(
    var version: String,
    var updateMsg: String?,
    var updateUrl: String,
    /**
     * Prompt mode: 0 = never prompt, 1 = prompt, 2 = forced update.
     */
    var updateType: Int = 0,

    ) : Parcelable


class UpdateActivity : AppCompatActivity() {
    companion object {
        const val CHECK_TIME = "CheckTime"
        const val UPDATE_INFO = "UpdateInfo"
        var forceMark = false
        var WHAT_PROGRESS = 1001
    }

    private var updateInfo: UpdateInfo? = null
    private var mDownloadManager: DownloadManager? = null
    private val mQueryProgressRunnable = QueryRunnable()
    private var updateDialog: UpdateDialog? = null
    private var mHandler: Handler? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setReceiver()
        showDialog(intent.getParcelableExtra(UPDATE_INFO)!!)
    }


    private fun showDialog(updateInfo: UpdateInfo) {
        this.updateInfo = updateInfo
        updateDialog = UpdateDialog(this, updateInfo)
        mHandler = MyHandler(updateDialog!!)
        updateDialog?.setCancelable(false)
        mDownloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        if (getDownloadId() != 0L) {
            updateDialog!!.isUpdateing()
            startQuery()
        }
        updateDialog?.setOnClickListener { type: Int ->
            if (type == 1) {
                startDownload(this@UpdateActivity, updateInfo)
                startQuery()
            } else {
                qFinish()
                return@setOnClickListener
            }
        }
        if (updateInfo.updateType == 2) {
            forceMark = true
        }
        try {
            updateDialog?.show()
        } catch (e: Exception) {
            Timber.w(e, "Update dialog could not be shown")
        }

        getSharedPreferences("config", Context.MODE_PRIVATE).edit()
            .putLong(CHECK_TIME, Clock.System.now().toEpochMilliseconds()).apply()
    }

    private fun qFinish() {
        if (updateInfo?.updateType != 2) {
            finish()
            overridePendingTransition(0, 0)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (updateDialog != null && updateDialog!!.isShowing) {
            updateDialog?.dismiss()
        }
    }

    private var isRegisterReceiver = false

    private fun setReceiver() {
        if (!isRegisterReceiver) {
            val receiver = DownloadReceiver()
            val intentFilter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                this.registerReceiver(receiver, intentFilter, RECEIVER_EXPORTED)
            } else {
                this.registerReceiver(receiver, intentFilter)
            }
            isRegisterReceiver = true
        }
    }

    private fun startQuery() {
        if (getDownloadId() != 0L) {
            mHandler!!.post(mQueryProgressRunnable)
        }
    }

    private inner class QueryRunnable : Runnable {
        override fun run() {
            queryState()
            mHandler!!.postDelayed(mQueryProgressRunnable, 100)
        }
    }

    @SuppressLint("Range")
    private fun queryState() {
        val c = mDownloadManager!!.query(DownloadManager.Query().setFilterById(getDownloadId()))
        if (c == null) {
            Toast.makeText(this, getString(commonR.string.update_download_failed), Toast.LENGTH_SHORT).show()
            qFinish()
        } else {
            if (!c.moveToFirst()) {
                qFinish()
                updateDialog?.setProgress(1, 1)
                if (!c.isClosed) {
                    c.close()
                }
                return
            }
            val mDownload_so_far =
                c.getInt(c.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val mDownload_all = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            val msg = Message.obtain()
            if (mDownload_all > 0) {
                msg.what = WHAT_PROGRESS
                msg.arg1 = mDownload_so_far
                msg.arg2 = mDownload_all
                mHandler!!.sendMessage(msg)
            }
            if (!c.isClosed) {
                c.close()
            }
        }
    }

    private fun startDownload(context: Context, updateInfo: UpdateInfo) {
        if (getDownloadId() != 0L) {
            clearCurrentTask(context, getDownloadId())
        }
        downLoadApk(
            context,
            updateInfo.updateUrl,
            "HomeAssistant"
        )
    }

    private fun clearCurrentTask(mContext: Context, downloadId: Long) {
        val dm = mContext.getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        try {
            dm.remove(downloadId)
        } catch (ex: IllegalArgumentException) {
            ex.printStackTrace()
        }
    }

    private fun stopQuery() {
        mHandler!!.removeCallbacks(mQueryProgressRunnable)
    }

    private fun removeDownload() {
        if (mDownloadManager != null) {
            mDownloadManager!!.remove(getDownloadId())
        }
    }

    private class MyHandler(updateDialog: UpdateDialog) : Handler() {
        private val updateDialogWeak: WeakReference<UpdateDialog>

        init {
            updateDialogWeak = WeakReference(updateDialog)
        }

        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            val updateDialog = updateDialogWeak.get()
            if (msg.what == WHAT_PROGRESS) {
                updateDialog?.setProgress(msg.arg1, msg.arg2)
            }
        }
    }
}
