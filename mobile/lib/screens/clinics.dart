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

class ClinicDetailScreen extends StatelessWidget {
  const ClinicDetailScreen({super.key, required this.auth, required this.clinic, required this.pets});
  final AuthStore auth;
  final Map clinic;
  final List pets;

  @override
  Widget build(BuildContext context) {
    final i = I18n.instance;
    final name = '${clinic['commercialName'] ?? clinic['name']}';
    return Scaffold(
      appBar: AppBar(title: Text(name)),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          if (asString(clinic['logoUrl']).isNotEmpty) Image.network(clinic['logoUrl'], height: 72, errorBuilder: (_, __, ___) => const SizedBox.shrink()),
          ListTile(title: Text(i.t('phone')), subtitle: Text(asString(clinic['phone'], '—'))),
          ListTile(title: Text(i.t('email')), subtitle: Text(asString(clinic['email'], '—'))),
          ListTile(title: Text(i.t('address')), subtitle: Text('${clinic['address'] ?? ''} ${clinic['city'] ?? ''} ${clinic['country'] ?? ''}'.trim())),
          ListTile(title: Text(i.t('hours')), subtitle: Text(asString(clinic['timezone']).isNotEmpty ? clinic['timezone'] : i.t('hoursHint'))),
          const SizedBox(height: 12),
          FilledButton.icon(
            onPressed: pets.isEmpty ? null : () => Navigator.push(context, MaterialPageRoute(builder: (_) => BookScreen(auth: auth, pets: pets))),
            icon: const Icon(Icons.event),
            label: Text(i.t('book')),
          ),
          const SizedBox(height: 8),
          OutlinedButton.icon(
            onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => MessagesScreen(auth: auth))),
            icon: const Icon(Icons.chat_bubble_outline),
            label: Text(i.t('newMessage')),
          ),
        ],
      ),
    );
  }
}
