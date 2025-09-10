import 'package:flutter/material.dart';
import '../models/vpn_server.dart';
import '../services/vpn_service.dart';
import '../widgets/server_list_item.dart';

class ServerSelectionScreen extends StatefulWidget {
  const ServerSelectionScreen({super.key});

  @override
  State<ServerSelectionScreen> createState() => _ServerSelectionScreenState();
}

class _ServerSelectionScreenState extends State<ServerSelectionScreen> {
  final VpnService _vpnService = VpnService();
  List<VpnServer> _servers = [];
  bool _isLoading = true;
  String _searchQuery = '';
  String _selectedCountry = 'All';
  List<String> _countries = ['All'];

  @override
  void initState() {
    super.initState();
    _loadServers();
  }

  Future<void> _loadServers({bool forceRefresh = false}) async {
    setState(() {
      _isLoading = true;
    });

    try {
      // Load servers from VPN Gate API (use cache unless force refresh)
      List<VpnServer> servers = await _vpnService.fetchVpnServers(
        forceRefresh: forceRefresh,
      );

      if (mounted) {
        setState(() {
          _servers = servers;
          _countries = [
            'All',
            ...servers.map((s) => s.country).toSet().toList()..sort(),
          ];
          _isLoading = false;
        });

        // Show message if no servers found
        if (servers.isEmpty) {
          _showErrorSnackBar(
            'No VPN servers available. Please check your internet connection and try again.',
          );
        }
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _isLoading = false;
        });
        _showErrorSnackBar('Failed to load servers: $e');
      }
    }
  }

  void _showErrorSnackBar(String message) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(message), backgroundColor: Colors.red),
    );
  }

  List<VpnServer> get _filteredServers {
    var filtered = _servers;

    // Filter by country
    if (_selectedCountry != 'All') {
      filtered = filtered
          .where((server) => server.country == _selectedCountry)
          .toList();
    }

    // Filter by search query
    if (_searchQuery.isNotEmpty) {
      filtered = filtered
          .where(
            (server) =>
                server.country.toLowerCase().contains(
                  _searchQuery.toLowerCase(),
                ) ||
                server.ipAddress.contains(_searchQuery) ||
                server.hostName.toLowerCase().contains(
                  _searchQuery.toLowerCase(),
                ),
          )
          .toList();
    }

    return filtered;
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF0A0E27),
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        elevation: 0,
        title: const Text(
          'Select Server',
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
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh, color: Colors.white),
            onPressed: () => _loadServers(forceRefresh: true),
          ),
        ],
      ),
      body: Column(
        children: [
          // Search and Filter Bar
          Padding(
            padding: const EdgeInsets.all(20.0),
            child: Column(
              children: [
                // Search Bar
                Container(
                  decoration: BoxDecoration(
                    color: const Color(0xFF1A1D29),
                    borderRadius: BorderRadius.circular(15),
                    border: Border.all(
                      color: Colors.white.withOpacity(0.1),
                      width: 1,
                    ),
                  ),
                  child: TextField(
                    style: const TextStyle(color: Colors.white),
                    decoration: const InputDecoration(
                      hintText: 'Search servers...',
                      hintStyle: TextStyle(color: Colors.white54),
                      prefixIcon: Icon(Icons.search, color: Colors.white54),
                      border: InputBorder.none,
                      contentPadding: EdgeInsets.symmetric(
                        horizontal: 20,
                        vertical: 15,
                      ),
                    ),
                    onChanged: (value) {
                      setState(() {
                        _searchQuery = value;
                      });
                    },
                  ),
                ),

                const SizedBox(height: 15),

                // Country Filter
                Container(
                  height: 50,
                  decoration: BoxDecoration(
                    color: const Color(0xFF1A1D29),
                    borderRadius: BorderRadius.circular(15),
                    border: Border.all(
                      color: Colors.white.withOpacity(0.1),
                      width: 1,
                    ),
                  ),
                  child: DropdownButtonHideUnderline(
                    child: DropdownButton<String>(
                      value: _selectedCountry,
                      isExpanded: true,
                      dropdownColor: const Color(0xFF1A1D29),
                      style: const TextStyle(color: Colors.white),
                      icon: const Icon(
                        Icons.arrow_drop_down,
                        color: Colors.white54,
                      ),
                      items: _countries.map((String country) {
                        return DropdownMenuItem<String>(
                          value: country,
                          child: Padding(
                            padding: const EdgeInsets.symmetric(horizontal: 20),
                            child: Text(country),
                          ),
                        );
                      }).toList(),
                      onChanged: (String? newValue) {
                        if (newValue != null) {
                          setState(() {
                            _selectedCountry = newValue;
                          });
                        }
                      },
                    ),
                  ),
                ),
              ],
            ),
          ),

          // Server List
          Expanded(
            child: _isLoading
                ? const Center(
                    child: CircularProgressIndicator(
                      valueColor: AlwaysStoppedAnimation<Color>(Colors.blue),
                    ),
                  )
                : _filteredServers.isEmpty
                ? const Center(
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Icon(Icons.search_off, color: Colors.white54, size: 64),
                        SizedBox(height: 16),
                        Text(
                          'No servers found',
                          style: TextStyle(color: Colors.white54, fontSize: 18),
                        ),
                        SizedBox(height: 8),
                        Text(
                          'Try adjusting your search or filter',
                          style: TextStyle(color: Colors.white38, fontSize: 14),
                        ),
                      ],
                    ),
                  )
                : ListView.builder(
                    padding: const EdgeInsets.symmetric(horizontal: 20),
                    itemCount: _filteredServers.length,
                    itemBuilder: (context, index) {
                      final server = _filteredServers[index];
                      return ServerListItem(
                        server: server,
                        onTap: () {
                          Navigator.pop(context, server);
                        },
                      );
                    },
                  ),
          ),
        ],
      ),
    );
  }
}
