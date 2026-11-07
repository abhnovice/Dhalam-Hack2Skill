import 'package:flutter/services.dart';

/// Handles notification permissions and status checks.
class NotificationService {
  static const _channel = MethodChannel('com.dhalam.ap/notifications');

  /// Check if notifications are enabled for this app.
  static Future<bool?> areNotificationsEnabled() async {
    try {
      final result = await _channel.invokeMethod<bool>(
        'areNotificationsEnabled',
      );
      return result;
    } on MissingPluginException {
      return null;
    }
  }

  /// Request or open notification settings.
  static Future<void> enableAlerts() async {
    try {
      await _channel.invokeMethod('enableAlerts');
    } on MissingPluginException {
      // Not available on this platform
    }
  }
}
