import 'package:flutter/material.dart';
import '../l10n/app_language.dart';
import '../models/security_models.dart';

class SensitivePermissionChart extends StatelessWidget {
  final PermissionInventory inventory;
  const SensitivePermissionChart({super.key, required this.inventory});

  @override
  Widget build(BuildContext context) {
    const permissions = {
      'Camera': 'android.permission.CAMERA',
      'Microphone': 'android.permission.RECORD_AUDIO',
      'Location': 'android.permission.ACCESS_FINE_LOCATION',
      'Contacts': 'android.permission.READ_CONTACTS',
      'Messages': 'android.permission.READ_SMS',
    };
    return Card.outlined(
      color: Theme.of(context).colorScheme.surface,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              tr('Sensitive permissions'),
              style: Theme.of(context).textTheme.titleMedium,
            ),
            Text(tr('Apps with granted access · tap to review')),
            for (final entry in permissions.entries)
              Builder(
                builder: (context) {
                  final matches = inventory.permissions.where(
                    (p) => p.name == entry.value,
                  );
                  final report = matches.isEmpty ? null : matches.first;
                  final count = report?.apps
                      .where((a) => a.granted == true)
                      .map((a) => a.packageName)
                      .toSet()
                      .length;
                  return ExpansionTile(
                    tilePadding: EdgeInsets.zero,
                    title: Row(
                      children: [
                        Expanded(child: Text(tr(entry.key))),
                        Text(
                          count?.toString() ?? tr('Not observed'),
                          style: Theme.of(context).textTheme.bodySmall,
                        ),
                      ],
                    ),
                    subtitle: count == null
                        ? null
                        : LinearProgressIndicator(
                            value: inventory.packageCount == 0
                                ? 0
                                : (count / inventory.packageCount).clamp(0, 1),
                            semanticsLabel: tr(
                              '{name}: {count} of {n} visible apps have granted access',
                              {
                                'name': tr(entry.key),
                                'count': count,
                                'n': inventory.packageCount,
                              },
                            ),
                          ),
                    children: [
                      if (report == null)
                        Text(tr('This permission was not observed.'))
                      else if (count == 0)
                        Text(
                          tr('No granted access reported among visible apps.'),
                        )
                      else
                        for (final app in report.apps.where(
                          (a) => a.granted == true,
                        ))
                          ListTile(
                            title: Text(app.label),
                            subtitle: Text(app.packageName),
                          ),
                    ],
                  );
                },
              ),
            Text(
              tr('Out of {n} visible apps. Access does not mean use.', {
                'n': inventory.packageCount,
              }),
              style: Theme.of(context).textTheme.bodySmall,
            ),
          ],
        ),
      ),
    );
  }
}
