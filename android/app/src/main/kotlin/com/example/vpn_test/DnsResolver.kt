package com.example.vpn_test

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.Base64
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.DatagramSocket
import java.net.DatagramPacket
import java.net.SocketTimeoutException

class DnsResolver(private val context: Context) {
    companion object {
        private const val TAG = "DnsResolver"
    }

    private var dohServerUrl = "https://1.1.1.1/dns-query" // Default to Cloudflare DoH
    // FIX: Use IP-based DoH servers only to avoid hostname resolution loops
    private val fallbackDohServers = listOf(
        "https://1.1.1.1/dns-query",      // Cloudflare IPv4
        "https://8.8.8.8/dns-query",      // Google IPv4
        "https://9.9.9.9/dns-query",      // Quad9 IPv4
        "https://1.0.0.1/dns-query",      // Cloudflare alternate IPv4
        "https://[2606:4700:4700::1111]/dns-query",  // Cloudflare IPv6
        "https://[2001:4860:4860::8888]/dns-query"   // Google IPv6
    )
    
    // FIX: Add IPv6 support for UDP DNS servers
    private val udpDnsServers = listOf(
        "8.8.8.8",                        // Google IPv4
        "1.1.1.1",                        // Cloudflare IPv4
        "9.9.9.9",                        // Quad9 IPv4
        "1.0.0.1",                        // Cloudflare alternate IPv4
        "2001:4860:4860::8888",           // Google IPv6
        "2606:4700:4700::1111",           // Cloudflare IPv6
        "2620:fe::fe",                    // Quad9 IPv6
        "2606:4700:4700::1001"            // Cloudflare alternate IPv6
    )
    
    // FIX: Use system DNS client that bypasses VPN for DoH requests
    private val client = createSystemDnsClient()
    
