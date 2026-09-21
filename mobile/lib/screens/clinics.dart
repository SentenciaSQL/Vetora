import 'package:flutter/material.dart';
import '../core/auth.dart';
import '../core/format.dart';
import '../core/l10n.dart';
import '../core/widgets.dart';
import 'book.dart';
import 'messages.dart';

class ClinicsScreen extends StatefulWidget {
  const ClinicsScreen({super.key, required this.auth, required this.pets});
  final AuthStore auth;
  final List pets;
  @override
  State<ClinicsScreen> createState() => _ClinicsScreenState();
}

class _ClinicsScreenState extends State<ClinicsScreen> {
  List clinics = [];
  bool loading = true;
  String? error;

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
      final related = asList(await widget.auth.api.get('/clinics/related'));
      if (!mounted) return;
      setState(() {
        clinics = related;
        loading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        loading = false;
        error = userMessage(e);
      });
    }
  }

  Future<void> _open(Map clinic) async {
    Map branding = clinic;
    final slug = asString(clinic['slug']);
    if (slug.isNotEmpty) {
      try {
        branding = asMap(await widget.auth.api.get('/public/tenants/$slug/branding'));
      } catch (_) {}
    }
    if (!mounted) return;
    await Navigator.push(context, MaterialPageRoute(builder: (_) => ClinicDetailScreen(auth: widget.auth, clinic: branding, pets: widget.pets)));
  }

  @override
  Widget build(BuildContext context) {
    final i = I18n.instance;
    return Scaffold(
      appBar: AppBar(title: Text(i.t('clinics'))),
      body: RefreshIndicator(
        onRefresh: _load,
        child: loading || error != null || clinics.isEmpty
            ? ListView(children: [StatusView(loading: loading, error: error, empty: loading ? null : i.t('empty'), onRetry: _load)])
            : ListView(
                padding: const EdgeInsets.all(16),
                children: [
                  for (final clinic in clinics)
                    Card(
                      child: ListTile(
                        leading: asString(clinic['logoUrl']).isNotEmpty
                            ? CircleAvatar(backgroundImage: NetworkImage(clinic['logoUrl']))
                            : const CircleAvatar(child: Icon(Icons.local_hospital_outlined)),
                        title: Text('${clinic['commercialName'] ?? clinic['name']}'),
                        subtitle: Text('${clinic['city'] ?? ''} ${clinic['country'] ?? ''}'),
                        onTap: () => _open(asMap(clinic)),
                      ),
                    ),
                ],
              ),
      ),
    );
  }
}

class ClinicDetailScreen extends StatefulWidget {
  const ClinicDetailScreen({super.key, required this.auth, required this.clinic, required this.pets});
  final AuthStore auth;
  final Map clinic;
  final List pets;

  @override
  State<ClinicDetailScreen> createState() => _ClinicDetailScreenState();
}

class _ClinicDetailScreenState extends State<ClinicDetailScreen> {
  Map hours = {};
  Map status = {};
  bool loadingHours = true;

  I18n get i => I18n.instance;

  @override
  void initState() {
    super.initState();
    _loadHours();
  }

  Future<void> _loadHours() async {
    final slug = asString(widget.clinic['slug']);
    final tenantId = asInt(widget.clinic['tenantId'], asInt(widget.clinic['id']));
    try {
      dynamic hoursRaw;
      dynamic statusRaw;
      if (slug.isNotEmpty) {
        hoursRaw = await widget.auth.api.get('/public/tenants/$slug/business-hours');
        statusRaw = await widget.auth.api.get('/public/tenants/$slug/availability-status');
      } else if (tenantId > 0) {
        hoursRaw = await widget.auth.api.get('/clinics/$tenantId/business-hours');
        statusRaw = await widget.auth.api.get('/clinics/$tenantId/availability-status');
      }
      if (!mounted) return;
      setState(() {
        hours = asMap(hoursRaw);
        status = asMap(statusRaw);
        loadingHours = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() => loadingHours = false);
    }
  }

  String _dayName(int day) => i.t('hoursDay$day');

  String _hoursSummary() {
    if (hours['configured'] != true) return i.t('hoursUnavailable');
    final days = asList(hours['days']);
    if (days.isEmpty) return i.t('hoursUnavailable');
    final buffer = StringBuffer();
    for (final day in days) {
      final map = asMap(day);
      final name = _dayName(asInt(map['dayOfWeek'], 0));
      if (map['closed'] == true) {
        buffer.writeln('$name: ${i.t('closedNow')}');
        continue;
      }
      final intervals = asList(map['intervals'])
          .map(asMap)
          .map((interval) => '${interval['open'] ?? ''}–${interval['close'] ?? ''}')
          .where((item) => item != '–')
          .join(', ');
      buffer.writeln('$name: $intervals');
    }
    return buffer.toString().trim();
  }

  @override
  Widget build(BuildContext context) {
    final clinic = widget.clinic;
    final name = '${clinic['commercialName'] ?? clinic['name']}';
    final open = status['open'] == true;
    final configured = status['configured'] == true || hours['configured'] == true;
    final nextOpening = asString(status['nextOpeningAt']);
    final exceptions = asList(hours['exceptions']);
    return Scaffold(
      appBar: AppBar(title: Text(name)),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          if (asString(clinic['logoUrl']).isNotEmpty) Image.network(clinic['logoUrl'], height: 72, errorBuilder: (_, __, ___) => const SizedBox.shrink()),
          ListTile(title: Text(i.t('phone')), subtitle: Text(asString(clinic['phone'], '—'))),
          ListTile(title: Text(i.t('email')), subtitle: Text(asString(clinic['email'], '—'))),
          ListTile(title: Text(i.t('address')), subtitle: Text('${clinic['address'] ?? ''} ${clinic['city'] ?? ''} ${clinic['country'] ?? ''}'.trim())),
          ListTile(
            title: Text(i.t('hours')),
            subtitle: loadingHours
                ? Text(i.t('loading'))
                : Text(configured ? (open ? i.t('openNow') : i.t('closedNow')) : i.t('hoursUnavailable')),
          ),
          if (!loadingHours && configured && !open && nextOpening.isNotEmpty)
            ListTile(title: Text(i.t('nextOpening')), subtitle: Text(nextOpening)),
          if (!loadingHours)
            ListTile(title: Text(i.t('weeklyHours')), subtitle: Text(_hoursSummary())),
          if (exceptions.isNotEmpty)
            ListTile(
              title: Text(i.t('hoursExceptions')),
              subtitle: Text(exceptions.map((item) {
                final map = asMap(item);
                final closed = map['closed'] == true;
                final note = asString(map['description']);
                return '${map['date']}: ${closed ? i.t('closedNow') : asList(map['intervals']).map((interval) => '${asMap(interval)['open']}–${asMap(interval)['close']}').join(', ')}${note.isEmpty ? '' : ' · $note'}';
              }).join('\n')),
            ),
          const SizedBox(height: 12),
          FilledButton.icon(
            onPressed: widget.pets.isEmpty ? null : () => Navigator.push(context, MaterialPageRoute(builder: (_) => BookScreen(auth: widget.auth, pets: widget.pets))),
            icon: const Icon(Icons.event),
            label: Text(i.t('book')),
          ),
          const SizedBox(height: 8),
          OutlinedButton.icon(
            onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => MessagesScreen(auth: widget.auth))),
            icon: const Icon(Icons.chat_bubble_outline),
            label: Text(i.t('newMessage')),
          ),
        ],
      ),
    );
  }
}
