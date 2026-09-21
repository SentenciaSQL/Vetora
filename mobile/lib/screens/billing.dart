import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';
import '../core/auth.dart';
import '../core/config.dart';
import '../core/format.dart';
import '../core/l10n.dart';
import '../core/widgets.dart';

class BillingScreen extends StatefulWidget {
  const BillingScreen({super.key, required this.auth});
  final AuthStore auth;
  @override
  State<BillingScreen> createState() => _BillingScreenState();
}

class _BillingScreenState extends State<BillingScreen> {
  Map<String, dynamic> sub = {};
  bool loading = true;
  String? error;
  bool opening = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      loading = true;
      error = null;
    });
    try {
      sub = asMap(await widget.auth.api.get('/billing/subscription'));
      widget.auth.subscription = sub;
      if (mounted) setState(() => loading = false);
    } catch (e) {
      if (mounted) {
        setState(() {
          loading = false;
          error = userMessage(e);
        });
      }
    }
  }

  Future<void> _openWeb() async {
    if (opening) return;
    setState(() => opening = true);
    try {
      final uri = Uri.parse(AppConfig.billingWebUrl);
      final ok = await launchUrl(uri, mode: LaunchMode.externalApplication);
      if (!ok && mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(I18n.instance.t('billingWebOnly'))));
      }
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(I18n.instance.t('billingWebOnly'))));
      }
    } finally {
      if (mounted) setState(() => opening = false);
    }
  }

  Widget _metric(String label, Map usage) {
    final limit = usage['limit'];
    final unlimited = limit == null || asInt(limit, 0) < 0;
    return ListTile(
      title: Text(label),
      subtitle: Text('${usage['current'] ?? 0} / ${unlimited ? '—' : limit}'),
    );
  }

  String _yesNo(dynamic value) => value == true ? I18n.instance.t('included') : I18n.instance.t('notIncluded');

  @override
  Widget build(BuildContext context) {
    final i = I18n.instance;
    final usage = asMap(sub['usage']);
    final limits = asMap(sub['limits']);
    final hasSub = sub.isNotEmpty && (sub['id'] != null || sub['planCode'] != null || sub['status'] != null);
    return Scaffold(
      appBar: AppBar(title: Text(i.t('billing'))),
      body: RefreshIndicator(
        onRefresh: _load,
        child: loading || error != null
            ? ListView(children: [StatusView(loading: loading, error: error, onRetry: _load)])
            : ListView(
                padding: const EdgeInsets.all(20),
                children: [
                  if (!hasSub)
                    Text(i.t('noSubscription'))
                  else ...[
                    ListTile(title: Text(i.t('plan')), subtitle: Text('${sub['planName'] ?? sub['planCode'] ?? '—'}')),
                    ListTile(title: Text(i.t('billingCycle')), subtitle: Text('${sub['billingCycle'] ?? '—'}')),
                    ListTile(title: Text(i.t('subscriptionStatus')), subtitle: Text('${sub['status'] ?? '—'}')),
                    ListTile(title: Text(i.t('startedAt')), subtitle: Text(formatDay(sub['startedAt']))),
                    ListTile(title: Text(i.t('nextBillingAt')), subtitle: Text(formatDay(sub['nextBillingAt'] ?? sub['currentPeriodEndsAt']))),
                    if (sub['trial'] == true || '${sub['status']}' == 'TRIALING')
                      ListTile(title: Text(i.t('trialEnds')), subtitle: Text(formatDay(sub['currentPeriodEndsAt']))),
                    if (sub['gracePeriod'] == true)
                      ListTile(title: Text(i.t('graceUntil')), subtitle: Text(formatDay(sub['gracePeriodEndsAt']))),
                    if (sub['suspended'] == true)
                      ListTile(title: Text(i.t('suspendedAt')), subtitle: Text(formatDay(sub['suspendedAt']))),
                    if (sub['canceledAt'] != null)
                      ListTile(title: Text(i.t('canceledAt')), subtitle: Text(formatDay(sub['canceledAt']))),
                    ListTile(
                      title: Text(i.t('paymentStatus')),
                      subtitle: Text(sub['gracePeriod'] == true
                          ? i.t('paymentPastDue')
                          : (sub['suspended'] == true ? i.t('paymentSuspended') : i.t('paymentOk'))),
                    ),
                    const Divider(),
                    Text(i.t('planLimits'), style: Theme.of(context).textTheme.titleMedium),
                    _metric(i.t('users'), asMap(usage['users'])),
                    _metric(i.t('veterinarians'), asMap(usage['veterinarians'])),
                    _metric(i.t('branches'), asMap(usage['branches'])),
                    _metric(i.t('storage'), asMap(usage['storageMb'])),
                    _metric(i.t('messagesMonth'), asMap(usage['messagesMonth'])),
                    ListTile(title: Text(i.t('reports')), subtitle: Text(_yesNo(limits['reportsEnabled']))),
                    ListTile(title: Text(i.t('labs')), subtitle: Text(_yesNo(limits['laboratoryEnabled']))),
                    ListTile(title: Text(i.t('messages')), subtitle: Text(_yesNo(limits['messagingEnabled']))),
                  ],
                  const SizedBox(height: 16),
                  Text(i.t('billingWebOnly'), style: Theme.of(context).textTheme.bodyMedium),
                  const SizedBox(height: 12),
                  OutlinedButton.icon(
                    onPressed: opening ? null : _openWeb,
                    icon: const Icon(Icons.open_in_browser),
                    label: Text(i.t('openWebBilling')),
                  ),
                ],
              ),
      ),
    );
  }
}
