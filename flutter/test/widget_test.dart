import 'dart:async';
import 'package:flutter/material.dart';
import 'package:dhalam/features/security_tools.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:dhalam/main.dart';
import 'package:dhalam/screens/triage_screen.dart';
import 'package:dhalam/screens/security_tools_screen.dart';
import 'package:dhalam/services/security_scanner.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const security = MethodChannel('com.dhalam.ap/security');
  const notifications = MethodChannel('com.dhalam.ap/notifications');
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;

  setUp(() {
    debugDefaultTargetPlatformOverride = TargetPlatform.iOS;
    messenger.setMockMethodCallHandler(
      toolsChannel,
      (call) async => call.method == 'captureStatus' ? 'Inactive' : null,
    );
    messenger.setMockMethodCallHandler(notifications, (call) async => false);
  });
  tearDown(() {
    debugDefaultTargetPlatformOverride = null;
    messenger.setMockMethodCallHandler(toolsChannel, null);
    messenger.setMockMethodCallHandler(security, null);
    messenger.setMockMethodCallHandler(notifications, null);
  });

  testWidgets('iOS checks show limitations and hide Android settings', (
    tester,
  ) async {
    messenger.setMockMethodCallHandler(
      security,
      (call) async => <Object?, Object?>{
        'observedAt': 1234,
        'deviceSignals': <Object?>[
          <Object?, Object?>{'label': 'Device lock', 'status': 'Configured'},
        ],
        'services': null,
        'permissionInventory': null,
        'developerSignals': <Object?>[
          <Object?, Object?>{'label': 'Operating system', 'status': 'iOS'},
        ],
      },
    );
    await tester.pumpWidget(const DhalamApp());
    await tester.pumpAndSettle();
    await tester.tap(find.text('Run security check'));
    await tester.pumpAndSettle();
    await tester.scrollUntilVisible(find.text('Device lock'), 200);
    expect(find.text('Configured'), findsOneWidget);
    await tester.scrollUntilVisible(find.text('Not available on iOS.'), 250);
    expect(find.text('Not available on iOS.'), findsOneWidget);
    expect(find.text('Open accessibility settings'), findsNothing);
    expect(find.text('Open developer options'), findsNothing);
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox());
    debugDefaultTargetPlatformOverride = null;
  });

  test(
    'Android channel decodes nested codec maps and unknown permissions',
    () async {
      debugDefaultTargetPlatformOverride = TargetPlatform.android;
      messenger.setMockMethodCallHandler(
        security,
        (call) async => <Object?, Object?>{
          'observedAt': 1234,
          'deviceSignals': <Object?>[
            <Object?, Object?>{'label': 'Device lock', 'status': 'Configured'},
          ],
          'services': <Object?>[
            <Object?, Object?>{
              'name': 'Reader',
              'capabilities': ['Read window content'],
            },
          ],
          'permissionInventory': <Object?, Object?>{
            'packageCount': 1,
            'permissions': <Object?>[
              <Object?, Object?>{
                'name': 'camera',
                'apps': <Object?>[
                  <Object?, Object?>{
                    'packageName': 'example.app',
                    'label': 'Example',
                    'granted': null,
                  },
                ],
              },
            ],
          },
        },
      );
      final observation = await SecurityScanner.scan();
      expect(observation.deviceSignals.single.status, 'Configured');
      expect(observation.services!.single.name, 'Reader');
      expect(
        observation.permissionInventory!.permissions.single.apps.single.granted,
        isNull,
      );
    },
  );

  testWidgets('scan failure is visible and can be retried', (tester) async {
    messenger.setMockMethodCallHandler(security, (call) async {
      throw PlatformException(code: 'SCAN_FAILED', message: 'Try again');
    });
    await tester.pumpWidget(const DhalamApp());
    await tester.pumpAndSettle();
    await tester.tap(find.text('Run security check'));
    await tester.pumpAndSettle();
    expect(find.text('Scan failed: Try again'), findsOneWidget);
    await tester.tap(find.text('Run security check'));
    await tester.pumpAndSettle();
    expect(find.text('Scan failed: Try again'), findsOneWidget);
    await tester.pumpWidget(const SizedBox());
    debugDefaultTargetPlatformOverride = null;
  });

  testWidgets('triage requires consent and uploads no device inventory', (
    tester,
  ) async {
    Map<String, dynamic>? submitted;
    await tester.pumpWidget(
      MaterialApp(
        home: TriageScreen(
          journal: SecurityJournal(),
          ios: true,
          analyze: (payload) async {
            submitted = payload;
            return {
              'summary': 'Synthetic result',
              'scam_probability': 40,
              'threat_level': 'MEDIUM',
              'scam_category': 'Unclear',
              'recommended_actions': ['Verify independently.'],
            };
          },
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.enterText(
      find.widgetWithText(TextField, 'Suspicious message or situation'),
      'A suspicious request',
    );
    await tester.scrollUntilVisible(
      find.text('Analyze submission'),
      250,
      scrollable: find.byType(Scrollable).first,
    );
    final button = tester.widget<FilledButton>(
      find.ancestor(
        of: find.text('Analyze submission'),
        matching: find.byWidgetPredicate((w) => w is FilledButton),
      ),
    );
    expect(button.onPressed, isNull);
    expect(submitted, isNull);
    await tester.tap(find.byType(CheckboxListTile));
    await tester.pump();
    await tester.tap(find.text('Analyze submission'));
    await tester.pumpAndSettle();
    expect(submitted, {'consent': true, 'text': 'A suspicious request'});
    await tester.scrollUntilVisible(
      find.text('Synthetic result'),
      150,
      scrollable: find.byType(Scrollable).first,
    );
    expect(find.text('Synthetic result'), findsOneWidget);
    await tester.pumpWidget(const SizedBox());
    debugDefaultTargetPlatformOverride = null;
  });

  testWidgets('capture warning reacts to capture starting and stopping', (
    tester,
  ) async {
    var status = 'Inactive';
    messenger.setMockMethodCallHandler(toolsChannel, (call) async => status);
    await tester.pumpWidget(
      const MaterialApp(home: Scaffold(body: CaptureWarning())),
    );
    await tester.pump();
    expect(find.text('Coercion Shield · screen capture active'), findsNothing);
    status = 'Active';
    await tester.pump(const Duration(seconds: 2));
    await tester.pump();
    expect(
      find.text('Coercion Shield · screen capture active'),
      findsOneWidget,
    );
    status = 'Inactive';
    await tester.pump(const Duration(seconds: 2));
    await tester.pump();
    expect(find.text('Coercion Shield · screen capture active'), findsNothing);
    await tester.pumpWidget(const SizedBox());
    debugDefaultTargetPlatformOverride = null;
  });

  testWidgets('native capture events supersede an outstanding poll', (
    tester,
  ) async {
    debugDefaultTargetPlatformOverride = null;
    final poll = Completer<String>();
    messenger.setMockMethodCallHandler(toolsChannel, (call) => poll.future);
    await tester.pumpWidget(
      const MaterialApp(home: Scaffold(body: CaptureWarning())),
    );
    Future<void> emit(String status) async {
      await messenger.handlePlatformMessage(
        toolsChannel.name,
        const StandardMethodCodec().encodeMethodCall(
          MethodCall('captureChanged', status),
        ),
        (_) {},
      );
      await tester.pump();
    }

    await emit('Active');
    expect(
      find.text('Coercion Shield · screen capture active'),
      findsOneWidget,
    );
    poll.complete('Inactive');
    await tester.pump();
    expect(
      find.text('Coercion Shield · screen capture active'),
      findsOneWidget,
    );
    await emit('Inactive');
    expect(find.text('Coercion Shield · screen capture active'), findsNothing);
    await tester.pumpWidget(const SizedBox());
  });

  testWidgets('background checks are opt-in and can be disabled', (
    tester,
  ) async {
    debugDefaultTargetPlatformOverride = TargetPlatform.android;
    var enabled = false;
    var changes = 0;
    messenger.setMockMethodCallHandler(toolsChannel, (call) async {
      if (call.method == 'setBackgroundChecks') {
        enabled = call.arguments as bool;
        changes++;
      }
      if (call.method == 'backgroundStatus' ||
          call.method == 'setBackgroundChecks') {
        return {'enabled': enabled, 'checkedAt': 0, 'changes': 0};
      }
      return call.method == 'captureStatus' ? 'Inactive' : null;
    });
    await tester.pumpWidget(const DhalamApp());
    await tester.pumpAndSettle();
    expect(changes, 0);
    await tester.scrollUntilVisible(
      find.byType(SwitchListTile),
      250,
      scrollable: find.byType(Scrollable).first,
    );
    expect(
      tester.widget<SwitchListTile>(find.byType(SwitchListTile)).value,
      false,
    );
    await tester.tap(find.byType(SwitchListTile));
    await tester.pumpAndSettle();
    expect(enabled, true);
    await tester.tap(find.byType(SwitchListTile));
    await tester.pumpAndSettle();
    expect(enabled, false);
    expect(changes, 2);
    await tester.pumpWidget(const SizedBox());
    debugDefaultTargetPlatformOverride = null;
  });

  test('missing native implementation is an error, not a safe scan', () async {
    expect(SecurityScanner.scan(), throwsA(isA<SecurityException>()));
  });
}
