import 'package:flutter/material.dart';
import '../core/auth.dart';
import '../core/format.dart';
import '../core/l10n.dart';
import '../core/specialty.dart';
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

  Future<void> _editVaccine([Map? existing]) async {
    final result = await showDialog<Map>(
      context: context,
      builder: (ctx) => _VaccineEditor(existing: existing),
    );
    if (result == null || !mounted) return;
    try {
      if (result['delete'] == true) {
        await widget.auth.api.delete('/vaccinations/${existing!['id']}');
      } else if (existing == null) {
        await widget.auth.api.post('/vaccinations', {
          'petId': widget.pet['id'],
          ...result,
        });
      } else {
        await widget.auth.api.put('/vaccinations/${existing['id']}', {
          'petId': widget.pet['id'],
          ...result,
        });
      }
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
            IconButton(tooltip: i.t('addVaccine'), onPressed: () => _editVaccine(), icon: const Icon(Icons.vaccines_outlined)),
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
                      _vaccinesTab(i),
                      _list(treatments, (t) => ListTile(title: Text('${t['name']}'), subtitle: Text('${statusLabel(t['status'])} · ${t['startDate'] ?? ''}'))),
                      _list(prescriptions, (p) => ListTile(
                        title: Text('${p['notes'] ?? i.t('prescriptions')}'),
                        subtitle: Text('${formatDate(p['issuedAt'])} · ${pet['tenantName'] ?? ''}'),
                      )),
                      _list(documents, (d) => ListTile(title: Text('${d['title']}'), subtitle: Text('${d['category'] ?? ''} · ${formatDate(d['createdAt'])}'))),
                      _list(appointments, (a) => ListTile(
                        title: Text('${a['serviceName'] ?? ''} · ${statusLabel(a['status'])}'),
                        subtitle: Text([
                          formatDate(a['startAt']),
                          [a['tenantName'] ?? '', a['veterinarianName'] ?? '', specialtyLabel(a['veterinarianSpecialty'], other: a['veterinarianSpecialtyOther'])]
                              .where((part) => '$part'.trim().isNotEmpty)
                              .join(' · '),
                        ].join('\n')),
                        isThreeLine: true,
                      )),
                      _list(
                        widget.auth.laboratoryEnabled ? labs : const [],
                        (l) => ListTile(title: Text('${l['name'] ?? l['testName'] ?? i.t('labs')}'), subtitle: Text('${statusLabel(l['status'])} · ${formatDate(l['createdAt'] ?? l['collectedAt'])}')),
                        empty: widget.auth.laboratoryEnabled ? null : i.t('featureUnavailable'),
                      ),
                    ],
                  ),
                ),
              ],
            ),
    );
  }

  Widget _vaccinesTab(I18n i) {
    final canWrite = widget.auth.canWriteMedical;
    return Column(
      children: [
        if (canWrite)
          Align(
            alignment: Alignment.centerLeft,
            child: Padding(
              padding: const EdgeInsets.fromLTRB(12, 8, 12, 0),
              child: TextButton.icon(
                onPressed: () => _editVaccine(),
                icon: const Icon(Icons.add),
                label: Text(i.t('addVaccine')),
              ),
            ),
          ),
        Expanded(
          child: vaccines.isEmpty
              ? Center(child: Text(i.t('empty'), textAlign: TextAlign.center))
              : ListView(
                  children: [
                    for (final v in vaccines) _vaccineTile(i, asMap(v), canWrite),
                  ],
                ),
        ),
      ],
    );
  }

  Widget _vaccineTile(I18n i, Map<String, dynamic> vaccine, bool canWrite) {
    final next = asString(vaccine['nextDoseAt']);
    final lines = [
      statusLabel(vaccine['status']),
      '${i.t('appliedOn')} ${formatDay(vaccine['appliedAt'])}',
      if (next.isNotEmpty) '${i.t('nextDose')} ${formatDay(vaccine['nextDoseAt'])}',
    ].where((part) => part.trim().isNotEmpty).join('\n');
    return ListTile(
      title: Text(asString(vaccine['vaccineName'])),
      subtitle: Text(lines),
      isThreeLine: next.isNotEmpty,
      onTap: canWrite ? () => _editVaccine(vaccine) : null,
      trailing: canWrite
          ? IconButton(
              tooltip: i.t('editVaccine'),
              onPressed: () => _editVaccine(vaccine),
              icon: const Icon(Icons.edit_outlined),
            )
          : null,
    );
  }

  Widget _list(List items, Widget Function(dynamic) builder, {String? empty}) {
    final i = I18n.instance;
    if (items.isEmpty) return Center(child: Text(empty ?? i.t('empty')));
    return ListView(children: [for (final item in items) builder(item)]);
  }
}

class _VaccineEditor extends StatefulWidget {
  const _VaccineEditor({this.existing});

