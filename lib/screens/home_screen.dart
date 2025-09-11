// import 'package:flutter/material.dart';
// import 'package:flutter/foundation.dart';
// import '../models/connection_status.dart';
// import '../models/vpn_server.dart';
// import '../services/vpn_service.dart';
// import '../widgets/connection_button.dart';
// import '../widgets/status_card.dart';
// import '../widgets/server_info_card.dart';
// import 'server_selection_screen.dart';
// import 'settings_screen.dart';

// class HomeScreen extends StatefulWidget {
//   const HomeScreen({super.key});

//   @override
//   State<HomeScreen> createState() => _HomeScreenState();
// }

// class _HomeScreenState extends State<HomeScreen> with TickerProviderStateMixin {
//   final VpnService _vpnService = VpnService();
//   late AnimationController _pulseController;
//   late Animation<double> _pulseAnimation;

//   VpnConnectionInfo _connectionInfo = VpnConnectionInfo(
//     status: VpnConnectionStatus.disconnected,
//   );

//   VpnServer? _selectedServer;
//   String? _currentIP;
//   String? _originalIP;
//   bool _isLoadingIP = false;

//   @override
//   void initState() {
//     super.initState();
//     _initializeAnimations();
//     _loadLastConnectedServer();
//     _listenToConnectionStatus();
//     _saveOriginalIP();
//     _loadCurrentIP();
//   }

//   void _initializeAnimations() {
//     _pulseController = AnimationController(
//       duration: const Duration(seconds: 2),
//       vsync: this,
//     );

//     _pulseAnimation = Tween<double>(begin: 0.8, end: 1.2).animate(
//       CurvedAnimation(parent: _pulseController, curve: Curves.easeInOut),
//     );

//     _pulseController.repeat(reverse: true);
//   }

//   void _loadLastConnectedServer() async {
//     final server = await _vpnService.getLastConnectedServer();
//     if (mounted) {
//       setState(() {
//         _selectedServer = server;
//       });
//     }
//   }

//   void _loadCurrentIP() async {
//     if (_isLoadingIP) return;

//     setState(() {
//       _isLoadingIP = true;
//     });

//     try {
//       final ip = await _vpnService.getCurrentIP();
//       if (mounted) {
//         setState(() {
//           _currentIP = ip;
//           _isLoadingIP = false;
//         });
//       }
//     } catch (e) {
//       if (mounted) {
//         setState(() {
//           _isLoadingIP = false;
//         });
//       }
//     }
//   }

//   void _saveOriginalIP() async {
//     if (_originalIP == null) {
//       final ip = await _vpnService.getCurrentIP();
//       if (mounted) {
//         setState(() {
//           _originalIP = ip;
//         });
//       }
//     }
//   }

//   void _listenToConnectionStatus() {
//     _vpnService.connectionStream.listen((status) {
//       if (mounted) {
//         setState(() {
//           _connectionInfo = status;
//         });

//         if (status.isConnected) {
//           _pulseController.forward();
//           // Check IP after connection
//           Future.delayed(const Duration(seconds: 5), () {
//             _loadCurrentIP();
//           });
//         } else {
//           _pulseController.stop();
//           // Check IP after disconnection
//           if (status.status == VpnConnectionStatus.disconnected) {
//             Future.delayed(const Duration(seconds: 2), () {
//               _loadCurrentIP();
//             });
//           }
//         }
//       }
//     });
//   }

//   @override
//   void dispose() {
//     _pulseController.dispose();
//     super.dispose();
//   }

//   @override
//   Widget build(BuildContext context) {
//     return Scaffold(
//       backgroundColor: const Color(0xFF0A0E27),
//       appBar: AppBar(
//         backgroundColor: Colors.transparent,
//         elevation: 0,
//         title: const Text(
//           'VPN Gate',
//           style: TextStyle(
//             color: Colors.white,
//             fontSize: 24,
//             fontWeight: FontWeight.bold,
//           ),
//         ),
//         actions: [
//           IconButton(
//             icon: const Icon(Icons.settings, color: Colors.white),
//             onPressed: () {
//               Navigator.push(
//                 context,
//                 MaterialPageRoute(builder: (context) => const SettingsScreen()),
//               );
//             },
//           ),
//         ],
//       ),
//       body: SafeArea(
//         child: SingleChildScrollView(
//           padding: const EdgeInsets.all(20.0),
//           child: Column(
//             children: [
//               // Status Card
//               StatusCard(connectionInfo: _connectionInfo),

