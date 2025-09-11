import 'dart:async';
import 'dart:developer';
import 'package:flutter/services.dart';
import 'package:flutter/foundation.dart';
import '../models/connection_status.dart';
import '../models/vpn_server.dart';

class NativeVpnService {
  static const MethodChannel _channel = MethodChannel('vpn_service');
  static final NativeVpnService _instance = NativeVpnService._internal();
  factory NativeVpnService() => _instance;
  NativeVpnService._internal();

  final StreamController<VpnConnectionInfo> _connectionController =
      StreamController<VpnConnectionInfo>.broadcast();

  Stream<VpnConnectionInfo> get connectionStream =>
      _connectionController.stream;

  VpnConnectionInfo _currentStatus = VpnConnectionInfo(
    status: VpnConnectionStatus.disconnected,
  );

  VpnConnectionInfo get currentStatus => _currentStatus;

  bool get isConnected =>
      _currentStatus.status == VpnConnectionStatus.connected;
  bool get isDisconnected =>
      _currentStatus.status == VpnConnectionStatus.disconnected;

  Timer? _connectionTimer;
  DateTime? _connectedAt;
  bool _isInitialized = false;

  Future<void> initialize() async {
    if (_isInitialized) return;

    try {
      // Set up method channel callbacks
      _channel.setMethodCallHandler(_handleMethodCall);
      _isInitialized = true;

      if (kDebugMode) {
        log('Native VPN Service initialized');
      }
    } catch (e) {
      if (kDebugMode) {
        log('Error initializing Native VPN Service: $e');
      }
    }
  }

  Future<dynamic> _handleMethodCall(MethodCall call) async {
    switch (call.method) {
      case 'onVpnStatusChanged':
        final status = call.arguments as String;
        _updateStatusFromNative(status);
        break;
      default:
        if (kDebugMode) {
          log('Unknown method call: ${call.method}');
        }
    }
  }

  void _updateStatusFromNative(String status) {
    VpnConnectionStatus connectionStatus;

    switch (status.toLowerCase()) {
      case 'connected':
        connectionStatus = VpnConnectionStatus.connected;
        _connectedAt = DateTime.now();
        _startConnectionTimer();
        break;
      case 'disconnected':
        connectionStatus = VpnConnectionStatus.disconnected;
        _connectedAt = null;
        _connectionTimer?.cancel();
        _connectionTimer = null;
        break;
      case 'connecting':
        connectionStatus = VpnConnectionStatus.connecting;
        break;
      case 'disconnecting':
        connectionStatus = VpnConnectionStatus.disconnecting;
        break;
      case 'error':
      case 'failed':
        connectionStatus = VpnConnectionStatus.error;
        break;
      default:
        connectionStatus = VpnConnectionStatus.disconnected;
    }

    _currentStatus = VpnConnectionInfo(
      status: connectionStatus,
      connectedDuration: _connectedAt != null
          ? DateTime.now().difference(_connectedAt!)
          : null,
    );

    _connectionController.add(_currentStatus);

    if (kDebugMode) {
      log('Native VPN Status: $status -> $connectionStatus');
    }
  }

  void _startConnectionTimer() {
    _connectionTimer?.cancel();
    _connectionTimer = Timer.periodic(const Duration(seconds: 1), (timer) {
      if (_connectedAt != null) {
        final duration = DateTime.now().difference(_connectedAt!);
        _currentStatus = _currentStatus.copyWith(connectedDuration: duration);
        _connectionController.add(_currentStatus);
      }
    });
  }

