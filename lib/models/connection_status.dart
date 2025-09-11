enum VpnConnectionStatus {
  disconnected,
  connecting,
  connected,
  disconnecting,
  error,
}

class VpnConnectionInfo {
  final VpnConnectionStatus status;
  final String? serverName;
  final String? serverIp;
  final String? country;
  final Duration? connectedDuration;
  final String? errorMessage;
  final DateTime? connectedAt;

  VpnConnectionInfo({
    required this.status,
    this.serverName,
    this.serverIp,
    this.country,
    this.connectedDuration,
    this.errorMessage,
    this.connectedAt,
  });

  VpnConnectionInfo copyWith({
    VpnConnectionStatus? status,
    String? serverName,
    String? serverIp,
    String? country,
    Duration? connectedDuration,
    String? errorMessage,
    DateTime? connectedAt,
  }) {
    return VpnConnectionInfo(
      status: status ?? this.status,
      serverName: serverName ?? this.serverName,
      serverIp: serverIp ?? this.serverIp,
      country: country ?? this.country,
      connectedDuration: connectedDuration ?? this.connectedDuration,
      errorMessage: errorMessage ?? this.errorMessage,
      connectedAt: connectedAt ?? this.connectedAt,
    );
  }

  String get statusText {
    switch (status) {
      case VpnConnectionStatus.disconnected:
        return 'Disconnected';
      case VpnConnectionStatus.connecting:
        return 'Connecting...';
      case VpnConnectionStatus.connected:
        return 'Connected';
      case VpnConnectionStatus.disconnecting:
        return 'Disconnecting...';
      case VpnConnectionStatus.error:
        return 'Error';
    }
  }

  bool get isConnected => status == VpnConnectionStatus.connected;
  bool get isConnecting => status == VpnConnectionStatus.connecting;
  bool get isDisconnecting => status == VpnConnectionStatus.disconnecting;
  bool get isDisconnected => status == VpnConnectionStatus.disconnected;
  bool get hasError => status == VpnConnectionStatus.error;
}
