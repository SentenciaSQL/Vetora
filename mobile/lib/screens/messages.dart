import 'package:flutter/material.dart';
import '../core/auth.dart';
import '../core/l10n.dart';

class MessagesScreen extends StatefulWidget {
  const MessagesScreen({super.key, required this.auth});
  final AuthStore auth;
  @override
  State<MessagesScreen> createState() => _MessagesScreenState();
}

class _MessagesScreenState extends State<MessagesScreen> {
  List convos = [];
  List pets = [];
  Map? current;
  List messages = [];
  final draft = TextEditingController();
  bool composing = false;
  int? petId;
  final subject = TextEditingController();

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final c = await widget.auth.api.get('/messages');
    final p = await widget.auth.api.get('/pets/mine');
    setState(() {
      convos = c as List? ?? [];
      pets = p as List? ?? [];
    });
  }

  Future<void> _open(Map convo) async {
    final m = await widget.auth.api.get('/messages/${convo['id']}');
    setState(() {
      current = convo;
      messages = m as List? ?? [];
    });
  }

  Future<void> _send() async {
    if (current == null || draft.text.trim().isEmpty) return;
    await widget.auth.api.post('/messages/${current!['id']}', {'body': draft.text.trim()});
    draft.clear();
    await _open(current!);
  }

  Future<void> _start() async {
    if (petId == null) return;
    final created = await widget.auth.api.post('/messages', {
      'petId': petId,
      'subject': subject.text,
    });
    composing = false;
    subject.clear();
    await _load();
    if (created is Map) await _open(created);
  }

  @override
  Widget build(BuildContext context) {
    final i = I18n.instance;
    if (current != null) {
      return SafeArea(
        child: Column(
          children: [
            ListTile(
              leading: IconButton(icon: const Icon(Icons.arrow_back), onPressed: () => setState(() => current = null)),
              title: Text('${current!['ownerName'] ?? current!['subject'] ?? i.t('messages')}'),
              subtitle: Text('${current!['petName'] ?? ''}'),
            ),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: Text(i.t('emergency'), style: Theme.of(context).textTheme.bodySmall),
            ),
            Expanded(
              child: ListView(
                padding: const EdgeInsets.all(16),
                children: [
                  for (final m in messages)
                    Align(
                      alignment: m['senderId'] == widget.auth.user?['id'] ? Alignment.centerRight : Alignment.centerLeft,
                      child: Card(
                        child: Padding(
                          padding: const EdgeInsets.all(12),
                          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                            Text('${m['senderName']}', style: Theme.of(context).textTheme.labelSmall),
                            Text('${m['body']}'),
                          ]),
                        ),
                      ),
                    ),
                ],
              ),
            ),
            Padding(
              padding: const EdgeInsets.all(12),
              child: Row(children: [
                Expanded(child: TextField(controller: draft, decoration: InputDecoration(hintText: i.t('send')))),
                IconButton(onPressed: _send, icon: const Icon(Icons.send)),
              ]),
            ),
          ],
        ),
      );
    }
    return SafeArea(
      child: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          Row(children: [
            Expanded(child: Text(i.t('messages'), style: Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.w700))),
            IconButton(onPressed: () => setState(() => composing = true), icon: const Icon(Icons.add)),
          ]),
          const SizedBox(height: 8),
          Text(i.t('emergency'), style: Theme.of(context).textTheme.bodySmall),
          const SizedBox(height: 12),
          if (convos.isEmpty) Text(i.t('empty')),
          for (final c in convos)
            Card(
              child: ListTile(
                title: Text('${c['subject'] ?? c['ownerName'] ?? i.t('messages')}'),
                subtitle: Text('${c['lastMessage'] ?? ''}\n${c['petName'] ?? ''}'),
                isThreeLine: true,
                trailing: (c['unread'] ?? 0) > 0 ? Badge(label: Text('${c['unread']}')) : null,
                onTap: () => _open(c as Map),
              ),
            ),
          if (composing)
            Card(
              child: Padding(
                padding: const EdgeInsets.all(12),
                child: Column(children: [
                  DropdownButtonFormField<int>(
                    decoration: InputDecoration(labelText: i.t('pets')),
                    items: [
                      for (final p in pets)
                        DropdownMenuItem(value: p['id'] as int, child: Text('${p['name']} · ${p['tenantName'] ?? ''}')),
                    ],
                    onChanged: (v) => petId = v,
                  ),
                  TextField(controller: subject, decoration: InputDecoration(labelText: i.t('subject'))),
                  const SizedBox(height: 8),
                  FilledButton(onPressed: _start, child: Text(i.t('newMessage'))),
                ]),
              ),
            ),
        ],
      ),
    );
  }
}
