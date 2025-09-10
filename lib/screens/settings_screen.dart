import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  bool _autoConnect = false;
  bool _killSwitch = false;
  bool _notifications = true;
  String _protocol = 'UDP';
  int _timeout = 30;

  @override
  void initState() {
    super.initState();
    _loadSettings();
  }

  Future<void> _loadSettings() async {
    final prefs = await SharedPreferences.getInstance();
    setState(() {
      _autoConnect = prefs.getBool('auto_connect') ?? false;
      _killSwitch = prefs.getBool('kill_switch') ?? false;
      _notifications = prefs.getBool('notifications') ?? true;
      _protocol = prefs.getString('protocol') ?? 'UDP';
      _timeout = prefs.getInt('timeout') ?? 30;
    });
  }

  Future<void> _saveSettings() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool('auto_connect', _autoConnect);
    await prefs.setBool('kill_switch', _killSwitch);
    await prefs.setBool('notifications', _notifications);
    await prefs.setString('protocol', _protocol);
    await prefs.setInt('timeout', _timeout);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF0A0E27),
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        elevation: 0,
        title: const Text(
          'Settings',
          style: TextStyle(
            color: Colors.white,
            fontSize: 24,
            fontWeight: FontWeight.bold,
          ),
        ),
        leading: IconButton(
          icon: const Icon(Icons.arrow_back, color: Colors.white),
          onPressed: () => Navigator.pop(context),
        ),
      ),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          // Connection Settings
          _buildSectionHeader('Connection'),
          _buildSettingsCard([
            _buildSwitchTile(
              'Auto Connect',
              'Automatically connect to VPN on app startup',
              _autoConnect,
              (value) {
                setState(() {
                  _autoConnect = value;
                });
                _saveSettings();
              },
              Icons.power_settings_new,
            ),
            _buildDivider(),
            _buildSwitchTile(
              'Kill Switch',
              'Block internet if VPN connection drops',
              _killSwitch,
              (value) {
                setState(() {
                  _killSwitch = value;
                });
                _saveSettings();
              },
              Icons.security,
            ),
            _buildDivider(),
            _buildDropdownTile(
              'Protocol',
              'Connection protocol',
              _protocol,
              ['UDP', 'TCP'],
              (value) {
                setState(() {
                  _protocol = value!;
                });
                _saveSettings();
              },
              Icons.network_check,
            ),
            _buildDivider(),
            _buildSliderTile(
              'Connection Timeout',
              '${_timeout}s',
              _timeout.toDouble(),
              10,
              60,
              (value) {
                setState(() {
                  _timeout = value.round();
                });
                _saveSettings();
              },
              Icons.timer,
            ),
          ]),

          const SizedBox(height: 20),

          // Notification Settings
          _buildSectionHeader('Notifications'),
          _buildSettingsCard([
            _buildSwitchTile(
              'Connection Notifications',
              'Show notifications for connection status',
              _notifications,
              (value) {
                setState(() {
                  _notifications = value;
                });
                _saveSettings();
              },
              Icons.notifications,
            ),
          ]),

          const SizedBox(height: 20),

          // About Section
          _buildSectionHeader('About'),
          _buildSettingsCard([
            _buildInfoTile('Version', '1.0.0', Icons.info),
            _buildDivider(),
            _buildInfoTile('VPN Gate', 'Free VPN Service', Icons.public),
            _buildDivider(),
            _buildInfoTile(
              'Privacy Policy',
              'View our privacy policy',
              Icons.privacy_tip,
              onTap: () {
                _showPrivacyPolicy();
              },
            ),
          ]),

          const SizedBox(height: 20),

          // Danger Zone
          _buildSectionHeader('Danger Zone'),
          _buildSettingsCard([
            _buildActionTile(
              'Clear All Data',
              'Reset all settings and clear cache',
              Icons.delete_forever,
              Colors.red,
              () {
                _showClearDataDialog();
              },
            ),
          ]),
        ],
      ),
    );
  }

  Widget _buildSectionHeader(String title) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12, left: 4),
      child: Text(
        title,
        style: const TextStyle(
          color: Colors.white70,
          fontSize: 16,
          fontWeight: FontWeight.bold,
        ),
      ),
    );
  }

  Widget _buildSettingsCard(List<Widget> children) {
    return Container(
      decoration: BoxDecoration(
        color: const Color(0xFF1A1D29),
        borderRadius: BorderRadius.circular(15),
        border: Border.all(color: Colors.white.withOpacity(0.1), width: 1),
      ),
      child: Column(children: children),
    );
  }

  Widget _buildSwitchTile(
    String title,
    String subtitle,
    bool value,
    ValueChanged<bool> onChanged,
    IconData icon,
  ) {
    return ListTile(
      leading: Icon(icon, color: Colors.white70),
      title: Text(title, style: const TextStyle(color: Colors.white)),
      subtitle: Text(subtitle, style: const TextStyle(color: Colors.white54)),
      trailing: Switch(
        value: value,
        onChanged: onChanged,
        activeColor: Colors.blue,
      ),
    );
  }

  Widget _buildDropdownTile(
    String title,
    String subtitle,
    String value,
    List<String> items,
    ValueChanged<String?> onChanged,
    IconData icon,
  ) {
    return ListTile(
      leading: Icon(icon, color: Colors.white70),
      title: Text(title, style: const TextStyle(color: Colors.white)),
      subtitle: Text(subtitle, style: const TextStyle(color: Colors.white54)),
      trailing: DropdownButton<String>(
        value: value,
        dropdownColor: const Color(0xFF1A1D29),
        style: const TextStyle(color: Colors.white),
        underline: Container(),
        items: items.map((String item) {
          return DropdownMenuItem<String>(value: item, child: Text(item));
        }).toList(),
        onChanged: onChanged,
      ),
    );
  }

  Widget _buildSliderTile(
    String title,
    String value,
    double currentValue,
    double min,
    double max,
    ValueChanged<double> onChanged,
    IconData icon,
  ) {
    return ListTile(
      leading: Icon(icon, color: Colors.white70),
      title: Text(title, style: const TextStyle(color: Colors.white)),
      subtitle: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(value, style: const TextStyle(color: Colors.white54)),
          Slider(
            value: currentValue,
            min: min,
            max: max,
            divisions: (max - min).round(),
            activeColor: Colors.blue,
            inactiveColor: Colors.white24,
            onChanged: onChanged,
          ),
        ],
      ),
    );
  }

  Widget _buildInfoTile(
    String title,
    String subtitle,
    IconData icon, {
    VoidCallback? onTap,
  }) {
    return ListTile(
      leading: Icon(icon, color: Colors.white70),
      title: Text(title, style: const TextStyle(color: Colors.white)),
      subtitle: Text(subtitle, style: const TextStyle(color: Colors.white54)),
      trailing: onTap != null
          ? const Icon(Icons.arrow_forward_ios, color: Colors.white54, size: 16)
          : null,
      onTap: onTap,
    );
  }

  Widget _buildActionTile(
    String title,
    String subtitle,
    IconData icon,
    Color color,
    VoidCallback onTap,
  ) {
    return ListTile(
      leading: Icon(icon, color: color),
      title: Text(title, style: TextStyle(color: color)),
      subtitle: Text(subtitle, style: const TextStyle(color: Colors.white54)),
      trailing: const Icon(
        Icons.arrow_forward_ios,
        color: Colors.white54,
        size: 16,
      ),
      onTap: onTap,
    );
  }

  Widget _buildDivider() {
    return Divider(color: Colors.white.withOpacity(0.1), height: 1, indent: 60);
  }

  void _showPrivacyPolicy() {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: const Color(0xFF1A1D29),
        title: const Text(
          'Privacy Policy',
          style: TextStyle(color: Colors.white),
        ),
        content: const Text(
          'VPN Gate is a free VPN service provided by the University of Tsukuba. '
          'This app connects to their public servers. We do not collect or store '
          'any personal data. All connections are handled by VPN Gate servers.',
          style: TextStyle(color: Colors.white70),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('OK', style: TextStyle(color: Colors.blue)),
          ),
        ],
      ),
    );
  }

  void _showClearDataDialog() {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: const Color(0xFF1A1D29),
        title: const Text(
          'Clear All Data',
          style: TextStyle(color: Colors.white),
        ),
        content: const Text(
          'This will reset all settings and clear cached data. '
          'This action cannot be undone.',
          style: TextStyle(color: Colors.white70),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text(
              'Cancel',
              style: TextStyle(color: Colors.white70),
            ),
          ),
          TextButton(
            onPressed: () async {
              Navigator.pop(context);
              await _clearAllData();
              _loadSettings();
            },
            child: const Text('Clear', style: TextStyle(color: Colors.red)),
          ),
        ],
      ),
    );
  }

  Future<void> _clearAllData() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.clear();

    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('All data cleared successfully'),
          backgroundColor: Colors.green,
        ),
      );
    }
  }
}
