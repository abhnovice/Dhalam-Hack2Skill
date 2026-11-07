import 'dart:async';
import 'package:flutter/material.dart';
import '../features/security_tools.dart';
import 'triage_screen.dart';

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
    with WidgetsBindingObserver {
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
  LinkInspection? _inspection;
  String? _report;
  bool _busy = false;
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
          const SnackBar(
            content: Text('Could not complete this action. Please try again.'),
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Widget _list(List<Widget> children, {Key? key}) =>
      ListView(key: key, padding: const EdgeInsets.all(16), children: children);
  @override
  Widget build(BuildContext context) => DefaultTabController(
    length: 5,
    child: Scaffold(
      appBar: AppBar(
        title: const Text('Security tools'),
        bottom: const TabBar(
          isScrollable: true,
          tabs: [
            Tab(text: 'Findings'),
            Tab(text: 'History'),
            Tab(text: 'Privacy review'),
            Tab(text: 'Link inspector'),
            Tab(text: 'Report'),
          ],
        ),
      ),
      body: SafeArea(
        child: ListenableBuilder(
          listenable: widget.journal,
          builder: (context, _) {
            final j = widget.journal;
            return Column(
              children: [
                if (_capture == 'Active')
                  const Padding(
                    padding: EdgeInsets.all(12),
                    child: Text(
                      'Dhalam is visible in a capture session. Stop recording or sharing before viewing private reports if unintended.',
                    ),
                  ),
                if (j.storageError != null)
                  Padding(
                    padding: const EdgeInsets.all(12),
                    child: Text(
                      j.storageError!,
                      style: TextStyle(
                        color: Theme.of(context).colorScheme.error,
                      ),
                    ),
                  ),
                Expanded(
                  child: TabBarView(
                    children: [
                      _list([
                        const Text(
                          'Latest recorded findings. Run a new security check from the dashboard after changing settings.',
                        ),
                        if (j.records.isEmpty)
                          const Text('No checks recorded yet.'),
                        for (final f
                            in j.records.firstOrNull?.findings ?? <Finding>[])
                          Card(
                            child: Padding(
                              padding: const EdgeInsets.all(16),
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    '${f.title} · ${f.state}',
                                    style: Theme.of(
                                      context,
                                    ).textTheme.titleMedium,
                                  ),
                                  Text(f.detail),
                                  const SizedBox(height: 8),
                                  Text(f.action),
                                  Text(
                                    'Last readable result: ${j.lastSuccess(f.title) == null ? 'Never recorded' : stamp(j.lastSuccess(f.title)!)}',
                                  ),
                                ],
                              ),
                            ),
                          ),
                      ]),
                      _list([
                        Text(
                          'Changes since previous check',
                          style: Theme.of(context).textTheme.titleLarge,
                        ),
                        for (final c in j.changes) ListTile(title: Text(c)),
                        const Text(
                          'Last 30 successful foreground checks, stored locally. New checks retain service IDs and permission states. Missing apps may reflect visibility changes, not removal. Times are UTC.',
                        ),
                        for (final r in j.records)
                          ExpansionTile(
                            title: Text(stamp(r.at)),
                            children: [
                              for (final f in r.findings)
                                ListTile(
                                  title: Text('${f.title} · ${f.state}'),
                                  subtitle: Text(f.detail),
                                ),
                            ],
                          ),
                        TextButton(
                          onPressed: _busy
                              ? null
                              : () async {
                                  final confirmed = await showDialog<bool>(
                                    context: context,
                                    builder: (context) => AlertDialog(
                                      title: const Text(
                                        'Clear local history and review confirmations?',
                                      ),
                                      content: const Text(
                                        'This removes the saved checks and checklist timestamps from Dhalam on this device.',
                                      ),
                                      actions: [
                                        TextButton(
                                          onPressed: () =>
                                              Navigator.pop(context, false),
                                          child: const Text('Cancel'),
                                        ),
                                        TextButton(
                                          onPressed: () =>
                                              Navigator.pop(context, true),
                                          child: const Text('Clear'),
                                        ),
                                      ],
                                    ),
                                  );
                                  if (confirmed == true) {
                                    await _action(j.clear);
                                    if (mounted) setState(() => _report = null);
                                  }
                                },
                          child: const Text('Clear local history and reviews'),
                        ),
                      ]),
                      _list([
                        const Text(
                          'Manual review. Check a box only after you review the setting. Confirmations are yours, not automatic verification; revisit them after changes.',
                        ),
                        for (final entry in reviewSteps(widget.ios).entries)
                          CheckboxListTile(
                            value: j.reviewed.containsKey(entry.key),
                            onChanged: _busy
                                ? null
                                : (value) => _action(
                                    () => j.review(entry.key, value ?? false),
                                  ),
                            title: Text(entry.key),
                            subtitle: Text(
                              '${entry.value}\n${j.reviewed[entry.key] == null ? 'Not confirmed' : 'User-confirmed: ${stamp(j.reviewed[entry.key]!)}'}',
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
                            widget.ios
                                ? 'Open Dhalam settings'
                                : 'Open system privacy settings',
                          ),
                        ),
                        const Text(
                          'Follow the paths above for other settings. Menu names vary by device.',
                        ),
                      ]),
                      _list([
                        const Text(
                          'Inspect a pasted link locally. It will not be opened, fetched, stored in history or included in reports.',
                        ),
                        TextField(
                          controller: _url,
                          maxLength: 4096,
                          autocorrect: false,
                          enableSuggestions: false,
                          decoration: const InputDecoration(
                            labelText: 'URL to inspect',
                          ),
                        ),
                        FilledButton(
                          onPressed: () => setState(
                            () => _inspection = inspectLink(_url.text),
                          ),
                          child: const Text('Inspect link'),
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
                          child: const Text('Review this link with Google AI'),
                        ),
                        if (_inspection != null) ...[
                          const SizedBox(height: 12),
                          SelectableText(
                            'Actual hostname: ${_inspection!.host.isEmpty ? 'Unavailable — invalid input' : _inspection!.host}',
                            textDirection: TextDirection.ltr,
                          ),
                          for (final note in _inspection!.notes)
                            ListTile(
                              leading: const Icon(Icons.info_outline),
                              title: Text(note),
                            ),
                        ],
                      ]),
                      _list([
                        const Text(
                          'Preview before sharing. Reports include device configuration, check times and your manual review confirmations. Choose the recipient in the share sheet.',
                        ),
                        FilledButton(
                          onPressed: () =>
                              setState(() => _report = j.report(widget.ios)),
                          child: const Text('Generate report preview'),
                        ),
                        if (_report != null) ...[
                          const SizedBox(height: 16),
                          SelectableText(_report!),
                          FilledButton.icon(
                            onPressed: _busy
                                ? null
                                : () => _action(() async {
                                    await toolsChannel.invokeMethod<void>(
                                      'shareReport',
                                      _report!,
                                    );
                                  }),
                            icon: const Icon(Icons.share),
                            label: const Text('Share this report'),
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
    with WidgetsBindingObserver {
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
                    'Coercion Shield · screen capture active',
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                  Text(
                    widget.accessibilityEnabled
                        ? 'Accessibility access was also enabled at the last check. Both can be legitimate, but review unfamiliar access if someone is pressuring you.'
                        : 'Dhalam is visible in a capture session. Pause before opening private messages or financial information.',
                  ),
                  const Text(
                    'Stop sharing using the system controls or the sharing app. Never reveal OTPs or PINs. Dhalam cannot disconnect another app’s session.',
                  ),
                ],
              ),
            ),
          ),
        );
}
