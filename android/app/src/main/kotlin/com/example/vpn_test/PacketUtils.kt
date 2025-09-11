package com.example.vpn_test

object PacketUtils {
    
    /**
     * Builds a UDP response packet with proper checksums
     */
    fun buildUdpResponsePacket(
        srcIp: ByteArray,
        dstIp: ByteArray,
        srcPort: Int,
        dstPort: Int,
        dnsPayload: ByteArray
    ): ByteArray {
        val ipHeaderLength = 20
        val udpHeaderLength = 8
        val totalLength = ipHeaderLength + udpHeaderLength + dnsPayload.size
        val packet = ByteArray(totalLength)

        // IP Header
        packet[0] = 0x45.toByte() // Version 4, header length 5 (20 bytes)
        packet[1] = 0x00.toByte() // Type of Service
        packet[2] = (totalLength shr 8).toByte()
        packet[3] = (totalLength and 0xFF).toByte()
        
        // Identification
        val identification = (System.currentTimeMillis() % 65536).toInt()
        packet[4] = (identification shr 8).toByte()
        packet[5] = identification.toByte()
        
        // Flags & Fragment Offset
        packet[6] = 0x40.toByte() // Don't fragment
        packet[7] = 0x00.toByte() // Fragment offset
        
        // TTL
        packet[8] = 64.toByte() // Time to live
        
        // Protocol
        packet[9] = 17.toByte() // UDP
        
        // Header Checksum (initially 0, will be calculated later)
        packet[10] = 0x00.toByte()
        packet[11] = 0x00.toByte()

        // Source IP (swapped for response)
        System.arraycopy(dstIp, 0, packet, 12, 4)
        // Destination IP (swapped for response)
        System.arraycopy(srcIp, 0, packet, 16, 4)

        // Calculate IP header checksum
        val ipChecksum = calculateIPChecksum(packet, 0, ipHeaderLength)
        packet[10] = (ipChecksum shr 8).toByte()
        packet[11] = (ipChecksum and 0xFF).toByte()

        // UDP Header
        val udpStart = ipHeaderLength
        packet[udpStart] = (dstPort shr 8).toByte()
        packet[udpStart + 1] = (dstPort and 0xFF).toByte()
        packet[udpStart + 2] = (srcPort shr 8).toByte()
        packet[udpStart + 3] = (srcPort and 0xFF).toByte()

        val udpLength = udpHeaderLength + dnsPayload.size
        packet[udpStart + 4] = (udpLength shr 8).toByte()
        packet[udpStart + 5] = (udpLength and 0xFF).toByte()
        
        // UDP Checksum (initially 0, will be calculated later)
        packet[udpStart + 6] = 0x00.toByte()
        packet[udpStart + 7] = 0x00.toByte()

        // DNS Payload
        System.arraycopy(dnsPayload, 0, packet, ipHeaderLength + udpHeaderLength, dnsPayload.size)

        // Calculate UDP checksum
        val udpChecksum = calculateUDPChecksum(packet, udpStart, udpLength, dstIp, srcIp)
        packet[udpStart + 6] = (udpChecksum shr 8).toByte()
        packet[udpStart + 7] = (udpChecksum and 0xFF).toByte()

        return packet
    }

    /**
     * Creates a TCP packet with proper checksums
     */
    fun createTcpPacket(
        srcIp: ByteArray, 
        dstIp: ByteArray, 
        srcPort: Int, 
        dstPort: Int, 
        flags: Int, 
        seqNum: Long, 
        ackNum: Long, 
        payload: ByteArray
    ): ByteArray {
        val ipHeaderLength = 20
        val tcpHeaderLength = 20
        val totalLength = ipHeaderLength + tcpHeaderLength + payload.size
        val packet = ByteArray(totalLength)

        // IP header
        packet[0] = 0x45.toByte() // Version 4, header length 5 (20 bytes)
        packet[1] = 0x00.toByte() // Type of Service
        packet[2] = (totalLength shr 8).toByte()
        packet[3] = totalLength.toByte()
        
        // Identification
        val identification = (System.currentTimeMillis() % 65536).toInt()
        packet[4] = (identification shr 8).toByte()
        packet[5] = identification.toByte()
        
        // Flags & Fragment Offset
        packet[6] = 0x40.toByte() // Don't fragment
        packet[7] = 0x00.toByte() // Fragment offset
        
        // TTL
        packet[8] = 64.toByte() // Time to live
        
        // Protocol
        packet[9] = 6.toByte() // TCP
        
        // Header Checksum (initially 0, will be calculated later)
        packet[10] = 0x00.toByte()
        packet[11] = 0x00.toByte()

        // Source IP
        System.arraycopy(srcIp, 0, packet, 12, 4)
        // Destination IP
        System.arraycopy(dstIp, 0, packet, 16, 4)

        // TCP header
        // Source Port
        packet[20] = (srcPort shr 8).toByte()
        packet[21] = srcPort.toByte()
        // Destination Port
        packet[22] = (dstPort shr 8).toByte()
        packet[23] = dstPort.toByte()
        
        // Sequence Number
        packet[24] = (seqNum shr 24).toByte()
        packet[25] = (seqNum shr 16).toByte()
        packet[26] = (seqNum shr 8).toByte()
        packet[27] = seqNum.toByte()
        
        // Acknowledgment Number
        packet[28] = (ackNum shr 24).toByte()
        packet[29] = (ackNum shr 16).toByte()
        packet[30] = (ackNum shr 8).toByte()
        packet[31] = ackNum.toByte()
        
        // Data Offset, Reserved, Flags
        packet[32] = (5 shl 4).toByte() // Header length = 5 (20 bytes)
        packet[33] = flags.toByte() // Flags (ACK, PSH, SYN, FIN, etc.)
        
        // Window Size
        val windowSize = 65535 // Maximum window size
        packet[34] = (windowSize shr 8).toByte()
        packet[35] = windowSize.toByte()
        
        // Checksum (initially 0, will be calculated later)
        packet[36] = 0x00.toByte()
        packet[37] = 0x00.toByte()
        
        // Urgent Pointer
        packet[38] = 0x00.toByte()
        packet[39] = 0x00.toByte()

        // Copy payload
        if (payload.isNotEmpty()) {
            System.arraycopy(payload, 0, packet, ipHeaderLength + tcpHeaderLength, payload.size)
        }

        // Calculate IP header checksum
        val ipChecksum = calculateIPChecksum(packet, 0, ipHeaderLength)
        packet[10] = (ipChecksum shr 8).toByte()
        packet[11] = (ipChecksum and 0xFF).toByte()

        // Calculate TCP checksum
        val tcpChecksum = calculateTCPChecksum(packet, ipHeaderLength, tcpHeaderLength + payload.size, srcIp, dstIp)
        packet[36] = (tcpChecksum shr 8).toByte()
        packet[37] = (tcpChecksum and 0xFF).toByte()

        return packet
    }

