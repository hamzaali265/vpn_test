package com.example.vpn_test

import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import java.net.MalformedURLException
import java.net.URL

/**
 * FIX: DoH VPN Manager based on android 2 project
 * This manager handles DoH VPN service lifecycle and configuration
 */
interface DohVpnManager {
    fun isVpnRunning(): Boolean
    fun startVpn(alwaysOn: Boolean)
    fun startVpn(alwaysOn: Boolean, clearCache: Boolean)
    fun stopVpn()
    fun setDohUrl(url: String)
    fun getDohUrl(): String
    fun clearDnsCache()
}

class DohVpnManagerImp(private val context: Context): DohVpnManager {
    private var dohVpnForwarder: DohVpnForwarder? = null
    private var isBound = false
    private var dohURL: String
    private var receiverRegistered = false
    
    // Initialize SharedPreferences
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "DOH_VPN_MANAGER"
        private const val PREF_NAME = "DohVpnPreferences"
        private const val KEY_DOH_URL = "doh_url"
    }

    // Network monitoring
    private val connectivityManager by lazy {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            checkVpnState()
        }
        override fun onLost(network: Network) {
            checkVpnState()
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName?, service: IBinder?) {
            val binder = service as DohVpnForwarder.LocalBinder
            dohVpnForwarder = binder.getService()
            isBound = true
            Log.d(TAG, "DoH VPN service connected")
        }

        override fun onServiceDisconnected(p0: ComponentName?) {
            dohVpnForwarder = null
            isBound = false
            Log.d(TAG, "DoH VPN service disconnected")
        }
    }

    init {
        dohURL = sharedPreferences.getString(KEY_DOH_URL, "") ?: ""
        Log.d(TAG, "Loaded DoH URL from SharedPreferences: $dohURL")
    }

    private fun checkVpnState() {
        // Only check if we believe our VPN was previously running
        if (DohVpnForwarder.isVpnRunning) {
            val vpnActive = isAnyVpnActive()

            if (!vpnActive) {
                Log.d(TAG, "VPN is no longer active - likely disconnected from settings")
                stopVpn()
            } else {
                Log.d(TAG, "VPN is activated")
            }
        }
    }

    private fun isAnyVpnActive(): Boolean {
        val networks = connectivityManager.allNetworks
        for (network in networks) {
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) {
                return true
            }
        }
        return false
    }

    override fun isVpnRunning(): Boolean {
        return DohVpnForwarder.isVpnRunning
    }

    override fun setDohUrl(url: String) {
        if (!isDohUrlFormatValid(url)) {
            Toast.makeText(context, "DoH URL is invalid.", Toast.LENGTH_SHORT).show()
            Log.w(TAG, "DoH Server URL is not valid: $url")
            return
        }

        dohURL = url

        // Save to SharedPreferences
        sharedPreferences.edit().putString(KEY_DOH_URL, dohURL).apply()
        Log.d(TAG, "Saved DoH URL to SharedPreferences: $dohURL")

        // Update DohVpnForwarder if bound
        dohVpnForwarder?.setDohServerUrl(dohURL)
        Log.d(TAG, "DoH URL set to $url")
    }

    override fun getDohUrl(): String {
        return dohURL
    }
    
    override fun clearDnsCache() {
        if (isVpnRunning()) {
            // Restart VPN with cache clearing flag
            stopVpn()
            Toast.makeText(context, "Clearing DNS cache...", Toast.LENGTH_SHORT).show()
            startVpn(false, true)
        } else {
            Toast.makeText(context, "VPN not running. Start VPN first.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun startVpn(alwaysOn: Boolean) {
        startVpn(alwaysOn, false)
    }
    
    override fun startVpn(alwaysOn: Boolean, clearCache: Boolean) {
        // Check if VPN is already active at system level
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        var isVpnActive = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val networks = connectivityManager.allNetworks
            for (network in networks) {
                val capabilities = connectivityManager.getNetworkCapabilities(network)
                if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    isVpnActive = true
                    break
                }
            }
        } else {
            try {
                val networks = connectivityManager.allNetworks
                for (network in networks) {
                    val info = connectivityManager.getNetworkInfo(network)
                    if (info != null && info.type == ConnectivityManager.TYPE_VPN) {
                        isVpnActive = true
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking VPN status: ${e.message}")
            }
        }

        // If VPN is already active at system level but our service is not running, restart it
        if (isVpnActive && dohVpnForwarder == null) {
            Log.d(TAG, "VPN interface active but service not running, restarting service")
            stopVpn()
            Handler(Looper.getMainLooper()).postDelayed({
                actuallyStartVPN(alwaysOn, clearCache)
            }, 500)
            return
        }

        // Normal flow for starting VPN
        val intent = VpnService.prepare(context)

        if (intent != null) {
            // VPN permission not granted, need to request it
            Log.d(TAG, "VPN permission not granted, need to request it")
            return
        }

        actuallyStartVPN(alwaysOn, clearCache)

        // Register for network changes
        connectivityManager.registerNetworkCallback(NetworkRequest.Builder().build(), networkCallback)
    }

    private fun actuallyStartVPN(alwaysOn: Boolean, clearCache: Boolean = false) {
        if (dohURL.isEmpty()) {
            Toast.makeText(context, "VPN failed. Please add DoH URL to start VPN", Toast.LENGTH_SHORT).show()
            return
        }

        if (isVpnRunning()) {
            Toast.makeText(context, "VPN is already started.", Toast.LENGTH_SHORT).show()
            return
        }

        val serviceIntent = Intent(context, DohVpnForwarder::class.java).apply {
            putExtra("DOH_URL", dohURL)
            putExtra("ALWAYS_ON", alwaysOn)
            putExtra("CLEAR_CACHE", clearCache)
        }
        context.startForegroundService(serviceIntent)
        context.bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)

        if (alwaysOn) {
            showAlwaysOnVpnInstructions()
        } else {
            val message = if (clearCache) "DoH VPN started with cleared cache" else "DoH VPN started"
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }

        Log.d(TAG, "DoH VPN started with URL: $dohURL, always-on: $alwaysOn, clear-cache: $clearCache")
    }

    override fun stopVpn() {
        val vpnIntent = Intent(context, DohVpnForwarder::class.java)
        context.stopService(vpnIntent)

        if (dohVpnForwarder != null) {
            dohVpnForwarder?.disconnectVpn()
        }

        if (isBound) {
            context.unbindService(connection)
            isBound = false
        }

        connectivityManager.unregisterNetworkCallback(networkCallback)

        Log.d(TAG, "DoH VPN stopped")
    }

    private fun isDohUrlFormatValid(dohUrl: String?): Boolean {
        if (dohUrl.isNullOrBlank()) return false

        return try {
            val url = URL(dohUrl)
            url.protocol == "https" && url.host.isNotEmpty()
        } catch (e: MalformedURLException) {
            false
        }
    }

    private fun openAlwaysOnSettings() {
        val intent = Intent("android.net.vpn.SETTINGS")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)

        Log.d(TAG, "Prompting user to set Always-On VPN via settings")
    }

    private fun showAlwaysOnVpnInstructions() {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Enable Always-on VPN")
            .setMessage("To ensure your connection remains secure, we recommend enabling Always-on VPN.\n\n" +
                    "Follow these steps:\n" +
                    "1. Go to the VPN settings screen.\n" +
                    "2. Select DoH VPN.\n" +
                    "3. Tap the gear ⚙️ icon or Settings.\n" +
                    "4. Toggle Always-on VPN to ON.\n")
            .setPositiveButton("Go to Settings") { dialog, which ->
                openAlwaysOnSettings()
            }
            .setNegativeButton("Cancel") { dialog, which ->
                dialog.dismiss()
            }
            .setCancelable(false)

        builder.create().show()
    }
}
