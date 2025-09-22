package com.example.vpn_test

import android.content.Context
import android.util.Log
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.Base64

/**
 * FIX: DNS-over-HTTPS resolver based on android 2 project
 * This resolver handles DoH requests with proper error handling and performance monitoring
 */
class DohDnsResolver(private val context: Context? = null) {
    companion object {
        private const val TAG = "DohDnsResolver"
    }

    private var dohServerUrl = ""
    
    // Performance monitoring
    private var totalRequests = 0
    private var networkResponses = 0

    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .connectionPool(ConnectionPool(2, 5, TimeUnit.SECONDS))
        .build()

    fun setDohServerUrl(url: String) {
        dohServerUrl = url
        Log.d(TAG, "DoH server URL set to: $url")
    }

    /**
     * Resolves a DNS query using DNS-over-HTTPS
     * @param dnsQuery The DNS query to resolve
     * @return The DNS response or null if resolution failed
     */
    fun resolveDnsOverHttps(dnsQuery: ByteArray): ByteArray? {
        totalRequests++
        
        if (dohServerUrl.isEmpty()) {
            Log.w(TAG, "DoH URL is empty")
            return null
        }

        return try {
            val startTime = System.nanoTime()
            
            val mediaType = "application/dns-message".toMediaType()
            val dnsQueryEncoded = Base64.getEncoder().encodeToString(dnsQuery)
            val request = Request.Builder()
                .url("$dohServerUrl?dns=$dnsQueryEncoded")
                .addHeader("Content-Type", "application/dns-message")
                .addHeader("Accept", "application/dns-message")
                .build()

            val response = client.newCall(request).execute()
            val dohTime = (System.nanoTime() - startTime) / 1_000_000.0 // ms

            if (response.isSuccessful) {
                val responseBytes = response.body?.bytes()
                if (responseBytes != null) {
                    networkResponses++

                    // Log performance data periodically
                    if (totalRequests % 20 == 0) {
                        Log.d(TAG, "DoH resolution time: ${dohTime}ms, Total requests: $totalRequests")
                    }
                    
                    responseBytes
                } else {
                    Log.w(TAG, "DoH response body is null")
                    null
                }
            } else {
                Log.w(TAG, "DoH request failed with code ${response.code}")
                null
            }
        } catch (e: IOException) {
            Log.w(TAG, "DoH request failed: ${e.message}")
            null
        }
    }
    
    /**
     * Clears any cached data (placeholder for future caching implementation)
     */
    fun clearCache() {
        totalRequests = 0
        networkResponses = 0
        Log.d(TAG, "DoH resolver cache cleared")
    }
    
    /**
     * Gets performance statistics
     */
    fun getStats(): String {
        return "Total: $totalRequests, Network: $networkResponses"
    }
}
