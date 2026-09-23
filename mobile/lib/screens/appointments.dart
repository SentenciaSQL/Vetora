import 'package:flutter/material.dart';
import '../core/auth.dart';
import '../core/format.dart';
import '../core/l10n.dart';
import '../core/specialty.dart';
import '../core/widgets.dart';
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
  bool loading = true;
  String? error;
  String? statusFilter;

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
      if (widget.auth.isStaff) {
        final now = DateTime.now().toUtc();
        final from = now.subtract(const Duration(days: 7)).toIso8601String();
        final to = now.add(const Duration(days: 21)).toIso8601String();
        final query = {'from': from, 'to': to};
        if (statusFilter != null) query['status'] = statusFilter!;
        items = asList(await widget.auth.api.get('/appointments', query));
      } else {
        items = asList(await widget.auth.api.get('/appointments/mine'));
        pets = asList(await widget.auth.api.get('/pets/mine'));
        if (statusFilter != null) {
          items = items.where((a) => a['status'] == statusFilter).toList();
        }
      }
      if (!mounted) return;
      setState(() => loading = false);
    } catch (e) {
      if (!mounted) return;
      setState(() {
        loading = false;
        error = userMessage(e);
      });
    }
  }

  Future<void> _cancel(dynamic id) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(I18n.instance.t('cancel')),
        content: Text(I18n.instance.t('cancelConfirm')),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: Text(I18n.instance.t('back'))),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: Text(I18n.instance.t('cancel'))),
        ],
      ),
    );
    if (ok != true) return;
    try {
      await widget.auth.api.post('/appointments/$id/status', {'status': 'CANCELLED'});
      await _load();
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(userMessage(e))));
    }
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
      slots = asList(await widget.auth.api.get('/appointments/availability', {
        'veterinarianId': '${a['veterinarianId']}',
        if (a['branchId'] != null) 'branchId': '${a['branchId']}',
        if (a['serviceId'] != null) 'serviceId': '${a['serviceId']}',
        'date': day,
      }));
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(userMessage(e))));
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
              ListTile(title: Text(_slotLabel(s['startAt'])), onTap: () => Navigator.pop(ctx, s as Map)),
          ],
        ),
      ),
    );
    if (slot == null) return;
    try {
      await widget.auth.api.put('/appointments/${a['id']}', {
        'petId': a['petId'],
        'veterinarianId': a['veterinarianId'],
        'serviceId': a['serviceId'],
        'branchId': a['branchId'],
        'startAt': slot['startAt'],
      });
      await _load();
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(userMessage(e))));
    }
  }

  Future<void> _setStatus(Map a, String status) async {
    try {
      await widget.auth.api.post('/appointments/${a['id']}/status', {'status': status});
      await _load();
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(userMessage(e))));
    }
  }

  bool _canManage(dynamic status) => ['REQUESTED', 'PENDING', 'CONFIRMED'].contains(status);

  @override
  Widget build(BuildContext context) {
    final i = I18n.instance;
    final statuses = const [null, 'REQUESTED', 'CONFIRMED', 'COMPLETED', 'CANCELLED', 'NO_SHOW'];
    return Scaffold(
      body: SafeArea(
        child: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 20, 20, 8),
            child: Row(children: [
              Expanded(child: Text(widget.auth.isStaff ? i.t('agenda') : i.t('appointments'), style: Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.w700))),
              if (!widget.auth.isStaff)
                FilledButton(
                  onPressed: pets.isEmpty ? null : () => Navigator.push(context, MaterialPageRoute(builder: (_) => BookScreen(auth: widget.auth, pets: pets))).then((_) => _load()),
                  child: Text(i.t('book')),
                ),
            ]),
          ),
          SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Row(
              children: [
                for (final status in statuses)
                  Padding(
                    padding: const EdgeInsets.only(right: 8),
                    child: FilterChip(
                      label: Text(status == null ? i.t('all') : statusLabel(status)),
                      selected: statusFilter == status,
                      onSelected: (_) {
                        statusFilter = status;
                        _load();
                      },
                    ),
                  ),
              ],
            ),
          ),
          Expanded(
            child: RefreshIndicator(
              onRefresh: _load,
              child: loading || error != null || items.isEmpty
                  ? ListView(children: [StatusView(loading: loading, error: error, empty: loading ? null : i.t('empty'), onRetry: _load)])
                  : ListView(
                      padding: const EdgeInsets.all(20),
                      children: [
                        for (final a in items)
                          Card(
                            child: ListTile(
                              leading: RemoteCircleAvatar(url: asString(a['tenantLogoUrl'])),
                              title: Text('${a['petName'] ?? a['pet'] ?? ''} · ${a['serviceName'] ?? ''}'),
                              subtitle: Text([
                                formatDate(a['startAt']),
                                [
                                  a['tenantName'] ?? a['owner'] ?? '',
                                  a['veterinarianName'] ?? a['veterinarian'] ?? '',
                                  specialtyLabel(a['veterinarianSpecialty'], other: a['veterinarianSpecialtyOther']),
                                  statusLabel(a['status']),
                                ].where((part) => '$part'.trim().isNotEmpty).join(' · '),
                              ].join('\n')),
                              isThreeLine: true,
                              onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => AppointmentDetailScreen(auth: widget.auth, appointment: asMap(a)))).then((changed) {
                                if (changed == true) _load();
                              }),
                              trailing: _canManage(a['status'])
                                  ? PopupMenuButton<String>(
                                      onSelected: (value) {
                                        if (value == 'cancel') _cancel(a['id']);
                                        if (value == 'reschedule') _reschedule(asMap(a));
                                        if (value == 'confirm') _setStatus(asMap(a), 'CONFIRMED');
                                        if (value == 'complete') _setStatus(asMap(a), 'COMPLETED');
                                        if (value == 'noshow') _setStatus(asMap(a), 'NO_SHOW');
                                      },
                                      itemBuilder: (_) => [
                                        if (widget.auth.isStaff) PopupMenuItem(value: 'confirm', child: Text(i.t('status_CONFIRMED'))),
                                        PopupMenuItem(value: 'reschedule', child: Text(i.t('reschedule'))),
                                        if (widget.auth.isStaff) PopupMenuItem(value: 'complete', child: Text(i.t('status_COMPLETED'))),
                                        if (widget.auth.isStaff) PopupMenuItem(value: 'noshow', child: Text(i.t('status_NO_SHOW'))),
                                        PopupMenuItem(value: 'cancel', child: Text(i.t('cancel'))),
                                      ],
                                    )
                                  : null,
                            ),
                          ),
                      ],
                    ),
            ),
          ),
        ],
      ),
      ),
    );
  }
}

