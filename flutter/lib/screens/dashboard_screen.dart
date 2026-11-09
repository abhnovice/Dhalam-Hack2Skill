import 'dart:async';
import '../features/security_tools.dart';
import '../l10n/app_language.dart';
import 'security_tools_screen.dart';
import 'triage_screen.dart';
import 'sensitive_permission_chart.dart';
import 'package:flutter/material.dart';
import 'package:material_symbols_icons/symbols.dart';
import '../models/security_models.dart';
import '../services/notification_service.dart';
import '../services/security_scanner.dart';

class DashboardScreen extends StatefulWidget {
  const DashboardScreen({super.key});

  @override
  State<DashboardScreen> createState() => _DashboardScreenState();
}

class _DashboardScreenState extends State<DashboardScreen>
    with WidgetsBindingObserver, LanguageListener {
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
  bool _sensitiveOnly = true;
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
        ).showSnackBar(SnackBar(content: Text(trText(error.toString()))));
      }
    } finally {
      if (mounted) setState(() => _settingsBusy = false);
    }
  }

  Widget _section(String title, List<Widget> children) => Card.outlined(
    color: Theme.of(context).colorScheme.surface,
    child: ExpansionTile(
      title: Text(title, style: Theme.of(context).textTheme.titleMedium),
      childrenPadding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
      expandedCrossAxisAlignment: CrossAxisAlignment.start,
      children: children,
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
          const LanguageMenuButton(),
          TextButton(onPressed: _openTools, child: Text(tr('Security tools'))),
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
                  icon: const Icon(Symbols.chat),
                  label: Text(tr('Ask Dhalam — check a suspicious request')),
                ),
                const SizedBox(height: 16),
                Text(
                  tr('Device overview'),
                  style: Theme.of(context).textTheme.headlineMedium,
                ),
                const SizedBox(height: 12),
                Text(
                  tr(
                    ios
                        ? 'iOS privacy overview. Apple limits access to other apps and system settings. This is not a malware scan or a device safety verdict.'
                        : 'Review the security information available to Dhalam. These observations do not determine whether your device is safe.',
                  ),
                ),
                const SizedBox(height: 16),
                FilledButton.icon(
                  onPressed: _scanning ? null : _scan,
                  icon: const Icon(Symbols.shield),
                  label: Text(
                    tr(_scanning ? 'Checking…' : 'Run security check'),
                  ),
                ),
                if (_scanning) const LinearProgressIndicator(),
                if (!ios && observation?.permissionInventory != null)
                  SensitivePermissionChart(
                    inventory: observation!.permissionInventory!,
                  ),

                if (_error != null)
                  Padding(
                    padding: const EdgeInsets.all(16),
                    child: Text(
                      trText(_error!),
                      style: TextStyle(
                        color: Theme.of(context).colorScheme.error,
                      ),
                    ),
                  ),
                ExpansionTile(
                  tilePadding: EdgeInsets.zero,
                  leading: const Icon(Symbols.screen_share),
                  title: Text(
                    tr('Screen capture · {status}', {
                      'status': trText(_capture),
                    }),
                  ),
                  children: [
                    Padding(
                      padding: const EdgeInsets.all(12),
                      child: Text(
                        tr(
                          'Supported detection while Dhalam is visible only. This cannot detect every recording or capture of other apps.',
                        ),
                      ),
                    ),
                  ],
                ),
                OutlinedButton(
                  onPressed: _openTools,
                  child: Text(
                    tr('Findings, history, privacy review and reports'),
                  ),
                ),
                if (SecurityScanner.isAndroid)
                  _section(tr('Periodic local checks'), [
                    SwitchListTile(
                      contentPadding: EdgeInsets.zero,
                      title: Text(
                        tr('Review access changes in the background'),
                      ),
                      value: _background?['enabled'] == true,
                      onChanged: _settingsBusy || _background == null
                          ? null
                          : _setBackground,
                    ),
                    Text(
                      tr(
                        'Opt-in, on-device checks approximately every 15 minutes or later when Android permits. No cloud uploads, continuous screen monitoring or automatic blocking. Turn off to clear the background baseline.',
                      ),
                    ),
                    Text(
                      _background == null
                          ? tr('Background scheduling unavailable.')
                          : (_background!['checkedAt'] as int? ?? 0) == 0
                          ? tr('No scheduled check has completed yet.')
                          : tr(
                              'Last scheduled check: {time} · {n} access changes.',
                              {
                                'time': stamp(_background!['checkedAt'] as int),
                                'n': _background!['changes'],
                              },
                            ),
                    ),
                    Text(
                      tr(
                        'Enable notifications below to receive a quiet change reminder.',
                      ),
                    ),
                  ]),
                _section(tr('Notifications'), [
                  Text(
                    tr(
                      _notifications == null
                          ? 'Notification status unavailable'
                          : _notifications!
                          ? 'Permission granted'
                          : 'Permission not granted',
                    ),
                  ),
                  Text(
                    tr(
                      'Notification permission alone does not enable background security monitoring.',
                    ),
                  ),
                  TextButton(
                    onPressed: _settingsBusy
                        ? null
                        : () => _settings(NotificationService.enableAlerts),
                    child: Text(
                      tr(
                        _notifications == true
                            ? 'Notification settings'
                            : 'Enable notifications',
                      ),
                    ),
                  ),
                ]),
                if (ios)
                  _section(tr('Review on your iPhone or iPad'), [
                    Text(
                      tr(
                        'In Settings, review Privacy & Security permissions, General → Software Update, and General → VPN & Device Management. Dhalam cannot inspect these settings for you.',
                      ),
                    ),
                    TextButton(
                      onPressed: _settingsBusy
                          ? null
                          : () => _settings(SecurityScanner.openAppSettings),
                      child: Text(tr('Open Dhalam settings')),
                    ),
                  ]),
                if (observation != null) ...[
                  Text(
                    tr('Checked: {time}', {
                      'time': DateTime.fromMillisecondsSinceEpoch(
                        observation.observedAt,
                      ).toLocal(),
                    }),
                  ),
                  _section(tr('Device security'), [
                    for (final signal in observation.deviceSignals)
                      ListTile(
                        contentPadding: EdgeInsets.zero,
                        title: Text(trText(signal.label)),
                        subtitle: Text(trText(signal.status)),
                      ),
                    Text(
                      tr(
                        'These checks report configuration and availability, not passcode strength or proof of device safety.',
                      ),
                    ),
                  ]),
                  _section(
                    tr(ios ? 'Platform limitations' : 'Developer settings'),
                    [
                      for (final signal in observation.developerSignals)
                        ListTile(
                          contentPadding: EdgeInsets.zero,
                          title: Text(trText(signal.label)),
                          subtitle: Text(trText(signal.status)),
                        ),
                      if (SecurityScanner.isAndroid)
                        TextButton(
                          onPressed: _settingsBusy
                              ? null
                              : () => _settings(
                                  SecurityScanner.openDeveloperOptions,
                                ),
                          child: Text(tr('Open developer options')),
                        ),
                    ],
                  ),
                  _section(tr('Accessibility services'), [
                    if (observation.services == null)
                      Text(
                        tr(
                          ios
                              ? 'Not available on iOS.'
                              : 'Service information is unavailable.',
                        ),
                      )
                    else if (observation.services!.isEmpty)
                      Text(tr('No enabled services reported.'))
                    else
                      for (final service in observation.services!)
                        ListTile(
                          contentPadding: EdgeInsets.zero,
                          title: Text(service.name),
                          subtitle: Text(
                            '${service.id ?? tr('Identifier unavailable')}\n${service.capabilities.map(trText).join(', ')}',
                          ),
                        ),
                    if (SecurityScanner.isAndroid)
                      TextButton(
                        onPressed: _settingsBusy
                            ? null
                            : () => _settings(
                                SecurityScanner.openAccessibilitySettings,
                              ),
                        child: Text(tr('Open accessibility settings')),
                      ),
                  ]),
                  _section(tr('App permissions'), [
                    if (ios) ...[
                      ListTile(
                        leading: const Icon(Symbols.checklist),
                        title: Text(tr('Review permissions on your iPhone')),
                        subtitle: Text(
                          tr(
                            '1. Open Settings → Privacy & Security.\n2. Choose a permission such as Camera.\n3. Review apps and remove access you no longer need.',
                          ),
                        ),
                      ),
                      TextButton(
                        onPressed: _openTools,
                        child: Text(
                          tr('Open manual privacy review in Security tools'),
                        ),
                      ),
                    ],
                    if (!ios)
                      TextField(
                        onChanged: (value) =>
                            setState(() => _search = value.toLowerCase()),
                        decoration: InputDecoration(
                          labelText: tr(
                            'Search apps, package IDs or permissions',
                          ),
                          prefixIcon: const Icon(Symbols.search),
                        ),
                      ),
                    if (!ios)
                      FilterChip(
                        label: Text(tr('Sensitive permissions only')),
                        selected: _sensitiveOnly,
                        onSelected: (value) =>
                            setState(() => _sensitiveOnly = value),
                      ),
                    Text(
                      tr(
                        'Sensitive access includes SMS, contacts, microphone, camera, location and overlays. A grant is not proof of misuse.',
                      ),
                    ),
                    if (observation.permissionInventory == null)
                      Text(
                        tr(
                          ios
                              ? 'iOS does not expose other apps’ permission inventories.'
                              : 'Permission inventory is unavailable.',
                        ),
                      )
                    else ...[
                      Text(
                        tr(
                          '{n} visible packages. Android may limit which apps are visible.',
                          {'n': observation.permissionInventory!.packageCount},
                        ),
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
                          subtitle: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                tr('{n} visible apps with permission', {
                                  'n': permission.apps
                                      .where((a) => a.granted == true)
                                      .map((a) => a.packageName)
                                      .toSet()
                                      .length,
                                }),
                              ),
                              LinearProgressIndicator(
                                value: permission.apps.isEmpty
                                    ? 0
                                    : permission.apps
                                              .where((a) => a.granted == true)
                                              .map((a) => a.packageName)
                                              .toSet()
                                              .length /
                                          permission.apps
                                              .map((a) => a.packageName)
                                              .toSet()
                                              .length,
                                semanticsLabel: tr(
                                  'Fraction of listed apps with this permission granted; not actual use',
                                ),
                              ),
                              Text(tr('Granted access, not actual use')),
                            ],
                          ),
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
                                  '${app.packageName}\n${tr(app.granted == null
                                      ? 'Unknown'
                                      : app.granted!
                                      ? 'Granted'
                                      : 'Not granted')}',
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
