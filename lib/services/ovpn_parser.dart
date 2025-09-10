import 'dart:io';
import 'package:flutter/services.dart';
import 'package:flutter/foundation.dart';

class OvpnConfig {
  final String remote;
  final int port;
  final String protocol;
  final String cipher;
  final String auth;
  final String? ca;
  final String? cert;
  final String? key;
  final String? tlsAuth;
  final String? tlsCrypt;
  final String? compLzo;
  final String? verb;
  final String? resolvRetry;
  final String? nobind;
  final String? persistKey;
  final String? persistTun;
  final String? muteReplayWarnings;
  final String? remoteCertTls;
  final String? redirectGateway;
  final String? dhcpOption;
  final String? ignoreUnknownOption;
  final String? remoteCertThumbprint;
  final String? config;

  OvpnConfig({
    required this.remote,
    required this.port,
    required this.protocol,
    required this.cipher,
    required this.auth,
    this.ca,
    this.cert,
    this.key,
    this.tlsAuth,
    this.tlsCrypt,
    this.compLzo,
    this.verb,
    this.resolvRetry,
    this.nobind,
    this.persistKey,
    this.persistTun,
    this.muteReplayWarnings,
    this.remoteCertTls,
    this.redirectGateway,
    this.dhcpOption,
    this.ignoreUnknownOption,
    this.remoteCertThumbprint,
    required this.config,
  });

  Map<String, dynamic> toMap() {
    return {
      'remote': remote,
      'port': port,
      'protocol': protocol,
      'cipher': cipher,
      'auth': auth,
      'ca': ca,
      'cert': cert,
      'key': key,
      'tlsAuth': tlsAuth,
      'tlsCrypt': tlsCrypt,
      'compLzo': compLzo,
      'verb': verb,
      'resolvRetry': resolvRetry,
      'nobind': nobind,
      'persistKey': persistKey,
      'persistTun': persistTun,
      'muteReplayWarnings': muteReplayWarnings,
      'remoteCertTls': remoteCertTls,
      'redirectGateway': redirectGateway,
      'dhcpOption': dhcpOption,
      'ignoreUnknownOption': ignoreUnknownOption,
      'remoteCertThumbprint': remoteCertThumbprint,
      'config': config,
    };
  }
}

class OvpnParser {
  static Future<OvpnConfig> parseFromAsset(String assetPath) async {
    try {
      final configContent = await rootBundle.loadString(assetPath);
      return parseFromString(configContent);
    } catch (e) {
      if (kDebugMode) {
        print('Error loading OVPN file from asset: $e');
      }
      rethrow;
    }
  }

  static Future<OvpnConfig> parseFromFile(String filePath) async {
    try {
      final file = File(filePath);
      final configContent = await file.readAsString();
      return parseFromString(configContent);
    } catch (e) {
      if (kDebugMode) {
        print('Error loading OVPN file from path: $e');
      }
      rethrow;
    }
  }

