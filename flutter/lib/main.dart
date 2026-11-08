import 'package:flutter/material.dart';
import 'screens/triage_screen.dart';
import 'features/security_tools.dart';
import 'services/security_scanner.dart';
import 'theme/app_theme.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const DhalamApp());
}

class DhalamApp extends StatelessWidget {
  const DhalamApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Dhalam',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.lightTheme,
      darkTheme: AppTheme.darkTheme,
      themeMode: ThemeMode.system,
      home: TriageScreen(
        journal: SecurityJournal(),
        ios: SecurityScanner.isIOS,
      ),
    );
  }
}
