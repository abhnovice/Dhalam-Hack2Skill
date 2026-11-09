import 'dart:convert';
import 'dart:async';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import '../l10n/app_language.dart';
import '../models/security_models.dart';

const toolsChannel = MethodChannel('com.dhalam.ap/tools');
final _captureEvents = StreamController<String>.broadcast();
bool _captureEventsRegistered = false;
Stream<String> get captureEvents {
  if (!_captureEventsRegistered) {
    _captureEventsRegistered = true;
    toolsChannel.setMethodCallHandler((call) async {
      if (call.method == 'captureChanged') {
        _captureEvents.add(call.arguments as String? ?? 'Unavailable');
      }
    });
  }
  return _captureEvents.stream;
}

class Finding {
  final String title, detail, state, action;
  const Finding(this.title, this.detail, this.state, this.action);
  Map<String, dynamic> toJson() => {
    'title': title,
    'detail': detail,
    'state': state,
    'action': action,
  };
  factory Finding.fromJson(Map<String, dynamic> m) => Finding(
    m['title'] as String,
    m['detail'] as String,
    m['state'] as String,
    m['action'] as String,
  );
}

Finding explain(String title, String detail) {
  final s = detail.toLowerCase();
  if (s.contains('unavailable') || s.contains('unknown')) {
    return Finding(
      title,
      detail,
      'Unavailable',
      'Review this setting manually; no passing result was recorded.',
    );
  }
  if (title == 'Device lock') {
    final missing = s.startsWith('not configured');
    return Finding(
      title,
      detail,
      missing ? 'Needs attention' : 'Configured',
      missing
          ? 'Set a device passcode or PIN in system Settings.'
          : 'Use a strong, private passcode. Its strength cannot be measured here.',
    );
  }
  if (title == 'Biometric authentication') {
    return Finding(
      title,
      detail,
      s.startsWith('ready') ? 'Configured' : 'Needs attention',
      s.startsWith('ready')
          ? 'Review enrolled biometrics in Settings if you share this device.'
          : 'Review biometric settings. For a temporary lockout, unlock with your device passcode.',
    );
  }
  if (title == 'Notifications') {
    return Finding(
      title,
      detail,
      detail == 'Permission granted' ? 'Configured' : 'Needs attention',
      'Use Dhalam notification settings to manage permission. Permission alone does not enable monitoring.',
    );
  }
  if (title == 'Screen capture') {
    return Finding(
      title,
      detail,
      detail == 'Active' ? 'Needs attention' : 'Configured',
      detail == 'Active'
          ? 'Dhalam is visible in a capture session. Stop recording or sharing before viewing private reports if unintended.'
          : 'No active capture reported for Dhalam. This does not rule out every capture method.',
    );
  }
  return Finding(
    title,
    detail,
    'Manual review',
    title == 'Operating system'
        ? 'Open Software Update in Settings. The version alone does not establish update status.'
        : 'Review the reported setting and access in system Settings. Enabled access is not proof of abuse.',
  );
}

class CheckRecord {
  final int at;
  final List<Finding> findings;
  final Map<String, String>? access;
  CheckRecord(this.at, this.findings, [this.access]);
  Map<String, dynamic> toJson() => {
    'at': at,
    'findings': findings.map((f) => f.toJson()).toList(),
    'access': access,
  };
  factory CheckRecord.fromJson(Map<String, dynamic> m) => CheckRecord(
    m['at'] as int,
    (m['findings'] as List)
        .map((f) => Finding.fromJson(Map<String, dynamic>.from(f as Map)))
        .toList(),
    m['access'] == null ? null : Map<String, String>.from(m['access'] as Map),
  );
}

class SecurityJournal extends ChangeNotifier {
  List<CheckRecord> records = [];
  Map<String, int> reviewed = {};
  String? storageError;
  bool _readFailed = false;

  Future<void> load() async {
    try {
      final raw = await toolsChannel.invokeMethod<String>('loadJournal');
      if (raw != null && raw.isNotEmpty) {
        final data = jsonDecode(raw) as Map<String, dynamic>;
        if (data['version'] != 1) {
          throw const FormatException('Unsupported journal version');
        }
        records = (data['records'] as List)
            .map(
              (r) => CheckRecord.fromJson(Map<String, dynamic>.from(r as Map)),
            )
            .take(30)
            .toList();
        reviewed = Map<String, int>.from(data['reviewed'] as Map);
      }
    } catch (_) {
      _readFailed = true;
      storageError =
          'Could not read saved history. Existing storage will not be overwritten. Clear local history to reset it.';
    }
    notifyListeners();
  }

