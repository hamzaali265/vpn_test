import 'package:flutter/material.dart';
import '../models/vpn_server.dart';

class ServerInfoCard extends StatelessWidget {
  final VpnServer server;
  final VoidCallback onTap;

  const ServerInfoCard({super.key, required this.server, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        width: double.infinity,
        padding: const EdgeInsets.all(20),
        decoration: BoxDecoration(
          gradient: const LinearGradient(
            colors: [Color(0xFF1E3A8A), Color(0xFF3B82F6)],
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
          ),
          borderRadius: BorderRadius.circular(20),
          boxShadow: [
            BoxShadow(
              color: Colors.blue.withOpacity(0.3),
              blurRadius: 20,
              offset: const Offset(0, 10),
            ),
          ],
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                // Country Flag
                Container(
                  width: 40,
                  height: 40,
                  decoration: BoxDecoration(
                    color: Colors.white.withOpacity(0.2),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Center(
                    child: Text(
                      _getCountryFlag(server.country),
                      style: const TextStyle(fontSize: 20),
                    ),
                  ),
                ),
                const SizedBox(width: 15),
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
                              server.country,
                              style: const TextStyle(
                                color: Colors.white,
                                fontSize: 18,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                          ),
                        ],
                      ),
                      Text(
                        server.ipAddress,
                        style: const TextStyle(
                          color: Colors.white70,
                          fontSize: 14,
                        ),
                      ),
                    ],
                  ),
                ),
                const Icon(
                  Icons.arrow_forward_ios,
                  color: Colors.white70,
                  size: 16,
                ),
              ],
            ),

            const SizedBox(height: 15),

            // Server Stats
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceAround,
              children: [
                _buildStatItem(
                  'Users',
                  server.totalUsers.toString(),
                  Icons.people,
                ),
                _buildStatItem(
                  'Uptime',
                  _formatUptime(server.uptime),
                  Icons.timer,
                ),
                _buildStatItem('Speed', _getSpeedIndicator(), Icons.speed),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildStatItem(String label, String value, IconData icon) {
    return Column(
      children: [
        Icon(icon, color: Colors.white70, size: 20),
        const SizedBox(height: 5),
        Text(
          value,
          style: const TextStyle(
            color: Colors.white,
            fontSize: 14,
            fontWeight: FontWeight.bold,
          ),
        ),
        Text(
          label,
          style: const TextStyle(color: Colors.white70, fontSize: 12),
        ),
      ],
    );
  }

  String _formatUptime(int seconds) {
    final days = seconds ~/ 86400;
    final hours = (seconds % 86400) ~/ 3600;

    if (days > 0) return '${days}d';
    if (hours > 0) return '${hours}h';
    return '${(seconds % 3600) ~/ 60}m';
  }

  String _getSpeedIndicator() {
    // Simple speed indicator based on user count
    if (server.totalUsers < 10) return 'Fast';
    if (server.totalUsers < 50) return 'Good';
    if (server.totalUsers < 100) return 'Medium';
    return 'Slow';
  }

  String _getCountryFlag(String country) {
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
