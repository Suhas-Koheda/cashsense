package com.cashsense.sync

import com.cashsense.db.CashSenseDb
import com.cashsense.db.TransactionEntity
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SyncClient(private val database: CashSenseDb) {
    private val client = HttpClient {
        install(WebSockets)
    }
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun syncWithPeer(host: String, port: Int = 8080) {
        try {
            client.webSocket(host = host, port = port, path = "/sync") {
                // 1. Send last sync timestamp
                val lastSync = 0L // TODO: Store last sync time in settings/DB
                send(Frame.Text(lastSync.toString()))

                // 2. Receive changes from peer
                val changesFrame = incoming.receive() as? Frame.Text
                changesFrame?.readText()?.let { jsonStr ->
                    val incomingChanges = json.decodeFromString<List<TransactionEntity>>(jsonStr)
                    incomingChanges.forEach { transaction ->
                        val localTransaction = database.cashSenseQueries.selectAll().executeAsList()
                            .find { it.id == transaction.id }
                        if (localTransaction == null || transaction.lastModified > localTransaction.lastModified) {
                            database.cashSenseQueries.insertTransaction(transaction)
                        }
                    }
                }

                // 3. Send our changes
                val localChanges = database.cashSenseQueries.getChangesSince(lastSync).executeAsList()
                send(Frame.Text(json.encodeToString(localChanges)))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