  Future<void> _save() async {
    if (_readFailed) return;
    try {
      String encode() => jsonEncode({
        'version': 1,
        'records': records.map((r) => r.toJson()).toList(),
        'reviewed': reviewed,
      });
      var payload = encode();
      // Detailed inventories vary greatly by device. Respect both native limits.
      while (records.length > 1 && utf8.encode(payload).length > 1800000) {
        records = records.take(records.length - 1).toList();
        payload = encode();
      }
      await toolsChannel.invokeMethod<void>('saveJournal', payload);
      storageError = null;
    } catch (_) {
      storageError =
          'Changes are visible for this session but could not be saved on this device.';
    }
    notifyListeners();
  }

  Future<void> record(
    SecurityObservation o,
    bool? notifications,
    String capture,
  ) async {
    if (records.any((r) => r.at == o.observedAt)) return;
    final signals = [...o.deviceSignals, ...o.developerSignals];
    final findings = signals.map((s) => explain(s.label, s.status)).toList();
    findings.add(
      explain(
        'Notifications',
        notifications == null
            ? 'Unavailable'
            : notifications
            ? 'Permission granted'
            : 'Permission not granted',
      ),
    );
    findings.add(explain('Screen capture', capture));
    findings.add(
      explain(
        'Accessibility inventory',
        o.services == null
            ? 'Unavailable'
            : '${o.services!.length} enabled services reported',
      ),
    );
    findings.add(
      explain(
        'Permission inventory',
        o.permissionInventory == null
            ? 'Unavailable'
            : '${o.permissionInventory!.packageCount} visible packages; ${o.permissionInventory!.permissions.fold<int>(0, (sum, p) => sum + p.grantedCount)} granted app-permission pairs',
      ),
    );
    records = [
      CheckRecord(o.observedAt, findings, {
        if (o.services != null) 'coverage:accessibility': 'available',
        if (o.permissionInventory != null) 'coverage:permissions': 'available',
        for (final service in o.services ?? <ObservedAccessibilityService>[])
          if (service.id != null)
            'service:${service.id}': service.capabilities.join(', '),
        for (final permission
            in o.permissionInventory?.permissions ?? <PermissionReport>[])
          for (final app in permission.apps)
            'permission:${app.packageName}:${permission.name}':
                app.granted == null
                ? 'unknown'
                : app.granted!
                ? 'granted'
                : 'not granted',
      }),
      ...records,
    ].take(30).toList();
    await _save();
    notifyListeners();
  }

  Future<void> review(String id, bool checked) async {
    if (checked) {
      reviewed[id] = DateTime.now().millisecondsSinceEpoch;
    } else {
      reviewed.remove(id);
    }
    await _save();
    notifyListeners();
  }

  Future<void> clear() async {
    await toolsChannel.invokeMethod<void>(
      'saveJournal',
      jsonEncode({'version': 1, 'records': [], 'reviewed': {}}),
    );
    records = [];
    reviewed = {};
    _readFailed = false;
    storageError = null;
    notifyListeners();
  }

  int? lastSuccess(String title) {
    for (final r in records) {
      if (r.findings.any((f) => f.title == title && f.state != 'Unavailable')) {
        return r.at;
      }
    }
    return null;
  }

  List<String> get changes => changesAt(0);

