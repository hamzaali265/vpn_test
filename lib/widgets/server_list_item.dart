import 'package:flutter/material.dart';
import '../models/vpn_server.dart';

class ServerListItem extends StatelessWidget {
  final VpnServer server;
  final VoidCallback onTap;

  const ServerListItem({super.key, required this.server, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      decoration: BoxDecoration(
        color: const Color(0xFF1A1D29),
        borderRadius: BorderRadius.circular(15),
        border: Border.all(
          color: Colors.white.withValues(alpha: 0.1),
          width: 1,
        ),
      ),
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(15),
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Row(
              children: [
                // Country Flag/Icon
                Container(
                  width: 50,
                  height: 50,
                  decoration: BoxDecoration(
                    color: _getCountryColor().withOpacity(0.2),
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(
                      color: _getCountryColor().withOpacity(0.3),
                      width: 1,
                    ),
                  ),
                  child: Center(
                    child: Text(
                      _getCountryFlag(server.country),
                      style: const TextStyle(fontSize: 20),
                    ),
                  ),
                ),

                const SizedBox(width: 16),

                // Server Info
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Text(
                            _getCountryFlag(server.country),
                            style: const TextStyle(fontSize: 16),
                          ),
                          const SizedBox(width: 8),
                          Expanded(
                            child: Text(
                              server.country.isNotEmpty
                                  ? server.country
                                  : (server.hostName.isNotEmpty
                                        ? server.hostName
                                        : 'Unknown Server'),
                              style: const TextStyle(
                                color: Colors.white,
                                fontSize: 16,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 4),
                      Text(
                        server.ipAddress,
                        style: const TextStyle(
                          color: Colors.white70,
                          fontSize: 14,
                        ),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        server.statusText,
                        style: const TextStyle(
                          color: Colors.white54,
                          fontSize: 12,
                        ),
                      ),
                    ],
                  ),
                ),

                // Server Stats
                Column(
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    _buildStatChip(
                      '${server.totalUsers} users',
                      _getUserCountColor(),
                    ),
                    const SizedBox(height: 4),
                    _buildStatChip(_getSpeedIndicator(), _getSpeedColor()),
                  ],
                ),

                const SizedBox(width: 12),

                // Arrow Icon
                const Icon(
                  Icons.arrow_forward_ios,
                  color: Colors.white54,
                  size: 16,
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildStatChip(String text, Color color) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: color.withOpacity(0.2),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: color.withOpacity(0.3), width: 1),
      ),
      child: Text(
        text,
        style: TextStyle(
          color: color,
          fontSize: 10,
          fontWeight: FontWeight.bold,
        ),
      ),
    );
  }

  Color _getCountryColor() {
    // Simple color assignment based on country name hash
    final colors = [
      Colors.blue,
      Colors.green,
      Colors.orange,
      Colors.purple,
      Colors.red,
      Colors.teal,
      Colors.indigo,
      Colors.pink,
    ];

    final hash = server.country.hashCode;
    return colors[hash.abs() % colors.length];
  }

  Color _getUserCountColor() {
    if (server.totalUsers < 10) return Colors.green;
    if (server.totalUsers < 50) return Colors.orange;
    return Colors.red;
  }

  Color _getSpeedColor() {
    final speed = _getSpeedIndicator();
    switch (speed) {
      case 'Fast':
        return Colors.green;
      case 'Good':
        return Colors.blue;
      case 'Medium':
        return Colors.orange;
      default:
        return Colors.red;
    }
  }

  String _getSpeedIndicator() {
    // Simple speed indicator based on user count
    if (server.totalUsers < 10) return 'Fast';
    if (server.totalUsers < 50) return 'Good';
    if (server.totalUsers < 100) return 'Medium';
    return 'Slow';
  }

  String _getCountryFlag(String country) {
    // Return world flag if country is empty
    if (country.isEmpty) return '🌍';

    // Map country names to flag emojis
    final countryFlags = {
      'United States': '🇺🇸',
      'Japan': '🇯🇵',
      'Germany': '🇩🇪',
      'United Kingdom': '🇬🇧',
      'France': '🇫🇷',
      'Canada': '🇨🇦',
      'Australia': '🇦🇺',
      'Netherlands': '🇳🇱',
      'Sweden': '🇸🇪',
      'Norway': '🇳🇴',
      'Denmark': '🇩🇰',
      'Finland': '🇫🇮',
      'Switzerland': '🇨🇭',
      'Austria': '🇦🇹',
      'Belgium': '🇧🇪',
      'Italy': '🇮🇹',
      'Spain': '🇪🇸',
      'Portugal': '🇵🇹',
      'Poland': '🇵🇱',
      'Czech Republic': '🇨🇿',
      'Hungary': '🇭🇺',
      'Romania': '🇷🇴',
      'Bulgaria': '🇧🇬',
      'Greece': '🇬🇷',
      'Turkey': '🇹🇷',
      'Russia': '🇷🇺',
      'Ukraine': '🇺🇦',
      'Brazil': '🇧🇷',
      'Argentina': '🇦🇷',
      'Chile': '🇨🇱',
      'Mexico': '🇲🇽',
      'South Korea': '🇰🇷',
      'China': '🇨🇳',
      'India': '🇮🇳',
      'Singapore': '🇸🇬',
      'Thailand': '🇹🇭',
      'Malaysia': '🇲🇾',
      'Indonesia': '🇮🇩',
      'Philippines': '🇵🇭',
      'Vietnam': '🇻🇳',
      'Taiwan': '🇹🇼',
      'Hong Kong': '🇭🇰',
      'New Zealand': '🇳🇿',
      'South Africa': '🇿🇦',
      'Israel': '🇮🇱',
      'UAE': '🇦🇪',
      'Saudi Arabia': '🇸🇦',
      'Egypt': '🇪🇬',
      'Morocco': '🇲🇦',
      'Nigeria': '🇳🇬',
      'Kenya': '🇰🇪',
    };

    // Try to find exact match first
    if (countryFlags.containsKey(country)) {
      return countryFlags[country]!;
    }

    // Try to find partial match
    for (final entry in countryFlags.entries) {
      if (country.toLowerCase().contains(entry.key.toLowerCase()) ||
          entry.key.toLowerCase().contains(country.toLowerCase())) {
        return entry.value;
      }
    }

    // Default to world flag if no match found
    return '🌍';
  }
}
