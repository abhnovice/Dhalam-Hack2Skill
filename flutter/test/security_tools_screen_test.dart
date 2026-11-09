import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:dhalam/features/security_tools.dart';
import 'package:dhalam/l10n/app_language.dart';
import 'package:dhalam/l10n/chat_language.dart';
import 'package:dhalam/screens/security_tools_screen.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  late List<String> shared;
  setUp(() {
    appLanguage.value = chatLanguages.first;
    shared = [];
    messenger.setMockMethodCallHandler(toolsChannel, (call) async {
      if (call.method == 'captureStatus') return 'Active';
      if (call.method == 'shareReport') shared.add(call.arguments as String);
      return null;
    });
  });
  tearDown(() => messenger.setMockMethodCallHandler(toolsChannel, null));

  testWidgets('finding counts filter results and keep unavailable separate', (
    tester,
  ) async {
    final journal = SecurityJournal();
    journal.records = [
      CheckRecord(1, [
        explain('Device lock', 'Not configured'),
        explain('Biometric authentication', 'Unavailable'),
      ]),
    ];
    await tester.pumpWidget(
      MaterialApp(home: SecurityToolsScreen(journal: journal, ios: true)),
    );
    await tester.pumpAndSettle();
    expect(
      find.byKey(const ValueKey('finding-filter-Needs attention')),
      findsOneWidget,
    );
    expect(
      find.byKey(const ValueKey('finding-filter-Unavailable')),
      findsOneWidget,
    );
    await tester.ensureVisible(
      find.byKey(const ValueKey('finding-filter-Unavailable')),
    );
    await tester.tap(find.byKey(const ValueKey('finding-filter-Unavailable')));
    await tester.pumpAndSettle();
    expect(find.text('Device lock · Needs attention'), findsNothing);
    expect(find.text('Biometric authentication · Unavailable'), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('finding-filter-Configured')));
    await tester.pumpAndSettle();
    expect(find.text('No recorded findings in this category.'), findsOneWidget);
    await tester.tap(find.text('Show all findings'));
    await tester.pumpAndSettle();
    expect(find.text('Device lock · Needs attention'), findsOneWidget);
    await tester.pumpWidget(const SizedBox());
  });

  testWidgets('editing an inspected link clears its previous hostname', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        home: SecurityToolsScreen(journal: SecurityJournal(), ios: false),
      ),
    );
    await tester.pumpAndSettle();
    await tester.ensureVisible(find.text('Link inspector'));
    await tester.tap(find.text('Link inspector'));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byType(TextField),
      'https://trusted.example@evil.example/login',
    );
    await tester.tap(find.text('Inspect link'));
    await tester.pumpAndSettle();
    expect(find.text('evil.example'), findsOneWidget);
    expect(find.text('Path: /login'), findsOneWidget);
    await tester.enterText(find.byType(TextField), 'https://different.example');
    await tester.pumpAndSettle();
    expect(find.text('evil.example'), findsNothing);
    await tester.pumpWidget(const SizedBox());
  });

  testWidgets(
    'capture warning is visible on tools and report requires preview',
    (tester) async {
      final journal = SecurityJournal();
      await journal.load();
      await tester.pumpWidget(
        MaterialApp(home: SecurityToolsScreen(journal: journal, ios: true)),
      );
      await tester.pumpAndSettle();
      expect(
        find.textContaining('Dhalam is visible in a capture session'),
        findsOneWidget,
      );
      await tester.ensureVisible(find.text('Report'));
      await tester.tap(find.text('Report'));
      await tester.pumpAndSettle();
      expect(shared, isEmpty);
      expect(find.text('Share this report'), findsNothing);
      await tester.tap(find.text('Generate report preview'));
      await tester.pumpAndSettle();
      expect(shared, isEmpty);
      await tester.scrollUntilVisible(
        find.text('Share this report'),
        250,
        scrollable: find
            .descendant(
              of: find.byKey(const ValueKey('report-list')),
              matching: find.byType(Scrollable),
            )
            .first,
      );
      await tester.tap(find.text('Share this report'));
      await tester.pumpAndSettle();
      expect(shared.single, contains('No check recorded.'));
      expect(shared.single, contains('not automatically verified'));
      await tester.pumpWidget(const SizedBox());
    },
  );
}
