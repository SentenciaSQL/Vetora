import 'package:flutter/material.dart';
import '../core/auth.dart';
import '../core/l10n.dart';

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
  Map? branch;
  Map? service;
  Map? vet;
  DateTime date = DateTime.now().add(const Duration(days: 1));
  Map? slot;
  final reason = TextEditingController();
  bool loading = false;
  String? error;

  I18n get i => I18n.instance;
  bool get es => i.locale != 'en';

  String _serviceName(Map s) => '${es ? (s['nameEs'] ?? s['nameEn']) : (s['nameEn'] ?? s['nameEs'])}';

  String _slotLabel(dynamic value) {
    final raw = '$value';
    final time = raw.contains('T') ? raw.split('T').last : raw;
    return time.length >= 5 ? time.substring(0, 5) : raw;
  }

  Future<void> loadCatalog() async {
    tenantId = pet?['tenantId'] as int?;
    if (tenantId == null) return;
    branches = await widget.auth.api.get('/branches/tenant/$tenantId') as List? ?? [];
    services = await widget.auth.api.get('/services/tenant/$tenantId') as List? ?? [];
    vets = await widget.auth.api.get('/veterinarians/tenant/$tenantId') as List? ?? [];
    setState(() {});
  }

  Future<void> loadSlots() async {
    if (vet == null) return;
    final day = '${date.year.toString().padLeft(4, '0')}-${date.month.toString().padLeft(2, '0')}-${date.day.toString().padLeft(2, '0')}';
    slots = await widget.auth.api.get('/appointments/availability', {
      'veterinarianId': '${vet!['id']}',
      if (branch != null) 'branchId': '${branch!['id']}',
      if (service != null) 'serviceId': '${service!['id']}',
      'date': day,
    }) as List? ?? [];
    setState(() {});
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
      setState(() => error = i.t('invalid'));
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
                  leading: (p['tenantLogoUrl'] as String?)?.isNotEmpty == true
                      ? CircleAvatar(backgroundImage: NetworkImage(p['tenantLogoUrl']))
                      : const CircleAvatar(child: Icon(Icons.pets)),
                  onTap: () async {
                    pet = p as Map;
                    await loadCatalog();
                    setState(() => step = 1);
                  },
                ),
            ])),
            if (step == 1) Expanded(child: ListView(children: [
              Text(i.t('branch'), style: Theme.of(context).textTheme.titleMedium),
              for (final b in branches)
                ListTile(title: Text('${b['name']}'), subtitle: Text('${b['address'] ?? ''}'), onTap: () { branch = b as Map; setState(() => step = 2); }),
            ])),
            if (step == 2) Expanded(child: ListView(children: [
              Text(i.t('service'), style: Theme.of(context).textTheme.titleMedium),
              for (final s in services)
                ListTile(title: Text(_serviceName(s as Map)), subtitle: Text('${s['durationMin'] ?? ''} min'), onTap: () { service = s; setState(() => step = 3); }),
            ])),
            if (step == 3) Expanded(child: ListView(children: [
              Text(i.t('vet'), style: Theme.of(context).textTheme.titleMedium),
              for (final v in vets)
                ListTile(title: Text('${v['fullName']}'), subtitle: Text('${v['specialty'] ?? ''}'), onTap: () async { vet = v as Map; await loadSlots(); setState(() => step = 4); }),
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
              if (slots.isEmpty) Text(i.t('empty')),
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
              ListTile(title: Text('${vet?['fullName']}'), subtitle: Text(i.t('vet'))),
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