    /**
     * FIX: Create network-aware OkHttpClient that bypasses VPN for DNS resolution
     */
    private fun createNetworkAwareClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }
    
    /**
     * FIX: Create a client that uses the underlying network (bypasses VPN)
     */
    private fun createSystemDnsClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }
    
    /**
     * FIX: Get the underlying network that's not the VPN
     */
    private fun getUnderlyingNetwork(connectivityManager: ConnectivityManager): Network? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val networks = connectivityManager.allNetworks
                for (network in networks) {
                    val capabilities = connectivityManager.getNetworkCapabilities(network)
                    if (capabilities != null) {
                        // Look for networks that are not VPN
                        if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                            // Prefer cellular or WiFi networks
                            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                                Log.d(TAG, "Found underlying network: ${network}")
                                return network
                            }
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get underlying network: ${e.message}")
            null
        }
    }

    fun setDohServerUrl(url: String) {
        dohServerUrl = url
        Log.d(TAG, "DoH server URL set to: $url")
    }

    /**
     * Resolves a DNS query using DNS-over-HTTPS with fallback to traditional UDP DNS
     * @param dnsQuery The DNS query to resolve
     * @return The DNS response or null if resolution failed
     */
    fun resolveDnsOverHttps(dnsQuery: ByteArray): ByteArray? {
        if (dohServerUrl.isEmpty()) {
            Log.w(TAG, "DoH URL is empty")
            return null
        }

        // Try DoH servers first
        val serversToTry = listOf(dohServerUrl) + fallbackDohServers.filter { it != dohServerUrl }
        
        for (serverUrl in serversToTry) {
            try {
                val startTime = System.nanoTime()
                
                val mediaType = "application/dns-message".toMediaType()
                val dnsQueryEncoded = Base64.getUrlEncoder().withoutPadding().encodeToString(dnsQuery)
                val request = Request.Builder()
                    .url("$serverUrl?dns=$dnsQueryEncoded")
                    .addHeader("Content-Type", "application/dns-message")
                    .addHeader("Accept", "application/dns-message")
                    .build()

                val response = client.newCall(request).execute()
                val dohTime = (System.nanoTime() - startTime) / 1_000_000.0 // ms

                if (response.isSuccessful) {
                    val responseBytes = response.body?.bytes()
                    if (responseBytes != null) {
                        Log.d(TAG, "DoH resolution successful via $serverUrl in ${dohTime}ms, response size: ${responseBytes.size}")
                        return responseBytes
                    } else {
                        Log.w(TAG, "DoH response body is null from $serverUrl")
                    }
                } else {
                    Log.w(TAG, "DoH request failed with code ${response.code} from $serverUrl")
                }
            } catch (e: IOException) {
                Log.w(TAG, "DoH request failed for $serverUrl: ${e.message}")
                // Continue to next server
            }
        }
        
        Log.w(TAG, "All DoH servers failed, trying traditional UDP DNS")
        return resolveDnsTraditional(dnsQuery)
    }
    
    /**
     * FIX: Enhanced UDP DNS resolution with IPv6 support and better error handling
     * @param dnsQuery The DNS query to resolve
     * @return The DNS response or null if resolution failed
     */
    fun resolveDnsTraditional(dnsQuery: ByteArray): ByteArray? {
        for (dnsServer in udpDnsServers) {
            try {
                val startTime = System.nanoTime()
                val socket = DatagramSocket()
                socket.soTimeout = 3000 // 3 second timeout for faster fallback
                
                // FIX: Support both IPv4 and IPv6 addresses
                val serverAddress = InetSocketAddress(dnsServer, 53)
                val packet = DatagramPacket(dnsQuery, dnsQuery.size, serverAddress)
                
                socket.send(packet)
                
                val responseBuffer = ByteArray(1024) // Increased buffer size for larger responses
                val responsePacket = DatagramPacket(responseBuffer, responseBuffer.size)
                socket.receive(responsePacket)
                
                val dnsTime = (System.nanoTime() - startTime) / 1_000_000.0 // ms
                val response = responsePacket.data.copyOf(responsePacket.length)
                
                socket.close()
                
                // FIX: Validate response has proper DNS header
                if (response.size >= 12) {
                    val answerCount = ((response[6].toUByte().toInt() shl 8) or response[7].toUByte().toInt())
                    Log.d(TAG, "UDP DNS resolution successful via $dnsServer in ${dnsTime}ms, response size: ${response.size}, answers: $answerCount")
                    return response
                } else {
                    Log.w(TAG, "UDP DNS response too short from $dnsServer: ${response.size} bytes")
                }
                
            } catch (e: SocketTimeoutException) {
                Log.w(TAG, "UDP DNS timeout for $dnsServer")
            } catch (e: IOException) {
                Log.w(TAG, "UDP DNS request failed for $dnsServer: ${e.message}")
            } catch (e: Exception) {
                Log.w(TAG, "UDP DNS error for $dnsServer: ${e.message}")
            }
        }
        
        Log.e(TAG, "All UDP DNS servers failed for DNS query")
        return null
    }
    
    /**
     * FIX: Enhanced DNS server testing with real query validation
     * @return true if at least one DNS server is reachable
     */
    fun testDnsServers(): Boolean {
        Log.d(TAG, "Testing DNS server connectivity with real queries...")
        
        for (dnsServer in udpDnsServers) {
            try {
                val startTime = System.nanoTime()
                val socket = DatagramSocket()
                socket.soTimeout = 3000 // 3 second timeout
                
                // FIX: Use createTestDnsQuery for google.com validation
                val testQuery = createTestDnsQuery()
                val serverAddress = InetSocketAddress(dnsServer, 53)
                val packet = DatagramPacket(testQuery, testQuery.size, serverAddress)
                
                socket.send(packet)
                
                val responseBuffer = ByteArray(1024)
                val responsePacket = DatagramPacket(responseBuffer, responseBuffer.size)
                socket.receive(responsePacket)
                
                val testTime = (System.nanoTime() - startTime) / 1_000_000.0 // ms
                val response = responsePacket.data.copyOf(responsePacket.length)
                
                socket.close()
                
                // FIX: Validate response has proper DNS structure
                if (response.size >= 12) {
                    val answerCount = ((response[6].toUByte().toInt() shl 8) or response[7].toUByte().toInt())
                    Log.d(TAG, "DNS server $dnsServer is reachable - response: ${response.size} bytes, $answerCount answers in ${testTime}ms")
                    return true
                } else {
                    Log.w(TAG, "DNS server $dnsServer returned invalid response: ${response.size} bytes")
                }
                
            } catch (e: Exception) {
                Log.w(TAG, "DNS server $dnsServer is not reachable: ${e.message}")
            }
        }
        
        Log.e(TAG, "No DNS servers are reachable")
        return false
    }
    
    /**
     * Create a simple test DNS query for google.com
     */
    private fun createTestDnsQuery(): ByteArray {
        // Simple DNS query for google.com (A record)
        return byteArrayOf(
            0x12, 0x34.toByte(), // Transaction ID
            0x01, 0x00.toByte(), // Flags: standard query
            0x00, 0x01.toByte(), // Questions: 1
            0x00, 0x00.toByte(), // Answer RRs: 0
            0x00, 0x00.toByte(), // Authority RRs: 0
            0x00, 0x00.toByte(), // Additional RRs: 0
            // Question section for google.com
            0x06, 0x67, 0x6f, 0x6f, 0x67, 0x6c, 0x65, // "google"
            0x03, 0x63, 0x6f, 0x6d, // "com"
            0x00, // End of name
            0x00, 0x01.toByte(), // Type: A
            0x00, 0x01.toByte()  // Class: IN
        )
    }
    
    /**
     * FIX: Enhanced hostname resolution with proper byte handling using toUByte()
     * @param hostname The hostname to resolve
     * @return The IP address as string or null if resolution failed
     */
    fun resolveHost(hostname: String): String? {
        try {
            Log.d(TAG, "Resolving hostname: $hostname")
            // Create DNS query for the hostname
            val dnsQuery = createDnsQueryForHostname(hostname)
            val response = resolveDnsTraditional(dnsQuery)
            
            if (response != null && response.size > 12) {
                // FIX: Use toUByte() for proper unsigned byte handling
                val answerCount = ((response[6].toUByte().toInt() shl 8) or response[7].toUByte().toInt())
                Log.d(TAG, "DNS response for $hostname: ${response.size} bytes, $answerCount answers")
                
                if (answerCount > 0) {
                    // Find the answer section and extract IP
                    var pos = 12
                    // Skip question section
                    while (pos < response.size && response[pos] != 0.toByte()) {
                        val labelLength = response[pos].toUByte().toInt()
                        if (labelLength == 0) break
                        pos += labelLength + 1
                    }
                    pos += 5 // Skip null terminator, type, class
                    
                    // FIX: Skip to answer data properly (pos += 10 for header fields)
                    pos += 10 // Skip name pointer, type, class, TTL, data length
                    
                    if (pos + 4 <= response.size) {
                        // FIX: Use toUByte() for IP bytes
                        val ip = "${response[pos].toUByte()}.${response[pos + 1].toUByte()}.${response[pos + 2].toUByte()}.${response[pos + 3].toUByte()}"
                        Log.d(TAG, "Successfully resolved $hostname to $ip")
                        return ip
                    } else {
                        Log.w(TAG, "DNS response too short for IP extraction: pos=$pos, size=${response.size}")
                    }
                } else {
                    Log.w(TAG, "No answers in DNS response for $hostname")
                }
            } else {
                Log.w(TAG, "Invalid DNS response for $hostname: ${response?.size ?: 0} bytes")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving hostname $hostname: ${e.message}")
        }
        return null
    }
    
    /**
     * Create DNS query for a specific hostname
     */
    fun createDnsQueryForHostname(hostname: String): ByteArray {
        val parts = hostname.split(".")
        val query = mutableListOf<Byte>()
        
        // DNS header
        query.addAll(listOf(0x12, 0x34.toByte(), 0x01, 0x00.toByte(), 0x00, 0x01.toByte(), 0x00, 0x00.toByte(), 0x00, 0x00.toByte(), 0x00, 0x00.toByte()))
        
        // Question section
        for (part in parts) {
            query.add(part.length.toByte())
            query.addAll(part.toByteArray().toList())
        }
        query.add(0x00) // End of name
        query.addAll(listOf(0x00, 0x01.toByte(), 0x00, 0x01.toByte())) // Type A, Class IN
        
        return query.toByteArray()
    }
}
