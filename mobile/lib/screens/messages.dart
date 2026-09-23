import 'dart:async';
import 'package:flutter/material.dart';
import '../core/api.dart';
import '../core/auth.dart';
import '../core/format.dart';
import '../core/l10n.dart';
import '../core/notification_router.dart';
import '../core/widgets.dart';

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
  bool loading = true;
  bool sending = false;
  String? error;
  String? chatError;
  int? petId;
  final subject = TextEditingController();
  Timer? _poll;
  int? _consuming;

  @override
  void initState() {
    super.initState();
    NotificationRouter.instance.addListener(_consumeRoute);
    NotificationRouter.instance.onVisibleChat = (id) async {
      if (!mounted || asInt(current?['id']) != id) return;
      await _open(asMap(current), silent: true);
    };
    _load();
    WidgetsBinding.instance.addPostFrameCallback((_) => _consumeRoute());
    _poll = Timer.periodic(const Duration(seconds: 45), (_) {
      if (current == null) {
        _load(silent: true);
      } else {
        _open(current!, silent: true);
      }
    });
  }

  @override
  void dispose() {
    _poll?.cancel();
    draft.dispose();
    subject.dispose();
    NotificationRouter.instance.removeListener(_consumeRoute);
    if (NotificationRouter.instance.onVisibleChat != null) {
      NotificationRouter.instance.onVisibleChat = null;
    }
    if (NotificationRouter.instance.visibleConversationId == asInt(current?['id'])) {
      NotificationRouter.instance.visibleConversationId = null;
    }
    super.dispose();
  }

  List<Map<String, dynamic>> _uniqueMessages(List<dynamic> raw) {
    final seen = <int>{};
    final items = <Map<String, dynamic>>[];
    for (final item in raw) {
      final map = asMap(item);
      final id = asInt(map['id']);
      if (id != 0 && !seen.add(id)) continue;
      items.add(map);
    }
    return items;
  }

  Future<void> _consumeRoute() async {
    final id = NotificationRouter.instance.pendingConversationId;
    if (id == null || _consuming == id) return;
    _consuming = id;
    final shown = await _openById(id);
    if (!mounted) {
      if (_consuming == id) _consuming = null;
      return;
    }
    if (shown != null) {
      await NotificationRouter.instance.consume();
    }
    if (_consuming == id) _consuming = null;
  }

  Future<bool?> _openById(int id) async {
    try {
      final page = await widget.auth.api.get('/messages/$id', {'size': '50', 'page': '0', 'sort': 'createdAt,asc'});
      if (!mounted) return null;
      var convo = <String, dynamic>{'id': id};
      try {
        final list = asList(await widget.auth.api.get('/messages', null, true));
        convos = list;
        for (final item in list) {
          final map = asMap(item);
          if (asInt(map['id']) == id) {
            convo = map;
            break;
          }
        }
      } catch (_) {}
      if (!mounted) return null;
      setState(() {
        current = convo;
        messages = _uniqueMessages(asList(page));
        chatError = null;
        loading = false;
        error = null;
      });
      NotificationRouter.instance.visibleConversationId = id;
      try {
        await widget.auth.api.post('/messages/$id/read', {});
        await widget.auth.inbox.refresh();
        await _load(silent: true);
        if (!mounted) return true;
        for (final item in convos) {
          final map = asMap(item);
          if (asInt(map['id']) == id) {
            setState(() => current = {...asMap(current), ...map});
            break;
          }
        }
      } catch (_) {}
      return true;
    } catch (e) {
      if (!mounted || e is UnauthorizedException) return null;
      NotificationRouter.instance.visibleConversationId = null;
      setState(() => current = null);
      final denied = e is ApiException && (e.status == 403 || e.status == 404);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(denied ? I18n.instance.t('conversationUnavailable') : userMessage(e))),
      );
      return false;
    }
  }

  Future<void> _load({bool silent = false}) async {
    if (!silent) {
      setState(() {
        loading = true;
        error = null;
      });
    }
    try {
      final c = asList(await widget.auth.api.get('/messages', null, silent));
      List p = pets;
      if (widget.auth.isOwner) {
        p = asList(await widget.auth.api.get('/pets/mine', null, silent));
      } else {
        p = asList(await widget.auth.api.get('/pets', {'size': '50'}, silent));
      }
      if (!mounted) return;
      setState(() {
        convos = c;
        pets = p;
        loading = false;
      });
      widget.auth.inbox.setMessagesUnread(c.fold<int>(0, (sum, item) => sum + asInt(item['unread'])));
    } catch (e) {
      if (!mounted || silent) return;
      setState(() {
        loading = false;
        error = userMessage(e);
      });
    }
  }

  int page = 0;

  Future<void> _open(Map convo, {bool silent = false}) async {
    if (!silent) {
      setState(() {
        current = Map<String, dynamic>.from(convo);
        chatError = null;
        page = 0;
      });
    }
    try {
      final m = await widget.auth.api.get('/messages/${convo['id']}', {'size': '50', 'page': '$page', 'sort': 'createdAt,asc'}, silent);
      if (!mounted) return;
      final items = _uniqueMessages(asList(m));
      setState(() {
        current = {...asMap(current), ...asMap(convo), 'id': convo['id']};
        messages = items;
        chatError = null;
      });
      NotificationRouter.instance.visibleConversationId = asInt(convo['id']);
      try {
        await widget.auth.api.post('/messages/${convo['id']}/read', {});
        await widget.auth.inbox.refresh();
        await _load(silent: true);
      } catch (_) {}
    } catch (e) {
      if (!mounted || silent) return;
      setState(() => chatError = userMessage(e));
    }
  }

  Future<void> _send() async {
    if (current == null || draft.text.trim().isEmpty || sending) return;
    final body = draft.text.trim();
    setState(() => sending = true);
    try {
      final message = asMap(await widget.auth.api.post('/messages/${current!['id']}', {'body': body}));
      draft.clear();
      final next = [...messages, message];
      setState(() {
        messages = _uniqueMessages(next);
        sending = false;
      });
      await _load(silent: true);
    } catch (e) {
      if (mounted) {
        setState(() => sending = false);
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(userMessage(e))));
      }
    }
  }

  Future<void> _start() async {
    if (petId == null) return;
    try {
      final created = await widget.auth.api.post('/messages', {'petId': petId, 'subject': subject.text});
      composing = false;
      subject.clear();
      await _load();
      if (created is Map) await _open(Map<String, dynamic>.from(created));
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(userMessage(e))));
    }
  }

  String _title(Map c) => asString(c['title'] ?? (widget.auth.isOwner ? c['tenantName'] : c['ownerName']) ?? c['subject'], I18n.instance.t('messages'));

  @override
  Widget build(BuildContext context) {
    final i = I18n.instance;
    final open = current != null;
    return PopScope(
      canPop: !open,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop && open) {
          NotificationRouter.instance.visibleConversationId = null;
          setState(() => current = null);
        }
      },
      child: Scaffold(
        appBar: AppBar(
          leading: open
              ? IconButton(
                  icon: const Icon(Icons.arrow_back),
                  onPressed: () {
                    NotificationRouter.instance.visibleConversationId = null;
                    setState(() => current = null);
                  },
                )
              : null,
          title: Text(open ? _title(asMap(current)) : i.t('messages')),
          actions: [
            if (!open && (widget.auth.isOwner || widget.auth.hasPermission('MESSAGE_WRITE')))
              IconButton(onPressed: () => setState(() => composing = true), icon: const Icon(Icons.add)),
          ],
        ),
        body: !widget.auth.messagingEnabled
            ? Center(child: Padding(padding: const EdgeInsets.all(24), child: Text(i.t('featureUnavailable'))))
            : (open ? _chat(i) : _inbox(i)),
      ),
    );
  }

  Widget _chat(I18n i) {
    return Column(
      children: [
        if (asString(current?['petName']).isNotEmpty)
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
            child: Align(alignment: Alignment.centerLeft, child: Text('${current!['petName']}', style: Theme.of(context).textTheme.bodySmall)),
          ),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
          child: Text(i.t('emergency'), style: Theme.of(context).textTheme.bodySmall),
        ),
        if (chatError != null)
          Expanded(child: StatusView(error: chatError, onRetry: () => _open(current!)))
        else
          Expanded(
            child: messages.isEmpty
                ? Center(child: Text(i.t('empty')))
                : ListView(
                    padding: const EdgeInsets.all(16),
                    children: [
                      for (final m in messages)
                        Align(
                          alignment: asInt(m['senderId']) == asInt(widget.auth.user?['id']) ? Alignment.centerRight : Alignment.centerLeft,
                          child: Card(
                            child: Padding(
                              padding: const EdgeInsets.all(12),
                              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                                Text('${m['senderName']} · ${formatDate(m['createdAt'])}', style: Theme.of(context).textTheme.labelSmall),
                                Text('${m['body']}'),
                                if (asInt(m['senderId']) == asInt(widget.auth.user?['id']))
                                  Text(m['readAt'] == null ? i.t('sent') : i.t('read'), style: Theme.of(context).textTheme.labelSmall),
                              ]),
                            ),
                          ),
                        ),
                    ],
                  ),
          ),
        Material(
          elevation: 2,
          child: SafeArea(
            top: false,
            child: Padding(
              padding: const EdgeInsets.all(12),
              child: Row(children: [
                Expanded(child: TextField(controller: draft, enabled: !sending, minLines: 1, maxLines: 4, decoration: InputDecoration(hintText: i.t('send')))),
                IconButton(onPressed: sending ? null : _send, icon: const Icon(Icons.send)),
              ]),
            ),
          ),
        ),
      ],
    );
  }

  Widget _inbox(I18n i) {
    return RefreshIndicator(
      onRefresh: _load,
      child: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          Text(i.t('emergency'), style: Theme.of(context).textTheme.bodySmall),
          const SizedBox(height: 12),
          if (loading || error != null)
            StatusView(loading: loading, error: error, onRetry: _load)
          else if (convos.isEmpty)
            Text(i.t('empty'))
          else
            for (final c in convos)
              Card(
                color: asInt(c['unread']) > 0 ? Theme.of(context).colorScheme.primary.withValues(alpha: 0.08) : null,
                child: ListTile(
                  title: Text(_title(asMap(c)), style: TextStyle(fontWeight: asInt(c['unread']) > 0 ? FontWeight.w700 : FontWeight.w400)),
                  subtitle: Text('${c['lastMessage'] ?? ''}\n${formatDate(c['updatedAt'])}'),
                  isThreeLine: true,
                  trailing: asInt(c['unread']) > 0 ? CountBadge(count: asInt(c['unread']), label: '${c['unread']} ${i.t('unread')}') : null,
                  onTap: () => _open(asMap(c)),
                ),
              ),
          if (composing)
            Card(
              child: Padding(
                padding: const EdgeInsets.all(12),
                child: Column(children: [
                  AppDropdownField<int>(
                    value: petId,
                    label: i.t('pets'),
                    options: [
                      for (final p in pets)
                        LabeledOption(asInt(p['id']), '${p['name']} · ${p['tenantName'] ?? ''}'),
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
