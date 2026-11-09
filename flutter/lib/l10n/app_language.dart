import 'package:flutter/material.dart';
import 'package:material_symbols_icons/symbols.dart';
import 'app_strings.dart';
import 'chat_language.dart';

/// The language shared by every screen. Screens listen to it, so choosing a
/// language in any app bar updates the whole app.
final ValueNotifier<ChatLanguage> appLanguage = ValueNotifier(
  chatLanguages.first,
);

/// Looks up [english] in the string table. English text is the key, so unknown
/// strings and English itself fall back to the original wording. `{name}`
/// placeholders are filled from [args].
String tr(String english, [Map<String, Object?> args = const {}]) {
  var text = english;
  final index = appLanguageOrder.indexOf(appLanguage.value.code);
  final row = index < 0 ? null : appStringTable[english];
  if (row != null) text = row[index];
  args.forEach((key, value) => text = text.replaceAll('{$key}', '$value'));
  return text;
}

/// Translates text that was stored or produced in English (journal findings,
/// scanner statuses, error messages). Stored values are never changed; this is
/// applied only when drawing the screen.
String trText(String english) {
  final direct = appStringTable[english];
  if (direct != null || appLanguage.value.code == 'en') return tr(english);
  final services = RegExp(r'^(\d+) enabled services reported$');
  final packages = RegExp(
    r'^(\d+) visible packages; (\d+) granted app-permission pairs$',
  );
  const updateTail = ' — review Software Update in Settings';
  const scanFailed = 'Scan failed: ';
  var match = services.firstMatch(english);
  if (match != null) {
    return tr('{n} enabled services reported', {'n': match.group(1)});
  }
  match = packages.firstMatch(english);
  if (match != null) {
    return tr('{n} visible packages; {m} granted app-permission pairs', {
      'n': match.group(1),
      'm': match.group(2),
    });
  }
  if (english.endsWith(updateTail)) {
    final os = english.substring(0, english.length - updateTail.length);
    return '$os — ${tr('review Software Update in Settings')}';
  }
  if (english.startsWith(scanFailed)) {
    return tr('Scan failed: {m}', {'m': english.substring(scanFailed.length)});
  }
  return english;
}

/// Rebuilds [builder] whenever the app language changes.
class LanguageBuilder extends StatelessWidget {
  final WidgetBuilder builder;
  const LanguageBuilder({super.key, required this.builder});
  @override
  Widget build(BuildContext context) => ValueListenableBuilder<ChatLanguage>(
    valueListenable: appLanguage,
    builder: (context, _, _) => builder(context),
  );
}

/// Rebuilds a [State] whenever the app language changes.
mixin LanguageListener<T extends StatefulWidget> on State<T> {
  void _languageChanged() {
    if (mounted) setState(() {});
  }

  @override
  void initState() {
    super.initState();
    appLanguage.addListener(_languageChanged);
  }

  @override
  void dispose() {
    appLanguage.removeListener(_languageChanged);
    super.dispose();
  }
}

/// App-bar menu that switches the language for every screen.
class LanguageMenuButton extends StatelessWidget {
  final bool enabled;

  /// Runs before the language changes, e.g. to stop speech on the chat screen.
  final Future<void> Function()? beforeChange;
  const LanguageMenuButton({super.key, this.enabled = true, this.beforeChange});

  @override
  Widget build(BuildContext context) => LanguageBuilder(
    builder: (context) => PopupMenuButton<ChatLanguage>(
      tooltip: appLanguage.value.strings.language,
      icon: const Icon(Symbols.translate),
      enabled: enabled,
      onSelected: (language) async {
        if (language.code == appLanguage.value.code) return;
        await beforeChange?.call();
        appLanguage.value = language;
      },
      itemBuilder: (_) => [
        for (final language in chatLanguages)
          CheckedPopupMenuItem(
            value: language,
            checked: language.code == appLanguage.value.code,
            child: Text(language.name),
          ),
      ],
    ),
  );
}
