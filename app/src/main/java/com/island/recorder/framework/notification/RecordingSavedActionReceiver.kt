package com.island.recorder.framework.notification

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

class RecordingSavedActionReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != RecordingSavedNotificationManager.ACTION_DELETE_RECORDING) return

        val uri = intent.getStringExtra(RecordingSavedNotificationManager.EXTRA_URI)
            ?.let(Uri::parse)
            ?: return
        val isDocumentUri = intent.getBooleanExtra(
            RecordingSavedNotificationManager.EXTRA_IS_DOCUMENT_URI,
            false
        )
        val notificationId = intent.getIntExtra(
            RecordingSavedNotificationManager.EXTRA_NOTIFICATION_ID,
            RecordingSavedNotificationManager.SAVED_NOTIFICATION_ID
        )
        val pendingResult = goAsync()

        scope.launch {
            try {
                if (isDocumentUri) {
                    DocumentsContract.deleteDocument(context.contentResolver, uri)
                } else {
                    context.contentResolver.delete(uri, null, null)
                }
                context.getSystemService(NotificationManager::class.java)
                    .cancel(notificationId)
                Timber.d("Deleted recording from saved notification: $uri")
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete recording from saved notification: $uri")
            } finally {
                pendingResult.finish()
            }
        }
    }
}
