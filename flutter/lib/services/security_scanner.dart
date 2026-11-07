import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import '../models/security_models.dart';

/// Shared contract implemented by Kotlin on Android and Swift on iOS.
class SecurityScanner {
  static const _channel = MethodChannel('com.dhalam.ap/security');
  static bool get isAndroid =>
      !kIsWeb && defaultTargetPlatform == TargetPlatform.android;
  static bool get isIOS =>
      !kIsWeb && defaultTargetPlatform == TargetPlatform.iOS;

  static Future<SecurityObservation> scan() async {
    if (!isAndroid && !isIOS) {
      throw SecurityException(
        'Security checks are supported on Android and iOS.',
      );
    }
    try {
      final result = await _channel.invokeMethod('collectSecurityObservation');
      if (result is! Map) {
        throw SecurityException('The device returned an invalid scan result.');
      }
      return SecurityObservation.fromMap(Map<String, dynamic>.from(result));
    } on MissingPluginException {
      throw SecurityException('Security checks are unavailable in this build.');
    } on PlatformException catch (e) {
      throw SecurityException('Scan failed: ${e.message ?? e.code}');
    }
  }

  static Future<void> openAccessibilitySettings() =>
      _openSettings('openAccessibilitySettings', isAndroid);

  static Future<void> openDeveloperOptions() =>
      _openSettings('openDeveloperOptions', isAndroid);

  static Future<void> openAppSettings() =>
      _openSettings('openAppSettings', isAndroid || isIOS);

  static Future<void> _openSettings(String method, bool supported) async {
    if (!supported) {
      throw SecurityException(
        'These settings are unavailable on this platform.',
      );
    }
    try {
      await _channel.invokeMethod<void>(method);
    } on MissingPluginException {
      throw SecurityException('Settings are unavailable in this build.');
    } on PlatformException catch (e) {
      throw SecurityException(e.message ?? 'Could not open settings.');
    }
  }
}

class SecurityException implements Exception {
  final String message;
  SecurityException(this.message);
  @override
  String toString() => message;
}
