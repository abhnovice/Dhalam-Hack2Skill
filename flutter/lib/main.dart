import 'dart:ui' as ui;
import 'package:flutter/material.dart';
import 'l10n/app_language.dart';
import 'l10n/chat_language.dart';
import 'screens/triage_screen.dart';
import 'features/security_tools.dart';
import 'services/security_scanner.dart';
import 'theme/app_theme.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  // The device language is the starting point; any app bar can change it.
  appLanguage.value = languageForCode(
    ui.PlatformDispatcher.instance.locale.languageCode,
  );
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
