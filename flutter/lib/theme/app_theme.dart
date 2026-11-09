import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

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

  /// Noto Sans (Google Fonts) with the matching Noto script families as
  /// fallbacks, so Hindi, Marathi, Bengali, Tamil and Telugu answers render in
  /// the same typeface family instead of an arbitrary system font.
  static TextTheme _buildTextTheme(TextTheme base) {
    final fallback = [
      GoogleFonts.notoSansDevanagari().fontFamily!,
      GoogleFonts.notoSansBengali().fontFamily!,
      GoogleFonts.notoSansTamil().fontFamily!,
      GoogleFonts.notoSansTelugu().fontFamily!,
    ];
    final themed = GoogleFonts.notoSansTextTheme(base);
    TextStyle? withFallback(TextStyle? style) =>
        style?.copyWith(fontFamilyFallback: fallback);
    return themed.copyWith(
      displayLarge: withFallback(themed.displayLarge),
      displayMedium: withFallback(themed.displayMedium),
      displaySmall: withFallback(themed.displaySmall),
      headlineLarge: withFallback(themed.headlineLarge),
      headlineMedium: withFallback(themed.headlineMedium),
      headlineSmall: withFallback(themed.headlineSmall),
      titleLarge: withFallback(themed.titleLarge),
      titleMedium: withFallback(themed.titleMedium),
      titleSmall: withFallback(themed.titleSmall),
      bodyLarge: withFallback(themed.bodyLarge),
      bodyMedium: withFallback(themed.bodyMedium),
      bodySmall: withFallback(themed.bodySmall),
      labelLarge: withFallback(themed.labelLarge),
      labelMedium: withFallback(themed.labelMedium),
      labelSmall: withFallback(themed.labelSmall),
    );
  }

  static ThemeData get lightTheme {
    final base = ThemeData.light(useMaterial3: true);
    return base.copyWith(
      colorScheme: const ColorScheme.light(
        secondary: Color(0xFF48635E),
        onSecondary: Colors.white,
        tertiary: Color(0xFF48635E),
        onTertiary: Colors.white,
        tertiaryContainer: Color(0xFFE0EAE7),
        onTertiaryContainer: Color(0xFF20332E),
        outline: Color(0xFF70847D),
        outlineVariant: Color(0xFFD4E0DB),
        surfaceTint: Colors.transparent,
        surfaceContainerLowest: Colors.white,
        surfaceContainerLow: Color(0xFFF4F8F6),
        surfaceContainer: Color(0xFFEEF4F1),
        surfaceContainerHigh: Color(0xFFE7EFEB),
        surfaceContainerHighest: Color(0xFFDFE9E4),
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
        secondary: Color(0xFFB4CDC3),
        onSecondary: Color(0xFF20332E),
        tertiary: Color(0xFFB4CDC3),
        onTertiary: Color(0xFF20332E),
        tertiaryContainer: Color(0xFF30463D),
        onTertiaryContainer: Color(0xFFD2F0E6),
        outline: Color(0xFF8A9E95),
        outlineVariant: Color(0xFF3C5147),
        surfaceTint: Colors.transparent,
        surfaceContainerLowest: Color(0xFF0C1410),
        surfaceContainerLow: Color(0xFF17231D),
        surfaceContainer: Color(0xFF1D2C24),
        surfaceContainerHigh: Color(0xFF26372D),
        surfaceContainerHighest: Color(0xFF304339),
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