  List<String> changesAt(int index) {
    if (index < 0 || index >= records.length) return [];
    if (index + 1 >= records.length) {
      return [tr('Run another check to compare observations.')];
    }
    final before = {for (final f in records[index + 1].findings) f.title: f};
    final changes = <String>[];
    for (final f in records[index].findings) {
      final old = before[f.title];
      if (old == null) {
        changes.add(tr('{t}: first observation.', {'t': trText(f.title)}));
        continue;
      }
      if (old.state == 'Unavailable' || f.state == 'Unavailable') {
        changes.add(
          tr(
            '{t}: comparison unavailable; one of the two reads was unavailable.',
            {'t': trText(f.title)},
          ),
        );
      } else if (old.detail != f.detail) {
        changes.add(
          tr('{t}: {old} → {new}', {
            't': trText(f.title),
            'old': trText(old.detail),
            'new': trText(f.detail),
          }),
        );
      }
    }
    final currentAccess = records[index].access,
        previousAccess = records[index + 1].access;
    if (currentAccess != null && previousAccess != null) {
      for (final group in ['service:', 'permission:']) {
        final coverage = group == 'service:'
            ? 'coverage:accessibility'
            : 'coverage:permissions';
        if (!currentAccess.containsKey(coverage) ||
            !previousAccess.containsKey(coverage)) {
          continue;
        }
        final keys = {
          ...currentAccess.keys,
          ...previousAccess.keys,
        }.where((key) => key.startsWith(group));
        for (final key in keys) {
          final old = previousAccess[key], now = currentAccess[key];
          if (old == now) continue;
          String shown(String? value) => value == null
              ? tr('not observed')
              : value.split(', ').map(tr).join(', ');
          changes.add(
            tr(
              '{key}: {old} → {new} (visibility can change; not proof of misuse)',
              {'key': key, 'old': shown(old), 'new': shown(now)},
            ),
          );
        }
      }
    }
    return changes.isEmpty
        ? [tr('No changes in the comparable checks.')]
        : changes;
  }

  String report(bool ios) {
    final b = StringBuffer(
      '${tr('Dhalam security report')}\n${tr('Generated: {t}', {'t': DateTime.now().toUtc().toIso8601String()})}\n${tr('Platform: {p}', {'p': tr(ios ? 'iOS' : 'Android')})}\n${tr('Configuration observations, not a malware scan or safety verdict.')}\n${tr('History contains the latest 30 successful foreground checks. New records include observed service IDs and permission states; older records contain summaries only.')}\n\n',
    );
    if (storageError != null) b.writeln(trText(storageError!));
    b.writeln(tr('CHANGES SINCE PREVIOUS CHECK'));
    for (final c in changes) {
      b.writeln(c);
    }
    b.writeln('\n${tr('OBSERVATIONS (newest first)')}');
    if (records.isEmpty) b.writeln(tr('No check recorded.'));
    for (final r in records) {
      b.writeln('\n${tr('Checked: {t}', {'t': stamp(r.at)})}');
      for (final f in r.findings) {
        b.writeln(
          '${trText(f.title)} [${trText(f.state)}]: ${trText(f.detail)}\n${tr('Next step: {a}', {'a': trText(f.action)})}',
        );
      }
    }
    b.writeln(
      '\n${tr('MANUAL REVIEW — user-confirmed, not automatically verified')}',
    );
    for (final entry in reviewSteps(ios).entries) {
      b.writeln(
        '${trText(entry.key)}: ${reviewed[entry.key] == null ? tr('Not confirmed') : tr('User-confirmed {t}', {'t': stamp(reviewed[entry.key]!)})}',
      );
    }
    b.writeln(
      '\n${tr('Unavailable readings are not passing results. Capture observations only describe supported detection while Dhalam is visible. Pasted links are never included in this report.')}',
    );
    return b.toString();
  }
}

String stamp(int at) =>
    DateTime.fromMillisecondsSinceEpoch(at).toUtc().toIso8601String();

Map<String, String> reviewSteps(bool ios) => {
  'App permissions': ios
      ? 'Settings → Privacy & Security. Review access to location, camera, microphone and contacts; remove access you no longer need.'
      : 'Settings → Privacy → Permission manager (names vary by device). Review location, camera, microphone and contacts.',
  'Account security': ios
      ? 'Settings → your name → Sign-In & Security. Review trusted devices and two-factor authentication.'
      : 'Settings → Google → Manage your Google Account → Security. Review devices and 2-Step Verification.',
  'Software updates': ios
      ? 'Settings → General → Software Update. Review available updates and automatic updates.'
      : 'Settings → System → Software update. Also review the Google Play system update in Security settings.',
  'Device management': ios
      ? 'Settings → General → VPN & Device Management. Review unfamiliar profiles with your administrator before removing work or school profiles.'
      : 'Settings → Security → Device admin apps, and Network → VPN. Review unfamiliar access; ask your administrator about work profiles.',
  'Sharing and access': ios
      ? 'On iPhone with iOS 16+: Settings → Privacy & Security → Safety Check → Manage Sharing & Access. Review people, apps and devices. Changes to sharing may be noticed by others. On iPad, review sharing and permissions individually. Dhalam cannot read or operate Safety Check.'
      : 'Review Google Account → People & sharing → Location sharing, Maps location sharing, and Privacy → Permission manager. Android has no identical Apple Safety Check; review each separately. Changes to sharing may be noticed by others.',
};

