import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

class NotificationRouter extends ChangeNotifier {
  NotificationRouter._();

  static final NotificationRouter instance = NotificationRouter._();
  static final GlobalKey<NavigatorState> navigatorKey = GlobalKey<NavigatorState>();

  final FlutterSecureStorage _storage = const FlutterSecureStorage();
  static const pendingConversationKey = 'pendingConversationId';
  static const pendingMessageKey = 'pendingMessageId';

  int? pendingConversationId;
  int? pendingMessageId;
  int? visibleConversationId;
  bool uiReady = false;
  bool loggedIn = false;
  Future<void> Function(int conversationId)? onVisibleChat;

  int? _lastNavMessageId;
  DateTime? _lastNavAt;

  Future<void> restore() async {
    final raw = await _storage.read(key: pendingConversationKey);
    final id = int.tryParse(raw ?? '');
    if (id == null || id <= 0) return;
    pendingConversationId = id;
    pendingMessageId = int.tryParse(await _storage.read(key: pendingMessageKey) ?? '');
  }

  void markUiReady({required bool isLoggedIn}) {
    uiReady = true;
    loggedIn = isLoggedIn;
    notifyListeners();
  }

  void setLoggedIn(bool value) {
    loggedIn = value;
    if (value && pendingConversationId != null) {
      notifyListeners();
    }
  }

  Future<void> clearDestination() async {
    pendingConversationId = null;
    pendingMessageId = null;
    visibleConversationId = null;
    await _storage.delete(key: pendingConversationKey);
    await _storage.delete(key: pendingMessageKey);
  }

  Future<void> consume() async {
    pendingConversationId = null;
    pendingMessageId = null;
    await _storage.delete(key: pendingConversationKey);
    await _storage.delete(key: pendingMessageKey);
  }

  Future<bool> acceptData(Map<String, dynamic> data) async {
    final type = '${data['type'] ?? ''}';
    if (type != 'CHAT_MESSAGE') return false;
    final route = data['route'];
    if (route != null && '$route'.isNotEmpty && '$route' != '/messages') return false;
    final conversationId = int.tryParse('${data['conversationId'] ?? ''}');
    if (conversationId == null || conversationId <= 0) return false;
    final messageId = int.tryParse('${data['messageId'] ?? ''}');
    if (!_shouldNavigate(messageId, conversationId)) return true;
    pendingConversationId = conversationId;
    pendingMessageId = messageId != null && messageId > 0 ? messageId : null;
    await _storage.write(key: pendingConversationKey, value: '$conversationId');
    if (pendingMessageId != null) {
      await _storage.write(key: pendingMessageKey, value: '${pendingMessageId!}');
    } else {
      await _storage.delete(key: pendingMessageKey);
    }
    if (uiReady) notifyListeners();
    return true;
  }

  Future<void> acceptPayload(String? payload) async {
    if (payload == null || payload.isEmpty) return;
    try {
      final decoded = jsonDecode(payload);
      if (decoded is Map) {
        await acceptData(Map<String, dynamic>.from(decoded));
      }
    } catch (_) {}
  }

  bool _shouldNavigate(int? messageId, int conversationId) {
    final now = DateTime.now();
    if (messageId != null &&
        messageId == _lastNavMessageId &&
        pendingConversationId == conversationId &&
        _lastNavAt != null &&
        now.difference(_lastNavAt!).inSeconds < 2) {
      return false;
    }
    _lastNavMessageId = messageId;
    _lastNavAt = now;
    return true;
  }
}
