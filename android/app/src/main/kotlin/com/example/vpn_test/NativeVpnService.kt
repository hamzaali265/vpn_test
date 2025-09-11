package com.example.vpn_test

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.util.concurrent.atomic.AtomicBoolean

class NativeVpnService : VpnService() {
    companion object {
        private const val TAG = "NativeVpnService"
        private const val VPN_ADDRESS = "10.0.0.2"
        private const val VPN_ROUTE = "0.0.0.0"
        private const val DNS_SERVER_1 = "8.8.8.8"
        private const val DNS_SERVER_2 = "8.8.4.4"
        private const val DNS_SERVER_3 = "1.1.1.1"
        private const val DNS_SERVER_4 = "1.0.0.1"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "VPN_SERVICE_CHANNEL"
        
        // Connection status tracking
        @Volatile
        var isVpnRunning = false
            private set
        
        @Volatile
        var connectionStatus = "disconnected"
            private set
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private val isRunning = AtomicBoolean(false)
    private var vpnThread: Thread? = null
    private var serverConfig: String? = null
    private var serverHost: String? = null
    private var serverPort: Int = 1194
    private var vpnSocket: DatagramSocket? = null
    private var serverAddress: InetSocketAddress? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        
        // Start as foreground service immediately to avoid timeout
        startForeground(NOTIFICATION_ID, createNotification())
        
        Log.d(TAG, "NativeVpnService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "NativeVpnService onStartCommand: ${intent?.action}")
        
        when (intent?.action) {
            "START_VPN" -> {
                serverConfig = intent.getStringExtra("server_config")
                serverHost = intent.getStringExtra("server_host")
                serverPort = intent.getIntExtra("server_port", 1194)
                
                Log.d(TAG, "Received VPN start command - Host: $serverHost, Port: $serverPort, Config: $serverConfig")
                
                startVpn()
            }
            "STOP_VPN" -> stopVpn()
        }
        
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "VPN Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "VPN Service Notification Channel"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val stopIntent = Intent(this, NativeVpnService::class.java).apply {
            action = "STOP_VPN"
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VPN Connected")
            .setContentText("Connected to $serverHost:$serverPort")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Disconnect",
                stopPendingIntent
            )
            .build()
    }

    private fun startVpn() {
        if (isRunning.get()) {
            Log.d(TAG, "VPN already running")
            return
        }

        try {
            Log.d(TAG, "Starting VPN connection to server: $serverHost:$serverPort")
            
            // Validate server host
            if (serverHost.isNullOrEmpty()) {
                throw IllegalArgumentException("Server host is null or empty")
            }
            
            // Create UDP socket for VPN server connection (this is allowed on main thread)
            vpnSocket = DatagramSocket()
            serverAddress = InetSocketAddress(serverHost, serverPort)
            Log.d(TAG, "Created UDP socket and server address: $serverAddress")
            
            // Note: We'll test the connection in the VPN thread to avoid NetworkOnMainThreadException
            
            val builder = Builder()
                .setSession("VPN Test - $serverHost")
                .addAddress(VPN_ADDRESS, 32)
                .addRoute(VPN_ROUTE, 0)
                .addDnsServer(DNS_SERVER_1)
                .addDnsServer(DNS_SERVER_2)
                .addDnsServer(DNS_SERVER_3)
                .addDnsServer(DNS_SERVER_4)
                .setMtu(1500)
                .setBlocking(false)

            vpnInterface = builder.establish()
            
            if (vpnInterface != null) {
                Log.d(TAG, "VPN interface established successfully")
                isRunning.set(true)
                isVpnRunning = true
                connectionStatus = "connected"
                
                // Start the VPN thread
                startVpnThread()
                
                Log.d(TAG, "VPN started successfully")
            } else {
                Log.e(TAG, "Failed to establish VPN interface")
                // Clean up any partial state
                isRunning.set(false)
                isVpnRunning = false
                connectionStatus = "disconnected"
                vpnSocket?.close()
                vpnSocket = null
                stopSelf()
            }
        } catch (e: Exception) {
            val errorMessage = e.message ?: "Unknown error occurred"
            Log.e(TAG, "Error starting VPN: $errorMessage", e)
            // Clean up any partial state
            isRunning.set(false)
            isVpnRunning = false
            connectionStatus = "disconnected"
            vpnInterface?.close()
            vpnInterface = null
            vpnSocket?.close()
            vpnSocket = null
            stopSelf()
        }
    }

