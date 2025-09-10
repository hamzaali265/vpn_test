class VpnServer {
  final String hostName;
  final String ipAddress;
  final String country;
  final String countryCode;
  final int sessionCount;
  final int uptime;
  final int totalUsers;
  final int totalTraffic;
  final String logType;
  final String operator;
  final String message;
  final String openVpnConfigData;
  final int port;
  final String protocol;

  VpnServer({
    required this.hostName,
    required this.ipAddress,
    required this.country,
    required this.countryCode,
    required this.sessionCount,
    required this.uptime,
    required this.totalUsers,
    required this.totalTraffic,
    required this.logType,
    required this.operator,
    required this.message,
    required this.openVpnConfigData,
    required this.port,
    required this.protocol,
  });

  factory VpnServer.fromCsv(List<String> csvRow) {
    // VPN Gate CSV format: HostName,IP,Score,Ping,Speed,CountryLong,CountryShort,NumVpnSessions,Uptime,TotalUsers,TotalTraffic,LogType,Operator,Message,OpenVPN_ConfigData_Base64
    return VpnServer(
      hostName: csvRow.length > 0 ? csvRow[0] : '',
      ipAddress: csvRow.length > 1 ? csvRow[1] : '',
      country: csvRow.length > 5 ? csvRow[5] : '', // CountryLong is at index 5
      countryCode: csvRow.length > 6
          ? csvRow[6]
          : '', // CountryShort is at index 6
      sessionCount: csvRow.length > 7
          ? int.tryParse(csvRow[7]) ?? 0
          : 0, // NumVpnSessions
      uptime: csvRow.length > 8 ? int.tryParse(csvRow[8]) ?? 0 : 0, // Uptime
      totalUsers: csvRow.length > 9
          ? int.tryParse(csvRow[9]) ?? 0
          : 0, // TotalUsers
      totalTraffic: csvRow.length > 10
          ? int.tryParse(csvRow[10]) ?? 0
          : 0, // TotalTraffic
      logType: csvRow.length > 11 ? csvRow[11] : '', // LogType
      operator: csvRow.length > 12 ? csvRow[12] : '', // Operator
      message: csvRow.length > 13 ? csvRow[13] : '', // Message
      openVpnConfigData: csvRow.length > 14
          ? csvRow[14]
          : '', // OpenVPN_ConfigData_Base64
      port: 1194, // Default port
      protocol: 'UDP', // Default protocol
    );
  }

  String get displayName => '$country ($ipAddress)';
  String get statusText =>
      'Users: $totalUsers | Uptime: ${_formatUptime(uptime)}';

  String _formatUptime(int seconds) {
    final days = seconds ~/ 86400;
    final hours = (seconds % 86400) ~/ 3600;
    final minutes = (seconds % 3600) ~/ 60;

    if (days > 0) return '${days}d ${hours}h';
    if (hours > 0) return '${hours}h ${minutes}m';
    return '${minutes}m';
  }

  Map<String, dynamic> toJson() {
    return {
      'hostName': hostName,
      'ipAddress': ipAddress,
      'country': country,
      'countryCode': countryCode,
      'sessionCount': sessionCount,
      'uptime': uptime,
      'totalUsers': totalUsers,
      'totalTraffic': totalTraffic,
      'logType': logType,
      'operator': operator,
      'message': message,
      'openVpnConfigData': openVpnConfigData,
      'port': port,
      'protocol': protocol,
    };
  }

  factory VpnServer.fromJson(Map<String, dynamic> json) {
    return VpnServer(
      hostName: json['HostName'] ?? '',
      ipAddress: json['IP'] ?? '',
      country: json['CountryLong'] ?? '',
      countryCode: json['CountryShort'] ?? '',
      sessionCount: json['NumVpnSessions'] ?? 0,
      uptime: json['Uptime'] ?? 0,
      totalUsers: json['TotalUsers'] ?? 0,
      totalTraffic: json['TotalTraffic'] ?? 0,
      logType: json['LogType'] ?? '',
      operator: json['Operator'] ?? '',
      message: json['Message'] ?? '',
      openVpnConfigData: json['OpenVPN_ConfigData_Base64'] ?? '',
      port: json['Port'] ?? 1194,
      protocol: json['Protocol'] ?? 'UDP',
    );
  }
}