  final Map? existing;

  @override
  State<_VaccineEditor> createState() => _VaccineEditorState();
}

class _VaccineEditorState extends State<_VaccineEditor> {
  late final TextEditingController name;
  late final TextEditingController brand;
  late final TextEditingController lot;
  late final TextEditingController notes;
  late DateTime applied;
  DateTime? nextDose;

  @override
  void initState() {
    super.initState();
    final existing = widget.existing;
    name = TextEditingController(text: asString(existing?['vaccineName']));
    brand = TextEditingController(text: asString(existing?['brand']));
    lot = TextEditingController(text: asString(existing?['lot']));
    notes = TextEditingController(text: asString(existing?['notes']));
    applied = _day(existing?['appliedAt']) ?? DateTime.now();
    nextDose = _day(existing?['nextDoseAt']);
  }

  @override
  void dispose() {
    name.dispose();
    brand.dispose();
    lot.dispose();
    notes.dispose();
    super.dispose();
  }

  bool get editing => widget.existing != null;

  DateTime? _day(dynamic value) {
    final parsed = DateTime.tryParse(asString(value));
    if (parsed == null) return null;
    final local = parsed.toLocal();
    return DateTime(local.year, local.month, local.day);
  }

  String _iso(DateTime day) {
    String two(int n) => n.toString().padLeft(2, '0');
    return '${day.year}-${two(day.month)}-${two(day.day)}';
  }

  Future<void> _pick(bool next) async {
    final current = next ? (nextDose ?? applied) : applied;
    final picked = await showDatePicker(
      context: context,
      initialDate: current,
      firstDate: DateTime(2000),
      lastDate: DateTime.now().add(const Duration(days: 365 * 5)),
    );
    if (picked == null) return;
    setState(() {
      final day = DateTime(picked.year, picked.month, picked.day);
      if (next) {
        nextDose = day;
      } else {
        applied = day;
      }
    });
  }

  Future<void> _delete() async {
    final i = I18n.instance;
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(i.t('deleteVaccine')),
        content: Text(i.t('deleteVaccineConfirm')),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: Text(i.t('back'))),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: Text(i.t('deleteVaccine'))),
        ],
      ),
    );
    if (ok == true && mounted) Navigator.pop(context, {'delete': true});
  }

  void _save() {
    final trimmed = name.text.trim();
    if (trimmed.isEmpty) return;
    Navigator.pop(context, {
      'vaccineName': trimmed,
      'brand': brand.text.trim(),
      'lot': lot.text.trim(),
      'notes': notes.text.trim(),
      'appliedAt': _iso(applied),
      'nextDoseAt': nextDose == null ? null : _iso(nextDose!),
    });
  }

  @override
  Widget build(BuildContext context) {
    final i = I18n.instance;
    return AlertDialog(
      title: Text(editing ? i.t('editVaccine') : i.t('addVaccine')),
      content: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              controller: name,
              textCapitalization: TextCapitalization.sentences,
              decoration: InputDecoration(labelText: i.t('name')),
              onChanged: (_) => setState(() {}),
            ),
            const SizedBox(height: 8),
            TextField(
              controller: brand,
              textCapitalization: TextCapitalization.words,
              decoration: InputDecoration(labelText: i.t('brand')),
            ),
            const SizedBox(height: 8),
            TextField(
              controller: lot,
              decoration: InputDecoration(labelText: i.t('lot')),
            ),
            ListTile(
              contentPadding: EdgeInsets.zero,
              title: Text(i.t('appliedOn')),
              subtitle: Text(formatDay(applied)),
              trailing: const Icon(Icons.calendar_today_outlined),
              onTap: () => _pick(false),
            ),
            ListTile(
              contentPadding: EdgeInsets.zero,
              title: Text(i.t('nextDose')),
              subtitle: Text(nextDose == null ? '—' : formatDay(nextDose)),
              trailing: const Icon(Icons.calendar_today_outlined),
              onTap: () => _pick(true),
            ),
            if (nextDose != null)
              Align(
                alignment: Alignment.centerLeft,
                child: TextButton(onPressed: () => setState(() => nextDose = null), child: Text(i.t('clearDate'))),
              ),
            TextField(
              controller: notes,
              minLines: 1,
              maxLines: 3,
              textCapitalization: TextCapitalization.sentences,
              decoration: InputDecoration(labelText: i.t('notes')),
            ),
          ],
        ),
      ),
      actions: [
        if (editing)
          TextButton(
            onPressed: _delete,
            child: Text(i.t('deleteVaccine'), style: TextStyle(color: Theme.of(context).colorScheme.error)),
          ),
        TextButton(onPressed: () => Navigator.pop(context), child: Text(i.t('back'))),
        FilledButton(onPressed: name.text.trim().isEmpty ? null : _save, child: Text(i.t('save'))),
      ],
    );
  }
}
