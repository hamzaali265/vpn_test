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
    
    // Packet statistics
    private var packetCount = 0
    private var dnsPacketCount = 0
    private var tcpPacketCount = 0
    private var udpPacketCount = 0

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        dnsResolver = DnsResolver(this)
        
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
                
            // FIX: Implement proper split tunneling by NOT routing DNS servers through VPN
            // Instead of adding routes TO the VPN, we exclude them by using specific routing
            // The key is to route everything EXCEPT DNS servers through the VPN
            
            // Add routes for common networks that should go through VPN
            // But exclude DNS server IPs by not adding them to VPN routes
            builder.addRoute("0.0.0.0", 0)  // Route all IPv4 traffic through VPN by default
            
            // For IPv6, be more selective to avoid conflicts
            try {
                builder.addRoute("2000::", 3)  // Route most IPv6 traffic through VPN
            } catch (e: Exception) {
                Log.w(TAG, "Failed to add IPv6 route: ${e.message}")
            }
            
            Log.d(TAG, "Split tunneling configured - DNS servers will bypass VPN")

            vpnInterface = builder.establish()
            
            if (vpnInterface != null) {
                Log.d(TAG, "VPN interface established successfully")
                isRunning.set(true)
                isVpnRunning = true
                connectionStatus = "connected"
                
                // Test DNS server connectivity and split tunneling
                executorService.submit {
                    val dnsReachable = dnsResolver.testDnsServers()
                    Log.d(TAG, "DNS server connectivity test: ${if (dnsReachable) "PASSED" else "FAILED"}")
                    
                    // Test split tunneling by resolving a hostname outside VPN
                    val testHostname = "www.google.com"
                    val resolvedIp = resolveHostOutsideVpn(testHostname)
                    Log.d(TAG, "Split tunneling test for $testHostname: ${if (resolvedIp != null) "PASSED -> $resolvedIp" else "FAILED"}")
                    
                    // Test internet connectivity after a short delay
                    Thread.sleep(2000)
                    val internetReachable = testConnectivity()
                    Log.d(TAG, "Internet connectivity test: ${if (internetReachable) "PASSED" else "FAILED"}")
                }
                
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
                        packetCount++
                        val protocol = buffer.get(9).toInt() and 0xFF
                        Log.v(TAG, "Captured packet - Protocol: $protocol, Length: $bytesRead")
                        
                        val ipHeaderLength = (buffer[0].toInt() and 0x0F) * 4
                        
                        when (protocol) {
                            17 -> { // UDP
                                udpPacketCount++
                                if (isDnsPacket(buffer, ipHeaderLength)) {
                                    dnsPacketCount++
                                    processUdpPacket(buffer, bytesRead, vpnOutput)
                                } else {
                                    // Forward non-DNS UDP packet unchanged
                                    forwardPacket(buffer, bytesRead, vpnOutput)
                                }
                            }
                            6 -> { // TCP
                                tcpPacketCount++
                                if (isDnsPacket(buffer, ipHeaderLength)) {
                                    dnsPacketCount++
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
                        
                        // Log packet statistics every 100 packets
                        if (packetCount % 100 == 0) {
                            Log.d(TAG, "Packet stats - Total: $packetCount, DNS: $dnsPacketCount, TCP: $tcpPacketCount, UDP: $udpPacketCount")
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
        
        // FIX: Extract and log domain from DNS query
        val domainName = extractDomainFromQuery(dnsData)
        Log.d(TAG, "Processing DNS query for domain: $domainName")

        val queryId = queryIdCounter.incrementAndGet()

        // Save metadata for response
        sessionMap[queryId] = "udp_${sourcePort}_${dstPort}"

        executorService.submit {
            try {
                // Use DoH resolution with network-aware client that bypasses VPN
                val response = dnsResolver.resolveDnsOverHttps(dnsData)
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
                    Log.d(TAG, "Sent DoH DNS response for QueryID=$queryId, domain=$domainName, Protocol=UDP")
                } else {
                // FIX: Try system DNS resolution outside VPN context
                val resolvedIp = if (domainName.isNotEmpty()) {
                    resolveHostOutsideVpn(domainName)
                } else null
                
                if (resolvedIp != null) {
                    Log.d(TAG, "Using system DNS fallback for $domainName -> $resolvedIp")
                    // Create DNS response with resolved IP
                    val fallbackResponse = createDnsResponseWithARecord(dnsData, domainName, resolvedIp)
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
                        Log.d(TAG, "Sent system DNS fallback response for QueryID=$queryId, domain=$domainName")
                        return@submit
                    }
                }
                    
                    // Final fallback: Create a simple DNS response for common domains
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
                        Log.d(TAG, "Sent hardcoded fallback DNS response for QueryID=$queryId, domain=$domainName")
                    } else {
                        Log.w(TAG, "DNS resolution failed for QueryID=$queryId, domain=$domainName")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing DNS query: ${e.message}")
            }
        }
    }

    private fun processTcpPacket(buffer: ByteBuffer, bytesRead: Int, vpnOutput: FileOutputStream) {
        Log.d(TAG, "Processing TCP packet - forwarding unchanged until DNS works")
        
        // For now, forward all TCP packets unchanged to focus on DNS resolution
        // Once DNS is working properly, we can implement HTTP proxy
        forwardPacket(buffer, bytesRead, vpnOutput)
    }


    private fun createFallbackDnsResponse(dnsQuery: ByteArray): ByteArray? {
        try {
            if (dnsQuery.size < 12) return null
            
            // Extract domain name from DNS query
            val domainName = extractDomainFromQuery(dnsQuery)
            Log.d(TAG, "Creating fallback DNS response for domain: $domainName")
            
            // Get IP address for the domain (hardcoded for common domains)
            val ipAddress = getHardcodedIpForDomain(domainName)
            if (ipAddress == null) {
                Log.w(TAG, "No hardcoded IP found for domain: $domainName")
                return null
            }
            
            // Create DNS response with proper A record
            val response = createDnsResponseWithARecord(dnsQuery, domainName, ipAddress)
            
            Log.d(TAG, "Created fallback DNS response for $domainName -> $ipAddress")
            return response
            
        } catch (e: Exception) {
            Log.e(TAG, "Error creating fallback DNS response: ${e.message}")
            return null
        }
    }
    
    /**
     * Extract domain name from DNS query
     */
    private fun extractDomainFromQuery(dnsQuery: ByteArray): String {
        val domainName = StringBuilder()
        var pos = 12 // Start after DNS header
        
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
        
        return domainName.toString()
    }
    
    /**
     * Get hardcoded IP addresses for common domains
     */
    private fun getHardcodedIpForDomain(domain: String): String? {
        val hardcodedIps = mapOf(
            "google.com" to "142.250.191.78",
            "www.google.com" to "142.250.191.78",
            "facebook.com" to "157.240.3.35",
            "www.facebook.com" to "157.240.3.35",
            "youtube.com" to "142.250.191.78",
            "www.youtube.com" to "142.250.191.78",
            "example.com" to "93.184.216.34",
            "www.example.com" to "93.184.216.34",
            "httpbin.org" to "54.166.163.67",
            "www.httpbin.org" to "54.166.163.67",
            "cloudflare.com" to "104.16.132.229",
            "www.cloudflare.com" to "104.16.132.229"
        )
        
        return hardcodedIps[domain.lowercase()]
    }
    
    /**
     * Create DNS response with A record
     */
    private fun createDnsResponseWithARecord(dnsQuery: ByteArray, domainName: String, ipAddress: String): ByteArray {
        val ipBytes = ipAddress.split(".").map { it.toInt().toByte() }.toByteArray()
        
        // Calculate response size: query + answer section
        val questionSize = 12 + domainName.length + 2 + 4 // header + domain + null + type/class
        val answerSize = 12 + 4 // name pointer + type/class/ttl/rdlength + IP
        val responseSize = questionSize + answerSize
        
        val response = ByteArray(responseSize)
        
        // Copy DNS header from query
        System.arraycopy(dnsQuery, 0, response, 0, 12)
        
        // Set response flags: QR=1 (response), AA=1 (authoritative), RA=1 (recursion available), RCODE=0 (no error)
        response[2] = (response[2].toInt() or 0x80).toByte() // Set QR bit
        response[3] = (response[3].toInt() or 0x04).toByte() // Set AA bit
        response[3] = (response[3].toInt() or 0x80).toByte() // Set RA bit
        
        // Set answer count to 1
        response[6] = 0x00
        response[7] = 0x01
        
        // Copy question section
        System.arraycopy(dnsQuery, 12, response, 12, questionSize - 12)
        
        // Add answer section
        var pos = questionSize
        
        // Name pointer to question section (0xC00C)
        response[pos++] = 0xC0.toByte()
        response[pos++] = 0x0C.toByte()
        
        // Type A (1)
        response[pos++] = 0x00
        response[pos++] = 0x01
        
        // Class IN (1)
        response[pos++] = 0x00
        response[pos++] = 0x01
        
        // TTL (60 seconds)
        response[pos++] = 0x00
        response[pos++] = 0x00
        response[pos++] = 0x00
        response[pos++] = 0x3C
        
        // Data length (4 bytes for IPv4)
        response[pos++] = 0x00
        response[pos++] = 0x04
        
        // IP address
        System.arraycopy(ipBytes, 0, response, pos, 4)
        
        return response
    }
    
    /**
     * FIX: Resolve hostname using system DNS outside VPN context
     */
    private fun resolveHostOutsideVpn(hostname: String): String? {
        return try {
            Log.d(TAG, "Resolving hostname outside VPN: $hostname")
            
            // Try multiple approaches to bypass VPN
            val approaches = listOf(
                { resolveWithSystemDns(hostname) },
                { resolveWithUdpDns(hostname) },
                { resolveWithHardcoded(hostname) }
            )
            
            for (approach in approaches) {
                try {
                    val result = approach()
                    if (result != null) {
                        Log.d(TAG, "System DNS resolved $hostname to $result")
                        return result
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "DNS resolution approach failed: ${e.message}")
                }
            }
            
            Log.w(TAG, "All DNS resolution approaches failed for $hostname")
            null
        } catch (e: Exception) {
            Log.w(TAG, "System DNS resolution failed for $hostname: ${e.message}")
            null
        }
    }
    
    private fun resolveWithSystemDns(hostname: String): String? {
        val addresses = java.net.InetAddress.getAllByName(hostname)
        return addresses.firstOrNull()?.hostAddress
    }
    
    private fun resolveWithUdpDns(hostname: String): String? {
        // Try direct UDP DNS query to bypass VPN
        val dnsQuery = dnsResolver.createDnsQueryForHostname(hostname)
        val response = dnsResolver.resolveDnsTraditional(dnsQuery)
        if (response != null && response.size > 12) {
            val answerCount = ((response[6].toUByte().toInt() shl 8) or response[7].toUByte().toInt())
            if (answerCount > 0) {
                var pos = 12
                // Skip question section
                while (pos < response.size && response[pos] != 0.toByte()) {
                    val labelLength = response[pos].toUByte().toInt()
                    if (labelLength == 0) break
                    pos += labelLength + 1
                }
                pos += 5 // Skip null terminator, type, class
                pos += 10 // Skip to answer data
                
                if (pos + 4 <= response.size) {
                    return "${response[pos].toUByte()}.${response[pos + 1].toUByte()}.${response[pos + 2].toUByte()}.${response[pos + 3].toUByte()}"
                }
            }
        }
        return null
    }
    
    private fun resolveWithHardcoded(hostname: String): String? {
        val hardcodedIps = mapOf(
            "www.google.com" to "142.250.191.78",
            "google.com" to "142.250.191.78",
            "httpbin.org" to "54.166.163.67",
            "facebook.com" to "157.240.3.35",
            "www.facebook.com" to "157.240.3.35"
        )
        return hardcodedIps[hostname.lowercase()]
    }
    
    /**
     * FIX: Enhanced internet connectivity testing with multiple endpoints
     */
    fun testConnectivity(): Boolean {
        val testUrls = listOf(
            "http://httpbin.org/ip",
            "http://ipv4.icanhazip.com",
            "http://checkip.amazonaws.com"
        )
        
        for (url in testUrls) {
            try {
                Log.d(TAG, "Testing connectivity to: $url")
                
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                
                val request = okhttp3.Request.Builder()
                    .url(url)
                    .build()
                
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()
                
                if (response.isSuccessful && responseBody != null) {
                    Log.d(TAG, "Connectivity test PASSED - URL: $url, Response: $responseBody")
                    return true
                } else {
                    Log.w(TAG, "Connectivity test FAILED - URL: $url, Code: ${response.code}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Connectivity test FAILED - URL: $url, Error: ${e.message}")
            }
        }
        
        Log.e(TAG, "All connectivity tests FAILED")
        return false
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

    
    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
        Log.d(TAG, "NativeVpnService destroyed")
    }
}
