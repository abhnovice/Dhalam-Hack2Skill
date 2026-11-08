import 'dart:convert';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:dhalam/features/security_tools.dart';
import 'package:dhalam/models/security_models.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  String? stored;
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  setUp(() {
    stored = null;
    messenger.setMockMethodCallHandler(toolsChannel, (call) async {
      if (call.method == 'loadJournal') return stored;
      if (call.method == 'saveJournal') {
        stored = call.arguments as String;
        return null;
      }
      return null;
    });
  });
  tearDown(() => messenger.setMockMethodCallHandler(toolsChannel, null));

  SecurityObservation observation(int at, String status) => SecurityObservation(
    observedAt: at,
    deviceSignals: [DeveloperSignal(label: 'Device lock', status: status)],
  );

  test(
    'history persists across restart, caps at 30 and retains unavailable states',
    () async {
      final journal = SecurityJournal();
      await journal.load();
      for (var i = 1; i <= 35; i++) {
        await journal.record(observation(i, 'Configured'), true, 'Inactive');
      }
      await journal.review('App permissions', true);
      final restored = SecurityJournal();
      await restored.load();
      expect(restored.records.length, 30);
      expect(restored.records.first.at, 35);
      expect(restored.records.last.at, 6);
      expect(restored.reviewed.containsKey('App permissions'), true);
      await restored.record(
        observation(36, 'Unavailable'),
        null,
        'Unavailable',
      );
      expect(restored.lastSuccess('Device lock'), 35);
      expect(
        restored.changes.any(
          (c) => c.startsWith('Device lock: comparison unavailable'),
        ),
        true,
      );
      expect(restored.records.first.findings.first.state, 'Unavailable');
    },
  );

  test('actual configuration changes produce actionable findings', () async {
    final journal = SecurityJournal();
    await journal.load();
    await journal.record(observation(1, 'Configured'), true, 'Inactive');
    await journal.record(
      observation(2, 'Not configured — set a passcode'),
      false,
      'Active',
    );
    expect(
      journal.changes.any((c) => c.contains('Configured → Not configured')),
      true,
    );
    expect(journal.records.first.findings.first.state, 'Needs attention');
    expect(
      journal.report(true),
      contains('user-confirmed, not automatically verified'),
    );
    expect(journal.report(true), contains('Unavailable'));
    await journal.clear();
    expect(journal.records, isEmpty);
    expect(jsonDecode(stored!)['records'], isEmpty);
  });

  test('corrupt storage is not silently overwritten', () async {
    stored = '{broken';
    final journal = SecurityJournal();
    await journal.load();
    await journal.record(observation(1, 'Configured'), true, 'Inactive');
    expect(stored, '{broken');
    expect(journal.storageError, isNotNull);
    await journal.clear();
    expect(jsonDecode(stored!)['version'], 1);
  });

  test(
    'failed save is surfaced without discarding the current observation',
    () async {
      messenger.setMockMethodCallHandler(toolsChannel, (call) async {
        if (call.method == 'saveJournal') {
          throw PlatformException(code: 'DISK_FULL');
        }
        return null;
      });
      final journal = SecurityJournal();
      await journal.load();
      await journal.record(observation(1, 'Configured'), true, 'Inactive');
      expect(journal.records.length, 1);
      expect(journal.storageError, contains('could not be saved'));
    },
  );

  test('link inspector exposes userinfo trick and the actual destination', () {
    final result = inspectLink('https://trusted.example@evil.example/login');
    expect(result.host, 'evil.example');
    expect(result.scheme, 'https');
    expect(result.path, '/login');
    expect(result.notes.any((n) => n.contains('before @')), true);
    expect(
      inspectLink(
        'https://login.bank.example.evil.test',
      ).notes.any((n) => n.contains('Many domain labels')),
      true,
    );
    expect(
      inspectLink(
        'https://аррӏе.com',
      ).notes.any((n) => n.contains('Internationalized')),
      true,
    );
    expect(
      inspectLink(
        'https://xn--pple-43d.example',
      ).notes.any((n) => n.contains('Internationalized')),
      true,
    );
    expect(
      inspectLink(
        'http://127.0.0.1:8080',
      ).notes.any((n) => n.contains('unusual port')),
      true,
    );
    expect(
      inspectLink(
        'https://example.com',
      ).notes.any((n) => n.contains('does not establish')),
      true,
    );
  });

  test('same-count service replacement survives journal reload', () async {
    final journal = SecurityJournal();
    await journal.load();
    await journal.record(
      const SecurityObservation(
        observedAt: 1,
        services: [
          ObservedAccessibilityService(name: 'Reader', id: 'reader.one'),
        ],
      ),
      true,
      'Inactive',
    );
    await journal.record(
      const SecurityObservation(
        observedAt: 2,
        services: [
          ObservedAccessibilityService(name: 'Reader', id: 'reader.two'),
        ],
      ),
      true,
      'Inactive',
    );
    final restored = SecurityJournal();
    await restored.load();
    expect(restored.changes.any((c) => c.contains('reader.one')), true);
    expect(restored.changes.any((c) => c.contains('reader.two')), true);
  });

  test(
    'timeline compares the selected adjacent pair, preserving unknown reads',
    () {
      final journal = SecurityJournal();
      journal.records = [
        CheckRecord(3, [explain('Device lock', 'Unavailable')]),
        CheckRecord(2, [explain('Device lock', 'Configured')]),
        CheckRecord(1, [explain('Device lock', 'Not configured')]),
      ];
      expect(journal.changesAt(0).single, contains('comparison unavailable'));
      expect(
        journal.changesAt(1).single,
        contains('Not configured → Configured'),
      );
      expect(journal.changesAt(2).single, contains('Run another check'));
      expect(journal.changesAt(-1), isEmpty);
      expect(journal.changesAt(3), isEmpty);
    },
  );

  test('invalid and ambiguous links are rejected', () {
    for (final input in [
      '',
      'javascript://alert',
      'https://a b.com',
      'https://a\\b.com',
      'http://',
      'https://example.com:abc',
      'https://example.com:99999',
      'https://bad_host.com',
    ]) {
      expect(inspectLink(input).host, isEmpty, reason: input);
    }
  });
}
