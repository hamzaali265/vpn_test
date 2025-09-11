package com.example.vpn_test

import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.Base64

class DnsResolver {
    companion object {
        private const val TAG = "DnsResolver"
    }

    private var dohServerUrl = "https://1.1.1.1/dns-query" // Default to Cloudflare DoH
    private val fallbackDohServers = listOf(
        "https://1.1.1.1/dns-query",      // Cloudflare
        "https://8.8.8.8/dns-query",      // Google
        "https://9.9.9.9/dns-query",      // Quad9
        "https://1.0.0.1/dns-query"       // Cloudflare alternate
    )
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    fun setDohServerUrl(url: String) {
        dohServerUrl = url
        Log.d(TAG, "DoH server URL set to: $url")
    }

    /**
     * Resolves a DNS query using DNS-over-HTTPS with fallback servers
     * @param dnsQuery The DNS query to resolve
     * @return The DNS response or null if resolution failed
     */
    fun resolveDnsOverHttps(dnsQuery: ByteArray): ByteArray? {
        if (dohServerUrl.isEmpty()) {
            Log.w(TAG, "DoH URL is empty")
            return null
        }

        // Try primary server first, then fallbacks
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
        
        Log.e(TAG, "All DoH servers failed for DNS query")
        return null
    }
}
