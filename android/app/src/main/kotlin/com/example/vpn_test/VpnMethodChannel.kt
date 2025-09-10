package com.example.vpn_test

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result

class VpnMethodChannel : FlutterPlugin, MethodCallHandler {
    private lateinit var channel: MethodChannel
    private lateinit var context: Context
    private var activity: Activity? = null
    private var vpnServiceIntent: Intent? = null

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "vpn_service")
        channel.setMethodCallHandler(this)
        context = flutterPluginBinding.applicationContext
    }

    fun onAttachedToEngine(flutterEngine: FlutterEngine, activity: Activity) {
        channel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "vpn_service")
        channel.setMethodCallHandler(this)
        this.context = activity
        this.activity = activity
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "startVpn" -> {
                startVpn(result)
            }
            "stopVpn" -> {
                stopVpn(result)
            }
            "getVpnStatus" -> {
                getVpnStatus(result)
            }
            "requestVpnPermission" -> {
                requestVpnPermission(result)
            }
            else -> {
                result.notImplemented()
            }
        }
    }

    private fun startVpn(result: Result) {
        try {
            Log.d("VpnMethodChannel", "Starting VPN")
            
            // Check if VPN permission is granted
            val intent = VpnService.prepare(context)
            if (intent != null) {
                Log.d("VpnMethodChannel", "VPN permission not granted")
                result.error("PERMISSION_DENIED", "VPN permission not granted", null)
                return
            }

            // Start the VPN service
            vpnServiceIntent = Intent(context, NativeVpnService::class.java).apply {
                action = "START_VPN"
            }
            context.startForegroundService(vpnServiceIntent)
            
            Log.d("VpnMethodChannel", "VPN service started")
            result.success("VPN started successfully")
        } catch (e: Exception) {
            Log.e("VpnMethodChannel", "Error starting VPN: ${e.message}")
            result.error("START_ERROR", e.message, null)
        }
    }

    private fun stopVpn(result: Result) {
        try {
            Log.d("VpnMethodChannel", "Stopping VPN")
            
            val stopIntent = Intent(context, NativeVpnService::class.java).apply {
                action = "STOP_VPN"
            }
            context.startService(stopIntent)
            
            Log.d("VpnMethodChannel", "VPN service stopped")
            result.success("VPN stopped successfully")
        } catch (e: Exception) {
            Log.e("VpnMethodChannel", "Error stopping VPN: ${e.message}")
            result.error("STOP_ERROR", e.message, null)
        }
    }

    private fun getVpnStatus(result: Result) {
        // Check if VPN service is running and connected
        // This is a simplified implementation - in a real app you'd check the actual VPN state
        if (vpnServiceIntent != null) {
            result.success("connected")
        } else {
            result.success("disconnected")
        }
    }

    private fun requestVpnPermission(result: Result) {
        try {
            val intent = VpnService.prepare(context)
            if (intent != null) {
                Log.d("VpnMethodChannel", "VPN permission needed, launching permission dialog")
                activity?.startActivityForResult(intent, 24) // VPN_PERMISSION_REQUEST_CODE
                result.success("VPN permission dialog launched")
            } else {
                Log.d("VpnMethodChannel", "VPN permission already granted")
                result.success("VPN permission granted")
            }
        } catch (e: Exception) {
            Log.e("VpnMethodChannel", "Error requesting VPN permission: ${e.message}")
            result.error("PERMISSION_ERROR", e.message, null)
        }
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }
}
