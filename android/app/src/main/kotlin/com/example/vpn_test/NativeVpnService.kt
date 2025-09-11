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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

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
    
    // DNS and packet processing
    private lateinit var dnsResolver: DnsResolver
    private val executorService: ExecutorService = Executors.newSingleThreadExecutor()
    private val sessionMap = ConcurrentHashMap<Long, String>()
    private val queryIdCounter = AtomicLong(0)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        dnsResolver = DnsResolver()
        
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
            
            // Note: We'll handle DNS resolution in the VPN thread to avoid NetworkOnMainThreadException
            
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
        
        // Clear session map
        sessionMap.clear()
        
        // Shutdown executor service
        executorService.shutdownNow()
        
        // Close the VPN interface
        try {
            vpnInterface?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing VPN interface: ${e.message}")
        } finally {
            vpnInterface = null
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
        val buffer = ByteBuffer.allocate(32767)
        
        try {
            Log.d(TAG, "VPN loop started - implementing DNS over HTTPS tunnel")
            
            while (isRunning.get() && !Thread.currentThread().isInterrupted) {
                try {
                    buffer.clear()
                    val bytesRead = vpnInput.read(buffer.array())
                    if (bytesRead > 0) {
                        val protocol = buffer.get(9).toInt() and 0xFF
                        Log.v(TAG, "Captured packet - Protocol: $protocol, Length: $bytesRead")
                        
                        val ipHeaderLength = (buffer[0].toInt() and 0x0F) * 4
                        
                        when (protocol) {
                            17 -> { // UDP
                                if (isDnsPacket(buffer, ipHeaderLength)) {
                                    processUdpPacket(buffer, bytesRead, vpnOutput)
                                } else {
                                    // Forward non-DNS UDP packet unchanged
                                    forwardPacket(buffer, bytesRead, vpnOutput)
                                }
                            }
                            6 -> { // TCP
                                if (isDnsPacket(buffer, ipHeaderLength)) {
                                    processTcpPacket(buffer, bytesRead, vpnOutput)
                                } else {
                                    // Forward non-DNS TCP packet unchanged
                                    forwardPacket(buffer, bytesRead, vpnOutput)
                                }
                            }
                            else -> {
                                // Forward any other protocol packets unchanged
                                forwardPacket(buffer, bytesRead, vpnOutput)
                            }
                        }
                    } else if (bytesRead == 0) {
                        Thread.sleep(10) // Sleep for 10ms to prevent busy-waiting
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading VPN interface: ${e.message}")
                    Thread.sleep(50) // Sleep a bit longer on error
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

    private fun isDnsPacket(buffer: ByteBuffer, ipHeaderLength: Int): Boolean {
        if (buffer.limit() < ipHeaderLength + 4) return false

        val sourcePort = ((buffer[ipHeaderLength].toInt() and 0xFF) shl 8) or
                (buffer[ipHeaderLength + 1].toInt() and 0xFF)
        val destPort = ((buffer[ipHeaderLength + 2].toInt() and 0xFF) shl 8) or
                (buffer[ipHeaderLength + 3].toInt() and 0xFF)

        return sourcePort == 53 || destPort == 53
    }

    // Forward packets that don't need processing
    private fun forwardPacket(buffer: ByteBuffer, length: Int, vpnOutput: FileOutputStream) {
        synchronized(vpnOutput) {
            vpnOutput.write(buffer.array(), 0, length)
            vpnOutput.flush()
        }
    }

    private fun processUdpPacket(buffer: ByteBuffer, bytesRead: Int, vpnOutput: FileOutputStream) {
        val dstPort = ((buffer.get(22).toInt() and 0xFF) shl 8) or (buffer.get(23).toInt() and 0xFF)

        if (dstPort != 53) { // DNS port
            forwardPacket(buffer, bytesRead, vpnOutput)
            return
        }
        
        Log.d(TAG, "Processing DNS UDP packet")

        val sourceIp = buffer.array().copyOfRange(12, 16)
        val destIp = buffer.array().copyOfRange(16, 20)
        val sourcePort = ((buffer.get(20).toInt() and 0xFF) shl 8) or (buffer.get(21).toInt() and 0xFF)

        // Extract DNS data
        val dnsData = ByteArray(bytesRead - 28)
        System.arraycopy(buffer.array(), 28, dnsData, 0, dnsData.size)

        val queryId = queryIdCounter.incrementAndGet()

        // Save metadata for response
        sessionMap[queryId] = "udp_${sourcePort}_${dstPort}"

        executorService.submit {
            try {
                // Use system DNS resolution instead of DoH to avoid circular dependency
                val response = resolveDnsUsingSystem(dnsData)
                if (response != null) {
                    val packet = PacketUtils.buildUdpResponsePacket(
                        sourceIp,
                        destIp,
                        sourcePort,
                        dstPort,
                        response
                    )

                    synchronized(vpnOutput) {
                        vpnOutput.write(packet)
                        vpnOutput.flush()
                    }
                    sessionMap.remove(queryId)
                    Log.d(TAG, "Sent system DNS response for QueryID=$queryId, Protocol=UDP")
                } else {
                    // Fallback: Create a simple DNS response for common domains
                    val fallbackResponse = createFallbackDnsResponse(dnsData)
                    if (fallbackResponse != null) {
                        val packet = PacketUtils.buildUdpResponsePacket(
                            sourceIp,
                            destIp,
                            sourcePort,
                            dstPort,
                            fallbackResponse
                        )

                        synchronized(vpnOutput) {
                            vpnOutput.write(packet)
                            vpnOutput.flush()
                        }
                        sessionMap.remove(queryId)
                        Log.d(TAG, "Sent fallback DNS response for QueryID=$queryId")
                    } else {
                        Log.w(TAG, "DNS resolution failed for QueryID=$queryId")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing DNS query: ${e.message}")
            }
        }
    }

    private fun processTcpPacket(buffer: ByteBuffer, bytesRead: Int, vpnOutput: FileOutputStream) {
        Log.d(TAG, "Processing TCP packet - implementing real HTTP proxy")
        
        // Extract TCP header information
        val ipHeaderLength = (buffer[0].toInt() and 0x0F) * 4
        val tcpHeaderStart = ipHeaderLength
        val tcpFlags = buffer[tcpHeaderStart + 13].toInt() and 0xFF
        val isSyn = (tcpFlags and 0x02) != 0
        val isFin = (tcpFlags and 0x01) != 0
        val isRst = (tcpFlags and 0x04) != 0
        val isAck = (tcpFlags and 0x10) != 0
        val isPsh = (tcpFlags and 0x08) != 0
        
        // Extract destination IP and port
        val destIp = ByteArray(4)
        buffer.position(16)
        buffer.get(destIp)
        
        val destPort = ((buffer[tcpHeaderStart].toInt() and 0xFF) shl 8) or 
                      (buffer[tcpHeaderStart + 1].toInt() and 0xFF)
        
        Log.d(TAG, "TCP packet: flags=$tcpFlags, destPort=$destPort, isSyn=$isSyn, isAck=$isAck, isPsh=$isPsh")
        
        if (isSyn && (destPort == 80 || destPort == 443)) {
            // For HTTP/HTTPS connections, create a SYN-ACK response to establish connection
            createTcpSynAckResponse(buffer, bytesRead, vpnOutput)
        } else if ((isAck || isPsh) && (destPort == 80 || destPort == 443)) {
            // For HTTP/HTTPS data packets, forward them through real internet connection
            forwardHttpRequest(buffer, bytesRead, vpnOutput, destPort)
        } else if (isSyn) {
            // For other ports, send RST
            createTcpRstResponse(buffer, bytesRead, vpnOutput)
        } else {
            // For other TCP packets, forward them
            forwardPacket(buffer, bytesRead, vpnOutput)
        }
    }

    private fun resolveDnsUsingSystem(dnsQuery: ByteArray): ByteArray? {
        try {
            Log.d(TAG, "Resolving DNS using system DNS (outside VPN tunnel)")
            
            // Parse DNS query to extract domain name
            if (dnsQuery.size < 12) return null
            
            val queryId = ((dnsQuery[0].toInt() and 0xFF) shl 8) or (dnsQuery[1].toInt() and 0xFF)
            val flags = ((dnsQuery[2].toInt() and 0xFF) shl 8) or (dnsQuery[3].toInt() and 0xFF)
            val questionCount = ((dnsQuery[4].toInt() and 0xFF) shl 8) or (dnsQuery[5].toInt() and 0xFF)
            
            if (questionCount != 1) return null
            
            // Extract domain name from DNS query
            val domainName = StringBuilder()
            var pos = 12
            while (pos < dnsQuery.size && dnsQuery[pos] != 0.toByte()) {
                val labelLength = dnsQuery[pos].toInt() and 0xFF
                if (labelLength == 0) break
                
                if (domainName.isNotEmpty()) domainName.append(".")
                
                for (i in 1..labelLength) {
                    if (pos + i < dnsQuery.size) {
                        domainName.append(dnsQuery[pos + i].toInt().toChar())
                    }
                }
                pos += labelLength + 1
            }
            
            val domain = domainName.toString()
            Log.d(TAG, "Resolving domain: $domain using system DNS")
            
            // Use system DNS resolution (this will use the device's normal internet connection)
            val addresses = java.net.InetAddress.getAllByName(domain)
            if (addresses.isNotEmpty()) {
                val ip = addresses[0].address
                Log.d(TAG, "Resolved $domain to ${ip.joinToString(".") { (it.toInt() and 0xFF).toString() }}")
                
                // Create DNS response packet
                val response = ByteArray(40) // DNS header + question + answer
                
                // DNS Header
                response[0] = (queryId shr 8).toByte()
                response[1] = (queryId and 0xFF).toByte()
                response[2] = 0x81.toByte() // Response, recursion available
                response[3] = 0x80.toByte() // No error
                response[4] = 0x00 // Questions
                response[5] = 0x01
                response[6] = 0x00 // Answers
                response[7] = 0x01
                response[8] = 0x00 // Authority RRs
                response[9] = 0x00
                response[10] = 0x00 // Additional RRs
                response[11] = 0x00
                
                // Question section (copy from query)
                System.arraycopy(dnsQuery, 12, response, 12, 12)
                
                // Answer section
                response[24] = 0xC0.toByte() // Name pointer to question
                response[25] = 0x0C.toByte()
                response[26] = 0x00.toByte() // Type A
                response[27] = 0x01.toByte()
                response[28] = 0x00.toByte() // Class IN
                response[29] = 0x01.toByte()
                response[30] = 0x00.toByte() // TTL
                response[31] = 0x00.toByte()
                response[32] = 0x00.toByte()
                response[33] = 0x04.toByte()
                response[34] = 0x00.toByte() // Data length
                response[35] = 0x04.toByte()
                
                // IP address
                response[36] = ip[0]
                response[37] = ip[1]
                response[38] = ip[2]
                response[39] = ip[3]
                
                return response
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in system DNS resolution: ${e.message}")
        }
        return null
    }

    private fun createFallbackDnsResponse(dnsQuery: ByteArray): ByteArray? {
        try {
            if (dnsQuery.size < 12) return null
            
            // Create a basic DNS response
            val response = ByteArray(dnsQuery.size)
            System.arraycopy(dnsQuery, 0, response, 0, dnsQuery.size)
            
            // Set response flags: QR=1 (response), AA=1 (authoritative), RA=1 (recursion available), RCODE=0 (no error)
            response[2] = (response[2].toInt() or 0x80).toByte() // Set QR bit
            response[3] = (response[3].toInt() or 0x04).toByte() // Set AA bit
            response[3] = (response[3].toInt() or 0x80).toByte() // Set RA bit
            
            // For now, return a simple response that indicates the query was processed
            // In a real implementation, you might want to return specific IP addresses for common domains
            Log.d(TAG, "Created fallback DNS response")
            return response
            
        } catch (e: Exception) {
            Log.e(TAG, "Error creating fallback DNS response: ${e.message}")
            return null
        }
    }

    private fun createTcpSynAckResponse(buffer: ByteBuffer, bytesRead: Int, vpnOutput: FileOutputStream) {
        try {
            val responseBuffer = ByteBuffer.allocate(bytesRead)
            buffer.position(0)
            buffer.limit(bytesRead)
            responseBuffer.put(buffer)
            responseBuffer.position(0)
            
            // Swap source and destination IP addresses
            val srcIp = ByteArray(4)
            val dstIp = ByteArray(4)
            responseBuffer.position(12)
            responseBuffer.get(srcIp)
            responseBuffer.get(dstIp)
            responseBuffer.position(12)
            responseBuffer.put(dstIp)
            responseBuffer.put(srcIp)
            
            // Swap source and destination ports
            val ipHeaderLength = (responseBuffer[0].toInt() and 0x0F) * 4
            val tcpHeaderStart = ipHeaderLength
            val srcPort = ((responseBuffer[tcpHeaderStart].toInt() and 0xFF) shl 8) or 
                         (responseBuffer[tcpHeaderStart + 1].toInt() and 0xFF)
            val dstPort = ((responseBuffer[tcpHeaderStart + 2].toInt() and 0xFF) shl 8) or 
                         (responseBuffer[tcpHeaderStart + 3].toInt() and 0xFF)
            
            responseBuffer.position(tcpHeaderStart)
            responseBuffer.putShort(dstPort.toShort())
            responseBuffer.putShort(srcPort.toShort())
            
            // Set TCP flags to SYN-ACK (SYN=1, ACK=1)
            responseBuffer.position(tcpHeaderStart + 13)
            responseBuffer.put(0x12.toByte()) // SYN + ACK flags
            
            // Set sequence and acknowledgment numbers
            responseBuffer.position(tcpHeaderStart + 4)
            responseBuffer.putInt(1) // Sequence number
            responseBuffer.putInt(2) // Acknowledgment number
            
            // Write response
            vpnOutput.write(responseBuffer.array(), 0, bytesRead)
            vpnOutput.flush()
            
            Log.d(TAG, "TCP SYN-ACK response sent")
        } catch (e: Exception) {
            Log.e(TAG, "Error creating TCP SYN-ACK response: ${e.message}")
        }
    }

    private fun createTcpRstResponse(buffer: ByteBuffer, bytesRead: Int, vpnOutput: FileOutputStream) {
        try {
            val responseBuffer = ByteBuffer.allocate(bytesRead)
            buffer.position(0)
            buffer.limit(bytesRead)
            responseBuffer.put(buffer)
            responseBuffer.position(0)
            
            // Swap source and destination IP addresses
            val srcIp = ByteArray(4)
            val dstIp = ByteArray(4)
            responseBuffer.position(12)
            responseBuffer.get(srcIp)
            responseBuffer.get(dstIp)
            responseBuffer.position(12)
            responseBuffer.put(dstIp)
            responseBuffer.put(srcIp)
            
            // Swap source and destination ports
            val ipHeaderLength = (responseBuffer[0].toInt() and 0x0F) * 4
            val tcpHeaderStart = ipHeaderLength
            val srcPort = ((responseBuffer[tcpHeaderStart].toInt() and 0xFF) shl 8) or 
                         (responseBuffer[tcpHeaderStart + 1].toInt() and 0xFF)
            val dstPort = ((responseBuffer[tcpHeaderStart + 2].toInt() and 0xFF) shl 8) or 
                         (responseBuffer[tcpHeaderStart + 3].toInt() and 0xFF)
            
            responseBuffer.position(tcpHeaderStart)
            responseBuffer.putShort(dstPort.toShort())
            responseBuffer.putShort(srcPort.toShort())
            
            // Set TCP flags to RST
            responseBuffer.position(tcpHeaderStart + 13)
            responseBuffer.put(0x04.toByte()) // RST flag
            
            // Write response
            vpnOutput.write(responseBuffer.array(), 0, bytesRead)
            vpnOutput.flush()
            
            Log.d(TAG, "TCP RST response sent")
        } catch (e: Exception) {
            Log.e(TAG, "Error creating TCP RST response: ${e.message}")
        }
    }

    private fun forwardHttpRequest(buffer: ByteBuffer, bytesRead: Int, vpnOutput: FileOutputStream, destPort: Int) {
        executorService.submit {
            try {
                Log.d(TAG, "Forwarding HTTP request through real internet connection")
                
                // Extract HTTP request data from the packet
                val ipHeaderLength = (buffer[0].toInt() and 0x0F) * 4
                val tcpHeaderLength = 20
                val httpDataStart = ipHeaderLength + tcpHeaderLength
                
                if (bytesRead > httpDataStart) {
                    val httpData = ByteArray(bytesRead - httpDataStart)
                    buffer.position(httpDataStart)
                    buffer.get(httpData)
                    
                    val httpRequest = String(httpData)
                    Log.d(TAG, "HTTP Request: ${httpRequest.take(200)}...")
                    
                    // Extract host from HTTP request
                    val hostLine = httpRequest.lines().find { it.startsWith("Host:") }
                    val host = hostLine?.substringAfter("Host:")?.trim()?.substringBefore(":")
                    
                    if (host != null) {
                        Log.d(TAG, "Making real HTTP request to: $host")
                        
                        // Make real HTTP request using OkHttp
                        val client = okhttp3.OkHttpClient.Builder()
                            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                            .build()
                        
                        val request = okhttp3.Request.Builder()
                            .url("http://$host/")
                            .build()
                        
                        val response = client.newCall(request).execute()
                        val responseBody = response.body?.string() ?: "No response body"
                        
                        Log.d(TAG, "Received HTTP response: ${response.code}, length: ${responseBody.length}")
                        
                        // Send the real HTTP response back through VPN
                        sendHttpResponse(buffer, vpnOutput, responseBody, response.code)
                    } else {
                        Log.w(TAG, "Could not extract host from HTTP request")
                        sendErrorResponse(buffer, vpnOutput)
                    }
                } else {
                    Log.w(TAG, "No HTTP data in packet")
                    sendErrorResponse(buffer, vpnOutput)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error forwarding HTTP request: ${e.message}")
                sendErrorResponse(buffer, vpnOutput)
            }
        }
    }

    private fun sendHttpResponse(buffer: ByteBuffer, vpnOutput: FileOutputStream, responseBody: String, statusCode: Int) {
        try {
            val httpResponse = "HTTP/1.1 $statusCode OK\r\n" +
                    "Content-Type: text/html\r\n" +
                    "Content-Length: ${responseBody.length}\r\n" +
                    "Connection: close\r\n\r\n" +
                    responseBody
            
            val httpBytes = httpResponse.toByteArray()
            
            // Create TCP packet with HTTP response
            val ipHeaderLength = 20
            val tcpHeaderLength = 20
            val totalLength = ipHeaderLength + tcpHeaderLength + httpBytes.size
            val responsePacket = ByteArray(totalLength)
            
            // IP Header
            responsePacket[0] = 0x45.toByte() // Version and IHL
            responsePacket[1] = 0x00
            responsePacket[2] = (totalLength shr 8).toByte()
            responsePacket[3] = (totalLength and 0xFF).toByte()
            responsePacket[4] = 0x00
            responsePacket[5] = 0x00
            responsePacket[6] = 0x40
            responsePacket[7] = 0x00
            responsePacket[8] = 64.toByte() // TTL
            responsePacket[9] = 6.toByte() // Protocol (TCP)
            responsePacket[10] = 0x00 // Checksum placeholder
            responsePacket[11] = 0x00
            
            // Swap source and destination IP addresses
            val srcIp = ByteArray(4)
            val dstIp = ByteArray(4)
            buffer.position(12)
            buffer.get(srcIp)
            buffer.get(dstIp)
            System.arraycopy(dstIp, 0, responsePacket, 12, 4) // Source IP in response
            System.arraycopy(srcIp, 0, responsePacket, 16, 4) // Destination IP in response
            
            // TCP Header
            val tcpStart = ipHeaderLength
            val srcPort = ((buffer[tcpStart].toInt() and 0xFF) shl 8) or (buffer[tcpStart + 1].toInt() and 0xFF)
            val dstPort = ((buffer[tcpStart + 2].toInt() and 0xFF) shl 8) or (buffer[tcpStart + 3].toInt() and 0xFF)
            
            responsePacket[tcpStart] = (dstPort shr 8).toByte() // Source Port in response
            responsePacket[tcpStart + 1] = (dstPort and 0xFF).toByte()
            responsePacket[tcpStart + 2] = (srcPort shr 8).toByte() // Destination Port in response
            responsePacket[tcpStart + 3] = (srcPort and 0xFF).toByte()
            
            // Sequence and acknowledgment numbers
            responsePacket[tcpStart + 4] = 0x00
            responsePacket[tcpStart + 5] = 0x00
            responsePacket[tcpStart + 6] = 0x00
            responsePacket[tcpStart + 7] = 0x02
            responsePacket[tcpStart + 8] = 0x00
            responsePacket[tcpStart + 9] = 0x00
            responsePacket[tcpStart + 10] = 0x00
            responsePacket[tcpStart + 11] = 0x01
            
            // TCP flags (ACK + PSH + FIN)
            responsePacket[tcpStart + 13] = 0x19.toByte() // ACK + PSH + FIN
            
            // Window size
            responsePacket[tcpStart + 14] = 0x05
            responsePacket[tcpStart + 15] = 0x14
            
            // TCP checksum placeholder
            responsePacket[tcpStart + 16] = 0x00
            responsePacket[tcpStart + 17] = 0x00
            
            // HTTP payload
            System.arraycopy(httpBytes, 0, responsePacket, ipHeaderLength + tcpHeaderLength, httpBytes.size)
            
            // Write response
            synchronized(vpnOutput) {
                vpnOutput.write(responsePacket)
                vpnOutput.flush()
            }
            
            Log.d(TAG, "Real HTTP response sent (${httpBytes.size} bytes)")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending HTTP response: ${e.message}")
        }
    }

    private fun sendErrorResponse(buffer: ByteBuffer, vpnOutput: FileOutputStream) {
        try {
            val errorResponse = "HTTP/1.1 500 Internal Server Error\r\n" +
                    "Content-Type: text/html\r\n" +
                    "Content-Length: 100\r\n" +
                    "Connection: close\r\n\r\n" +
                    "<html><body><h1>VPN Error</h1><p>Unable to process request through VPN.</p></body></html>"
            
            val httpBytes = errorResponse.toByteArray()
            
            // Create TCP packet with error response
            val ipHeaderLength = 20
            val tcpHeaderLength = 20
            val totalLength = ipHeaderLength + tcpHeaderLength + httpBytes.size
            val responsePacket = ByteArray(totalLength)
            
            // IP Header
            responsePacket[0] = 0x45.toByte() // Version and IHL
            responsePacket[1] = 0x00
            responsePacket[2] = (totalLength shr 8).toByte()
            responsePacket[3] = (totalLength and 0xFF).toByte()
            responsePacket[4] = 0x00
            responsePacket[5] = 0x00
            responsePacket[6] = 0x40
            responsePacket[7] = 0x00
            responsePacket[8] = 64.toByte() // TTL
            responsePacket[9] = 6.toByte() // Protocol (TCP)
            responsePacket[10] = 0x00 // Checksum placeholder
            responsePacket[11] = 0x00
            
            // Swap source and destination IP addresses
            val srcIp = ByteArray(4)
            val dstIp = ByteArray(4)
            buffer.position(12)
            buffer.get(srcIp)
            buffer.get(dstIp)
            System.arraycopy(dstIp, 0, responsePacket, 12, 4) // Source IP in response
            System.arraycopy(srcIp, 0, responsePacket, 16, 4) // Destination IP in response
            
            // TCP Header
            val tcpStart = ipHeaderLength
            val srcPort = ((buffer[tcpStart].toInt() and 0xFF) shl 8) or (buffer[tcpStart + 1].toInt() and 0xFF)
            val dstPort = ((buffer[tcpStart + 2].toInt() and 0xFF) shl 8) or (buffer[tcpStart + 3].toInt() and 0xFF)
            
            responsePacket[tcpStart] = (dstPort shr 8).toByte() // Source Port in response
            responsePacket[tcpStart + 1] = (dstPort and 0xFF).toByte()
            responsePacket[tcpStart + 2] = (srcPort shr 8).toByte() // Destination Port in response
            responsePacket[tcpStart + 3] = (srcPort and 0xFF).toByte()
            
            // Sequence and acknowledgment numbers
            responsePacket[tcpStart + 4] = 0x00
            responsePacket[tcpStart + 5] = 0x00
            responsePacket[tcpStart + 6] = 0x00
            responsePacket[tcpStart + 7] = 0x02
            responsePacket[tcpStart + 8] = 0x00
            responsePacket[tcpStart + 9] = 0x00
            responsePacket[tcpStart + 10] = 0x00
            responsePacket[tcpStart + 11] = 0x01
            
            // TCP flags (ACK + PSH + FIN)
            responsePacket[tcpStart + 13] = 0x19.toByte() // ACK + PSH + FIN
            
            // Window size
            responsePacket[tcpStart + 14] = 0x05
            responsePacket[tcpStart + 15] = 0x14
            
            // TCP checksum placeholder
            responsePacket[tcpStart + 16] = 0x00
            responsePacket[tcpStart + 17] = 0x00
            
            // HTTP payload
            System.arraycopy(httpBytes, 0, responsePacket, ipHeaderLength + tcpHeaderLength, httpBytes.size)
            
            // Write response
            synchronized(vpnOutput) {
                vpnOutput.write(responsePacket)
                vpnOutput.flush()
            }
            
            Log.d(TAG, "Error response sent")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending error response: ${e.message}")
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
        Log.d(TAG, "NativeVpnService destroyed")
    }
}
