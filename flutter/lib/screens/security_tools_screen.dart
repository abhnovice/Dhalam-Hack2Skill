import 'dart:async';
import 'package:flutter/material.dart';
import 'package:material_symbols_icons/symbols.dart';
import '../features/security_tools.dart';
import '../l10n/app_language.dart';
import 'triage_screen.dart';
import 'findings_overview.dart';

class SecurityToolsScreen extends StatefulWidget {
  final SecurityJournal journal;
  final bool ios;
  const SecurityToolsScreen({
    super.key,
    required this.journal,
    required this.ios,
  });
  @override
  State<SecurityToolsScreen> createState() => _SecurityToolsScreenState();
}

class _SecurityToolsScreenState extends State<SecurityToolsScreen>
    with WidgetsBindingObserver, LanguageListener {
  Timer? _timer;
  String _capture = "Unavailable";
  bool _active = true;
  bool _captureBusy = false;
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _pollCapture();
    _timer = Timer.periodic(const Duration(seconds: 2), (_) {
      if (_active) _pollCapture();
    });
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    _active = state == AppLifecycleState.resumed;
    if (_active) _pollCapture();
  }

  Future<void> _pollCapture() async {
    if (_captureBusy) return;
    _captureBusy = true;
    try {
      final status = await toolsChannel.invokeMethod<String>("captureStatus");
      if (mounted) setState(() => _capture = status ?? "Unavailable");
    } catch (_) {
      if (mounted) setState(() => _capture = "Unavailable");
    } finally {
      _captureBusy = false;
    }
  }

  final _url = TextEditingController();
  // The inspection and the report are rebuilt from these flags every time the
  // screen is drawn, so they follow the selected language.
  bool _inspected = false;
  bool _showReport = false;
  bool _busy = false;
  String? _findingFilter;
  @override
  void dispose() {
    _timer?.cancel();
    WidgetsBinding.instance.removeObserver(this);
    _url.dispose();
    super.dispose();
  }

  Future<void> _action(Future<void> Function() run) async {
    setState(() => _busy = true);
    try {
      await run();
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              tr('Could not complete this action. Please try again.'),
            ),
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Widget _list(List<Widget> children, {Key? key}) =>
      ListView(key: key, padding: const EdgeInsets.all(16), children: children);

  Widget _inspection(BuildContext context) {
    final inspection = inspectLink(_url.text);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const SizedBox(height: 12),
        Card(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(tr('Actual hostname')),
                SelectableText(
                  inspection.host.isEmpty
                      ? tr('Unavailable — invalid input')
                      : inspection.host,
                  style: Theme.of(context).textTheme.titleLarge,
                  textDirection: TextDirection.ltr,
                ),
                if (inspection.host.isNotEmpty) ...[
                  const Divider(),
                  Text(
                    tr('Scheme: {scheme} · transport only, not trust', {
                      'scheme': inspection.scheme,
                    }),
                  ),
                  SelectableText(
                    tr('Path: {path}', {'path': inspection.path}),
                    textDirection: TextDirection.ltr,
                  ),
                ],
                Text(tr('Inspected locally; link not opened.')),
              ],
            ),
          ),
        ),
        for (final note in inspection.notes)
          ListTile(leading: const Icon(Symbols.info), title: Text(note)),
      ],
    );
  }

  @override
  Widget build(BuildContext context) => DefaultTabController(
    length: 5,
    child: Scaffold(
      appBar: AppBar(
        title: Text(tr('Security tools')),
        actions: const [LanguageMenuButton()],
        bottom: TabBar(
          isScrollable: true,
          tabs: [
            Tab(text: tr('Findings')),
            Tab(text: tr('History')),
            Tab(text: tr('Link inspector')),
            Tab(text: tr('Privacy review')),
            Tab(text: tr('Report')),
          ],
        ),
      ),
      body: SafeArea(
        child: ListenableBuilder(
          listenable: widget.journal,
          builder: (context, _) {
            final j = widget.journal;
            final report = _showReport ? j.report(widget.ios) : null;
            return Column(
              children: [
                if (_capture == 'Active')
                  Padding(
                    padding: const EdgeInsets.all(12),
                    child: Text(
                      tr(
                        'Dhalam is visible in a capture session. Stop recording or sharing before viewing private reports if unintended.',
                      ),
                    ),
                  ),
                if (j.storageError != null)
                  Padding(
                    padding: const EdgeInsets.all(12),
                    child: Text(
                      trText(j.storageError!),
                      style: TextStyle(
                        color: Theme.of(context).colorScheme.error,
                      ),
                    ),
                  ),
                Expanded(
                  child: TabBarView(
                    children: [
                      _list([
                        if (j.records.isEmpty)
                          Text(tr('Run a scan to see your device review.')),
                        if (j.records.isNotEmpty) ...[
                          FindingsOverview(
                            findings: j.records.first.findings,
                            selected: _findingFilter,
                            onSelected: (value) =>
                                setState(() => _findingFilter = value),
                          ),
                          const SizedBox(height: 12),
                          Text(
                            tr('Observed {time}', {
                              'time':
                                  '${MaterialLocalizations.of(context).formatShortDate(DateTime.fromMillisecondsSinceEpoch(j.records.first.at))} · ${TimeOfDay.fromDateTime(DateTime.fromMillisecondsSinceEpoch(j.records.first.at)).format(context)}',
                            }),
                            style: Theme.of(context).textTheme.bodySmall,
                          ),
                          if (_findingFilter != null)
                            TextButton(
                              onPressed: () =>
                                  setState(() => _findingFilter = null),
                              child: Text(tr('Show all findings')),
                            ),
                          if (_findingFilter != null &&
                              !j.records.first.findings.any(
                                (f) => f.state == _findingFilter,
                              ))
                            Text(tr('No recorded findings in this category.')),
                        ],
                        for (final f
                            in (j.records.firstOrNull?.findings ?? <Finding>[])
                                .where(
                                  (f) =>
                                      _findingFilter == null ||
                                      f.state == _findingFilter,
                                ))
                          Card.outlined(
                            color: Theme.of(context).colorScheme.surface,
                            child: ExpansionTile(
                              title: Text(
                                '${trText(f.title)} · ${trText(f.state)}',
                                style: Theme.of(context).textTheme.titleSmall,
                              ),
                              childrenPadding: const EdgeInsets.fromLTRB(
                                16,
                                0,
                                16,
                                16,
                              ),
                              expandedCrossAxisAlignment:
                                  CrossAxisAlignment.start,
                              children: [
                                Text(trText(f.detail)),
                                const SizedBox(height: 8),
                                Text(trText(f.action)),
                                Text(
                                  tr('Last readable result: {value}', {
                                    'value': j.lastSuccess(f.title) == null
                                        ? tr('Never recorded')
                                        : stamp(j.lastSuccess(f.title)!),
                                  }),
                                  style: Theme.of(context).textTheme.bodySmall,
                                ),
                              ],
                            ),
                          ),
                      ]),
                      _list([
                        Text(
                          tr('Changes since previous check'),
                          style: Theme.of(context).textTheme.titleLarge,
                        ),
                        Text(
                          tr(
                            'Times show when a change was observed, not when it happened.',
                          ),
                        ),
                        Text(
                          tr(
                            'Last 30 successful foreground checks, stored locally. New checks retain service IDs and permission states. Missing apps may reflect visibility changes, not removal. Times are UTC.',
                          ),
                        ),
                        if (j.records.isEmpty)
                          Text(tr('No checks recorded yet.')),
                        for (var index = 0; index < j.records.length; index++)
                          Card(
                            child: ExpansionTile(
                              leading: const Icon(Symbols.history),
                              title: Text(
                                tr('Observed at {time}', {
                                  'time': stamp(j.records[index].at),
                                }),
                              ),
                              subtitle: Text(
                                index == j.records.length - 1
                                    ? tr('First retained observation')
                                    : j.changesAt(index).first,
                              ),
                              children: [
                                if (index < j.records.length - 1)
                                  for (final change in j.changesAt(index))
                                    ListTile(
                                      leading: const Icon(
                                        Symbols.compare_arrows,
                                      ),
                                      title: Text(change),
                                    ),
                                for (final f in j.records[index].findings)
                                  ListTile(
                                    title: Text(
                                      '${trText(f.title)} · ${trText(f.state)}',
                                    ),
                                    subtitle: Text(trText(f.detail)),
                                  ),
                              ],
                            ),
                          ),
                        TextButton(
                          onPressed: _busy
                              ? null
                              : () async {
                                  final confirmed = await showDialog<bool>(
                                    context: context,
                                    builder: (context) => AlertDialog(
                                      title: Text(
                                        tr(
                                          'Clear local history and review confirmations?',
                                        ),
                                      ),
                                      content: Text(
                                        tr(
                                          'This removes the saved checks and checklist timestamps from Dhalam on this device.',
                                        ),
                                      ),
                                      actions: [
                                        TextButton(
                                          onPressed: () =>
                                              Navigator.pop(context, false),
                                          child: Text(tr('Cancel')),
                                        ),
                                        TextButton(
                                          onPressed: () =>
                                              Navigator.pop(context, true),
                                          child: Text(tr('Clear')),
                                        ),
                                      ],
                                    ),
                                  );
                                  if (confirmed == true) {
                                    await _action(j.clear);
                                    if (mounted) {
                                      setState(() => _showReport = false);
                                    }
                                  }
                                },
                          child: Text(tr('Clear local history and reviews')),
                        ),
                      ]),
                      _list([
                        Text(
                          tr(
                            'Inspect a pasted link locally. It will not be opened, fetched, stored in history or included in reports.',
                          ),
                        ),
                        TextField(
                          controller: _url,
                          onChanged: (_) => setState(() => _inspected = false),
                          maxLength: 4096,
                          autocorrect: false,
                          enableSuggestions: false,
                          decoration: InputDecoration(
                            labelText: tr('URL to inspect'),
                          ),
                        ),
                        FilledButton(
                          onPressed: () => setState(() => _inspected = true),
                          child: Text(tr('Inspect link')),
                        ),
                        OutlinedButton(
                          onPressed: () => Navigator.of(context).push(
                            MaterialPageRoute<void>(
                              builder: (_) => TriageScreen(
                                journal: widget.journal,
                                ios: widget.ios,
                                initialUrl: _url.text.trim(),
                              ),
                            ),
                          ),
                          child: Text(tr('Review this link with Google AI')),
                        ),
                        if (_inspected) _inspection(context),
                      ]),
                      _list([
                        Text(
                          tr(
                            'Manual review. Check a box only after you review the setting. Confirmations are yours, not automatic verification; revisit them after changes.',
                          ),
                        ),
                        for (final entry in reviewSteps(widget.ios).entries)
                          CheckboxListTile(
                            value: j.reviewed.containsKey(entry.key),
                            onChanged: _busy
                                ? null
                                : (value) => _action(
                                    () => j.review(entry.key, value ?? false),
                                  ),
                            title: Text(trText(entry.key)),
                            subtitle: Text(
                              '${trText(entry.value)}\n${j.reviewed[entry.key] == null ? tr('Not confirmed') : tr('User-confirmed: {time}', {'time': stamp(j.reviewed[entry.key]!)})}',
                            ),
                          ),
                        TextButton(
                          onPressed: _busy
                              ? null
                              : () => _action(() async {
                                  await toolsChannel.invokeMethod<void>(
                                    'openPrivacySettings',
                                  );
                                }),
                          child: Text(
                            tr(
                              widget.ios
                                  ? 'Open Dhalam settings'
                                  : 'Open system privacy settings',
                            ),
                          ),
                        ),
                        Text(
                          tr(
                            'Follow the paths above for other settings. Menu names vary by device.',
                          ),
                        ),
                      ]),
                      _list([
                        Text(
                          tr(
                            'Preview before sharing. Reports include device configuration, check times and your manual review confirmations. Choose the recipient in the share sheet.',
                          ),
                        ),
                        FilledButton(
                          onPressed: () => setState(() => _showReport = true),
                          child: Text(tr('Generate report preview')),
                        ),
                        if (report != null) ...[
                          const SizedBox(height: 16),
                          SelectableText(report),
                          FilledButton.icon(
                            onPressed: _busy
                                ? null
                                : () => _action(() async {
                                    await toolsChannel.invokeMethod<void>(
                                      'shareReport',
                                      report,
                                    );
                                  }),
                            icon: const Icon(Symbols.share),
                            label: Text(tr('Share this report')),
                          ),
                        ],
                      ], key: const ValueKey('report-list')),
                    ],
                  ),
                ),
              ],
            );
          },
        ),
      ),
    ),
  );
}

