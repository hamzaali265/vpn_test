import 'dart:async';
import 'dart:convert';
import 'dart:developer';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';
import 'package:flutter/foundation.dart';
import 'dart:io';
import '../models/vpn_server.dart';
import '../models/connection_status.dart';
import 'native_vpn_service.dart';

class VpnService {
  static final VpnService _instance = VpnService._internal();
  factory VpnService() => _instance;
  VpnService._internal();

  late NativeVpnService _nativeVpnService;
  final StreamController<VpnConnectionInfo> _connectionController =
      StreamController<VpnConnectionInfo>.broadcast();

  Stream<VpnConnectionInfo> get connectionStream =>
      _connectionController.stream;

  VpnConnectionInfo _currentStatus = VpnConnectionInfo(
    status: VpnConnectionStatus.disconnected,
  );

  VpnConnectionInfo get currentStatus => _currentStatus;

  bool get isConnecting => _isConnecting;
  bool get isDisconnecting => _isDisconnecting;
  bool get isConnected =>
      _currentStatus.status == VpnConnectionStatus.connected;
  bool get isDisconnected =>
      _currentStatus.status == VpnConnectionStatus.disconnected;

  Timer? _connectionTimer;
  DateTime? _connectedAt;
  bool _isInitialized = false;

  // VPN Gate API URL
  static const String _vpnGateUrl = 'http://www.vpngate.net/api/iphone/';

  // IP detection services
  static const String _ipifyUrl = 'https://api.ipify.org?format=json';
  static const String _ipApiUrl = 'https://ipapi.co/json/';

  // Connection state management
  bool _isConnecting = false;
  bool _isDisconnecting = false;

  // Server caching
  List<VpnServer>? _cachedServers;
  DateTime? _lastServerFetch;
  static const Duration _serverCacheTimeout = Duration(minutes: 5);

