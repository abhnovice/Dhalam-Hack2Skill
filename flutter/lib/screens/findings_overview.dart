import 'dart:math' as math;
import 'package:flutter/material.dart';
import '../features/security_tools.dart';
import '../l10n/app_language.dart';

class FindingsOverview extends StatelessWidget {
  final List<Finding> findings;
  final String? selected;
  final ValueChanged<String?> onSelected;
  const FindingsOverview({
    super.key,
    required this.findings,
    required this.selected,
    required this.onSelected,
  });
  @override
  Widget build(BuildContext context) {
    final states = {
      'Needs attention',
      'Configured',
      'Manual review',
      'Unavailable',
      ...findings.map((f) => f.state),
    }.toList();
    final counts = [
      for (final state in states)
        findings.where((f) => f.state == state).length,
    ];
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.symmetric(vertical: 16),
          child: Row(
            children: [
              Semantics(
                label: tr('{n} recorded checks; breakdown follows', {
                  'n': findings.length,
                }),
                child: SizedBox(
                  width: 100,
                  height: 100,
                  child: CustomPaint(
                    painter: _Donut(
                      counts,
                      Theme.of(context).colorScheme.outlineVariant,
                    ),
                    child: Center(
                      child: Text(
                        '${findings.length}',
                        style: Theme.of(context).textTheme.headlineMedium,
                      ),
                    ),
                  ),
                ),
              ),
              const SizedBox(width: 20),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      tr('Your device review'),
                      style: Theme.of(context).textTheme.titleMedium,
                    ),
                    Text(tr('{n} need attention', {'n': counts.first})),
                    Text(
                      tr('Recorded checks, not a safety score'),
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
        LayoutBuilder(
          builder: (context, constraints) => Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              for (var index = 0; index < states.length; index++)
                SizedBox(
                  width: MediaQuery.textScalerOf(context).scale(14) > 24
                      ? constraints.maxWidth
                      : (constraints.maxWidth - 8) / 2,
                  child: Semantics(
                    selected: selected == states[index],
                    child: Card.outlined(
                      color: selected == states[index]
                          ? Theme.of(context).colorScheme.secondaryContainer
                          : Theme.of(context).colorScheme.surface,
                      margin: EdgeInsets.zero,
                      child: InkWell(
                        key: ValueKey('finding-filter-${states[index]}'),
                        borderRadius: BorderRadius.circular(12),
                        onTap: () => onSelected(
                          selected == states[index] ? null : states[index],
                        ),
                        child: Padding(
                          padding: const EdgeInsets.all(12),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                '${counts[index]}',
                                style: Theme.of(context).textTheme.titleLarge,
                              ),
                              Text(
                                trText(states[index]),
                                style: Theme.of(context).textTheme.labelLarge,
                              ),
                            ],
                          ),
                        ),
                      ),
                    ),
                  ),
                ),
            ],
          ),
        ),
      ],
    );
  }
}

class _Donut extends CustomPainter {
  final List<int> counts;
  final Color track;
  _Donut(this.counts, this.track);
  @override
  void paint(Canvas canvas, Size size) {
    final rect = (Offset.zero & size).deflate(8);
    final paint = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = 12;
    final total = counts.fold(0, (a, b) => a + b);
    if (total == 0) {
      canvas.drawArc(rect, 0, math.pi * 2, false, paint..color = track);
      return;
    }
    const colors = [
      Color(0xFF9A5B00),
      Color(0xFF00796B),
      Color(0xFF54746B),
      Color(0xFF9CAFA7),
    ];
    var start = -math.pi / 2;
    for (var index = 0; index < counts.length; index++) {
      final sweep = math.pi * 2 * counts[index] / total;
      if (sweep > 0) {
        canvas.drawArc(
          rect,
          start,
          sweep - math.min(.04, sweep / 2),
          false,
          paint..color = colors[index % colors.length],
        );
      }
      start += sweep;
    }
  }

  @override
  bool shouldRepaint(covariant _Donut oldDelegate) =>
      oldDelegate.counts != counts || oldDelegate.track != track;
}
