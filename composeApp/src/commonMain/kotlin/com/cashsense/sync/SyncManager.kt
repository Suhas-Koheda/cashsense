package com.cashsense.sync

import com.cashsense.db.CashSenseDb
import com.cashsense.db.TransactionEntity
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.cio.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

class SyncManager(private val database: CashSenseDb) {
    private val json = Json { ignoreUnknownKeys = true }
    private var server: ApplicationEngine? = null

    interface ServiceAdvertiser {
        fun advertise(name: String, type: String, port: Int)
        fun unadvertise()
    }

    private var advertiser: ServiceAdvertiser? = null

    fun startSyncServer(port: Int = 8080, advertiser: ServiceAdvertiser? = null) {
        this.advertiser = advertiser
        advertiser?.advertise("CashSense-${System.currentTimeMillis()}", "_cashsense._tcp", port)
        try {
            server = embeddedServer(CIO, port = port) {
                install(WebSockets)
                routing {
                    webSocket("/sync") {
                        val lastSyncFrame = incoming.receive() as? Frame.Text
                        val lastSync = lastSyncFrame?.readText()?.toLongOrNull() ?: 0L

                        val localChanges = database.cashSenseQueries.getChangesSince(lastSync).executeAsList()
                        send(Frame.Text(json.encodeToString(localChanges)))

                        val incomingFrame = incoming.receive() as? Frame.Text
                        incomingFrame?.readText()?.let { jsonStr ->
                            try {
                                val incomingChanges = json.decodeFromString<List<TransactionEntity>>(jsonStr)
                                incomingChanges.forEach { transaction ->
                                    val localTransaction = database.cashSenseQueries.selectAll().executeAsList()
                                        .find { it.id == transaction.id }
                                    if (localTransaction == null || transaction.lastModified > localTransaction.lastModified) {
                                        database.cashSenseQueries.insertTransaction(transaction)
                                    }
                                }
                            } catch (e: Exception) {
                                // Handle parsing error
                            }
                        }
                    }
                }
            }.start(wait = false)
        } catch (e: java.net.BindException) {
            // Port already in use, handle gracefully
            server = null
        }
    }

    fun stopSyncServer() {
        advertiser?.unadvertise()
        server?.stop(1, 5, TimeUnit.SECONDS)
        server = null
    }
}