  Future<void> initialize() async {
    if (_isInitialized) return;

    try {
      _nativeVpnService = NativeVpnService();
      await _nativeVpnService.initialize();

      // Listen to native VPN service status changes
      _nativeVpnService.connectionStream.listen((status) {
        _currentStatus = status;
        _connectionController.add(status);
      });

      _isInitialized = true;

      if (kDebugMode) {
        log('Native VPN Service initialized successfully');
      }
    } catch (e) {
      if (kDebugMode) {
        log('Failed to initialize Native VPN Service: $e');
      }
      _isInitialized = true;
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

  Future<List<VpnServer>> fetchVpnServers({bool forceRefresh = false}) async {
    // Check if we have cached servers and they're still fresh
    if (!forceRefresh &&
        _cachedServers != null &&
        _lastServerFetch != null &&
        DateTime.now().difference(_lastServerFetch!) < _serverCacheTimeout) {
      if (kDebugMode) {
        log('Using cached VPN servers (${_cachedServers!.length} servers)');
      }
      return _cachedServers!;
    }

    try {
      if (kDebugMode) {
        log('Fetching VPN servers from VPN Gate API...');
      }

      final response = await http.get(Uri.parse(_vpnGateUrl));
      if (response.statusCode == 200) {
        final servers = <VpnServer>[];

        if (kDebugMode) {
          log(
            'VPN Gate API response received, body length: ${response.body.length}',
          );
          log(
            'Response body preview: ${response.body.substring(0, response.body.length > 200 ? 200 : response.body.length)}...',
          );
        }

        // Check if response is CSV format (starts with *vpn_servers)
        if (response.body.startsWith('*vpn_servers')) {
          if (kDebugMode) {
            log('Detected CSV format, parsing directly');
          }
        } else {
          // Try JSON parsing first for non-CSV responses
          try {
            final jsonData = jsonDecode(response.body);
            if (jsonData is List) {
              for (final item in jsonData) {
                if (item is Map<String, dynamic>) {
                  final server = VpnServer.fromJson(item);
                  if (server.openVpnConfigData.isNotEmpty) {
                    servers.add(server);
                  }
                }
              }
            }
          } catch (jsonError) {
            if (kDebugMode) {
              log('JSON parsing failed, trying CSV: $jsonError');
            }
          }
        }

        // Parse CSV format (either detected or as fallback)
        if (servers.isEmpty) {
          // Parse CSV format
          final lines = response.body.split('\n');
          log(
            'VPN Gate CSV Header: ${lines.length > 0 ? lines[0] : 'No header'}',
          );
          log(
            'VPN Gate CSV Format: ${lines.length > 1 ? lines[1] : 'No format line'}',
          );

          for (int i = 0; i < lines.length; i++) {
            final line = lines[i].trim();
            if (line.isEmpty) continue;

            // Skip header lines that start with * or #
            if (line.startsWith('*') || line.startsWith('#')) {
              continue;
            }

            try {
              final csvRow = _parseCsvLine(line);
              if (csvRow.length >= 15) {
                // VPN Gate CSV has 15 fields
                final server = VpnServer.fromCsv(csvRow);
                if (server.openVpnConfigData.isNotEmpty &&
                    server.country.isNotEmpty) {
                  servers.add(server);
                  if (servers.length == 1) {
                    log(
                      'First server: Country=${server.country}, IP=${server.ipAddress}, HostName=${server.hostName}',
                    );
                  }
                }
              }
            } catch (e) {
              // Skip invalid lines
              if (kDebugMode) {
                log('Skipping invalid CSV line: $line, Error: $e');
              }
            }
          }
        }

        // Sort servers by session count (lower is better)
        servers.sort((a, b) => a.sessionCount.compareTo(b.sessionCount));

        if (kDebugMode) {
          log('Successfully parsed ${servers.length} VPN servers');
        }

        // Cache the servers
        _cachedServers = servers.take(50).toList();
        _lastServerFetch = DateTime.now();

        return _cachedServers!;
      } else {
        if (kDebugMode) {
          log('VPN Gate API returned status code: ${response.statusCode}');
        }
        return [];
      }
    } catch (e) {
      if (kDebugMode) {
        log('Error fetching VPN servers: $e');
      }

      // Return fallback servers if API fails
      _cachedServers = _getFallbackServers();
      _lastServerFetch = DateTime.now();
      return _cachedServers!;
    }
  }

  void clearServerCache() {
    _cachedServers = null;
    _lastServerFetch = null;
    if (kDebugMode) {
      log('Server cache cleared');
    }
  }

  List<VpnServer> _getFallbackServers() {
    if (kDebugMode) {
      log('Using fallback servers due to API failure');
    }

    // Create some sample servers using the VpnServer model
    return [
      VpnServer(
        hostName: 'fallback-us-1',
        ipAddress: '192.168.1.100',
        country: 'United States',
        countryCode: 'US',
        sessionCount: 1,
        uptime: 86400, // 1 day
        totalUsers: 100,
        totalTraffic: 1000000,
        logType: '1',
        operator: 'Fallback',
        message: 'Sample server',
        openVpnConfigData: 'dGVzdA==', // Base64 for "test"
        port: 1194,
        protocol: 'UDP',
      ),
      VpnServer(
        hostName: 'fallback-jp-1',
        ipAddress: '192.168.1.101',
        country: 'Japan',
        countryCode: 'JP',
        sessionCount: 2,
        uptime: 172800, // 2 days
        totalUsers: 150,
        totalTraffic: 2000000,
        logType: '1',
        operator: 'Fallback',
        message: 'Sample server',
        openVpnConfigData: 'dGVzdA==', // Base64 for "test"
        port: 1194,
        protocol: 'UDP',
      ),
      VpnServer(
        hostName: 'fallback-uk-1',
        ipAddress: '192.168.1.102',
        country: 'United Kingdom',
        countryCode: 'UK',
        sessionCount: 3,
        uptime: 259200, // 3 days
        totalUsers: 200,
        totalTraffic: 3000000,
        logType: '1',
        operator: 'Fallback',
        message: 'Sample server',
        openVpnConfigData: 'dGVzdA==', // Base64 for "test"
        port: 1194,
        protocol: 'UDP',
      ),
    ];
  }

  List<String> _parseCsvLine(String line) {
    final result = <String>[];
    bool inQuotes = false;
    String current = '';

    for (int i = 0; i < line.length; i++) {
      final char = line[i];

      if (char == '"') {
        inQuotes = !inQuotes;
      } else if (char == ',' && !inQuotes) {
        result.add(current.trim());
        current = '';
      } else {
        current += char;
      }
    }

    result.add(current.trim());
    return result;
  }

  Future<void> connectToServer(VpnServer server) async {
    // Prevent multiple simultaneous connections
    if (_isConnecting) {
      if (kDebugMode) {
        log('Connection already in progress, ignoring new request');
      }
      return;
    }

    // Only allow connection if not already connected
    if (_currentStatus.status == VpnConnectionStatus.connected) {
      if (kDebugMode) {
        log('Already connected, ignoring connect request');
      }
      return;
    }

    _isConnecting = true;

    try {
      await initialize();

      _updateStatus(VpnConnectionStatus.connecting, server: server);

      // Check if we have permission first
      final hasVpnPermission = await hasPermission();
      if (!hasVpnPermission) {
        if (kDebugMode) {
          print('VPN permission not granted, requesting permission...');
        }
        final granted = await requestPermission();
        if (!granted) {
          if (kDebugMode) {
            print('VPN permission denied by user');
          }
          _updateStatus(
            VpnConnectionStatus.error,
            errorMessage: 'VPN permission denied',
          );
          return;
        }
        if (kDebugMode) {
          print('VPN permission granted');
        }
      } else {
        if (kDebugMode) {
          print('VPN permission already granted');
        }
      }

      // Use real VPN connection on Android, simulation on iOS simulator
      if (Platform.isIOS && kDebugMode) {
        log('iOS Simulator detected - using simulation mode');
        log('Attempting to connect to: ${server.displayName}');
        log('Config data length: ${server.openVpnConfigData.length}');

        // Simulate connection process
        await Future.delayed(const Duration(seconds: 2));
        _updateStatus(VpnConnectionStatus.connected, server: server);
        _connectedAt = DateTime.now();
        _startConnectionTimer();
      } else {
        // Real VPN connection for Android and iOS devices
        log('Attempting real VPN connection to: ${server.displayName}');
        log('Config data length: ${server.openVpnConfigData.length}');

        try {
          // Decode Base64 config from VPN Gate API and add routing directives
          String configForConnect = utf8.decode(
            base64Decode(server.openVpnConfigData),
          );

          // Add essential routing directives if missing
          if (!configForConnect.contains('redirect-gateway')) {
            configForConnect += '\nredirect-gateway def1\n';
          }

          // Add DNS servers if not present
          if (!configForConnect.contains('dhcp-option DNS')) {
            configForConnect +=
                '\ndhcp-option DNS 8.8.8.8\ndhcp-option DNS 8.8.4.4\n';
          }

          // Force cert as required to prevent plugin from appending tokens incorrectly
          final bool certIsRequired = true;

          if (kDebugMode) {
            log('Starting VPN connection with config data...');
            log('Server: ${server.displayName}');
            log('Config length (enhanced): ${configForConnect.length}');
            log('certIsRequired: $certIsRequired');
            log(
              'Config preview: ${configForConnect.substring(0, configForConnect.length > 200 ? 200 : configForConnect.length)}...',
            );
          }

          if (kDebugMode) {
            log(
              'Calling _openvpn.connect() with server: ${server.displayName}',
            );
          }

          await _nativeVpnService.connectToServer(server);

          if (kDebugMode) {
            log('_openvpn.connect() call completed');
          }

          if (kDebugMode) {
            log('VPN connect() call completed, waiting for status updates...');
          }

          // Wait a moment for the connection to be established
          // The actual connection status will be updated via _onVpnStatusChanged
          await Future.delayed(const Duration(milliseconds: 2000));

          // Check if we're still connecting after 10 seconds
          Future.delayed(const Duration(seconds: 10), () {
            if (_currentStatus.status == VpnConnectionStatus.connecting) {
              if (kDebugMode) {
                log(
                  'VPN connection timeout - still connecting after 10 seconds',
                );
              }
              _updateStatus(
                VpnConnectionStatus.error,
                errorMessage:
                    'Connection timeout - VPN may not be routing traffic properly',
              );
            }
          });
        } catch (connectError) {
          if (kDebugMode) {
            log('Connection attempt failed: $connectError');
            log('Error type: ${connectError.runtimeType}');
          }
          _updateStatus(
            VpnConnectionStatus.error,
            errorMessage: 'Connection failed: $connectError',
          );
          return;
        }
      }

      await _saveConnectionInfo(server);
    } catch (e) {
      log('Connection error: $e');
      _updateStatus(VpnConnectionStatus.error, errorMessage: e.toString());
    } finally {
      _isConnecting = false;
    }
  }

  Future<void> disconnect() async {
    // Prevent multiple simultaneous disconnections
    if (_isDisconnecting) {
      if (kDebugMode) {
        log('Disconnection already in progress, ignoring new request');
      }
      return;
    }

    _isDisconnecting = true;

    try {
      await initialize();

      // Check if VPN is actually connected before trying to disconnect
      if (_currentStatus.status != VpnConnectionStatus.connected &&
          _currentStatus.status != VpnConnectionStatus.connecting) {
        if (kDebugMode) {
          log(
            'VPN is not connected, no need to disconnect. Current status: ${_currentStatus.status}',
          );
        }
        return;
      }

      _updateStatus(VpnConnectionStatus.disconnecting);

      // Use real VPN disconnection on Android, simulation on iOS simulator
      if (Platform.isIOS && kDebugMode) {
        log('iOS Simulator detected - using simulation mode for disconnect');

        // Simulate disconnection process
        await Future.delayed(const Duration(seconds: 1));
        _connectedAt = null;
        _connectionTimer?.cancel();
        _connectionTimer = null;
        _updateStatus(VpnConnectionStatus.disconnected);
        await _clearConnectionInfo();
      } else {
        // Real VPN disconnection for Android and iOS devices
        log('Attempting real VPN disconnection');
        try {
          await _nativeVpnService.disconnect();
          // Wait a bit for the disconnect to process
          await Future.delayed(const Duration(milliseconds: 500));
        } catch (disconnectError) {
          if (kDebugMode) {
            log('Error during disconnect: $disconnectError');
          }
          // Even if disconnect fails, clean up our state
          _connectedAt = null;
          _connectionTimer?.cancel();
          _connectionTimer = null;
          _updateStatus(VpnConnectionStatus.disconnected);
          await _clearConnectionInfo();
        }
      }
    } catch (e) {
      if (kDebugMode) {
        log('Disconnect error: $e');
      }
      // Clean up state even if there's an error
      _connectedAt = null;
      _connectionTimer?.cancel();
      _connectionTimer = null;
      _updateStatus(VpnConnectionStatus.error, errorMessage: e.toString());
    } finally {
      _isDisconnecting = false;
    }
  }

  Future<bool> hasPermission() async {
    try {
      await initialize();
      return await _nativeVpnService.hasPermission();
    } catch (e) {
      log('Error checking VPN permission: $e');
      return false;
    }
  }

  Future<bool> requestPermission() async {
    try {
      await initialize();
      return await _nativeVpnService.requestPermission();
    } catch (e) {
      log('Error requesting VPN permission: $e');
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

  Future<void> _saveConnectionInfo(VpnServer server) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString(
        'last_connected_server',
        jsonEncode(server.toJson()),
      );
    } catch (e) {
      log('Error saving connection info: $e');
    }
  }

  Future<void> _clearConnectionInfo() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.remove('last_connected_server');
    } catch (e) {
      log('Error clearing connection info: $e');
    }
  }