  static OvpnConfig parseFromString(String configContent) {
    // Add essential routing directives if missing
    String enhancedConfig = configContent;

    // Add redirect-gateway if not present
    if (!enhancedConfig.contains('redirect-gateway')) {
      enhancedConfig += '\nredirect-gateway def1\n';
    }

    // Add DNS servers if not present
    if (!enhancedConfig.contains('dhcp-option DNS')) {
      enhancedConfig += '\ndhcp-option DNS 8.8.8.8\ndhcp-option DNS 8.8.4.4\n';
    }

    // Add route configuration if missing
    if (!enhancedConfig.contains('route 0.0.0.0')) {
      enhancedConfig += '\nroute 0.0.0.0 0.0.0.0\n';
    }

    // Add verbosity for better debugging
    if (!enhancedConfig.contains('verb 3')) {
      enhancedConfig += '\nverb 3\n';
    }

    final lines = enhancedConfig.split('\n');
    final config = <String, String>{};
    final certificates = <String, String>{};
    String? currentCert;
    String? currentCertType;

    for (final line in lines) {
      final trimmedLine = line.trim();

      if (trimmedLine.isEmpty) continue;

      // Handle certificate blocks
      if (trimmedLine.startsWith('<')) {
        if (trimmedLine.startsWith('</')) {
          // End of certificate block
          if (currentCertType != null && currentCert != null) {
            certificates[currentCertType] = currentCert.trim();
          }
          currentCert = null;
          currentCertType = null;
        } else {
          // Start of certificate block
          currentCertType = trimmedLine
              .substring(1, trimmedLine.length - 1)
              .toLowerCase();
          currentCert = '';
        }
        continue;
      }

      if (currentCertType != null) {
        // We're inside a certificate block
        currentCert = (currentCert ?? '') + line + '\n';
        continue;
      }

      // Parse regular config lines
      if (trimmedLine.startsWith('#') || trimmedLine.startsWith(';')) {
        continue; // Skip comments
      }

      final parts = trimmedLine.split(RegExp(r'\s+'));
      if (parts.isNotEmpty) {
        final key = parts[0].toLowerCase();
        final value = parts.length > 1 ? parts.sublist(1).join(' ') : '';
        config[key] = value;
      }
    }

    // Extract remote host and port
    String remote = '';
    int port = 1194;
    final remoteLine = config['remote'] ?? '';
    final remoteTokens = remoteLine
        .split(RegExp(r'\s+'))
        .where((e) => e.isNotEmpty)
        .toList();
    if (remoteTokens.isNotEmpty) {
      remote = remoteTokens[0];
      if (remoteTokens.length > 1) {
        port = int.tryParse(remoteTokens[1]) ?? port;
      }
    }
    // If explicit port directive exists, it overrides
    final portStr = config['port'];
    if (portStr != null && portStr.trim().isNotEmpty) {
      port = int.tryParse(portStr.trim()) ?? port;
    }

    final protocol = config['proto'] ?? 'udp';
    final cipher = config['cipher'] ?? 'aes-256-gcm';
    final auth = config['auth'] ?? 'sha256';

    return OvpnConfig(
      remote: remote,
      port: port,
      protocol: protocol,
      cipher: cipher,
      auth: auth,
      ca: certificates['ca'],
      cert: certificates['cert'],
      key: certificates['key'],
      tlsAuth: certificates['tls-auth'],
      tlsCrypt: certificates['tls-crypt'],
      compLzo: config['comp-lzo'],
      verb: config['verb'],
      resolvRetry: config['resolv-retry'],
      nobind: config['nobind'],
      persistKey: config['persist-key'],
      persistTun: config['persist-tun'],
      muteReplayWarnings: config['mute-replay-warnings'],
      remoteCertTls: config['remote-cert-tls'],
      redirectGateway: config['redirect-gateway'],
      dhcpOption: config['dhcp-option'],
      ignoreUnknownOption: config['ignore-unknown-option'],
      remoteCertThumbprint: config['remote-cert-thumbprint'],
      config: configContent,
    );
  }

  static String getCountryFromFilename(String filename) {
    final name = filename.toLowerCase();
    if (name.contains('us') ||
        name.contains('united') ||
        name.contains('america')) {
      return 'United States';
    } else if (name.contains('jp') || name.contains('japan')) {
      return 'Japan';
    } else if (name.contains('uk') || name.contains('britain')) {
      return 'United Kingdom';
    } else if (name.contains('de') || name.contains('germany')) {
      return 'Germany';
    } else if (name.contains('fr') || name.contains('france')) {
      return 'France';
    } else if (name.contains('ca') || name.contains('canada')) {
      return 'Canada';
    } else if (name.contains('au') || name.contains('australia')) {
      return 'Australia';
    } else if (name.contains('sg') || name.contains('singapore')) {
      return 'Singapore';
    } else if (name.contains('nl') || name.contains('netherlands')) {
      return 'Netherlands';
    } else if (name.contains('ch') || name.contains('switzerland')) {
      return 'Switzerland';
    }
    return 'Unknown';
  }

  static String getCountryCodeFromFilename(String filename) {
    final name = filename.toLowerCase();
    if (name.contains('us') ||
        name.contains('united') ||
        name.contains('america')) {
      return 'US';
    } else if (name.contains('jp') || name.contains('japan')) {
      return 'JP';
    } else if (name.contains('uk') || name.contains('britain')) {
      return 'GB';
    } else if (name.contains('de') || name.contains('germany')) {
      return 'DE';
    } else if (name.contains('fr') || name.contains('france')) {
      return 'FR';
    } else if (name.contains('ca') || name.contains('canada')) {
      return 'CA';
    } else if (name.contains('au') || name.contains('australia')) {
      return 'AU';
    } else if (name.contains('sg') || name.contains('singapore')) {
      return 'SG';
    } else if (name.contains('nl') || name.contains('netherlands')) {
      return 'NL';
    } else if (name.contains('ch') || name.contains('switzerland')) {
      return 'CH';
    }
    return 'XX';
  }
}
