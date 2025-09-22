package com.example.vpn_test

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class VpnMethodChannel : MethodChannel.MethodCallHandler {
    companion object {
        private const val TAG = "VpnMethodChannel"
        private const val CHANNEL_NAME = "vpn_service"
        private const val VPN_PERMISSION_REQUEST_CODE = 24
    }

    private var methodChannel: MethodChannel? = null
    private var activity: Activity? = null
    private var context: Context? = null
    private var vpnServiceIntent: Intent? = null
    private var pendingPermissionResult: MethodChannel.Result? = null
    
    // FIX: Add DoH VPN manager
    private var dohVpnManager: DohVpnManager? = null

    fun onAttachedToEngine(flutterEngine: FlutterEngine, activity: Activity) {
        this.activity = activity
        this.context = activity.applicationContext
        
        // FIX: Initialize DoH VPN manager
        dohVpnManager = DohVpnManagerImp(activity.applicationContext)
        
        methodChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL_NAME)
        methodChannel?.setMethodCallHandler(this)
        
        Log.d(TAG, "VPN Method Channel attached")
    }

    fun onDetachedFromEngine() {
        methodChannel?.setMethodCallHandler(null)
        methodChannel = null
        activity = null
        context = null
        dohVpnManager = null
        Log.d(TAG, "VPN Method Channel detached")
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "requestVpnPermission" -> {
                requestVpnPermission(result)
            }
            "startVpn" -> {
                val serverHost = call.argument<String>("server_host")
                val serverPort = call.argument<Int>("server_port") ?: 1194
                val serverConfig = call.argument<String>("server_config")
                startVpn(serverHost, serverPort, serverConfig, result)
            }
            "stopVpn" -> {
                stopVpn(result)
            }
            "getVpnStatus" -> {
                getVpnStatus(result)
            }
            "isVpnPermissionGranted" -> {
                isVpnPermissionGranted(result)
            }
            // FIX: Add DoH VPN methods
            "startDohVpn" -> {
                val dohUrl = call.argument<String>("doh_url")
                val alwaysOn = call.argument<Boolean>("always_on") ?: false
                startDohVpn(dohUrl, alwaysOn, result)
            }
            "stopDohVpn" -> {
                stopDohVpn(result)
            }
            "setDohUrl" -> {
                val dohUrl = call.argument<String>("doh_url")
                setDohUrl(dohUrl, result)
            }
            "getDohUrl" -> {
                getDohUrl(result)
            }
            "getDohVpnStatus" -> {
                getDohVpnStatus(result)
            }
            "clearDnsCache" -> {
                clearDnsCache(result)
            }
            else -> {
                result.notImplemented()
            }
        }
    }

    private fun requestVpnPermission(result: MethodChannel.Result) {
        try {
            val intent = VpnService.prepare(context)
            if (intent != null) {
                // Permission required - store the result for later callback
                pendingPermissionResult = result
                activity?.startActivityForResult(intent, VPN_PERMISSION_REQUEST_CODE)
                Log.d(TAG, "Requesting VPN permission")
            } else {
                // Permission already granted
                result.success(mapOf(
                    "granted" to true,
                    "message" to "VPN permission already granted"
                ))
                Log.d(TAG, "VPN permission already granted")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting VPN permission: ${e.message}")
            result.error("PERMISSION_ERROR", "Failed to request VPN permission: ${e.message}", null)
        }
    }

    fun handleVpnPermissionResult(granted: Boolean) {
        pendingPermissionResult?.let { result ->
            if (granted) {
                result.success(mapOf(
                    "granted" to true,
                    "message" to "VPN permission granted"
                ))
                Log.d(TAG, "VPN permission granted")
            } else {
                result.success(mapOf(
                    "granted" to false,
                    "message" to "VPN permission denied"
                ))
                Log.d(TAG, "VPN permission denied")
            }
            pendingPermissionResult = null
        }
    }

    private fun isVpnPermissionGranted(result: MethodChannel.Result) {
        try {
            val intent = VpnService.prepare(context)
            result.success(intent == null)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking VPN permission: ${e.message}")
            result.error("PERMISSION_CHECK_ERROR", "Failed to check VPN permission: ${e.message}", null)
        }
    }

    private fun startVpn(serverHost: String?, serverPort: Int, serverConfig: String?, result: MethodChannel.Result) {
        try {
            if (serverHost.isNullOrEmpty()) {
                result.error("INVALID_PARAMS", "Server host is required", null)
                return
            }

            Log.d(TAG, "Starting VPN to $serverHost:$serverPort")
            
            // Check if VPN permission is granted
            val prepareIntent = VpnService.prepare(context)
            if (prepareIntent != null) {
                Log.w(TAG, "VPN permission not granted")
                result.error("PERMISSION_DENIED", "VPN permission not granted. Please request permission first.", null)
                return
            }

            // Start the VPN service with server information
            vpnServiceIntent = Intent(context, NativeVpnService::class.java).apply {
                action = "START_VPN"
                putExtra("server_host", serverHost)
                putExtra("server_port", serverPort)
                putExtra("server_config", serverConfig)
            }
            
            context?.startForegroundService(vpnServiceIntent)
            
            Log.d(TAG, "VPN service started successfully")
            result.success(mapOf(
                "status" to "started",
                "message" to "VPN started successfully",
                "server" to "$serverHost:$serverPort"
            ))
        } catch (e: Exception) {
            Log.e(TAG, "Error starting VPN: ${e.message}")
            result.error("START_ERROR", "Failed to start VPN: ${e.message}", null)
        }
    }

    private fun stopVpn(result: MethodChannel.Result) {
        try {
            Log.d(TAG, "Stopping VPN")
            
            stopVpnService()
            
            Log.d(TAG, "VPN service stopped successfully")
            result.success(mapOf(
                "status" to "stopped",
                "message" to "VPN stopped successfully"
            ))
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping VPN: ${e.message}")
            result.error("STOP_ERROR", "Failed to stop VPN: ${e.message}", null)
        }
    }

    private fun stopVpnService() {
        context?.let { ctx ->
            val stopIntent = Intent(ctx, NativeVpnService::class.java).apply {
                action = "STOP_VPN"
            }
            ctx.startService(stopIntent)
        }
        vpnServiceIntent = null
    }

    private fun getVpnStatus(result: MethodChannel.Result) {
        try {
            // Check the actual VPN service state
            val status = if (NativeVpnService.isVpnRunning) {
                mapOf(
                    "status" to NativeVpnService.connectionStatus,
                    "message" to "VPN is currently active"
                )
            } else {
                mapOf(
                    "status" to "disconnected",
                    "message" to "VPN is not active"
                )
            }
            result.success(status)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting VPN status: ${e.message}")
            result.error("STATUS_ERROR", "Failed to get VPN status: ${e.message}", null)
        }
    }
    
    // FIX: DoH VPN method implementations
    private fun startDohVpn(dohUrl: String?, alwaysOn: Boolean, result: MethodChannel.Result) {
        try {
            if (dohUrl.isNullOrEmpty()) {
                result.error("INVALID_PARAMS", "DoH URL is required", null)
                return
            }

            Log.d(TAG, "Starting DoH VPN with URL: $dohUrl")
            
            // Check if VPN permission is granted
            val prepareIntent = VpnService.prepare(context)
            if (prepareIntent != null) {
                Log.w(TAG, "VPN permission not granted")
                result.error("PERMISSION_DENIED", "VPN permission not granted. Please request permission first.", null)
                return
            }

            // Set DoH URL and start VPN
            dohVpnManager?.setDohUrl(dohUrl)
            dohVpnManager?.startVpn(alwaysOn)
            
            Log.d(TAG, "DoH VPN started successfully")
            result.success(mapOf(
                "status" to "started",
                "message" to "DoH VPN started successfully",
                "doh_url" to dohUrl,
                "always_on" to alwaysOn
            ))
        } catch (e: Exception) {
            Log.e(TAG, "Error starting DoH VPN: ${e.message}")
            result.error("START_ERROR", "Failed to start DoH VPN: ${e.message}", null)
        }
    }

    private fun stopDohVpn(result: MethodChannel.Result) {
        try {
            Log.d(TAG, "Stopping DoH VPN")
            
            dohVpnManager?.stopVpn()
            
            Log.d(TAG, "DoH VPN stopped successfully")
            result.success(mapOf(
                "status" to "stopped",
                "message" to "DoH VPN stopped successfully"
            ))
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping DoH VPN: ${e.message}")
            result.error("STOP_ERROR", "Failed to stop DoH VPN: ${e.message}", null)
        }
    }

    private fun setDohUrl(dohUrl: String?, result: MethodChannel.Result) {
        try {
            if (dohUrl.isNullOrEmpty()) {
                result.error("INVALID_PARAMS", "DoH URL is required", null)
                return
            }

            dohVpnManager?.setDohUrl(dohUrl)
            
            Log.d(TAG, "DoH URL set to: $dohUrl")
            result.success(mapOf(
                "status" to "success",
                "message" to "DoH URL set successfully",
                "doh_url" to dohUrl
            ))
        } catch (e: Exception) {
            Log.e(TAG, "Error setting DoH URL: ${e.message}")
            result.error("SET_URL_ERROR", "Failed to set DoH URL: ${e.message}", null)
        }
    }

    private fun getDohUrl(result: MethodChannel.Result) {
        try {
            val dohUrl = dohVpnManager?.getDohUrl() ?: ""
            result.success(mapOf(
                "doh_url" to dohUrl,
                "message" to if (dohUrl.isNotEmpty()) "DoH URL retrieved" else "No DoH URL set"
            ))
        } catch (e: Exception) {
            Log.e(TAG, "Error getting DoH URL: ${e.message}")
            result.error("GET_URL_ERROR", "Failed to get DoH URL: ${e.message}", null)
        }
    }

    private fun getDohVpnStatus(result: MethodChannel.Result) {
        try {
            val isRunning = dohVpnManager?.isVpnRunning() ?: false
            val status = if (isRunning) {
                mapOf(
                    "status" to "connected",
                    "message" to "DoH VPN is currently active"
                )
            } else {
                mapOf(
                    "status" to "disconnected",
                    "message" to "DoH VPN is not active"
                )
            }
            result.success(status)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting DoH VPN status: ${e.message}")
            result.error("STATUS_ERROR", "Failed to get DoH VPN status: ${e.message}", null)
        }
    }

    private fun clearDnsCache(result: MethodChannel.Result) {
        try {
            Log.d(TAG, "Clearing DNS cache")
            
            dohVpnManager?.clearDnsCache()
            
            Log.d(TAG, "DNS cache cleared successfully")
            result.success(mapOf(
                "status" to "success",
                "message" to "DNS cache cleared successfully"
            ))
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing DNS cache: ${e.message}")
            result.error("CLEAR_CACHE_ERROR", "Failed to clear DNS cache: ${e.message}", null)
        }
    }
}