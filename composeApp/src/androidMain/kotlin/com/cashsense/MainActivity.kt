package com.cashsense

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.cashsense.db.DriverFactory
import com.cashsense.sync.SyncManager
import com.cashsense.worker.SmsScanner
import com.cashsense.util.LogManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity(), SyncManager.ServiceAdvertiser {
    private lateinit var smsScanner: SmsScanner
    private val scope = CoroutineScope(Dispatchers.Main)
    private var nsdManager: android.net.nsd.NsdManager? = null

    override fun advertise(name: String, type: String, port: Int) {
        nsdManager = getSystemService(android.content.Context.NSD_SERVICE) as android.net.nsd.NsdManager
        val serviceInfo = android.net.nsd.NsdServiceInfo().apply {
            serviceName = name
            serviceType = "$type."
            setPort(port)
        }
        nsdManager?.registerService(serviceInfo, android.net.nsd.NsdManager.PROTOCOL_DNS_SD, object : android.net.nsd.NsdManager.RegistrationListener {
            override fun onServiceRegistered(NsdServiceInfo: android.net.nsd.NsdServiceInfo) {}
            override fun onRegistrationFailed(serviceInfo: android.net.nsd.NsdServiceInfo, errorCode: Int) {}
            override fun onServiceUnregistered(arg0: android.net.nsd.NsdServiceInfo) {}
            override fun onUnregistrationFailed(serviceInfo: android.net.nsd.NsdServiceInfo, errorCode: Int) {}
        })
    }

    override fun unadvertise() {
        // Implementation for unregistering service would go here if we kept a reference to the listener
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val readSms = permissions[Manifest.permission.READ_SMS] ?: false
            val receiveSms = permissions[Manifest.permission.RECEIVE_SMS] ?: false
            if (readSms && receiveSms) {
                startSmsScanWorker()
            }
        }

    private fun startSmsScanWorker() {
        val workRequest = androidx.work.OneTimeWorkRequestBuilder<com.cashsense.worker.SmsScanWorker>()
            .build()
        androidx.work.WorkManager.getInstance(this).enqueueUniqueWork(
            "SmsScanWork",
            androidx.work.ExistingWorkPolicy.KEEP,
            workRequest
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LogManager.d("MainActivity", "onCreate started")
        val db = com.cashsense.db.DatabaseModule.getDatabase(DriverFactory(this))
        val syncManager = SyncManager(db)
        try {
            syncManager.startSyncServer(advertiser = this)
        } catch (e: Exception) {
            LogManager.e("MainActivity", "Failed to start sync server: ${e.message}")
        }

        val syncDiscovery = com.cashsense.sync.SyncDiscovery(this)
        val repository = com.cashsense.repository.CashSenseRepository(db)
        val presenter = com.cashsense.ui.DashboardPresenter(repository, syncDiscovery, scope)

        smsScanner = SmsScanner(this, db)
        
        val hasReadSms = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        val hasReceiveSms = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        
        if (!hasReadSms || !hasReceiveSms) {
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS))
        } else {
            startSmsScanWorker()
        }

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    com.cashsense.ui.AppNavigator(presenter)
                }
            }
        }
    }
}
