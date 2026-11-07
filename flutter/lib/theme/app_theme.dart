import 'package:flutter/material.dart';

class AppTheme {
  // Light colors
  static const _lightPrimary = Color(0xFF006B60);
  static const _lightOnPrimary = Colors.white;
  static const _lightPrimaryContainer = Color(0xFFB6F1E5);
  static const _lightOnPrimaryContainer = Color(0xFF002822);
  static const _lightSecondaryContainer = Color(0xFFD2EDE5);
  static const _lightOnSecondaryContainer = Color(0xFF153D34);
  static const _lightBackground = Color(0xFFF4F9F7);
  static const _lightSurface = Colors.white;
  static const _lightOnSurface = Color(0xFF14211D);
  static const _lightOnSurfaceVariant = Color(0xFF475952);

  // Dark colors
  static const _darkPrimary = Color(0xFF8FE7D6);
  static const _darkOnPrimary = Color(0xFF00382F);
  static const _darkPrimaryContainer = Color(0xFF16483F);
  static const _darkOnPrimaryContainer = Color(0xFFB1F4E6);
  static const _darkSecondaryContainer = Color(0xFF25483E);
  static const _darkOnSecondaryContainer = Color(0xFFD2F0E6);
  static const _darkBackground = Color(0xFF101715);
  static const _darkSurface = Color(0xFF1A2521);
  static const _darkOnSurface = Color(0xFFE5EFEB);
  static const _darkOnSurfaceVariant = Color(0xFFB8CCC3);

  static TextTheme _buildTextTheme(TextTheme base) {
    const fontFamily = 'sans-serif';
    return base.copyWith(
      headlineSmall: base.headlineSmall?.copyWith(fontFamily: fontFamily),
      titleMedium: base.titleMedium?.copyWith(fontFamily: fontFamily),
      bodyLarge: base.bodyLarge?.copyWith(fontFamily: fontFamily),
      bodyMedium: base.bodyMedium?.copyWith(fontFamily: fontFamily),
      bodySmall: base.bodySmall?.copyWith(fontFamily: fontFamily),
      labelLarge: base.labelLarge?.copyWith(fontFamily: fontFamily),
      labelMedium: base.labelMedium?.copyWith(fontFamily: fontFamily),
      labelSmall: base.labelSmall?.copyWith(fontFamily: fontFamily),
    );
  }

  static ThemeData get lightTheme {
    final base = ThemeData.light(useMaterial3: true);
    return base.copyWith(
      colorScheme: const ColorScheme.light(
        primary: _lightPrimary,
        onPrimary: _lightOnPrimary,
        primaryContainer: _lightPrimaryContainer,
        onPrimaryContainer: _lightOnPrimaryContainer,
        secondaryContainer: _lightSecondaryContainer,
        onSecondaryContainer: _lightOnSecondaryContainer,
        surface: _lightSurface,
        onSurface: _lightOnSurface,
        onSurfaceVariant: _lightOnSurfaceVariant,
      ),
      scaffoldBackgroundColor: _lightBackground,
      textTheme: _buildTextTheme(base.textTheme),
    );
  }

  static ThemeData get darkTheme {
    final base = ThemeData.dark(useMaterial3: true);
    return base.copyWith(
      colorScheme: const ColorScheme.dark(
        primary: _darkPrimary,
        onPrimary: _darkOnPrimary,
        primaryContainer: _darkPrimaryContainer,
        onPrimaryContainer: _darkOnPrimaryContainer,
        secondaryContainer: _darkSecondaryContainer,
        onSecondaryContainer: _darkOnSecondaryContainer,
        surface: _darkSurface,
        onSurface: _darkOnSurface,
        onSurfaceVariant: _darkOnSurfaceVariant,
      ),
      scaffoldBackgroundColor: _darkBackground,
      textTheme: _buildTextTheme(base.textTheme),
    );
  }
}
