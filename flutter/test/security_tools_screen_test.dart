import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:dhalam/features/security_tools.dart';
import 'package:dhalam/screens/security_tools_screen.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  late List<String> shared;
  setUp(() {
    shared = [];
    messenger.setMockMethodCallHandler(toolsChannel, (call) async {
      if (call.method == 'captureStatus') return 'Active';
      if (call.method == 'shareReport') shared.add(call.arguments as String);
      return null;
    });
  });
  tearDown(() => messenger.setMockMethodCallHandler(toolsChannel, null));

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