    private fun calculateIPChecksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var i = offset
        while (i < offset + length) {
            val word = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }

        // Add carry
        while (sum > 0xFFFF) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }

        // Take one's complement
        return sum.inv() and 0xFFFF
    }

    private fun calculateUDPChecksum(
        packet: ByteArray,
        udpStart: Int,
        udpLength: Int,
        srcIp: ByteArray,
        dstIp: ByteArray
    ): Int {
        // Create pseudo header for UDP checksum
        val pseudoHeaderLength = 12 + udpLength
        val pseudoHeader = ByteArray(pseudoHeaderLength)

        // Source IP
        System.arraycopy(srcIp, 0, pseudoHeader, 0, 4)
        // Destination IP
        System.arraycopy(dstIp, 0, pseudoHeader, 4, 4)
        // Zeros + Protocol
        pseudoHeader[8] = 0
        pseudoHeader[9] = 17 // UDP
        // UDP Length
        pseudoHeader[10] = (udpLength shr 8).toByte()
        pseudoHeader[11] = udpLength.toByte()

        // UDP header and data
        System.arraycopy(packet, udpStart, pseudoHeader, 12, udpLength)

        // Zero the checksum field
        pseudoHeader[12 + 6] = 0
        pseudoHeader[12 + 7] = 0

        // Calculate checksum over pseudo header
        var sum = 0
        var i = 0
        while (i < pseudoHeaderLength - 1) {
            val word = ((pseudoHeader[i].toInt() and 0xFF) shl 8) or (pseudoHeader[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }

        // Add last byte if length is odd
        if (pseudoHeaderLength % 2 != 0) {
            sum += (pseudoHeader[pseudoHeaderLength - 1].toInt() and 0xFF) shl 8
        }

        // Add carry
        while (sum > 0xFFFF) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }

        // Take one's complement
        return sum.inv() and 0xFFFF
    }

    private fun calculateTCPChecksum(
        packet: ByteArray,
        tcpStart: Int,
        tcpLength: Int,
        srcIp: ByteArray,
        dstIp: ByteArray
    ): Int {
        // Create pseudo header
        val pseudoHeaderLength = 12 + tcpLength
        val pseudoHeader = ByteArray(pseudoHeaderLength)

        // Source IP
        System.arraycopy(srcIp, 0, pseudoHeader, 0, 4)
        // Destination IP
        System.arraycopy(dstIp, 0, pseudoHeader, 4, 4)
        // Zeros + Protocol
        pseudoHeader[8] = 0
        pseudoHeader[9] = 6 // TCP
        // TCP Length
        pseudoHeader[10] = (tcpLength shr 8).toByte()
        pseudoHeader[11] = tcpLength.toByte()

        // TCP header and data
        System.arraycopy(packet, tcpStart, pseudoHeader, 12, tcpLength)

        // Zero the checksum field
        pseudoHeader[12 + 16] = 0
        pseudoHeader[12 + 17] = 0

        // Calculate checksum over pseudo header
        var sum = 0
        var i = 0
        while (i < pseudoHeaderLength - 1) {
            val word = ((pseudoHeader[i].toInt() and 0xFF) shl 8) or (pseudoHeader[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }

        // Add last byte if length is odd
        if (pseudoHeaderLength % 2 != 0) {
            sum += (pseudoHeader[pseudoHeaderLength - 1].toInt() and 0xFF) shl 8
        }

        // Add carry
        while (sum > 0xFFFF) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }

        // Take one's complement
        return sum.inv() and 0xFFFF
    }
}
