package com.cashsense

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

class TransactionSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (sms in messages) {
                val body = sms.displayMessageBody
                val sender = sms.displayOriginatingAddress
                val workRequest = OneTimeWorkRequestBuilder<com.cashsense.worker.ProcessSmsWorker>()
                    .setInputData(workDataOf("sms_body" to body, "sms_sender" to sender))
                    .build()
                WorkManager.getInstance(context).enqueue(workRequest)
            }
        }
    }
}