//               const SizedBox(height: 20),

//               // IP Display Card
//               _buildIPCard(),

//               const SizedBox(height: 20),

//               // Connection Stats Card
//               _buildConnectionStatsCard(),

//               const SizedBox(height: 30),

//               // Connection Button
//               AnimatedBuilder(
//                 animation: _pulseAnimation,
//                 builder: (context, child) {
//                   return Transform.scale(
//                     scale: _connectionInfo.isConnected
//                         ? _pulseAnimation.value
//                         : 1.0,
//                     child: ConnectionButton(
//                       connectionInfo: _connectionInfo,
//                       onConnect: _handleConnect,
//                       onDisconnect: _handleDisconnect,
//                     ),
//                   );
//                 },
//               ),

//               const SizedBox(height: 30),

//               // Server Info Card
//               if (_selectedServer != null)
//                 ServerInfoCard(server: _selectedServer!, onTap: _selectServer)
//               else
//                 _buildSelectServerCard(),

//               const SizedBox(height: 30),

//               // Connection Stats
//               if (_connectionInfo.isConnected) _buildConnectionStats(),
//             ],
//           ),
//         ),
//       ),
//     );
//   }

//   Widget _buildSelectServerCard() {
//     return GestureDetector(
//       onTap: _selectServer,
//       child: Container(
//         width: double.infinity,
//         padding: const EdgeInsets.all(20),
//         decoration: BoxDecoration(
//           gradient: const LinearGradient(
//             colors: [Color(0xFF1E3A8A), Color(0xFF3B82F6)],
//             begin: Alignment.topLeft,
//             end: Alignment.bottomRight,
//           ),
//           borderRadius: BorderRadius.circular(20),
//           boxShadow: [
//             BoxShadow(
//               color: Colors.blue.withOpacity(0.3),
//               blurRadius: 20,
//               offset: const Offset(0, 10),
//             ),
//           ],
//         ),
//         child: Column(
//           children: [
//             const Icon(Icons.public, color: Colors.white, size: 40),
//             const SizedBox(height: 10),
//             const Text(
//               'Select Server',
//               style: TextStyle(
//                 color: Colors.white,
//                 fontSize: 18,
//                 fontWeight: FontWeight.bold,
//               ),
//             ),
//             const SizedBox(height: 5),
//             const Text(
//               'Choose from VPN Gate servers',
//               style: TextStyle(color: Colors.white70, fontSize: 14),
//             ),
//             const SizedBox(height: 10),
//             const Icon(
//               Icons.arrow_forward_ios,
//               color: Colors.white70,
//               size: 16,
//             ),
//           ],
//         ),
//       ),
//     );
//   }

//   Widget _buildConnectionStats() {
//     return Container(
//       width: double.infinity,
//       padding: const EdgeInsets.all(20),
//       decoration: BoxDecoration(
//         color: const Color(0xFF1A1D29),
//         borderRadius: BorderRadius.circular(15),
//         border: Border.all(color: Colors.green.withOpacity(0.3), width: 1),
//       ),
//       child: Column(
//         children: [
//           Row(
//             mainAxisAlignment: MainAxisAlignment.spaceAround,
//             children: [
//               _buildStatItem(
//                 'Connected',
//                 _connectionInfo.connectedDuration != null
//                     ? _formatDuration(_connectionInfo.connectedDuration!)
//                     : '0s',
//                 Icons.timer,
//                 Colors.green,
//               ),
//               _buildStatItem(
//                 'Server',
//                 _connectionInfo.country ?? 'Unknown',
//                 Icons.location_on,
//                 Colors.blue,
//               ),
//             ],
//           ),
//         ],
//       ),
//     );
//   }

