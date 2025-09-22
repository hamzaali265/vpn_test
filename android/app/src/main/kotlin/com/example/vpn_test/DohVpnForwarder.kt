package com.example.vpn_test

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.VpnService
import android.os.Binder
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import android.util.Log
import androidx.core.app.NotificationCompat
import org.xbill.DNS.Message
import org.xbill.DNS.Section
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * FIX: DNS-over-HTTPS VPN Forwarder based on android 2 project
 * This service intercepts DNS queries and forwards them via DoH
 */
@Suppress("LABEL_NAME_CLASH")
class DohVpnForwarder : VpnService(), Runnable {

    companion object {
        private const val TAG = "DohVpnForwarder"
        private const val LOCAL_DNS_IP = "10.0.0.53"
        private const val LOCAL_DNS_PORT = 5353          // For UDP
        private const val LOCAL_DNS_TCP_PORT = 5354      // For TCP
        private const val NOTIFICATION_ID = 2
        private const val NOTIFICATION_CHANNEL_ID = "doh_vpn_channel"

        @Volatile
        var isVpnRunning = false
    }

    private var thread: Thread? = null
    private val executor = Executors.newSingleThreadExecutor()
    private var vpnInterface: ParcelFileDescriptor? = null
    private var fileOutputStream: FileOutputStream? = null
    private var sessionMap = ConcurrentHashMap<Long, String>()
    private lateinit var dnsResolver: DohDnsResolver
    private val queryIdCounter = AtomicLong(0)

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): DohVpnForwarder = this@DohVpnForwarder
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        dnsResolver = DohDnsResolver(this)
        Log.d(TAG, "Starting DoH VPN Forwarder")
    }

    private fun createNotificationChannel() {
        val name = "DoH VPN Service"
        val descriptionText = "Notifications for DoH VPN service"
        val importance = NotificationManager.IMPORTANCE_LOW
        val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
            description = descriptionText
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
            setSound(null, null)
        }

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(contentText: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)

        return builder
            .setContentTitle("DoH VPN")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    @SuppressLint("ForegroundServiceType")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("DoH VPN Service Running"))

        val dohURL = intent?.getStringExtra("DOH_URL") ?: ""
        val alwaysOn = intent?.getBooleanExtra("ALWAYS_ON", false) ?: false
        val clearCache = intent?.getBooleanExtra("CLEAR_CACHE", false) ?: false

        if (dohURL.isEmpty()) {
            Log.w(TAG, "DoH URL is empty, stopping service")
            stopSelf()
            return START_STICKY
        }

        // Set the DoH server URL
        dnsResolver.setDohServerUrl(dohURL)
        Log.d(TAG, "DoH URL set to $dohURL")

        // Clear cache if requested
        if (clearCache) {
            Log.d(TAG, "Clearing DNS cache as requested")
            dnsResolver.clearCache()
        }

        // Start the VPN thread if not already running
        if (thread == null) {
            thread = Thread(this)
            thread?.start()
            isVpnRunning = true
            Log.d(TAG, "DoH VPN thread started")
        } else {
            Log.d(TAG, "DoH VPN thread already running")
        }

        return START_STICKY
    }

    fun setDohServerUrl(url: String) {
        dnsResolver.setDohServerUrl(url)
        Log.d(TAG, "DoH Server URL set to: $url")
    }

    override fun run() {
        try {
            val builder = Builder()
                .setSession("DoH VPN")
                .setMtu(1500)
                .addAddress("10.0.0.1", 32) // Fake VPN IP
                .addRoute("9.9.9.9", 32)    // Quad9 DNS
                .addDnsServer("9.9.9.9")
                .allowFamily(OsConstants.AF_INET) // Allow IPv4
                .addDisallowedApplication("com.example.vpn_test")

            vpnInterface = builder.establish()
            isVpnRunning = true
            
            if (vpnInterface == null) {
                Log.e(TAG, "Failed to establish VPN interface")
                stopSelf()
                return
            }
            
            // Simplified version without TcpHandler for now
            processVPNTraffic()

        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException in DoH VPN Service: ${e.message}")
            isVpnRunning = false
            stopSelf()
        } catch (e: Exception) {
            isVpnRunning = false
            stopSelf()
            Log.e(TAG, "Error in DoH VPN Service: ${e.message}")
        } finally {
            disconnectVpn()
        }
    }

    private fun processVPNTraffic() {
        val fileInputStream = FileInputStream(vpnInterface!!.fileDescriptor)
        fileOutputStream = FileOutputStream(vpnInterface!!.fileDescriptor)
        val buffer = ByteBuffer.allocate(32767)

        while (isVpnRunning && !Thread.interrupted()) {
            try {
                buffer.clear()
                val bytesRead = fileInputStream.read(buffer.array())
                if (bytesRead > 0) {
                    val protocol = buffer.get(9).toInt() and 0xFF
                    Log.d(TAG, "Captured packet - Protocol: $protocol, Length: $bytesRead")

                    val ipHeaderLength = (buffer[0].toInt() and 0x0F) * 4

                    when (protocol) {
                        17 -> { // UDP
                            if (isDnsPacket(buffer, ipHeaderLength)) {
                                processUdpPacket(buffer, bytesRead)
                            } else {
                                forwardPacket(buffer, bytesRead)
                            }
                        }
                        6 -> { // TCP
                            // Simplified: forward all TCP packets for now
                            forwardPacket(buffer, bytesRead)
                        }
                        else -> {
                            forwardPacket(buffer, bytesRead)
                        }
                    }
                } else if (bytesRead == 0) {
                    Thread.sleep(10)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading VPN interface: ${e.message}")
                Thread.sleep(50)
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

    private fun forwardPacket(buffer: ByteBuffer, length: Int) {
        synchronized(fileOutputStream!!) {
            fileOutputStream?.write(buffer.array(), 0, length)
            fileOutputStream?.flush()
        }
    }

    private fun processUdpPacket(buffer: ByteBuffer, bytesRead: Int) {
        val dstPort = ((buffer.get(22).toInt() and 0xFF) shl 8) or (buffer.get(23).toInt() and 0xFF)

        if (dstPort != 53) {
            forwardPacket(buffer, bytesRead)
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
        sessionMap[queryId] = "udp_${sourcePort}_${dstPort}"
        Log.d(TAG, "Added UDP query with QueryID $queryId")

        executor.submit {
            logDnsQuery(dnsData)

            val response = dnsResolver.resolveDnsOverHttps(dnsData)
            if (response != null) {
                val packet = PacketUtils.buildUdpResponsePacket(
                    sourceIp,
                    destIp,
                    sourcePort,
                    dstPort,
                    response
                )

                synchronized(fileOutputStream!!) {
                    fileOutputStream?.write(packet)
                    fileOutputStream?.flush()
                }
                sessionMap.remove(queryId)
                Log.d(TAG, "Sent DoH response for QueryID=$queryId, Protocol=UDP")
            } else {
                Log.w(TAG, "DoH resolution failed for QueryID $queryId")
            }
        }
    }

    // TCP packet processing removed for simplified implementation

    private fun logDnsQuery(dnsData: ByteArray) {
        try {
            val dnsMessage = Message(dnsData)
            val questions = dnsMessage.getSection(Section.QUESTION)
            for (question in questions) {
                Log.d(TAG, "DNS Query: $question")
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to parse DNS message: $e")
        }
    }

    override fun onRevoke() {
        super.onRevoke()
        Log.d(TAG, "DoH VPN revoked by system")
        isVpnRunning = false
        stopSelf()
    }

    override fun onDestroy() {
        sessionMap.clear()
        disconnectVpn()
        Log.d(TAG, "DoH VPN Service stopped")
        super.onDestroy()
    }

    fun disconnectVpn() {
        try {
            isVpnRunning = false
            executor.shutdown()
            vpnInterface?.close()
            vpnInterface = null
            Log.d(TAG, "DoH VPN Interface closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing DoH VPN interface: ${e.message}")
        }
    }
}
