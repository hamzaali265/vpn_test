package com.example.vpn_test

import android.content.Intent
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine

class MainActivity : FlutterActivity() {
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        
        // Register the VPN method channel
        VpnMethodChannel().apply {
            onAttachedToEngine(flutterEngine, this@MainActivity)
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        // Handle VPN permission result
        if (requestCode == VPN_PERMISSION_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                // VPN permission granted, start VPN
                // This will be handled by the Flutter side
            } else {
                // VPN permission denied
                // This will be handled by the Flutter side
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }
    
    companion object {
        const val VPN_PERMISSION_REQUEST_CODE = 24
    }
}
