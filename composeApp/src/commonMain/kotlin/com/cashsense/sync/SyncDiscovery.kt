package com.cashsense.sync

expect class SyncDiscovery(context: Any? = null) {
    suspend fun discoverPeers(): List<String>
}
