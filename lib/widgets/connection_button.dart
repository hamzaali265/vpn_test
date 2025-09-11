import 'package:flutter/material.dart';
import '../models/connection_status.dart';

class ConnectionButton extends StatelessWidget {
  final VpnConnectionInfo connectionInfo;
  final VoidCallback onConnect;
  final VoidCallback onDisconnect;

  const ConnectionButton({
    super.key,
    required this.connectionInfo,
    required this.onConnect,
    required this.onDisconnect,
  });

  @override
  Widget build(BuildContext context) {
    final screenSize = MediaQuery.of(context).size;
    final buttonSize = screenSize.width * 0.5; // Responsive button size

    return GestureDetector(
      onTap: _handleTap,
      child: Container(
        width: buttonSize.clamp(150.0, 200.0),
        height: buttonSize.clamp(150.0, 200.0),
        decoration: BoxDecoration(
          shape: BoxShape.circle,
          gradient: _getGradient(),
          boxShadow: [
            BoxShadow(
              color: _getShadowColor(),
              blurRadius: 30,
              spreadRadius: 5,
            ),
          ],
        ),
        child: Stack(
          alignment: Alignment.center,
          children: [
            // Outer ring
            Container(
              width: (buttonSize * 0.9).clamp(135.0, 180.0),
              height: (buttonSize * 0.9).clamp(135.0, 180.0),
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                border: Border.all(
                  color: Colors.white.withOpacity(0.3),
                  width: 2,
                ),
              ),
            ),
            // Inner content
            Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Icon(
                  _getIcon(),
                  size: (buttonSize * 0.3).clamp(45.0, 60.0),
                  color: Colors.white,
                ),
                SizedBox(height: (buttonSize * 0.05).clamp(8.0, 10.0)),
                Text(
                  _getButtonText(),
                  style: TextStyle(
                    color: Colors.white,
                    fontSize: (buttonSize * 0.08).clamp(12.0, 16.0),
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  void _handleTap() {
    if (connectionInfo.isConnected) {
      onDisconnect();
    } else if (!connectionInfo.isConnecting &&
        !connectionInfo.isDisconnecting) {
      onConnect();
    }
    // Do nothing if connecting or disconnecting
  }

  LinearGradient _getGradient() {
    switch (connectionInfo.status) {
      case VpnConnectionStatus.connected:
        return const LinearGradient(
          colors: [Color(0xFF10B981), Color(0xFF059669)],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        );
      case VpnConnectionStatus.connecting:
      case VpnConnectionStatus.disconnecting:
        return const LinearGradient(
          colors: [Color(0xFFF59E0B), Color(0xFFD97706)],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        );
      case VpnConnectionStatus.error:
        return const LinearGradient(
          colors: [Color(0xFFEF4444), Color(0xFFDC2626)],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        );
      default:
        return const LinearGradient(
          colors: [Color(0xFF3B82F6), Color(0xFF1D4ED8)],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        );
    }
  }

  Color _getShadowColor() {
    switch (connectionInfo.status) {
      case VpnConnectionStatus.connected:
        return Colors.green.withOpacity(0.5);
      case VpnConnectionStatus.connecting:
      case VpnConnectionStatus.disconnecting:
        return Colors.orange.withOpacity(0.5);
      case VpnConnectionStatus.error:
        return Colors.red.withOpacity(0.5);
      default:
        return Colors.blue.withOpacity(0.5);
    }
  }

  IconData _getIcon() {
    switch (connectionInfo.status) {
      case VpnConnectionStatus.connected:
        return Icons.vpn_key;
      case VpnConnectionStatus.connecting:
        return Icons.hourglass_empty;
      case VpnConnectionStatus.disconnecting:
        return Icons.stop;
      case VpnConnectionStatus.error:
        return Icons.error;
      default:
        return Icons.power_settings_new;
    }
  }

  String _getButtonText() {
    switch (connectionInfo.status) {
      case VpnConnectionStatus.connected:
        return 'DISCONNECT';
      case VpnConnectionStatus.connecting:
        return 'CONNECTING...';
      case VpnConnectionStatus.disconnecting:
        return 'DISCONNECTING...';
      case VpnConnectionStatus.error:
        return 'ERROR';
      default:
        return 'CONNECT';
    }
  }
}