/// Local, foreground-only warning; no telemetry leaves the device.
class CaptureWarning extends StatefulWidget {
  final bool accessibilityEnabled;
  const CaptureWarning({super.key, this.accessibilityEnabled = false});
  @override
  State<CaptureWarning> createState() => _CaptureWarningState();
}

class _CaptureWarningState extends State<CaptureWarning>
    with WidgetsBindingObserver, LanguageListener {
  StreamSubscription<String>? _events;
  Timer? _timer;
  String _status = 'Unavailable';
  bool _foreground = true, _busy = false;
  int _captureRevision = 0;
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _events = captureEvents.listen((status) {
      _captureRevision++;
      if (mounted && _foreground) setState(() => _status = status);
    });
    _refresh();
    _timer = Timer.periodic(const Duration(seconds: 2), (_) {
      if (_foreground) _refresh();
    });
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    _captureRevision++;
    _foreground = state == AppLifecycleState.resumed;
    if (_foreground) _refresh();
  }

  Future<void> _refresh() async {
    if (_busy) return;
    _busy = true;
    final revision = _captureRevision;
    var status = 'Unavailable';
    try {
      status =
          await toolsChannel.invokeMethod<String>('captureStatus') ?? status;
    } catch (_) {
      /* Unknown stays unknown. */
    }
    // A newer native event or lifecycle transition supersedes this poll.
    if (mounted && _foreground && revision == _captureRevision) {
      setState(() => _status = status);
    }
    _busy = false;
  }

  @override
  void dispose() {
    _events?.cancel();
    _timer?.cancel();
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => _status != 'Active'
      ? const SizedBox.shrink()
      : Material(
          color: Theme.of(context).colorScheme.errorContainer,
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Semantics(
              liveRegion: true,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    tr('Coercion Shield · screen capture active'),
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                  Text(
                    tr(
                      widget.accessibilityEnabled
                          ? 'Accessibility access was also enabled at the last check. Both can be legitimate, but review unfamiliar access if someone is pressuring you.'
                          : 'Dhalam is visible in a capture session. Pause before opening private messages or financial information.',
                    ),
                  ),
                  Text(
                    tr(
                      'Stop sharing using the system controls or the sharing app. Never reveal OTPs or PINs. Dhalam cannot disconnect another app’s session.',
                    ),
                  ),
                ],
              ),
            ),
          ),
        );
}
