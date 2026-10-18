package com.dkanada.gramophone.service.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.dkanada.gramophone.util.LocalLibrary

/** Handles Cancel in the save notification by dropping the whole save queue. */
class SaveCancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()

        Thread {
            try {
                LocalLibrary.cancelSaves()
            } catch (err: Exception) {
                Log.w(TAG, "onReceive: ${err.message}", err)
            } finally {
                result.finish()
            }
        }.start()
    }

    companion object {
        private const val TAG = "SaveCancelReceiver"
    }
}
