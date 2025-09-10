package com.example.vpn_test

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.nio.channels.DatagramChannel
import java.nio.channels.SocketChannel
import java.util.concurrent.atomic.AtomicBoolean

class NativeVpnService : VpnService() {
    companion object {
        private const val TAG = "NativeVpnService"
        private const val VPN_ADDRESS = "10.0.0.2"
        private const val VPN_ROUTE = "0.0.0.0"
        private const val DNS_SERVER = "8.8.8.8"
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private val isRunning = AtomicBoolean(false)
    private var vpnThread: Thread? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "NativeVpnService started")
        
        when (intent?.action) {
            "START_VPN" -> startVpn()
            "STOP_VPN" -> stopVpn()
        }
        
        return START_STICKY
    }

    private fun startVpn() {
        if (isRunning.get()) {
            Log.d(TAG, "VPN already running")
            return
        }

        try {
            val builder = Builder()
                .setSession("VPN Gate")
                .addAddress(VPN_ADDRESS, 32)
                .addRoute(VPN_ROUTE, 0)
                .addDnsServer(DNS_SERVER)
                .addDnsServer("8.8.4.4")
                .setMtu(1500)

            vpnInterface = builder.establish()
            
            if (vpnInterface != null) {
                Log.d(TAG, "VPN interface established successfully")
                isRunning.set(true)
                startVpnThread()
                
                // Send success callback to Flutter
                sendVpnStatus("connected")
            } else {
                Log.e(TAG, "Failed to establish VPN interface")
                sendVpnStatus("failed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting VPN: ${e.message}")
            sendVpnStatus("error")
        }
    }

    private fun stopVpn() {
        Log.d(TAG, "Stopping VPN")
        isRunning.set(false)
        
        vpnThread?.interrupt()
        vpnThread = null
        
        vpnInterface?.close()
        vpnInterface = null
        
        sendVpnStatus("disconnected")
        stopSelf()
    }

    private fun startVpnThread() {
        vpnThread = Thread {
            try {
                val vpnInput = FileInputStream(vpnInterface?.fileDescriptor)
                val vpnOutput = FileOutputStream(vpnInterface?.fileDescriptor)
                
                Log.d(TAG, "VPN thread started")
                
                while (isRunning.get() && !Thread.currentThread().isInterrupted) {
                    // Simple packet forwarding simulation
                    // In a real implementation, you would handle actual packet forwarding
                    Thread.sleep(100)
                }
                
                vpnInput.close()
                vpnOutput.close()
                Log.d(TAG, "VPN thread stopped")
            } catch (e: Exception) {
                Log.e(TAG, "Error in VPN thread: ${e.message}")
            }
        }
        vpnThread?.start()
    }

    private fun sendVpnStatus(status: String) {
        // Send status to Flutter via method channel
        // This will be handled by the Flutter side
        Log.d(TAG, "VPN Status: $status")
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
        Log.d(TAG, "NativeVpnService destroyed")
    }
}
