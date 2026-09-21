import 'dart:async';
import 'package:flutter/foundation.dart';
import 'auth.dart';
import 'format.dart';

class MessageInbox extends ChangeNotifier {
  MessageInbox(this.auth);

  final AuthStore auth;
  int unreadMessages = 0;
  int unreadNotifications = 0;
  Timer? _timer;
  bool _inFlight = false;

  String badge(int count) => count > 99 ? '99+' : '$count';

  void start() {
    stop();
    if (!auth.isLoggedIn || auth.isSuperAdmin) return;
    refresh();
    _timer = Timer.periodic(const Duration(seconds: 45), (_) => refresh());
  }

  void stop() {
    _timer?.cancel();
    _timer = null;
    _inFlight = false;
    unreadMessages = 0;
    unreadNotifications = 0;
  }

  Future<void> refresh() async {
    if (!auth.isLoggedIn || _inFlight) return;
    _inFlight = true;
    if (auth.messagingEnabled) {
      try {
        final messages = asMap(await auth.api.get('/messages/unread-count', null, true));
        unreadMessages = asInt(messages['count']);
      } catch (_) {}
    } else {
      unreadMessages = 0;
    }
    try {
      final notes = asMap(await auth.api.get('/notifications/unread-count', null, true));
      unreadNotifications = asInt(notes['count']);
    } catch (_) {}
    _inFlight = false;
    notifyListeners();
  }

  void setMessagesUnread(int count) {
    unreadMessages = count < 0 ? 0 : count;
    notifyListeners();
  }
}
