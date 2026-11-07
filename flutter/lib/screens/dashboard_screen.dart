import 'dart:async';
import '../features/security_tools.dart';
import 'security_tools_screen.dart';
import 'triage_screen.dart';
import 'package:flutter/material.dart';
import '../models/security_models.dart';
import '../services/notification_service.dart';
import '../services/security_scanner.dart';

class DashboardScreen extends StatefulWidget {
  const DashboardScreen({super.key});

  @override
  State<DashboardScreen> createState() => _DashboardScreenState();
}

class _DashboardScreenState extends State<DashboardScreen>
    with WidgetsBindingObserver {
  final _journal = SecurityJournal();
  late final Future<void> _journalReady;
  Timer? _captureTimer;
  String _capture = 'Unavailable';
  bool _captureBusy = false;
  bool _foreground = true;
  SecurityObservation? _observation;
  bool _scanning = false;
  bool _settingsBusy = false;
  bool? _notifications;
  String? _error;
  String _search = '';
  bool _sensitiveOnly = false;
  bool _returningFromSettings = false;
  Map<String, dynamic>? _background;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _refreshNotifications();
    _refreshBackground();
    _journalReady = _journal.load();
    _refreshCapture();
    _captureTimer = Timer.periodic(const Duration(seconds: 2), (_) {
      if (_foreground) _refreshCapture();
    });
  }

  @override
  void dispose() {
    _captureTimer?.cancel();
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    _foreground = state == AppLifecycleState.resumed;
    if (_foreground) {
      _refreshNotifications();
      _refreshBackground();
      _refreshCapture();
      if (_returningFromSettings && !_scanning) {
        _returningFromSettings = false;
        _scan();
      }
    }
  }

  Future<void> _refreshBackground() async {
    if (!SecurityScanner.isAndroid) return;
    try {
      final status = await toolsChannel.invokeMapMethod<String, dynamic>(
        'backgroundStatus',
      );
      if (mounted) setState(() => _background = status);
    } catch (_) {
      if (mounted) setState(() => _background = null);
    }
  }

  Future<void> _setBackground(bool enabled) async {
    await _settings(() async {
      await toolsChannel.invokeMethod('setBackgroundChecks', enabled);
      await _refreshBackground();
    });
  }

  Future<void> _refreshCapture() async {
    if (_captureBusy) return;
    _captureBusy = true;
    try {
      final status = await toolsChannel.invokeMethod<String>('captureStatus');
      if (mounted) setState(() => _capture = status ?? 'Unavailable');
    } catch (_) {
      if (mounted) setState(() => _capture = 'Unavailable');
    } finally {
      _captureBusy = false;
    }
  }

  Future<void> _openTools() async {
    await _journalReady;
    if (!mounted) return;
    await Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) =>
            SecurityToolsScreen(journal: _journal, ios: SecurityScanner.isIOS),
      ),
    );
  }

  Future<void> _refreshNotifications() async {
    try {
      final enabled = await NotificationService.areNotificationsEnabled();
      if (mounted) setState(() => _notifications = enabled);
    } catch (_) {
      if (mounted) setState(() => _notifications = null);
    }
  }

  Future<void> _scan() async {
    if (_scanning) return;
    setState(() {
      _scanning = true;
      _error = null;
      _observation = null;
    });
    try {
      await _journalReady;
      final result = await SecurityScanner.scan();
      await _refreshNotifications();
      await _refreshCapture();
      await _journal.record(result, _notifications, _capture);
      if (mounted) setState(() => _observation = result);
    } catch (error) {
      if (mounted) setState(() => _error = error.toString());
    } finally {
      if (mounted) setState(() => _scanning = false);
    }
  }

  Future<void> _settings(Future<void> Function() action) async {
    setState(() => _settingsBusy = true);
    try {
      _returningFromSettings = true;
      await action();
      await _refreshNotifications();
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(error.toString())));
      }
    } finally {
      if (mounted) setState(() => _settingsBusy = false);
    }
  }

  Widget _section(String title, List<Widget> children) => Card(
    child: Padding(
      padding: const EdgeInsets.all(20),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(title, style: Theme.of(context).textTheme.titleLarge),
          const SizedBox(height: 12),
          ...children,
        ],
      ),
    ),
  );

  @override
  Widget build(BuildContext context) {
    final observation = _observation;
    final ios = SecurityScanner.isIOS;
    return Scaffold(
      appBar: AppBar(
        title: const Text('Dhalam AI'),
        actions: [
          TextButton(
            onPressed: _openTools,
            child: const Text('Security tools'),
          ),
        ],
      ),
      body: SafeArea(
        child: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 800),
            child: ListView(
              padding: const EdgeInsets.all(16),
              children: [
                CaptureWarning(
                  accessibilityEnabled:
                      _observation?.services?.isNotEmpty == true,
                ),
                FilledButton.icon(
                  onPressed: () async {
                    await _journalReady;
                    if (!context.mounted) return;
                    await Navigator.of(context).push(
                      MaterialPageRoute<void>(
                        builder: (_) =>
                            TriageScreen(journal: _journal, ios: ios),
                      ),
                    );
                  },
                  icon: const Icon(Icons.chat_outlined),
                  label: const Text('Ask Dhalam — check a suspicious request'),
                ),
                const SizedBox(height: 16),
                Text(
                  'Security dashboard',
                  style: Theme.of(context).textTheme.headlineMedium,
                ),
                const SizedBox(height: 12),
                Text(
                  ios
                      ? 'iOS privacy overview. Apple limits access to other apps and system settings. This is not a malware scan or a device safety verdict.'
                      : 'Review the security information available to Dhalam. These observations do not determine whether your device is safe.',
                ),
                const SizedBox(height: 16),
                FilledButton.icon(
                  onPressed: _scanning ? null : _scan,
                  icon: const Icon(Icons.shield_outlined),
                  label: Text(_scanning ? 'Checking…' : 'Run security check'),
                ),
                if (_scanning) const LinearProgressIndicator(),
                if (_error != null)
                  Padding(
                    padding: const EdgeInsets.all(16),
                    child: Text(
                      _error!,
                      style: TextStyle(
                        color: Theme.of(context).colorScheme.error,
                      ),
                    ),
                  ),
                _section('Screen capture awareness', [
                  Text(
                    _capture == 'Active'
                        ? 'Dhalam is visible in a capture session.'
                        : _capture == 'Inactive'
                        ? 'No active capture reported for Dhalam.'
                        : 'Capture detection unavailable on this device or OS.',
                  ),
                  const Text(
                    'While Dhalam is visible only. This cannot detect every recording or capture of other apps.',
                  ),
                  if (_capture == 'Active')
                    const Text(
                      'If unintended, stop screen recording or sharing before viewing private information.',
                    ),
                ]),
                OutlinedButton(
                  onPressed: _openTools,
                  child: const Text(
                    'Findings, history, privacy review and reports',
                  ),
                ),
                if (SecurityScanner.isAndroid)
                  _section('Periodic local checks', [
                    SwitchListTile(
                      contentPadding: EdgeInsets.zero,
                      title: const Text(
                        'Review access changes in the background',
                      ),
                      value: _background?['enabled'] == true,
                      onChanged: _settingsBusy || _background == null
                          ? null
                          : _setBackground,
                    ),
                    const Text(
                      'Opt-in, on-device checks approximately every 15 minutes or later when Android permits. No cloud uploads, continuous screen monitoring or automatic blocking. Turn off to clear the background baseline.',
                    ),
                    Text(
                      _background == null
                          ? 'Background scheduling unavailable.'
                          : (_background!['checkedAt'] as int? ?? 0) == 0
                          ? 'No scheduled check has completed yet.'
                          : 'Last scheduled check: ${stamp(_background!['checkedAt'] as int)} · ${_background!['changes']} access changes.',
                    ),
                    const Text(
                      'Enable notifications below to receive a quiet change reminder.',
                    ),
                  ]),
                _section('Notifications', [
                  Text(
                    _notifications == null
                        ? 'Notification status unavailable'
                        : _notifications!
                        ? 'Permission granted'
                        : 'Permission not granted',
                  ),
                  const Text(
                    'Notification permission alone does not enable background security monitoring.',
                  ),
                  TextButton(
                    onPressed: _settingsBusy
                        ? null
                        : () => _settings(NotificationService.enableAlerts),
                    child: Text(
                      _notifications == true
                          ? 'Notification settings'
                          : 'Enable notifications',
                    ),
                  ),
                ]),
                if (ios)
                  _section('Review on your iPhone or iPad', [
                    const Text(
                      'In Settings, review Privacy & Security permissions, General → Software Update, and General → VPN & Device Management. Dhalam cannot inspect these settings for you.',
                    ),
                    TextButton(
                      onPressed: _settingsBusy
                          ? null
                          : () => _settings(SecurityScanner.openAppSettings),
                      child: const Text('Open Dhalam settings'),
                    ),
                  ]),
                if (observation != null) ...[
                  Text(
                    'Checked: ${DateTime.fromMillisecondsSinceEpoch(observation.observedAt).toLocal()}',
                  ),
                  _section('Device security', [
                    for (final signal in observation.deviceSignals)
                      ListTile(
                        contentPadding: EdgeInsets.zero,
                        title: Text(signal.label),
                        subtitle: Text(signal.status),
                      ),
                    const Text(
                      'These checks report configuration and availability, not passcode strength or proof of device safety.',
                    ),
                  ]),
                  _section(
                    ios ? 'Platform limitations' : 'Developer settings',
                    [
                      for (final signal in observation.developerSignals)
                        ListTile(
                          contentPadding: EdgeInsets.zero,
                          title: Text(signal.label),
                          subtitle: Text(signal.status),
                        ),
                      if (SecurityScanner.isAndroid)
                        TextButton(
                          onPressed: _settingsBusy
                              ? null
                              : () => _settings(
                                  SecurityScanner.openDeveloperOptions,
                                ),
                          child: const Text('Open developer options'),
                        ),
                    ],
                  ),
                  _section('Accessibility services', [
                    if (observation.services == null)
                      Text(
                        ios
                            ? 'Not available on iOS.'
                            : 'Service information is unavailable.',
                      )
                    else if (observation.services!.isEmpty)
                      const Text('No enabled services reported.')
                    else
                      for (final service in observation.services!)
                        ListTile(
                          contentPadding: EdgeInsets.zero,
                          title: Text(service.name),
                          subtitle: Text(
                            '${service.id ?? 'Identifier unavailable'}\n${service.capabilities.join(', ')}',
                          ),
                        ),
                    if (SecurityScanner.isAndroid)
                      TextButton(
                        onPressed: _settingsBusy
                            ? null
                            : () => _settings(
                                SecurityScanner.openAccessibilitySettings,
                              ),
                        child: const Text('Open accessibility settings'),
                      ),
                  ]),
                  _section('App permissions', [
                    TextField(
                      onChanged: (value) =>
                          setState(() => _search = value.toLowerCase()),
                      decoration: const InputDecoration(
                        labelText: 'Search apps, package IDs or permissions',
                        prefixIcon: Icon(Icons.search),
                      ),
                    ),
                    FilterChip(
                      label: const Text('Sensitive permissions'),
                      selected: _sensitiveOnly,
                      onSelected: (value) =>
                          setState(() => _sensitiveOnly = value),
                    ),
                    const Text(
                      'Sensitive access includes SMS, contacts, microphone, camera, location and overlays. A grant is not proof of misuse.',
                    ),
                    if (observation.permissionInventory == null)
                      Text(
                        ios
                            ? 'iOS does not expose other apps’ permission inventories.'
                            : 'Permission inventory is unavailable.',
                      )
                    else ...[
                      Text(
                        '${observation.permissionInventory!.packageCount} visible packages. Android may limit which apps are visible.',
                      ),
                      for (final permission
                          in observation.permissionInventory!.permissions.where(
                            (p) =>
                                (!_sensitiveOnly ||
                                    RegExp(
                                      r'sms|contacts|audio|camera|location|overlay|system_alert',
                                      caseSensitive: false,
                                    ).hasMatch(p.name)) &&
                                (p.name.toLowerCase().contains(_search) ||
                                    p.apps.any(
                                      (a) => '${a.label} ${a.packageName}'
                                          .toLowerCase()
                                          .contains(_search),
                                    )),
                          ))
                        ExpansionTile(
                          title: Text(permission.name),
                          subtitle: Text('${permission.grantedCount} granted'),
                          children: [
                            for (final app in permission.apps.where(
                              (a) =>
                                  permission.name.toLowerCase().contains(
                                    _search,
                                  ) ||
                                  '${a.label} ${a.packageName}'
                                      .toLowerCase()
                                      .contains(_search),
                            ))
                              ListTile(
                                title: Text(app.label),
                                subtitle: Text(
                                  '${app.packageName}\n${app.granted == null
                                      ? 'Unknown'
                                      : app.granted!
                                      ? 'Granted'
                                      : 'Not granted'}',
                                ),
                              ),
                          ],
                        ),
                    ],
                  ]),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }
}
