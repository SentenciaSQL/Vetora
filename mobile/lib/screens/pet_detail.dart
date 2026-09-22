import 'package:flutter/material.dart';
import '../core/auth.dart';
import '../core/format.dart';
import '../core/l10n.dart';
import '../core/widgets.dart';
import 'book.dart';
import 'pet_form.dart';

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
  List labs = [];
  List consultations = [];
  Map? detail;
  bool loading = true;
  String? error;

  @override
  void initState() {
    super.initState();
    tabs = TabController(length: 8, vsync: this);
    _load();
  }

  Future<void> _load() async {
    final id = widget.pet['id'];
    setState(() {
      loading = true;
      error = null;
    });
    try {
      detail = asMap(await widget.auth.api.get('/pets/$id'));
      timeline = asList(await widget.auth.api.get('/pets/$id/timeline'));
      vaccines = asList(await widget.auth.api.get('/pets/$id/vaccinations'));
      treatments = asList(await widget.auth.api.get('/pets/$id/treatments'));
      prescriptions = asList(await widget.auth.api.get('/pets/$id/prescriptions'));
      documents = asList(await widget.auth.api.get('/pets/$id/documents'));
      appointments = asList(await widget.auth.api.get('/appointments/pet/$id'));
      consultations = asList(await widget.auth.api.get('/pets/$id/consultations'));
      if (widget.auth.laboratoryEnabled) {
        try {
          labs = asList(await widget.auth.api.get('/pets/$id/labs'));
        } catch (_) {
          labs = [];
        }
      }
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

  Future<void> _addVaccine() async {
    final name = TextEditingController();
    final saved = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(I18n.instance.t('vaccines')),
        content: TextField(controller: name, decoration: InputDecoration(labelText: I18n.instance.t('name'))),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: Text(I18n.instance.t('back'))),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: Text(I18n.instance.t('save'))),
        ],
      ),
    );
    if (saved != true || name.text.trim().isEmpty) return;
    try {
      await widget.auth.api.post('/vaccinations', {
        'petId': widget.pet['id'],
        'vaccineName': name.text.trim(),
        'appliedAt': DateTime.now().toIso8601String().split('T').first,
      });
      await _load();
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(I18n.instance.t('saved'))));
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(userMessage(e))));
    }
  }

  bool get _canEditPhoto => widget.auth.hasPermission('PET_UPDATE') || widget.auth.isOwner;

  Future<void> _photo() async {
    if (!_canEditPhoto) return;
    final picked = await pickProfilePhoto(context);
    if (picked == null) return;
    try {
      final bytes = await picked.readAsBytes();
      if (!isProfileImage(picked.name, bytes.length)) {
        if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(I18n.instance.t('photoInvalid'))));
        return;
      }
      await widget.auth.api.upload('/pets/${widget.pet['id']}/photo', bytes, picked.name);
      await _load();
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(I18n.instance.t('saved'))));
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(userMessage(e))));
    }
  }

  Future<void> _removePhoto() async {
    if (!_canEditPhoto) return;
    try {
      await widget.auth.api.delete('/pets/${widget.pet['id']}/photo');
      await _load();
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(I18n.instance.t('saved'))));
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(userMessage(e))));
    }
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
    final logo = asString(pet['tenantLogoUrl']);
    return Scaffold(
      appBar: AppBar(
        title: Text(asString(pet['name'])),
        actions: [
          if (widget.auth.hasPermission('PET_UPDATE'))
            IconButton(onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => PetFormScreen(auth: widget.auth, pet: asMap(pet)))).then((_) => _load()), icon: const Icon(Icons.edit_outlined)),
          if (widget.auth.canWriteMedical)
            IconButton(onPressed: _addVaccine, icon: const Icon(Icons.vaccines_outlined)),
        ],
      ),
      body: loading || error != null
          ? StatusView(loading: loading, error: error, onRetry: _load)
          : Column(
              children: [
                Padding(
                  padding: const EdgeInsets.fromLTRB(20, 16, 20, 8),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                        Column(
                          children: [
                            RemoteCircleAvatar(
                              url: asString(pet['photoUrl']),
                              radius: 36,
                              fallbackText: asString(pet['name'], '?'),
                            ),
                            if (_canEditPhoto) ...[
                              TextButton(onPressed: _photo, child: Text(i.t('changePhoto'))),
                              if (asString(pet['photoUrl']).isNotEmpty)
                                TextButton(onPressed: _removePhoto, child: Text(i.t('removePhoto'))),
                            ],
                          ],
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                            Text('${pet['breed'] ?? pet['species'] ?? ''} · ${pet['sex'] ?? ''} · ${pet['age'] ?? ''} · ${pet['weightKg'] ?? ''} kg',
                                style: Theme.of(context).textTheme.titleMedium),
                            Row(children: [
                              if (logo.isNotEmpty)
                                Padding(
                                  padding: const EdgeInsets.only(right: 8),
                                  child: RemoteImage(logo, height: 20),
                                ),
                              Expanded(child: Text('${i.t('clinic')}: ${pet['tenantName'] ?? ''}')),
                            ]),
                            if (asString(pet['microchip']).isNotEmpty) Text('${i.t('microchip')}: ${pet['microchip']}'),
                            Text('${i.t('vet')}: ${pet['veterinarianName'] ?? ''}'),
                          ]),
                        ),
                      ]),
                      if (asString(pet['allergies']).isNotEmpty)
                        Card(color: const Color(0xFFFFF1F2), child: ListTile(title: Text('${i.t('allergies')}: ${pet['allergies']}'))),
                      if (asString(pet['medicalConditions']).isNotEmpty)
                        Card(color: const Color(0xFFFFFBEB), child: ListTile(title: Text('${i.t('conditions')}: ${pet['medicalConditions']}'))),
                      if (widget.auth.isOwner)
                        Align(
                          alignment: Alignment.centerLeft,
                          child: TextButton.icon(
                            onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => BookScreen(auth: widget.auth, pets: [pet]))),
                            icon: const Icon(Icons.event),
                            label: Text(i.t('book')),
                          ),
                        ),
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
                    Tab(text: i.t('labs')),
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
                        Text('${i.t('microchip')}: ${pet['microchip'] ?? '—'}'),
                        if (consultations.isNotEmpty)
                          Text('${i.t('nextIndications')}: ${consultations.first['recommendations'] ?? consultations.first['nextControlAt'] ?? i.t('empty')}'),
                      ]),
                      _list(timeline, (e) => ListTile(title: Text('${e['title']}'), subtitle: Text('${e['type']} · ${formatDate(e['at'])}'))),
                      _list(vaccines, (v) => ListTile(title: Text('${v['vaccineName']}'), subtitle: Text('${statusLabel(v['status'])} · ${formatDate(v['appliedAt'])}'))),
                      _list(treatments, (t) => ListTile(title: Text('${t['name']}'), subtitle: Text('${t['status']} · ${t['startDate'] ?? ''}'))),
                      _list(prescriptions, (p) => ListTile(
                        title: Text('${p['notes'] ?? i.t('prescriptions')}'),
                        subtitle: Text('${formatDate(p['issuedAt'])} · ${pet['tenantName'] ?? ''}'),
                      )),
                      _list(documents, (d) => ListTile(title: Text('${d['title']}'), subtitle: Text('${d['category'] ?? ''} · ${formatDate(d['createdAt'])}'))),
                      _list(appointments, (a) => ListTile(
                        title: Text('${a['serviceName'] ?? ''} · ${a['status']}'),
                        subtitle: Text('${formatDate(a['startAt'])}\n${a['tenantName'] ?? ''} · ${a['veterinarianName'] ?? ''}'),
                        isThreeLine: true,
                      )),
                      _list(
                        widget.auth.laboratoryEnabled ? labs : const [],
                        (l) => ListTile(title: Text('${l['name'] ?? l['testName'] ?? i.t('labs')}'), subtitle: Text('${l['status'] ?? ''} · ${formatDate(l['createdAt'] ?? l['collectedAt'])}')),
                        empty: widget.auth.laboratoryEnabled ? null : i.t('featureUnavailable'),
                      ),
                    ],
                  ),
                ),
              ],
            ),
    );
  }

  Widget _list(List items, Widget Function(dynamic) builder, {String? empty}) {
    final i = I18n.instance;
    if (items.isEmpty) return Center(child: Text(empty ?? i.t('empty')));
    return ListView(children: [for (final item in items) builder(item)]);
  }
}
