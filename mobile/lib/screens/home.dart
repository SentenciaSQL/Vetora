import 'package:flutter/material.dart';
import '../core/auth.dart';
import '../core/l10n.dart';
import 'book.dart';
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

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final dash = await widget.auth.api.get('/dashboard');
      final mine = await widget.auth.api.get('/pets/mine');
      setState(() {
        home = dash is Map<String, dynamic> ? dash : {};
        pets = mine as List? ?? [];
        loading = false;
      });
    } catch (_) {
      setState(() => loading = false);
    }
  }

  String _fmt(dynamic value) {
    if (value == null) return '';
    final raw = value.toString();
    if (raw.length >= 16) return raw.substring(0, 16).replaceFirst('T', ' ');
    return raw;
  }

  @override
  Widget build(BuildContext context) {
    final i = I18n.instance;
    final name = widget.auth.user?['firstName'] ?? '';
    final next = home['nextAppointment'] is Map ? home['nextAppointment'] as Map : {};
    final vaccine = home['nextVaccine'] is Map ? home['nextVaccine'] as Map : {};
    final treatments = home['activeTreatments'] as List? ?? [];
    return SafeArea(
      child: RefreshIndicator(
        onRefresh: _load,
        child: ListView(
          padding: const EdgeInsets.all(20),
          children: [
            Text('${i.t('hello')}, $name', style: Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.w700)),
            const SizedBox(height: 16),
            SizedBox(
              height: 108,
              child: ListView(
                scrollDirection: Axis.horizontal,
                children: [
                  for (final pet in pets)
                    Padding(
                      padding: const EdgeInsets.only(right: 12),
                      child: InkWell(
                        onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => PetDetailScreen(auth: widget.auth, pet: pet))),
                        child: Chip(
                          avatar: CircleAvatar(
                            backgroundImage: (pet['photoUrl'] as String?)?.isNotEmpty == true ? NetworkImage(pet['photoUrl']) : null,
                            child: (pet['photoUrl'] as String?)?.isNotEmpty == true ? null : Text('${pet['name']}'.substring(0, 1)),
                          ),
                          label: Text('${pet['name']}'),
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
                subtitle: Text(next['id'] == null ? i.t('empty') : '${next['pet'] ?? ''} · ${_fmt(next['startAt'])}'),
              ),
            ),
            const SizedBox(height: 8),
            Card(
              child: ListTile(
                leading: const Icon(Icons.vaccines_outlined),
                title: Text(i.t('nextVaccine')),
                subtitle: Text(vaccine['id'] == null ? i.t('empty') : '${vaccine['pet'] ?? ''} · ${vaccine['vaccine'] ?? ''}'),
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
            if ((home['unreadMessages'] ?? 0) > 0) ...[
              const SizedBox(height: 8),
              Card(
                child: ListTile(
                  leading: const Icon(Icons.chat_bubble_outline),
                  title: Text(i.t('messages')),
                  subtitle: Text('${home['unreadMessages']} ${i.t('unread')}'),
                ),
              ),
            ],
            if ((home['unreadNotifications'] ?? 0) > 0) ...[
              const SizedBox(height: 8),
              Card(
                child: ListTile(
                  leading: const Icon(Icons.notifications_outlined),
                  title: Text(i.t('notifications')),
                  subtitle: Text('${home['unreadNotifications']} ${i.t('unread')}'),
                ),
              ),
            ],
            const SizedBox(height: 12),
            Text(i.t('quickActions'), style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 8),
            FilledButton.icon(
              onPressed: pets.isEmpty ? null : () => Navigator.push(context, MaterialPageRoute(builder: (_) => BookScreen(auth: widget.auth, pets: pets))),
              icon: const Icon(Icons.add),
              label: Text(i.t('book')),
            ),
          ],
        ),
      ),
    );
  }
}
