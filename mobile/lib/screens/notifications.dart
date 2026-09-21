import 'package:flutter/material.dart';
import '../core/auth.dart';
import '../core/format.dart';
import '../core/l10n.dart';
import '../core/widgets.dart';
import 'appointments.dart';
import 'messages.dart';
import 'pet_detail.dart';

class NotificationsScreen extends StatefulWidget {
  const NotificationsScreen({super.key, required this.auth});
  final AuthStore auth;
  @override
  State<NotificationsScreen> createState() => _NotificationsScreenState();
}

class _NotificationsScreenState extends State<NotificationsScreen> {
  List items = [];
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
      items = asList(await widget.auth.api.get('/notifications', {'size': '30'}));
      if (mounted) setState(() => loading = false);
      await widget.auth.inbox.refresh();
    } catch (e) {
      if (mounted) {
        setState(() {
          loading = false;
          error = userMessage(e);
        });
      }
    }
  }

  Future<void> _open(Map item) async {
    try {
      await widget.auth.api.post('/notifications/${item['id']}/read', {});
    } catch (_) {}
    await widget.auth.inbox.refresh();
    if (!mounted) return;
    final type = asString(item['type']);
    final entity = asString(item['entityType']);
    if (type == 'NEW_MESSAGE' || entity == 'CONVERSATION') {
      await Navigator.push(context, MaterialPageRoute(builder: (_) => MessagesScreen(auth: widget.auth)));
    } else if (entity == 'APPOINTMENT' || type.startsWith('APPOINTMENT')) {
      await Navigator.push(context, MaterialPageRoute(builder: (_) => AppointmentsScreen(auth: widget.auth)));
    } else if (entity == 'PET' && item['entityId'] != null) {
      await Navigator.push(context, MaterialPageRoute(builder: (_) => PetDetailScreen(auth: widget.auth, pet: {'id': item['entityId']})));
    }
    await _load();
  }

  @override
  Widget build(BuildContext context) {
    final i = I18n.instance;
    return Scaffold(
      appBar: AppBar(title: Text(i.t('notifications'))),
      body: RefreshIndicator(
        onRefresh: _load,
        child: loading || error != null || items.isEmpty
            ? ListView(children: [StatusView(loading: loading, error: error, empty: loading ? null : i.t('empty'), onRetry: _load)])
            : ListView(
                children: [
                  for (final n in items)
                    ListTile(
                      leading: Icon(n['readAt'] == null ? Icons.notifications_active_outlined : Icons.notifications_none),
                      title: Text('${n['title'] ?? n['titleEs'] ?? ''}', style: TextStyle(fontWeight: n['readAt'] == null ? FontWeight.w700 : FontWeight.w400)),
                      subtitle: Text('${n['body'] ?? ''}\n${formatDate(n['createdAt'])}'),
                      isThreeLine: true,
                      onTap: () => _open(asMap(n)),
                    ),
                ],
              ),
      ),
    );
  }
}
