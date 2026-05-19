package com.cashsense

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.cashsense.db.CashSenseDb
import com.cashsense.db.DriverFactory
import com.cashsense.sync.SyncManager

fun main() = application {
    val driver = DriverFactory().createDriver()
    val db = CashSenseDb(driver)
    val syncManager = SyncManager(db)
    
    val advertiser = object : SyncManager.ServiceAdvertiser {
        private var jmdns: javax.jmdns.JmDNS? = null
        override fun advertise(name: String, type: String, port: Int) {
            jmdns = javax.jmdns.JmDNS.create()
            val serviceInfo = javax.jmdns.ServiceInfo.create("$type.local.", name, port, "CashSense sync")
            jmdns?.registerService(serviceInfo)
        }
        override fun unadvertise() {
            jmdns?.unregisterAllServices()
            jmdns?.close()
        }
    }
    syncManager.startSyncServer(advertiser = advertiser)

    val syncDiscovery = com.cashsense.sync.SyncDiscovery()
    val repository = com.cashsense.repository.CashSenseRepository(db)
    val presenter = com.cashsense.ui.DashboardPresenter(repository, syncDiscovery, kotlinx.coroutines.GlobalScope)

    Window(onCloseRequest = ::exitApplication, title = "CashSense") {
        MaterialTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                com.cashsense.ui.DashboardScreen(presenter)
            }
        }
    }
}
