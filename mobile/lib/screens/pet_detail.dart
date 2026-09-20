import 'package:flutter/material.dart';
import '../core/auth.dart';
import '../core/l10n.dart';

class PetDetailScreen extends StatefulWidget {
  const PetDetailScreen({super.key, required this.auth, required this.pet});
  final AuthStore auth;
  final Map pet;
  @override
  State<PetDetailScreen> createState() => _PetDetailScreenState();
}

class _PetDetailScreenState extends State<PetDetailScreen> with SingleTickerProviderStateMixin {
  late final TabController tabs;
  List timeline = [];
  List vaccines = [];
  List treatments = [];
  List prescriptions = [];
  List documents = [];
  List appointments = [];
  Map? detail;

  @override
  void initState() {
    super.initState();
    tabs = TabController(length: 7, vsync: this);
    final id = widget.pet['id'];
    widget.auth.api.get('/pets/$id').then((v) => setState(() => detail = v as Map?));
    widget.auth.api.get('/pets/$id/timeline').then((v) => setState(() => timeline = v as List? ?? []));
    widget.auth.api.get('/pets/$id/vaccinations').then((v) => setState(() => vaccines = v as List? ?? []));
    widget.auth.api.get('/pets/$id/treatments').then((v) => setState(() => treatments = v as List? ?? []));
    widget.auth.api.get('/pets/$id/prescriptions').then((v) => setState(() => prescriptions = v as List? ?? []));
    widget.auth.api.get('/pets/$id/documents').then((v) => setState(() => documents = v as List? ?? []));
    widget.auth.api.get('/appointments/pet/$id').then((v) => setState(() => appointments = v as List? ?? []));
  }

  @override
  void dispose() {
    tabs.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final i = I18n.instance;
    final pet = detail ?? widget.pet;
    final logo = pet['tenantLogoUrl'] as String?;
    return Scaffold(
      appBar: AppBar(title: Text('${pet['name']}')),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 16, 20, 8),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(children: [
                  CircleAvatar(
                    radius: 28,
                    backgroundImage: (pet['photoUrl'] as String?)?.isNotEmpty == true ? NetworkImage(pet['photoUrl']) : null,
                    child: (pet['photoUrl'] as String?)?.isNotEmpty == true ? null : Text('${pet['name']}'.substring(0, 1)),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                      Text('${pet['breed'] ?? pet['species'] ?? ''} · ${pet['age'] ?? ''} · ${pet['weightKg'] ?? ''} kg',
                          style: Theme.of(context).textTheme.titleMedium),
                      Row(children: [
                        if (logo != null && logo.isNotEmpty)
                          Padding(
                            padding: const EdgeInsets.only(right: 8),
                            child: Image.network(logo, height: 20, errorBuilder: (_, __, ___) => const SizedBox.shrink()),
                          ),
                        Expanded(child: Text('${i.t('clinic')}: ${pet['tenantName'] ?? ''}')),
                      ]),
                      Text('${i.t('vet')}: ${pet['veterinarianName'] ?? ''}'),
                    ]),
                  ),
                ]),
                if (pet['allergies'] != null && '${pet['allergies']}'.isNotEmpty)
                  Card(color: const Color(0xFFFFF1F2), child: ListTile(title: Text('${i.t('allergies')}: ${pet['allergies']}'))),
                if (pet['medicalConditions'] != null && '${pet['medicalConditions']}'.isNotEmpty)
                  Card(color: const Color(0xFFFFFBEB), child: ListTile(title: Text('${i.t('conditions')}: ${pet['medicalConditions']}'))),
              ],
            ),
          ),
          TabBar(
            controller: tabs,
            isScrollable: true,
            tabs: [
              Tab(text: i.t('summary')),
              Tab(text: i.t('history')),
              Tab(text: i.t('vaccines')),
              Tab(text: i.t('treatments')),
              Tab(text: i.t('prescriptions')),
              Tab(text: i.t('documents')),
              Tab(text: i.t('appointments')),
            ],
          ),
          Expanded(
            child: TabBarView(
              controller: tabs,
              children: [
                ListView(padding: const EdgeInsets.all(20), children: [
                  Text('${i.t('weight')}: ${pet['weightKg'] ?? '—'} kg'),
                  Text('${i.t('clinic')}: ${pet['tenantName'] ?? ''}'),
                  Text('${i.t('contact')}: ${pet['ownerName'] ?? ''}'),
                ]),
                _list(timeline, (e) => ListTile(title: Text('${e['title']}'), subtitle: Text('${e['type']} · ${e['at']}'))),
                _list(vaccines, (v) => ListTile(title: Text('${v['vaccineName']}'), subtitle: Text('${v['status']} · ${v['appliedAt']}'))),
                _list(treatments, (t) => ListTile(title: Text('${t['name']}'), subtitle: Text('${t['status']} · ${t['startDate'] ?? ''}'))),
                _list(prescriptions, (p) => ListTile(
                  title: Text('${p['notes'] ?? i.t('prescriptions')}'),
                  subtitle: Text('${p['issuedAt'] ?? ''} · ${pet['tenantName'] ?? ''}'),
                  trailing: IconButton(
                    icon: const Icon(Icons.picture_as_pdf_outlined),
                    onPressed: () async {
                      try {
                        await widget.auth.api.bytes('/prescriptions/${p['id']}/pdf');
                        if (context.mounted) {
                          ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(i.t('pdfReady'))));
                        }
                      } catch (_) {}
                    },
                  ),
                )),
                _list(documents, (d) => ListTile(title: Text('${d['title']}'), subtitle: Text('${d['category'] ?? ''} · ${d['createdAt'] ?? ''}'))),
                _list(appointments, (a) => ListTile(
                  title: Text('${a['serviceName'] ?? ''} · ${a['status']}'),
                  subtitle: Text('${a['startAt']}\n${a['tenantName'] ?? ''} · ${a['veterinarianName'] ?? ''}'),
                  isThreeLine: true,
                )),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _list(List items, Widget Function(dynamic) builder) {
    final i = I18n.instance;
    if (items.isEmpty) {
      return Center(child: Text(i.t('empty')));
    }
    return ListView(children: [for (final item in items) builder(item)]);
  }
}
