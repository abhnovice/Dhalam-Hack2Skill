import 'package:flutter/material.dart';
import 'package:material_symbols_icons/symbols.dart';
import '../features/security_tools.dart';
import '../l10n/app_language.dart';

/// Local parsing only; this widget never submits content or opens a website.
class ChatLinkPreview extends StatelessWidget {
  final String text;
  const ChatLinkPreview({super.key, required this.text});

  @override
  Widget build(BuildContext context) {
    final links = RegExp(
      r'https?://[^\s]+',
      caseSensitive: false,
    ).allMatches(text).map((m) => m.group(0)!).toSet().take(5).toList();
    if (links.isEmpty) return const SizedBox.shrink();
    return TextButton.icon(
      icon: const Icon(Symbols.link),
      label: Text(
        links.length > 1
            ? tr('Inspect link locally ({n})', {'n': links.length})
            : tr('Inspect link locally'),
      ),
      onPressed: () => showDialog<void>(
        context: context,
        builder: (context) => AlertDialog(
          title: Text(tr('Link breakdown')),
          content: SizedBox(
            width: 420,
            child: SingleChildScrollView(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    tr(
                      'Local check only. No link was opened or sent. Showing up to five distinct HTTP(S) links.',
                    ),
                  ),
                  for (final link in links) ...[
                    const SizedBox(height: 16),
                    SelectableText(link),
                    Builder(
                      builder: (context) {
                        final result = inspectLink(link);
                        return Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(tr('Actual hostname')),
                            SelectableText(
                              result.host.isEmpty
                                  ? tr('Unavailable — invalid input')
                                  : result.host,
                              textDirection: TextDirection.ltr,
                              style: Theme.of(context).textTheme.titleMedium,
                            ),
                            if (result.host.isNotEmpty) ...[
                              Text(
                                tr(
                                  'Scheme: {scheme} · transport only, not trust',
                                  {'scheme': result.scheme},
                                ),
                              ),
                              SelectableText(
                                tr('Path: {path}', {'path': result.path}),
                                textDirection: TextDirection.ltr,
                              ),
                            ],
                            for (final note in result.notes)
                              Padding(
                                padding: const EdgeInsets.only(top: 8),
                                child: Text(note),
                              ),
                          ],
                        );
                      },
                    ),
                    const Divider(),
                  ],
                ],
              ),
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: Text(tr('Close')),
            ),
          ],
        ),
      ),
    );
  }
}
