import 'package:flutter/material.dart';
import '../core/auth.dart';
import '../core/l10n.dart';
import 'book.dart';

class AppointmentsScreen extends StatefulWidget {
  const AppointmentsScreen({super.key, required this.auth});
  final AuthStore auth;
  @override
  State<AppointmentsScreen> createState() => _AppointmentsScreenState();
}

class _AppointmentsScreenState extends State<AppointmentsScreen> {
  List items = [];
  List pets = [];

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final a = await widget.auth.api.get('/appointments/mine');
    final p = await widget.auth.api.get('/pets/mine');
    setState(() {
      items = a as List? ?? [];
      pets = p as List? ?? [];
    });
  }

  Future<void> _cancel(dynamic id) async {
    await widget.auth.api.post('/appointments/$id/status', {'status': 'CANCELLED'});
    await _load();
  }

  String _slotLabel(dynamic value) {
    final raw = '$value';
    final time = raw.contains('T') ? raw.split('T').last : raw;
    return time.length >= 5 ? time.substring(0, 5) : raw;
  }

  Future<void> _reschedule(Map a) async {
    final i = I18n.instance;
    final picked = await showDatePicker(
      context: context,
      initialDate: DateTime.now().add(const Duration(days: 1)),
      firstDate: DateTime.now(),
      lastDate: DateTime.now().add(const Duration(days: 90)),
    );
    if (picked == null || !mounted) return;
    final day = '${picked.year.toString().padLeft(4, '0')}-${picked.month.toString().padLeft(2, '0')}-${picked.day.toString().padLeft(2, '0')}';
    List slots = [];
    try {
      slots = await widget.auth.api.get('/appointments/availability', {
        'veterinarianId': '${a['veterinarianId']}',
        if (a['branchId'] != null) 'branchId': '${a['branchId']}',
        if (a['serviceId'] != null) 'serviceId': '${a['serviceId']}',
        'date': day,
      }) as List? ?? [];
    } catch (_) {
      return;
    }
    if (!mounted) return;
    final slot = await showModalBottomSheet<Map>(
      context: context,
      builder: (ctx) => SafeArea(
        child: ListView(
          children: [
            ListTile(title: Text(i.t('slot'))),
            if (slots.isEmpty) ListTile(title: Text(i.t('empty'))),
            for (final s in slots)
              ListTile(
                title: Text(_slotLabel(s['startAt'])),
                onTap: () => Navigator.pop(ctx, s as Map),
              ),
          ],
        ),
      ),
    );
    if (slot == null) return;
    await widget.auth.api.put('/appointments/${a['id']}', {
      'petId': a['petId'],
      'veterinarianId': a['veterinarianId'],
      'serviceId': a['serviceId'],
      'branchId': a['branchId'],
      'startAt': slot['startAt'],
    });
    await _load();
  }

  bool _canManage(dynamic status) => ['REQUESTED', 'PENDING', 'CONFIRMED'].contains(status);

  @override
  Widget build(BuildContext context) {
    final i = I18n.instance;
    return SafeArea(
      child: RefreshIndicator(
        onRefresh: _load,
        child: ListView(
          padding: const EdgeInsets.all(20),
          children: [
            Row(children: [
              Expanded(child: Text(i.t('appointments'), style: Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.w700))),
              FilledButton(onPressed: pets.isEmpty ? null : () => Navigator.push(context, MaterialPageRoute(builder: (_) => BookScreen(auth: widget.auth, pets: pets))).then((_) => _load()), child: Text(i.t('book'))),
            ]),
            const SizedBox(height: 12),
            if (items.isEmpty) Text(i.t('empty')),
            for (final a in items)
              Card(
                child: ListTile(
                  leading: (a['tenantLogoUrl'] as String?)?.isNotEmpty == true
                      ? CircleAvatar(backgroundImage: NetworkImage(a['tenantLogoUrl']))
                      : const CircleAvatar(child: Icon(Icons.local_hospital_outlined)),
                  title: Text('${a['petName']} · ${a['serviceName'] ?? ''}'),
                  subtitle: Text('${a['startAt']}\n${a['tenantName'] ?? ''} · ${a['veterinarianName'] ?? ''} · ${a['status']}'),
                  isThreeLine: true,
                  trailing: _canManage(a['status'])
                      ? PopupMenuButton<String>(
                          onSelected: (value) {
                            if (value == 'cancel') _cancel(a['id']);
                            if (value == 'reschedule') _reschedule(a as Map);
                          },
                          itemBuilder: (_) => [
                            PopupMenuItem(value: 'reschedule', child: Text(i.t('reschedule'))),
                            PopupMenuItem(value: 'cancel', child: Text(i.t('cancel'))),
                          ],
                        )
                      : null,
                ),
              ),
          ],
        ),
      ),
    );
  }
}
