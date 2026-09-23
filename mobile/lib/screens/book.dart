import 'package:flutter/material.dart';
import '../core/auth.dart';
import '../core/format.dart';
import '../core/l10n.dart';
import '../core/specialty.dart';
import '../core/widgets.dart';

class BookScreen extends StatefulWidget {
  const BookScreen({super.key, required this.auth, required this.pets});
  final AuthStore auth;
  final List pets;
  @override
  State<BookScreen> createState() => _BookScreenState();
}

class _BookScreenState extends State<BookScreen> {
  int step = 0;
  Map? pet;
  int? tenantId;
  List branches = [];
  List services = [];
  List vets = [];
  List slots = [];
  String? hoursHint;
  Map? branch;
  Map? service;
  Map? vet;
  DateTime date = DateTime.now().add(const Duration(days: 1));
  Map? slot;
  final reason = TextEditingController();
  bool loading = false;
  bool loadingVets = false;
  int _vetRequest = 0;
  String? error;

  I18n get i => I18n.instance;
  bool get es => i.locale != 'en';

  String _serviceName(Map s) => '${es ? (s['nameEs'] ?? s['nameEn']) : (s['nameEn'] ?? s['nameEs'])}';

  String _slotLabel(dynamic value) {
    final raw = '$value';
    final time = raw.contains('T') ? raw.split('T').last : raw;
    return time.length >= 5 ? time.substring(0, 5) : raw;
  }

  void _clearClinicSelection() {
    _vetRequest++;
    branch = null;
    service = null;
    vet = null;
    slot = null;
    branches = [];
    services = [];
    vets = [];
    slots = [];
    hoursHint = null;
    loadingVets = false;
  }

  Future<void> loadCatalog() async {
    tenantId = asInt(pet?['tenantId'], 0) == 0 ? null : asInt(pet?['tenantId']);
    if (tenantId == null) return;
    try {
      branches = asList(await widget.auth.api.get('/branches/tenant/$tenantId'));
      services = asList(await widget.auth.api.get('/services/tenant/$tenantId'));
      if (mounted) setState(() {});
    } catch (e) {
      if (mounted) setState(() => error = userMessage(e));
    }
  }

  Future<void> selectBranch(Map selected) async {
    final request = ++_vetRequest;
    setState(() {
      branch = selected;
      vet = null;
      slot = null;
      slots = [];
      vets = [];
      loadingVets = true;
      error = null;
      step = 2;
    });
    if (tenantId == null) {
      if (mounted) setState(() => loadingVets = false);
      return;
    }
    try {
      final list = asList(await widget.auth.api.get('/veterinarians/tenant/$tenantId', {
        'branchId': '${selected['id']}',
      }));
      if (!mounted || request != _vetRequest) return;
      setState(() {
        vets = list;
        loadingVets = false;
      });
    } catch (e) {
      if (!mounted || request != _vetRequest) return;
      setState(() {
        vets = [];
        loadingVets = false;
        error = userMessage(e);
      });
    }
  }

  Future<void> loadSlots() async {
    if (vet == null) return;
    final day = '${date.year.toString().padLeft(4, '0')}-${date.month.toString().padLeft(2, '0')}-${date.day.toString().padLeft(2, '0')}';
    try {
      hoursHint = null;
      slots = asList(await widget.auth.api.get('/appointments/availability', {
        'veterinarianId': '${vet!['id']}',
        if (branch != null) 'branchId': '${branch!['id']}',
        if (service != null) 'serviceId': '${service!['id']}',
        'date': day,
      }));
      if (slots.isEmpty && tenantId != null && branch != null) {
        try {
          final status = asMap(await widget.auth.api.get('/clinics/$tenantId/availability-status', {
            'branchId': '${branch!['id']}',
          }));
          hoursHint = status['configured'] == true ? i.t('closedOrNoSlots') : i.t('hoursUnavailable');
        } catch (_) {
          hoursHint = i.t('hoursUnavailable');
        }
      }
      if (mounted) setState(() {});
    } catch (e) {
      if (mounted) setState(() => error = userMessage(e));
    }
  }