  Future<VpnServer?> getLastConnectedServer() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final serverJson = prefs.getString('last_connected_server');
      if (serverJson != null) {
        final serverData = jsonDecode(serverJson);
        return VpnServer.fromJson(serverData);
      }
    } catch (e) {
      log('Error getting last connected server: $e');
    }
    return null;
  }

  // Connection Statistics
  Map<String, dynamic> getConnectionStats() {
    return {
      'isConnected': isConnected,
      'isConnecting': isConnecting,
      'isDisconnecting': isDisconnecting,
      'connectedAt': _connectedAt?.toIso8601String(),
      'duration': _connectedAt != null
          ? DateTime.now().difference(_connectedAt!).inSeconds
          : 0,
      'status': _currentStatus.status.toString(),
      'errorMessage': _currentStatus.errorMessage,
    };
  }

  // IP Detection Methods
  Future<void> _checkIPAfterConnection() async {
    // Wait a bit for the VPN connection to fully establish
    await Future.delayed(const Duration(seconds: 3));

    try {
      final currentIP = await getCurrentIP();
      if (currentIP != null) {
        if (kDebugMode) {
          log('IP after VPN connection: $currentIP');
        }

        // Get detailed IP info
        final ipInfo = await getIPInfo();
        if (kDebugMode) {
          log('IP Info: $ipInfo');
        }
      } else {
        if (kDebugMode) {
          log('Could not detect IP after VPN connection');
        }
      }
    } catch (e) {
      if (kDebugMode) {
        log('Error checking IP after connection: $e');
      }
    }
  }

  Future<String?> getCurrentIP() async {
    try {
      // Try multiple IP detection services for reliability
      final services = [_ipifyUrl, _ipApiUrl];

      for (final service in services) {
        try {
          final response = await http
              .get(Uri.parse(service), headers: {'User-Agent': 'VPN-Test-App'})
              .timeout(const Duration(seconds: 10));

          if (response.statusCode == 200) {
            final data = json.decode(response.body);
            String? ip;

            if (data is Map<String, dynamic>) {
              // Try different field names that different services use
              ip = data['ip'] ?? data['query'] ?? data['origin'];
            }

            if (ip != null && ip.isNotEmpty) {
              if (kDebugMode) {
                print('Current IP detected: $ip (via $service)');
              }
              return ip;
            }
          }
        } catch (e) {
          if (kDebugMode) {
            print('Failed to get IP from $service: $e');
          }
          continue;
        }
      }

      if (kDebugMode) {
        print('Failed to detect IP from all services');
      }
      return null;
    } catch (e) {
      if (kDebugMode) {
        print('Error detecting IP: $e');
      }
      return null;
    }
  }

  Future<Map<String, String?>> getIPInfo() async {
    try {
      final response = await http
          .get(Uri.parse(_ipApiUrl), headers: {'User-Agent': 'VPN-Test-App'})
          .timeout(const Duration(seconds: 10));

      if (response.statusCode == 200) {
        final data = json.decode(response.body);
        return {
          'ip': data['ip'],
          'country': data['country_name'],
          'city': data['city'],
          'isp': data['org'],
          'timezone': data['timezone'],
        };
      }
    } catch (e) {
      if (kDebugMode) {
        print('Error getting IP info: $e');
      }
    }

    return {
      'ip': null,
      'country': null,
      'city': null,
      'isp': null,
      'timezone': null,
    };
  }

  // Method to check if VPN service is properly running
  Future<bool> isVpnServiceRunning() async {
    try {
      // This is a simplified check - in a real implementation,
      // you might want to check the actual VPN service status
      return _currentStatus.status == VpnConnectionStatus.connected;
    } catch (e) {
      if (kDebugMode) {
        log('Error checking VPN service status: $e');
      }
      return false;
    }
  }

  void dispose() {
    _connectionController.close();
    _connectionTimer?.cancel();
    _nativeVpnService.dispose();
  }
}
