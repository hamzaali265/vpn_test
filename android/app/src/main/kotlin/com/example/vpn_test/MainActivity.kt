package com.example.vpn_test

import android.app.Activity
import android.content.Intent
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine

class MainActivity : FlutterActivity() {
    private var vpnMethodChannel: VpnMethodChannel? = null
    
    companion object {
        const val VPN_PERMISSION_REQUEST_CODE = 24
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        
        // Register the VPN method channel
        vpnMethodChannel = VpnMethodChannel().apply {
            onAttachedToEngine(flutterEngine, this@MainActivity)
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        // Handle VPN permission result
        if (requestCode == VPN_PERMISSION_REQUEST_CODE) {
            vpnMethodChannel?.handleVpnPermissionResult(
                resultCode == Activity.RESULT_OK
            )
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        vpnMethodChannel?.onDetachedFromEngine()
    }
}