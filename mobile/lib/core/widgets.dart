import 'package:flutter/material.dart';
import 'l10n.dart';

class StatusView extends StatelessWidget {
  const StatusView({super.key, this.loading = false, this.error, this.empty, this.onRetry, this.child});

  final bool loading;
  final String? error;
  final String? empty;
  final VoidCallback? onRetry;
  final Widget? child;

  @override
  Widget build(BuildContext context) {
    if (loading) {
      return const Center(child: Padding(padding: EdgeInsets.all(32), child: CircularProgressIndicator()));
    }
    if (error != null && error!.isNotEmpty) {
      return Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Text(error!, textAlign: TextAlign.center, style: TextStyle(color: Theme.of(context).colorScheme.error)),
            if (onRetry != null) ...[
              const SizedBox(height: 12),
              FilledButton(onPressed: onRetry, child: Text(I18n.instance.t('retry'))),
            ],
          ],
        ),
      );
    }
    if (empty != null) {
      return Center(child: Padding(padding: const EdgeInsets.all(24), child: Text(empty!, textAlign: TextAlign.center)));
    }
    return child ?? const SizedBox.shrink();
  }
}

class CountBadge extends StatelessWidget {
  const CountBadge({super.key, required this.count, this.label});
  final int count;
  final String? label;

  @override
  Widget build(BuildContext context) {
    if (count <= 0) return const SizedBox.shrink();
    final text = count > 99 ? '99+' : '$count';
    return Semantics(
      label: label ?? text,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
        constraints: const BoxConstraints(minWidth: 20),
        decoration: BoxDecoration(color: const Color(0xFFE11D48), borderRadius: BorderRadius.circular(999)),
        child: Text(text, textAlign: TextAlign.center, style: const TextStyle(color: Colors.white, fontSize: 10, fontWeight: FontWeight.w700)),
      ),
    );
  }
}