class LinkInspection {
  final String host;
  final List<String> notes;
  final String scheme, path;
  LinkInspection(this.host, this.notes, {this.scheme = '', this.path = ''});
}

LinkInspection inspectLink(String input) {
  final text = input.trim();
  if (text.isEmpty ||
      text.length > 4096 ||
      RegExp(r'[\s\x00-\x1f\x7f\\]').hasMatch(text)) {
    return LinkInspection('', [
      tr(
        'Enter one URL up to 4096 characters without spaces, control characters or backslashes.',
      ),
    ]);
  }
  final uri = Uri.tryParse(text.contains('://') ? text : 'https://$text');
  if (uri == null ||
      !['http', 'https'].contains(uri.scheme) ||
      uri.host.isEmpty) {
    return LinkInspection('', [
      tr('Only a valid HTTP or HTTPS URL can be inspected.'),
    ]);
  }
  String host;
  try {
    host = Uri.decodeComponent(uri.host).toLowerCase();
  } on FormatException {
    return LinkInspection('', [tr('Invalid encoded hostname.')]);
  }
  if ((uri.hasPort && (uri.port < 1 || uri.port > 65535)) ||
      RegExp(
        r'[\s\x00-\x1f\x7f\\/@?#%\u202a-\u202e\u2066-\u2069]',
      ).hasMatch(host)) {
    return LinkInspection('', [
      tr(
        'Ambiguous hostname or invalid port. Verify the address independently.',
      ),
    ]);
  }
  if (!host.contains(':')) {
    final labels =
        (host.endsWith('.') ? host.substring(0, host.length - 1) : host).split(
          '.',
        );
    if (labels.any(
      (label) =>
          label.isEmpty ||
          label.startsWith('-') ||
          label.endsWith('-') ||
          !RegExp(r'^[a-z0-9\u0080-\uffff-]+$').hasMatch(label),
    )) {
      return LinkInspection('', [
        tr('Invalid hostname. Verify the address independently.'),
      ]);
    }
  }
  final notes = <String>[];
  if (!text.contains('://')) {
    notes.add(tr('No scheme supplied; interpreted as HTTPS for display only.'));
  }
  if (uri.scheme == 'http') {
    notes.add(tr('HTTP does not provide HTTPS transport encryption.'));
  }
  if (uri.userInfo.isNotEmpty) {
    notes.add(
      tr(
        'Contains user information before @. The destination is {host}, not the text before @.',
        {'host': host},
      ),
    );
  }
  if (RegExp(r'[^\x00-\x7f]').hasMatch(host) || host.contains('xn--')) {
    notes.add(
      tr(
        'Internationalized hostname: check for look-alike letters or encoded domain labels.',
      ),
    );
  }
  if (host.split('.').length > 3) {
    notes.add(
      tr(
        'Many domain labels: brand names on the left can be misleading. Verify the entire hostname independently.',
      ),
    );
  }
  if (RegExp(r'^\d+(\.\d+){3}$').hasMatch(host) ||
      host.contains(':') ||
      RegExp(r'^\d+$').hasMatch(host)) {
    notes.add(tr('Numeric/IP destination: verify who operates it.'));
  }
  if (uri.hasPort && ![80, 443].contains(uri.port)) {
    notes.add(tr('Uses an unusual port: {port}.', {'port': uri.port}));
  }
  if (notes.isEmpty) {
    notes.add(
      tr(
        'No listed structural indicators found. This does not establish that the link is safe.',
      ),
    );
  }
  notes.add(
    tr(
      'No website was opened or fetched. Redirects, reputation and page content were not checked. HTTPS alone does not mean trustworthy.',
    ),
  );
  return LinkInspection(
    host,
    notes,
    scheme: uri.scheme,
    path: uri.path.isEmpty ? '/' : uri.path,
  );
}
