// Data models matching the original Android SecurityTelemetry.kt

class ObservedAccessibilityService {
  final String name;
  final String? id;
  final List<String> capabilities;

  const ObservedAccessibilityService({
    required this.name,
    this.id,
    this.capabilities = const [],
  });

  factory ObservedAccessibilityService.fromMap(Map<String, dynamic> map) {
    return ObservedAccessibilityService(
      name: map['name'] as String? ?? 'Unknown',
      id: map['id'] as String?,
      capabilities: List<String>.from(map['capabilities'] ?? []),
    );
  }
}

class DeveloperSignal {
  final String label;
  final String status;

  const DeveloperSignal({required this.label, required this.status});

  factory DeveloperSignal.fromMap(Map<String, dynamic> map) {
    return DeveloperSignal(
      label: map['label'] as String? ?? '',
      status: map['status'] as String? ?? '',
    );
  }
}

class PermissionApp {
  final String packageName;
  final String label;
  final bool? granted;

  const PermissionApp({
    required this.packageName,
    required this.label,
    this.granted,
  });

  factory PermissionApp.fromMap(Map<String, dynamic> map) {
    return PermissionApp(
      packageName: map['packageName'] as String? ?? '',
      label: map['label'] as String? ?? '',
      granted: map['granted'] as bool?,
    );
  }
}

class PermissionReport {
  final String name;
  final List<PermissionApp> apps;

  const PermissionReport({required this.name, required this.apps});

  int get grantedCount => apps.where((a) => a.granted == true).length;

  factory PermissionReport.fromMap(Map<String, dynamic> map) {
    return PermissionReport(
      name: map['name'] as String? ?? '',
      apps:
          (map['apps'] as List?)
              ?.map(
                (a) =>
                    PermissionApp.fromMap(Map<String, dynamic>.from(a as Map)),
              )
              .toList() ??
          [],
    );
  }
}

class PermissionInventory {
  final int packageCount;
  final List<PermissionReport> permissions;

  const PermissionInventory({
    required this.packageCount,
    required this.permissions,
  });

  factory PermissionInventory.fromMap(Map<String, dynamic> map) {
    return PermissionInventory(
      packageCount: map['packageCount'] as int? ?? 0,
      permissions:
          (map['permissions'] as List?)
              ?.map(
                (p) => PermissionReport.fromMap(
                  Map<String, dynamic>.from(p as Map),
                ),
              )
              .toList() ??
          [],
    );
  }
}

class SecurityObservation {
  final int observedAt;
  final List<ObservedAccessibilityService>? services;
  final List<ObservedAccessibilityService>? installedServices;
  final List<DeveloperSignal> developerSignals;
  final List<DeveloperSignal> deviceSignals;
  final PermissionInventory? permissionInventory;

  const SecurityObservation({
    required this.observedAt,
    this.services,
    this.installedServices,
    this.developerSignals = const [],
    this.deviceSignals = const [],
    this.permissionInventory,
  });

  factory SecurityObservation.fromMap(Map<String, dynamic> map) {
    return SecurityObservation(
      observedAt: map['observedAt'] as int? ?? 0,
      services: (map['services'] as List?)
          ?.map(
            (s) => ObservedAccessibilityService.fromMap(
              Map<String, dynamic>.from(s as Map),
            ),
          )
          .toList(),
      installedServices: (map['installedServices'] as List?)
          ?.map(
            (s) => ObservedAccessibilityService.fromMap(
              Map<String, dynamic>.from(s as Map),
            ),
          )
          .toList(),
      deviceSignals:
          (map['deviceSignals'] as List?)
              ?.map(
                (d) => DeveloperSignal.fromMap(
                  Map<String, dynamic>.from(d as Map),
                ),
              )
              .toList() ??
          [],
      developerSignals:
          (map['developerSignals'] as List?)
              ?.map(
                (d) => DeveloperSignal.fromMap(
                  Map<String, dynamic>.from(d as Map),
                ),
              )
              .toList() ??
          [],
      permissionInventory: map['permissionInventory'] != null
          ? PermissionInventory.fromMap(
              Map<String, dynamic>.from(map['permissionInventory'] as Map),
            )
          : null,
    );
  }
}
