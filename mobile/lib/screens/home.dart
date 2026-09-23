import 'package:flutter/material.dart';
import '../core/auth.dart';
import '../core/format.dart';
import '../core/l10n.dart';
import '../core/widgets.dart';
import 'appointments.dart';
import 'book.dart';
import 'clinics.dart';
import 'messages.dart';
import 'notifications.dart';
import 'owners.dart';
import 'pet_detail.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key, required this.auth});
  final AuthStore auth;
  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  Map<String, dynamic> home = {};
  List pets = [];
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
      final dash = await widget.auth.api.get('/dashboard');
      List mine = const [];
      if (widget.auth.isOwner) {
        mine = asList(await widget.auth.api.get('/pets/mine'));
      }
      if (!mounted) return;
      setState(() {
        home = asMap(dash);
        pets = mine;
        loading = false;
      });
      widget.auth.inbox.setMessagesUnread(asInt(home['unreadMessages'], widget.auth.inbox.unreadMessages));
    } catch (e) {
      if (!mounted) return;
      setState(() {
        loading = false;
        error = userMessage(e);
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final i = I18n.instance;
    final name = widget.auth.user?['firstName'] ?? '';
    return SafeArea(
      child: RefreshIndicator(
        onRefresh: _load,
        child: ListView(
          padding: const EdgeInsets.all(20),
          children: [
            Text('${i.t('hello')}, $name', style: Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.w700)),
            const SizedBox(height: 8),
            Text(widget.auth.isStaff ? '${widget.auth.user?['tenantName'] ?? ''}' : i.t('tagline')),
            const SizedBox(height: 16),
            if (loading || error != null)
              SizedBox(
                height: (MediaQuery.sizeOf(context).height
                        - MediaQuery.paddingOf(context).vertical
                        - kBottomNavigationBarHeight
                        - 140)
                    .clamp(160, 640),
                child: StatusView(loading: loading, error: error, onRetry: _load),
              )
            else if (widget.auth.isStaff)
              ..._staffCards(i)
            else
              ..._ownerCards(i),
          ],
        ),
      ),
    );
  }

  List<Widget> _ownerCards(I18n i) {
    final next = asMap(home['nextAppointment']);
    final vaccine = asMap(home['nextVaccine']);
    final treatments = asList(home['activeTreatments']);
    return [
      SizedBox(
        height: 108,
        child: pets.isEmpty
            ? Text(i.t('emptyPets'))
            : ListView(
                scrollDirection: Axis.horizontal,
                children: [
                  for (final pet in pets)
                    Padding(
                      padding: const EdgeInsets.only(right: 12),
                      child: InkWell(
                        onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => PetDetailScreen(auth: widget.auth, pet: asMap(pet)))).then((_) {
                          if (mounted) _load();
                        }),
                        child: Chip(
                          avatar: RemoteCircleAvatar(
                            url: asString(pet['photoUrl']),
                            radius: 12,
                            fallbackText: asString(pet['name'], '?'),
                          ),
                          label: Text(asString(pet['name'])),
                        ),
                      ),
                    ),
                ],
              ),
      ),
      Card(
        child: ListTile(
          leading: const Icon(Icons.event),
          title: Text(i.t('nextAppointment')),
          subtitle: Text(next['id'] == null ? i.t('empty') : '${next['pet'] ?? ''} · ${formatDate(next['startAt'])}'),
        ),
      ),
      const SizedBox(height: 8),
      Card(
        child: ListTile(
          leading: const Icon(Icons.vaccines_outlined),
          title: Text(i.t('nextVaccine')),
          subtitle: Text(vaccine['id'] == null
              ? i.t('empty')
              : '${vaccine['pet'] ?? ''} · ${vaccine['vaccine'] ?? ''}${statusLabel(vaccine['status']).isEmpty ? '' : ' · ${statusLabel(vaccine['status'])}'}'),
        ),
      ),
      const SizedBox(height: 8),
      Card(
        child: ListTile(
          leading: const Icon(Icons.healing_outlined),
          title: Text(i.t('activeTreatments')),
          subtitle: Text(treatments.isEmpty ? i.t('empty') : treatments.map((t) => '${t['petName']}: ${t['name']}').join('\n')),
          isThreeLine: treatments.length > 1,
        ),
      ),
      if (widget.auth.inbox.unreadMessages > 0) ...[
        const SizedBox(height: 8),
        Card(
          child: ListTile(
            leading: const Icon(Icons.chat_bubble_outline),
            title: Text(i.t('messages')),
            subtitle: Text('${widget.auth.inbox.unreadMessages} ${i.t('unread')}'),
            onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => MessagesScreen(auth: widget.auth))),
          ),
        ),
      ],
      const SizedBox(height: 8),
      OutlinedButton.icon(
        onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => NotificationsScreen(auth: widget.auth))),
        icon: Badge(
          isLabelVisible: widget.auth.inbox.unreadNotifications > 0,
          label: Text(widget.auth.inbox.badge(widget.auth.inbox.unreadNotifications)),
          child: const Icon(Icons.notifications_outlined),
        ),
        label: Text(i.t('notifications')),
      ),
      const SizedBox(height: 12),
      Text(i.t('quickActions'), style: Theme.of(context).textTheme.titleMedium),
      const SizedBox(height: 8),
      FilledButton.icon(
        onPressed: pets.isEmpty ? null : () => Navigator.push(context, MaterialPageRoute(builder: (_) => BookScreen(auth: widget.auth, pets: pets))),
        icon: const Icon(Icons.add),
        label: Text(i.t('book')),
      ),
      const SizedBox(height: 8),
      OutlinedButton.icon(
        onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => ClinicsScreen(auth: widget.auth, pets: pets))),
        icon: const Icon(Icons.local_hospital_outlined),
        label: Text(i.t('clinics')),
      ),
    ];
  }

  List<Widget> _staffCards(I18n i) {
    final agenda = asList(home['todayAgenda']);
    return [
      Wrap(spacing: 8, runSpacing: 8, children: [
        _stat(i.t('appointmentsToday'), '${home['appointmentsToday'] ?? 0}'),
        _stat(i.t('pendingAppointments'), '${home['pendingAppointments'] ?? 0}'),
        _stat(i.t('unread'), '${home['unreadMessages'] ?? 0}'),
        _stat(i.t('upcomingVaccines'), '${home['upcomingVaccines'] ?? 0}'),
      ]),
      const SizedBox(height: 16),
      Text(i.t('agenda'), style: Theme.of(context).textTheme.titleMedium),
      if (agenda.isEmpty) Padding(padding: const EdgeInsets.symmetric(vertical: 12), child: Text(i.t('empty'))),
      for (final item in agenda)
        ListTile(
          title: Text('${item['pet']} · ${item['owner']}'),
          subtitle: Text([formatDate(item['startAt']), statusLabel(item['status'])].where((part) => part.isNotEmpty).join(' · ')),
        ),
      const SizedBox(height: 8),
      if (!widget.auth.clinicalLocked &&
          (widget.auth.hasPermission('OWNER_READ') || widget.auth.hasAnyRole(const ['TENANT_OWNER', 'TENANT_ADMIN', 'RECEPTIONIST', 'VETERINARIAN'])))
        OutlinedButton.icon(
          onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => OwnersScreen(auth: widget.auth))),
          icon: const Icon(Icons.people_outline),
          label: Text(i.t('owners')),
        ),
      const SizedBox(height: 8),
      if (!widget.auth.clinicalLocked)
        OutlinedButton.icon(
          onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => AppointmentsScreen(auth: widget.auth))),
          icon: const Icon(Icons.calendar_month_outlined),
          label: Text(i.t('agenda')),
        ),
      const SizedBox(height: 8),
      OutlinedButton.icon(
        onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => NotificationsScreen(auth: widget.auth))),
        icon: const Icon(Icons.notifications_outlined),
        label: Text(i.t('notifications')),
      ),
    ];
  }

  Widget _stat(String label, String value) {
    return SizedBox(
      width: 150,
      child: Card(
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(value, style: Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.w700)),
            Text(label),
          ]),
        ),
      ),
    );
  }
}
