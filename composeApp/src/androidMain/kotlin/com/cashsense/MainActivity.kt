package com.cashsense

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.work.*
import com.cashsense.db.DriverFactory
import com.cashsense.sync.SyncManager
import com.cashsense.worker.SmsScanner
import com.cashsense.util.LogManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

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

    override fun unadvertise() {}

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val readSms = permissions[Manifest.permission.READ_SMS] ?: false
            val receiveSms = permissions[Manifest.permission.RECEIVE_SMS] ?: false
            LogManager.d("MainActivity", "Permissions callback: READ_SMS=$readSms, RECEIVE_SMS=$receiveSms")
            if (readSms && receiveSms) {
                scheduleSmsWork()
            }
        }

    private fun scheduleSmsWork() {
        LogManager.d("MainActivity", "Scheduling WorkManager SMS scan tasks...")
        val workManager = WorkManager.getInstance(this)
        
        // 1. OneTimeWorkRequest for immediate/initial scan
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .setRequiresBatteryNotLow(false)
            .build()

        val oneTimeWork = OneTimeWorkRequestBuilder<com.cashsense.worker.SmsScanWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()

        workManager.enqueueUniqueWork(
            "SmsInitialScanWork",
            ExistingWorkPolicy.KEEP,
            oneTimeWork
        )

        // 2. PeriodicWorkRequest every 6 hours for ongoing checks
        val periodicWork = PeriodicWorkRequestBuilder<com.cashsense.worker.SmsScanWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        workManager.enqueueUniquePeriodicWork(
            "SmsPeriodicScanWork",
            ExistingPeriodicWorkPolicy.KEEP,
            periodicWork
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
        
        // Prepare list of permissions to request
        val permissionsToRequest = mutableListOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        val permissionsNeeded = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsNeeded.isNotEmpty()) {
            LogManager.d("MainActivity", "Requesting missing permissions: $permissionsNeeded")
            requestPermissionLauncher.launch(permissionsNeeded.toTypedArray())
        } else {
            LogManager.d("MainActivity", "All permissions already granted. Scheduling scan...")
            scheduleSmsWork()
        }

        setContent {
            Surface(
                modifier = Modifier.fillMaxSize()
            ) {
                com.cashsense.ui.AppNavigator(presenter)
            }
        }
    }
}
