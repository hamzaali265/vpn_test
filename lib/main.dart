import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'screens/simple_home_screen.dart';
import 'services/vpn_service.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // Set system UI overlay style
  SystemChrome.setSystemUIOverlayStyle(
    const SystemUiOverlayStyle(
      statusBarColor: Colors.transparent,
      statusBarIconBrightness: Brightness.light,
      systemNavigationBarColor: Color(0xFF0A0E27),
      systemNavigationBarIconBrightness: Brightness.light,
    ),
  );

  // Initialize VPN service
  await VpnService().initialize();

  runApp(const VpnGateApp());
}

class VpnGateApp extends StatelessWidget {
  const VpnGateApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'VPN Gate',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        primarySwatch: Colors.blue,
        scaffoldBackgroundColor: const Color(0xFF0A0E27),
        appBarTheme: const AppBarTheme(
          backgroundColor: Colors.transparent,
          elevation: 0,
          systemOverlayStyle: SystemUiOverlayStyle.light,
        ),
        colorScheme: const ColorScheme.dark(
          primary: Colors.blue,
          secondary: Colors.blueAccent,
          surface: Color(0xFF1A1D29),
          background: Color(0xFF0A0E27),
        ),
        useMaterial3: true,
      ),
      home: const SimpleHomeScreen(),
    );
  }
}
