import 'package:flutter/material.dart';
import 'package:flutter/foundation.dart';
import '../models/connection_status.dart';
import '../models/vpn_server.dart';
import '../services/vpn_service.dart';
import 'server_selection_screen.dart';

class SimpleHomeScreen extends StatefulWidget {
  const SimpleHomeScreen({super.key});

  @override
  State<SimpleHomeScreen> createState() => _SimpleHomeScreenState();
}

class _SimpleHomeScreenState extends State<SimpleHomeScreen> {
  final VpnService _vpnService = VpnService();
  VpnConnectionInfo _connectionInfo = VpnConnectionInfo(
    status: VpnConnectionStatus.disconnected,
  );
  VpnServer? _selectedServer;

  @override
  void initState() {
    super.initState();
    _loadLastConnectedServer();
    _listenToConnectionStatus();
  }

  void _loadLastConnectedServer() async {
    final server = await _vpnService.getLastConnectedServer();
    if (mounted) {
      setState(() {
        _selectedServer = server;
      });
    }
  }

  void _listenToConnectionStatus() {
    _vpnService.connectionStream.listen((status) {
      if (mounted) {
        setState(() {
          _connectionInfo = status;
        });
      }
    });
  }

  void _selectServer() async {
    final server = await Navigator.push<VpnServer>(
      context,
      MaterialPageRoute(builder: (context) => const ServerSelectionScreen()),
    );

    if (server != null && mounted) {
      setState(() {
        _selectedServer = server;
      });
    }
  }

  void _handleConnect() async {
    if (_selectedServer == null) {
      _selectServer();
      return;
    }

    if (_vpnService.isConnecting) {
      if (kDebugMode) {
        print('Connection already in progress');
      }
      return;
    }

    if (_vpnService.isConnected) {
      await _vpnService.disconnect();
    } else {
      await _vpnService.connectToServer(_selectedServer!);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF0A0E27),
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        elevation: 0,
        title: const Text(
          'VPN',
          style: TextStyle(
            color: Colors.white,
            fontSize: 24,
            fontWeight: FontWeight.bold,
          ),
        ),
        centerTitle: true,
      ),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(20.0),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              // Connection Status
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(20),
                decoration: BoxDecoration(
                  color: const Color(0xFF1A1F3A),
                  borderRadius: BorderRadius.circular(16),
                  border: Border.all(color: _getStatusColor(), width: 2),
                ),
                child: Column(
                  children: [
                    Icon(_getStatusIcon(), size: 48, color: _getStatusColor()),
                    const SizedBox(height: 16),
                    Text(
                      _getStatusText(),
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 18,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    const SizedBox(height: 8),
                    if (_selectedServer != null)
                      Text(
                        _selectedServer!.displayName,
                        style: const TextStyle(
                          color: Colors.grey,
                          fontSize: 14,
                        ),
                      ),
                  ],
                ),
              ),

              const SizedBox(height: 40),

              // Connect/Disconnect Button
              SizedBox(
                width: double.infinity,
                height: 60,
                child: ElevatedButton(
                  onPressed: _handleConnect,
                  style: ElevatedButton.styleFrom(
                    backgroundColor: _getButtonColor(),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(16),
                    ),
                    elevation: 0,
                  ),
                  child: _vpnService.isConnecting || _vpnService.isDisconnecting
                      ? const SizedBox(
                          width: 24,
                          height: 24,
                          child: CircularProgressIndicator(
                            color: Colors.white,
                            strokeWidth: 2,
                          ),
                        )
                      : Text(
                          _getButtonText(),
                          style: const TextStyle(
                            color: Colors.white,
                            fontSize: 18,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                ),
              ),

              const SizedBox(height: 20),

              // Server Selection Button
              SizedBox(
                width: double.infinity,
                height: 50,
                child: OutlinedButton(
                  onPressed: _selectServer,
                  style: OutlinedButton.styleFrom(
                    side: const BorderSide(color: Colors.grey),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(16),
                    ),
                  ),
                  child: Text(
                    _selectedServer == null ? 'Select Server' : 'Change Server',
                    style: const TextStyle(color: Colors.white, fontSize: 16),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Color _getStatusColor() {
    switch (_connectionInfo.status) {
      case VpnConnectionStatus.connected:
        return Colors.green;
      case VpnConnectionStatus.connecting:
        return Colors.orange;
      case VpnConnectionStatus.disconnecting:
        return Colors.orange;
      case VpnConnectionStatus.error:
        return Colors.red;
      default:
        return Colors.grey;
    }
  }

  IconData _getStatusIcon() {
    switch (_connectionInfo.status) {
      case VpnConnectionStatus.connected:
        return Icons.vpn_key;
      case VpnConnectionStatus.connecting:
        return Icons.vpn_key_outlined;
      case VpnConnectionStatus.disconnecting:
        return Icons.vpn_key_outlined;
      case VpnConnectionStatus.error:
        return Icons.error_outline;
      default:
        return Icons.vpn_key_off;
    }
  }

  String _getStatusText() {
    switch (_connectionInfo.status) {
      case VpnConnectionStatus.connected:
        return 'Connected';
      case VpnConnectionStatus.connecting:
        return 'Connecting...';
      case VpnConnectionStatus.disconnecting:
        return 'Disconnecting...';
      case VpnConnectionStatus.error:
        return 'Error';
      default:
        return 'Disconnected';
    }
  }

  Color _getButtonColor() {
    if (_vpnService.isConnected) {
      return Colors.red;
    } else if (_vpnService.isConnecting || _vpnService.isDisconnecting) {
      return Colors.orange;
    } else {
      return Colors.green;
    }
  }

  String _getButtonText() {
    if (_vpnService.isConnected) {
      return 'Disconnect';
    } else if (_vpnService.isConnecting) {
      return 'Connecting...';
    } else if (_vpnService.isDisconnecting) {
      return 'Disconnecting...';
    } else {
      return 'Connect';
    }
  }
}
