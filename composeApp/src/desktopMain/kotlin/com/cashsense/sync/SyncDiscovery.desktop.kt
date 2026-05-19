package com.cashsense.sync

import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceListener
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

actual class SyncDiscovery actual constructor(private val context: Any?) {
    actual suspend fun discoverPeers(): List<String> = suspendCancellableCoroutine { cont ->
        val jmdns = JmDNS.create()
        val peers = mutableListOf<String>()
        val listener = object : ServiceListener {
            override fun serviceAdded(event: ServiceEvent) {
                jmdns.requestServiceInfo(event.type, event.name)
            }
            override fun serviceRemoved(event: ServiceEvent) {}
            override fun serviceResolved(event: ServiceEvent) {
                event.info.inetAddresses.firstOrNull()?.hostAddress?.let { peers.add(it) }
            }
        }
        jmdns.addServiceListener("_cashsense._tcp.local.", listener)
        Thread {
            Thread.sleep(5000)
            jmdns.removeServiceListener("_cashsense._tcp.local.", listener)
            jmdns.close()
            cont.resume(peers.filter { it.isNotBlank() })
        }.start()
    }
}