//   Widget _buildStatItem(
//     String label,
//     String value,
//     IconData icon,
//     Color color,
//   ) {
//     return Column(
//       children: [
//         Icon(icon, color: color, size: 24),
//         const SizedBox(height: 8),
//         Text(
//           value,
//           style: TextStyle(
//             color: color,
//             fontSize: 16,
//             fontWeight: FontWeight.bold,
//           ),
//         ),
//         Text(
//           label,
//           style: const TextStyle(color: Colors.white70, fontSize: 12),
//         ),
//       ],
//     );
//   }

//   String _formatDuration(Duration duration) {
//     final hours = duration.inHours;
//     final minutes = duration.inMinutes % 60;
//     final seconds = duration.inSeconds % 60;

//     if (hours > 0) {
//       return '${hours}h ${minutes}m';
//     } else if (minutes > 0) {
//       return '${minutes}m ${seconds}s';
//     } else {
//       return '${seconds}s';
//     }
//   }

//   void _handleConnect() async {
//     if (_selectedServer == null) {
//       _selectServer();
//       return;
//     }

//     // Check if already connecting or connected
//     if (_vpnService.isConnecting) {
//       if (kDebugMode) {
//         print('Connection already in progress');
//       }
//       return;
//     }

//     if (_vpnService.isConnected) {
//       // Already connected, do nothing
//       return;
//     } else {
//       // Connect to selected server
//       await _vpnService.connectToServer(_selectedServer!);
//     }
//   }

//   void _handleDisconnect() async {
//     await _vpnService.disconnect();
//   }

//   void _selectServer() async {
//     final server = await Navigator.push<VpnServer>(
//       context,
//       MaterialPageRoute(builder: (context) => const ServerSelectionScreen()),
//     );

//     if (server != null && mounted) {
//       setState(() {
//         _selectedServer = server;
//       });
//     }
//   }

//   Widget _buildIPCard() {
//     return Card(
//       elevation: 8,
//       shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
//       child: Container(
//         padding: const EdgeInsets.all(20),
//         decoration: BoxDecoration(
//           borderRadius: BorderRadius.circular(16),
//           gradient: const LinearGradient(
//             colors: [Color(0xFF2C3E50), Color(0xFF34495E)],
//             begin: Alignment.topLeft,
//             end: Alignment.bottomRight,
//           ),
//         ),
//         child: Column(
//           crossAxisAlignment: CrossAxisAlignment.start,
//           children: [
//             Row(
//               children: [
//                 const Icon(Icons.public, color: Colors.white, size: 24),
//                 const SizedBox(width: 12),
//                 const Text(
//                   'Your IP Address',
//                   style: TextStyle(
//                     color: Colors.white,
//                     fontSize: 18,
//                     fontWeight: FontWeight.bold,
//                   ),
//                 ),
//                 const Spacer(),
//                 IconButton(
//                   icon: _isLoadingIP
//                       ? const SizedBox(
//                           width: 20,
//                           height: 20,
//                           child: CircularProgressIndicator(
//                             strokeWidth: 2,
//                             valueColor: AlwaysStoppedAnimation<Color>(
//                               Colors.white,
//                             ),
//                           ),
//                         )
//                       : const Icon(Icons.refresh, color: Colors.white),
//                   onPressed: _isLoadingIP ? null : _loadCurrentIP,
//                 ),
//               ],
//             ),
//             const SizedBox(height: 16),
//             if (_currentIP != null) ...[
//               Container(
//                 padding: const EdgeInsets.all(12),
//                 decoration: BoxDecoration(
//                   color: Colors.white.withOpacity(0.1),
//                   borderRadius: BorderRadius.circular(8),
//                 ),
//                 child: Row(
//                   children: [
//                     const Icon(Icons.computer, color: Colors.white70, size: 20),
//                     const SizedBox(width: 8),
//                     Expanded(
//                       child: Text(
//                         _currentIP!,
//                         style: const TextStyle(
//                           color: Colors.white,
//                           fontSize: 16,
//                           fontFamily: 'monospace',
//                         ),
//                       ),
//                     ),
//                   ],
//                 ),
//               ),
//               if (_originalIP != null && _originalIP != _currentIP) ...[
//                 const SizedBox(height: 8),
//                 Container(
//                   padding: const EdgeInsets.all(8),
//                   decoration: BoxDecoration(
//                     color: Colors.green.withOpacity(0.2),
//                     borderRadius: BorderRadius.circular(6),
//                   ),
//                   child: Row(
//                     children: [
//                       const Icon(
//                         Icons.check_circle,
//                         color: Colors.green,
//                         size: 16,
//                       ),
//                       const SizedBox(width: 8),
//                       const Text(
//                         'IP Changed Successfully!',
//                         style: TextStyle(
//                           color: Colors.green,
//                           fontSize: 12,
//                           fontWeight: FontWeight.bold,
//                         ),
//                       ),
//                     ],
//                   ),
//                 ),
//               ],
//             ] else ...[
//               Container(
//                 padding: const EdgeInsets.all(12),
//                 decoration: BoxDecoration(
//                   color: Colors.white.withOpacity(0.1),
//                   borderRadius: BorderRadius.circular(8),
//                 ),
//                 child: const Row(
//                   children: [
//                     Icon(Icons.help_outline, color: Colors.white70, size: 20),
//                     SizedBox(width: 8),
//                     Text(
//                       'Tap refresh to detect IP',
//                       style: TextStyle(color: Colors.white70, fontSize: 16),
//                     ),
//                   ],
//                 ),
//               ),
//             ],
//           ],
//         ),
//       ),
//     );
//   }