  Future<void> submit() async {
    setState(() { loading = true; error = null; });
    try {
      await widget.auth.api.post('/appointments', {
        'petId': pet?['id'],
        'veterinarianId': vet?['id'],
        'serviceId': service?['id'],
        'branchId': branch?['id'],
        'startAt': slot?['startAt'],
        'reason': reason.text,
      });
      if (mounted) Navigator.pop(context);
    } catch (e) {
      setState(() => error = userMessage(e));
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(i.t('book')),
        leading: step > 0
            ? IconButton(icon: const Icon(Icons.arrow_back), onPressed: () => setState(() => step -= 1))
            : null,
      ),
      body: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          children: [
            if (step == 0) Expanded(child: ListView(children: [
              for (final p in widget.pets)
                ListTile(
                  title: Text('${p['name']}'),
                  subtitle: Text('${p['tenantName'] ?? ''}'),
                  leading: RemoteCircleAvatar(
                    url: asString(p['tenantLogoUrl']),
                    fallbackIcon: Icons.pets,
                  ),
                  onTap: () async {
                    _clearClinicSelection();
                    pet = p as Map;
                    tenantId = null;
                    await loadCatalog();
                    if (mounted) setState(() => step = 1);
                  },
                ),
            ])),
            if (step == 1) Expanded(child: ListView(children: [
              Text(i.t('branch'), style: Theme.of(context).textTheme.titleMedium),
              for (final b in branches)
                ListTile(title: Text('${b['name']}'), subtitle: Text('${b['address'] ?? ''}'), onTap: () => selectBranch(b as Map)),
            ])),
            if (step == 2) Expanded(child: ListView(children: [
              Text(i.t('service'), style: Theme.of(context).textTheme.titleMedium),
              for (final s in services)
                ListTile(title: Text(_serviceName(s as Map)), subtitle: Text('${s['durationMin'] ?? ''} min'), onTap: () { service = s; setState(() => step = 3); }),
            ])),
            if (step == 3) Expanded(child: loadingVets
                ? const Center(child: CircularProgressIndicator())
                : ListView(children: [
              Text(i.t('vet'), style: Theme.of(context).textTheme.titleMedium),
              if (vets.isEmpty)
                Padding(
                  padding: const EdgeInsets.only(top: 12),
                  child: Text(error ?? i.t('noVetsAtBranch')),
                ),
              for (final v in vets)
                ListTile(
                  leading: RemoteCircleAvatar(
                    url: asString(v['photoUrl']),
                    fallbackText: personInitials(v['fullName']),
                  ),
                  title: Text('${v['fullName']}'),
                  subtitle: Text(specialtyLabel(v['specialty'], other: v['specialtyOther'])),
                  onTap: () async { vet = v as Map; await loadSlots(); setState(() => step = 4); },
                ),
            ])),
            if (step == 4) Expanded(child: ListView(children: [
              ListTile(
                title: Text(i.t('pickDate')),
                subtitle: Text('${date.year}-${date.month.toString().padLeft(2, '0')}-${date.day.toString().padLeft(2, '0')}'),
                trailing: const Icon(Icons.calendar_today),
                onTap: () async {
                  final picked = await showDatePicker(
                    context: context,
                    initialDate: date,
                    firstDate: DateTime.now(),
                    lastDate: DateTime.now().add(const Duration(days: 90)),
                  );
                  if (picked != null) {
                    date = picked;
                    await loadSlots();
                  }
                },
              ),
              Text(i.t('slot'), style: Theme.of(context).textTheme.titleMedium),
              if (slots.isEmpty) Text(hoursHint ?? i.t('empty')),
              for (final s in slots)
                ListTile(title: Text(_slotLabel(s['startAt'])), onTap: () { slot = s as Map; setState(() => step = 5); }),
            ])),
            if (step == 5) Expanded(child: ListView(children: [
              TextField(controller: reason, decoration: InputDecoration(labelText: i.t('reason'))),
              const SizedBox(height: 16),
              FilledButton(onPressed: () => setState(() => step = 6), child: Text(i.t('continue'))),
            ])),
            if (step == 6) Expanded(child: ListView(children: [
              Text(i.t('review'), style: Theme.of(context).textTheme.titleMedium),
              ListTile(title: Text('${pet?['name']}'), subtitle: Text('${pet?['tenantName'] ?? ''}')),
              ListTile(title: Text('${branch?['name']}'), subtitle: Text(i.t('branch'))),
              ListTile(title: Text(service == null ? '' : _serviceName(service!)), subtitle: Text(i.t('service'))),
              ListTile(
                title: Text('${vet?['fullName']}'),
                subtitle: Text([
                  i.t('vet'),
                  specialtyLabel(vet?['specialty'], other: vet?['specialtyOther']),
                ].where((line) => line.isNotEmpty).join(' · ')),
              ),
              ListTile(title: Text('${slot?['startAt']}'), subtitle: Text(reason.text)),
              if (error != null) Text(error!, style: const TextStyle(color: Colors.red)),
              const SizedBox(height: 16),
              FilledButton(onPressed: loading ? null : submit, child: Text(i.t('confirm'))),
            ])),
          ],
        ),
      ),
    );
  }
}