    private fun stopVpn() {
        Log.d(TAG, "Stopping VPN")
        isRunning.set(false)
        isVpnRunning = false
        connectionStatus = "disconnected"
        
        // Interrupt and stop the VPN thread
        vpnThread?.interrupt()
        vpnThread = null
        
        // Close the VPN interface
        try {
            vpnInterface?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing VPN interface: ${e.message}")
        } finally {
            vpnInterface = null
        }
        
        // Close the VPN socket
        try {
            vpnSocket?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing VPN socket: ${e.message}")
        } finally {
            vpnSocket = null
        }
        
        // Stop foreground service
        stopForeground(true)
        stopSelf()
        
        Log.d(TAG, "VPN stopped")
    }

    private fun startVpnThread() {
        vpnThread = Thread({
            try {
                Log.d(TAG, "VPN thread started")
                runVpnLoop()
            } catch (e: Exception) {
                Log.e(TAG, "Error in VPN thread: ${e.message}")
            } finally {
                Log.d(TAG, "VPN thread stopped")
            }
        }, "VPN-Thread")
        
        vpnThread?.start()
    }

    private fun runVpnLoop() {
        val vpnInput = FileInputStream(vpnInterface?.fileDescriptor)
        val vpnOutput = FileOutputStream(vpnInterface?.fileDescriptor)
        val buffer = ByteArray(32767)
        val receiveBuffer = ByteArray(32767)
        
        try {
            Log.d(TAG, "VPN loop started - forwarding packets to $serverHost:$serverPort")
            
            // Test connection to VPN server (now in background thread)
            if (vpnSocket != null && serverAddress != null) {
                try {
                    val testPacket = DatagramPacket(byteArrayOf(0x00), 1, serverAddress)
                    vpnSocket?.send(testPacket)
                    Log.d(TAG, "VPN server connection test sent successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending test packet to VPN server: ${e.message}", e)
                }
            }
            
            while (isRunning.get() && !Thread.currentThread().isInterrupted) {
                try {
                    // Read data from VPN interface (outgoing packets from device)
                    val length = vpnInput.read(buffer)
                    if (length > 0) {
                        Log.v(TAG, "Read $length bytes from TUN interface")
                        
                        // Process the packet here
                        processOutgoingPacket(buffer, length)
                        
                        // Forward packet to VPN server
                        if (vpnSocket != null && serverAddress != null) {
                            try {
                                val packet = DatagramPacket(buffer, length, serverAddress)
                                vpnSocket?.send(packet)
                                Log.v(TAG, "Sent $length bytes to VPN server")
                            } catch (e: IOException) {
                                Log.e(TAG, "Error sending packet to VPN server: ${e.message}")
                            }
                        }
                    }
                    
                    // Handle incoming packets from VPN server
                    handleIncomingPackets(vpnOutput, receiveBuffer)
                    
                } catch (e: IOException) {
                    if (isRunning.get()) {
                        Log.e(TAG, "IO error in VPN loop: ${e.message}")
                        break
                    }
                }
            }
        } finally {
            try {
                vpnInput.close()
                vpnOutput.close()
            } catch (e: IOException) {
                Log.e(TAG, "Error closing VPN streams: ${e.message}")
            }
        }
    }

    private fun processOutgoingPacket(buffer: ByteArray, length: Int) {
        // Analyze the IP packet
        if (length >= 20) { // Minimum IP header length
            val version = (buffer[0].toInt() and 0xF0) shr 4
            val headerLength = (buffer[0].toInt() and 0x0F) * 4
            val protocol = buffer[9].toInt() and 0xFF
            
            Log.v(TAG, "Outgoing packet - Version: $version, Header Length: $headerLength, Protocol: $protocol")
            
            // In a real VPN, you would encrypt this packet and send it to your server
        }
    }

    private fun handleIncomingPackets(vpnOutput: FileOutputStream, receiveBuffer: ByteArray) {
        // Receive packets from VPN server
        if (vpnSocket != null) {
            try {
                // Set a short timeout to avoid blocking
                vpnSocket?.soTimeout = 100
                val packet = DatagramPacket(receiveBuffer, receiveBuffer.size)
                vpnSocket?.receive(packet)
                
                if (packet.length > 0) {
                    Log.v(TAG, "Received ${packet.length} bytes from VPN server")
                    
                    // Write the packet to the TUN interface
                    vpnOutput.write(receiveBuffer, 0, packet.length)
                    vpnOutput.flush()
                    Log.v(TAG, "Forwarded ${packet.length} bytes to TUN interface")
                }
            } catch (e: java.net.SocketTimeoutException) {
                // Timeout is expected - no packets to receive
            } catch (e: IOException) {
                if (isRunning.get()) {
                    Log.e(TAG, "Error receiving packet from VPN server: ${e.message}")
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
        Log.d(TAG, "NativeVpnService destroyed")
    }
}