//   Widget _buildConnectionStatsCard() {
//     final stats = _vpnService.getConnectionStats();

//     return Card(
//       elevation: 8,
//       shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
//       child: Container(
//         padding: const EdgeInsets.all(20),
//         decoration: BoxDecoration(
//           borderRadius: BorderRadius.circular(16),
//           gradient: const LinearGradient(
//             colors: [Color(0xFF1E3C72), Color(0xFF2A5298)],
//             begin: Alignment.topLeft,
//             end: Alignment.bottomRight,
//           ),
//         ),
//         child: Column(
//           crossAxisAlignment: CrossAxisAlignment.start,
//           children: [
//             Row(
//               children: [
//                 const Icon(Icons.analytics, color: Colors.white, size: 24),
//                 const SizedBox(width: 12),
//                 const Text(
//                   'Connection Statistics',
//                   style: TextStyle(
//                     color: Colors.white,
//                     fontSize: 18,
//                     fontWeight: FontWeight.bold,
//                   ),
//                 ),
//               ],
//             ),
//             const SizedBox(height: 16),
//             Row(
//               children: [
//                 Expanded(
//                   child: _buildStatItem(
//                     'Status',
//                     stats['status'].toString().split('.').last,
//                     Icons.info_outline,
//                     Colors.white,
//                   ),
//                 ),
//                 Expanded(
//                   child: _buildStatItem(
//                     'Duration',
//                     _formatDuration(Duration(seconds: stats['duration'])),
//                     Icons.timer,
//                     Colors.white,
//                   ),
//                 ),
//               ],
//             ),
//             const SizedBox(height: 12),
//             Row(
//               children: [
//                 Expanded(
//                   child: _buildStatItem(
//                     'Connecting',
//                     stats['isConnecting'] ? 'Yes' : 'No',
//                     Icons.sync,
//                     Colors.white,
//                   ),
//                 ),
//                 Expanded(
//                   child: _buildStatItem(
//                     'Disconnecting',
//                     stats['isDisconnecting'] ? 'Yes' : 'No',
//                     Icons.stop_circle_outlined,
//                     Colors.white,
//                   ),
//                 ),
//               ],
//             ),
//             if (stats['errorMessage'] != null) ...[
//               const SizedBox(height: 12),
//               Container(
//                 padding: const EdgeInsets.all(8),
//                 decoration: BoxDecoration(
//                   color: Colors.red.withOpacity(0.2),
//                   borderRadius: BorderRadius.circular(6),
//                 ),
//                 child: Row(
//                   children: [
//                     const Icon(Icons.error, color: Colors.red, size: 16),
//                     const SizedBox(width: 8),
//                     Expanded(
//                       child: Text(
//                         stats['errorMessage'],
//                         style: const TextStyle(color: Colors.red, fontSize: 12),
//                       ),
//                     ),
//                   ],
//                 ),
//               ),
//             ],
//           ],
//         ),
//       ),
//     );
//   }
// }
