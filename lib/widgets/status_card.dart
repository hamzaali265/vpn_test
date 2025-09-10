import 'package:flutter/material.dart';
import 'package:animated_text_kit/animated_text_kit.dart';
import '../models/connection_status.dart';

class StatusCard extends StatelessWidget {
  final VpnConnectionInfo connectionInfo;

  const StatusCard({super.key, required this.connectionInfo});

  @override
  Widget build(BuildContext context) {
    final screenSize = MediaQuery.of(context).size;
    final isSmallScreen = screenSize.height < 700;

    return Container(
      width: double.infinity,
      padding: EdgeInsets.all(isSmallScreen ? 16 : 20),
      decoration: BoxDecoration(
        gradient: LinearGradient(
          colors: [
            const Color(0xFF1A1D29),
            const Color(0xFF1A1D29).withOpacity(0.8),
          ],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: _getBorderColor(), width: 2),
        boxShadow: [
          BoxShadow(
            color: _getShadowColor(),
            blurRadius: 20,
            offset: const Offset(0, 10),
          ),
        ],
      ),
      child: Column(
        children: [
          // Status Icon
          Container(
            width: isSmallScreen ? 60 : 80,
            height: isSmallScreen ? 60 : 80,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: _getStatusColor().withOpacity(0.2),
              border: Border.all(color: _getStatusColor(), width: 3),
            ),
            child: Icon(
              _getStatusIcon(),
              color: _getStatusColor(),
              size: isSmallScreen ? 30 : 40,
            ),
          ),

          SizedBox(height: isSmallScreen ? 15 : 20),

          // Status Text
          AnimatedTextKit(
            animatedTexts: [
              TypewriterAnimatedText(
                connectionInfo.statusText,
                textStyle: TextStyle(
                  color: _getStatusColor(),
                  fontSize: isSmallScreen ? 20 : 24,
                  fontWeight: FontWeight.bold,
                ),
                speed: const Duration(milliseconds: 100),
              ),
            ],
            totalRepeatCount: 1,
          ),

          const SizedBox(height: 10),

          // Additional Info
          if (connectionInfo.serverName != null)
            Text(
              connectionInfo.serverName!,
              style: const TextStyle(color: Colors.white70, fontSize: 16),
              textAlign: TextAlign.center,
            ),

          if (connectionInfo.errorMessage != null)
            Padding(
              padding: const EdgeInsets.only(top: 10),
              child: Text(
                connectionInfo.errorMessage!,
                style: const TextStyle(color: Colors.red, fontSize: 14),
                textAlign: TextAlign.center,
              ),
            ),
        ],
      ),
    );
  }

  Color _getStatusColor() {
    switch (connectionInfo.status) {
      case VpnConnectionStatus.connected:
        return Colors.green;
      case VpnConnectionStatus.connecting:
      case VpnConnectionStatus.disconnecting:
        return Colors.orange;
      case VpnConnectionStatus.error:
        return Colors.red;
      default:
        return Colors.grey;
    }
  }

  Color _getBorderColor() {
    return _getStatusColor().withOpacity(0.3);
  }

  Color _getShadowColor() {
    return _getStatusColor().withOpacity(0.2);
  }

  IconData _getStatusIcon() {
    switch (connectionInfo.status) {
      case VpnConnectionStatus.connected:
        return Icons.security;
      case VpnConnectionStatus.connecting:
        return Icons.hourglass_empty;
      case VpnConnectionStatus.disconnecting:
        return Icons.stop;
      case VpnConnectionStatus.error:
        return Icons.error_outline;
      default:
        return Icons.security_outlined;
    }
  }
}
