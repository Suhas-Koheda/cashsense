package com.cashsense.sync

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

actual class SyncDiscovery actual constructor(private val context: Any?) {
    actual suspend fun discoverPeers(): List<String> = suspendCancellableCoroutine { cont ->
        val ctx = context as? Context
        if (ctx == null) {
            cont.resume(emptyList())
            return@suspendCancellableCoroutine
        }
        val nsdManager = ctx.getSystemService(Context.NSD_SERVICE) as NsdManager
        val serviceType = "_cashsense._tcp."
        val listeners = mutableListOf<String>()

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                serviceInfo.host?.hostAddress?.let { listeners.add(it) }
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
            override fun onDiscoveryStopped(serviceType: String) {
                cont.resume(listeners.filter { it.isNotBlank() })
            }
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                cont.resume(emptyList())
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                cont.resume(listeners.filter { it.isNotBlank() })
            }
        }

        nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        Thread {
            Thread.sleep(5000)
            nsdManager.stopServiceDiscovery(discoveryListener)
        }.start()
    }
}