  Future<void> connectToServer(VpnServer server) async {
    try {
      await initialize();

      if (kDebugMode) {
        log('Connecting to server: ${server.displayName}');
      }

      _updateStatus(VpnConnectionStatus.connecting, server: server);

      // Request VPN permission first
      final permissionResult = await _channel.invokeMethod(
        'requestVpnPermission',
      );

      if (permissionResult == 'PERMISSION_NEEDED') {
        if (kDebugMode) {
          log('VPN permission needed');
        }
        _updateStatus(
          VpnConnectionStatus.error,
          errorMessage: 'VPN permission needed',
        );
        return;
      }

      // Start VPN with server information
      final result = await _channel.invokeMethod('startVpn', {
        'server_host': server.ipAddress,
        'server_port': 1194, // Default OpenVPN port
        'server_config': server.openVpnConfigData,
      });

      if (result is Map && result['status'] == 'started') {
        if (kDebugMode) {
          log('VPN started successfully: ${result['message']}');
        }

        // Keep status as connecting - let the VPN establish properly
        _updateStatus(VpnConnectionStatus.connecting, server: server);

        // Wait longer for the VPN to establish connection
        await Future.delayed(const Duration(seconds: 5));

        // Check VPN status after proper establishment time
        final statusResult = await _channel.invokeMethod('getVpnStatus');
        if (statusResult is Map && statusResult['status'] == 'connected') {
          _updateStatus(VpnConnectionStatus.connected, server: server);
          _connectedAt = DateTime.now();
          _startConnectionTimer();
        } else {
          // Keep as connecting if not yet connected
          _updateStatus(VpnConnectionStatus.connecting, server: server);
        }
      } else {
        if (kDebugMode) {
          log('Failed to start VPN: $result');
        }
        _updateStatus(
          VpnConnectionStatus.error,
          errorMessage: 'Failed to start VPN: $result',
        );
      }
    } catch (e) {
      if (kDebugMode) {
        log('Error connecting to VPN: $e');
      }
      _updateStatus(VpnConnectionStatus.error, errorMessage: e.toString());
    }
  }

  Future<void> disconnect() async {
    try {
      if (kDebugMode) {
        log('Disconnecting VPN');
      }

      _updateStatus(VpnConnectionStatus.disconnecting);

      final result = await _channel.invokeMethod('stopVpn');

      if (result is Map && result['status'] == 'stopped') {
        if (kDebugMode) {
          log('VPN stopped successfully: ${result['message']}');
        }
        _updateStatus(VpnConnectionStatus.disconnected);
        _connectedAt = null;
        _connectionTimer?.cancel();
      } else {
        if (kDebugMode) {
          log('Failed to stop VPN: $result');
        }
        _updateStatus(
          VpnConnectionStatus.error,
          errorMessage: 'Failed to stop VPN: $result',
        );
      }
    } catch (e) {
      if (kDebugMode) {
        log('Error disconnecting VPN: $e');
      }
      _updateStatus(VpnConnectionStatus.error, errorMessage: e.toString());
    }
  }

  Future<bool> hasPermission() async {
    try {
      await initialize();
      final result = await _channel.invokeMethod('requestVpnPermission');
      return result != 'PERMISSION_NEEDED';
    } catch (e) {
      if (kDebugMode) {
        log('Error checking VPN permission: $e');
      }
      return false;
    }
  }

  Future<bool> requestPermission() async {
    try {
      await initialize();
      final result = await _channel.invokeMethod('requestVpnPermission');
      return result != 'PERMISSION_NEEDED';
    } catch (e) {
      if (kDebugMode) {
        log('Error requesting VPN permission: $e');
      }
      return false;
    }
  }

  void _updateStatus(
    VpnConnectionStatus status, {
    VpnServer? server,
    String? errorMessage,
  }) {
    _currentStatus = VpnConnectionInfo(
      status: status,
      serverName: server?.displayName,
      serverIp: server?.ipAddress,
      country: server?.country,
      errorMessage: errorMessage,
      connectedDuration: _connectedAt != null
          ? DateTime.now().difference(_connectedAt!)
          : null,
    );
    _connectionController.add(_currentStatus);
  }

  void dispose() {
    _connectionController.close();
    _connectionTimer?.cancel();
  }
}