class AppointmentDetailScreen extends StatefulWidget {
  const AppointmentDetailScreen({super.key, required this.auth, required this.appointment});
  final AuthStore auth;
  final Map<String, dynamic> appointment;

  @override
  State<AppointmentDetailScreen> createState() => _AppointmentDetailScreenState();
}

class _AppointmentDetailScreenState extends State<AppointmentDetailScreen> {
  Map<String, dynamic> item = {};
  bool loading = true;
  String? error;
  bool busy = false;

  @override
  void initState() {
    super.initState();
    item = widget.appointment;
    _load();
  }

  Future<void> _load() async {
    setState(() {
      loading = true;
      error = null;
    });
    try {
      item = asMap(await widget.auth.api.get('/appointments/${widget.appointment['id']}'));
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

  bool get canManage => ['REQUESTED', 'PENDING', 'CONFIRMED'].contains(item['status']);

  Future<void> _status(String status) async {
    if (busy) return;
    if (status == 'CANCELLED') {
      final ok = await showDialog<bool>(
        context: context,
        builder: (ctx) => AlertDialog(
          title: Text(I18n.instance.t('cancel')),
          content: Text(I18n.instance.t('cancelConfirm')),
          actions: [
            TextButton(onPressed: () => Navigator.pop(ctx, false), child: Text(I18n.instance.t('back'))),
            FilledButton(onPressed: () => Navigator.pop(ctx, true), child: Text(I18n.instance.t('cancel'))),
          ],
        ),
      );
      if (ok != true) return;
    }
    setState(() => busy = true);
    try {
      await widget.auth.api.post('/appointments/${item['id']}/status', {'status': status});
      if (mounted) Navigator.pop(context, true);
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(userMessage(e))));
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final i = I18n.instance;
    return Scaffold(
      appBar: AppBar(title: Text(i.t('appointments'))),
      body: loading || error != null
          ? StatusView(loading: loading, error: error, onRetry: _load)
          : ListView(
              padding: const EdgeInsets.all(20),
              children: [
                ListTile(title: Text(i.t('pets')), subtitle: Text('${item['petName'] ?? '—'}')),
                ListTile(title: Text(i.t('clinic')), subtitle: Text('${item['tenantName'] ?? '—'}')),
                ListTile(title: Text(i.t('service')), subtitle: Text('${item['serviceName'] ?? '—'}')),
                ListTile(
                  title: Text(i.t('vet')),
                  subtitle: Text([
                    asString(item['veterinarianName'], '—'),
                    specialtyLabel(item['veterinarianSpecialty'], other: item['veterinarianSpecialtyOther']),
                  ].where((line) => line.isNotEmpty).join('\n')),
                ),
                ListTile(title: Text(i.t('date')), subtitle: Text(formatDate(item['startAt']))),
                ListTile(title: Text(i.t('subscriptionStatus')), subtitle: Text(statusLabel(item['status']))),
                if (asString(item['reason']).isNotEmpty) ListTile(title: Text(i.t('reason')), subtitle: Text('${item['reason']}')),
                const SizedBox(height: 16),
                if (canManage && !busy) ...[
                  if (widget.auth.isStaff)
                    FilledButton(onPressed: () => _status('CONFIRMED'), child: Text(i.t('status_CONFIRMED'))),
                  if (widget.auth.isStaff) const SizedBox(height: 8),
                  if (widget.auth.isStaff)
                    OutlinedButton(onPressed: () => _status('COMPLETED'), child: Text(i.t('status_COMPLETED'))),
                  if (widget.auth.isStaff) const SizedBox(height: 8),
                  OutlinedButton(onPressed: () => _status('CANCELLED'), child: Text(i.t('cancel'))),
                ],
              ],
            ),
    );
  }
}
