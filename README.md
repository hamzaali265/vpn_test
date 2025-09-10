# VPN Gate - Flutter VPN App

A modern, beautiful VPN client app built with Flutter that connects to VPN Gate servers for free VPN access.

## Features

- 🌍 **Global Server Selection**: Choose from hundreds of VPN Gate servers worldwide
- 🔒 **Secure Connection**: Connect to free VPN servers provided by VPN Gate
- 📱 **Modern UI**: Beautiful, dark-themed interface with smooth animations
- ⚡ **Fast Connection**: Quick server selection and connection process
- 📊 **Server Stats**: View server information including user count, uptime, and speed
- ⚙️ **Settings**: Customizable connection preferences and app settings
- 🔄 **Auto-reconnect**: Automatic connection management
- 📱 **Cross-platform**: Works on Android and iOS

## Screenshots

The app features a sleek dark theme with:
- Main connection screen with animated connection button
- Server selection with filtering and search
- Real-time connection status monitoring
- Comprehensive settings panel

## Getting Started

### Prerequisites

- Flutter SDK (3.8.1 or higher)
- Android Studio / Xcode for platform-specific builds
- Android device/emulator or iOS device/simulator

### Installation

1. Clone the repository:
```bash
git clone <repository-url>
cd vpn_test
```

2. Install dependencies:
```bash
flutter pub get
```

3. Run the app:
```bash
flutter run
```

## Architecture

### Core Components

- **VpnService**: Handles VPN Gate API integration and connection management
- **VpnServer**: Data model for server information
- **VpnConnectionInfo**: Connection status and information tracking
- **HomeScreen**: Main app interface with connection controls
- **ServerSelectionScreen**: Server browsing and selection
- **SettingsScreen**: App configuration and preferences

### Key Features

1. **VPN Gate Integration**: Fetches real-time server list from VPN Gate API
2. **Connection Management**: Simulates VPN connection with status tracking
3. **Server Filtering**: Search and filter servers by country and performance
4. **Persistent Settings**: Saves user preferences and last connected server
5. **Responsive UI**: Adapts to different screen sizes and orientations

## VPN Gate Service

This app uses the free VPN Gate service provided by the University of Tsukuba. VPN Gate is a volunteer-run service that provides free VPN access through public servers.

### Important Notes

- This is a demonstration app that simulates VPN connections
- For actual VPN functionality, you would need to implement platform-specific VPN APIs
- VPN Gate servers are provided by volunteers and may vary in quality and availability
- Always use VPN services responsibly and in accordance with local laws

## Permissions

### Android
- `INTERNET`: For fetching server data
- `ACCESS_NETWORK_STATE`: For monitoring network connectivity
- `BIND_VPN_SERVICE`: For VPN service binding (future implementation)

### iOS
- Network access permissions for server communication
- VPN configuration permissions (future implementation)

## Development

### Project Structure

```
lib/
├── models/           # Data models
├── services/         # Business logic and API services
├── screens/          # UI screens
├── widgets/          # Reusable UI components
└── main.dart         # App entry point
```

### Dependencies

- `http`: For API communication
- `shared_preferences`: For local data storage
- `connectivity_plus`: For network status monitoring
- `permission_handler`: For permission management
- `flutter_secure_storage`: For secure data storage
- `animated_text_kit`: For text animations
- `lottie`: For advanced animations

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

## License

This project is for educational and demonstration purposes. Please respect the terms of service of VPN Gate and use responsibly.

## Disclaimer

This app is a demonstration of VPN client UI and functionality. It does not provide actual VPN services but simulates the user experience. For production use, you would need to implement actual VPN protocols and platform-specific APIs.

# vpn_test
