import 'dart:async';
import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:http/http.dart' as http;
import 'api.dart';
import 'format.dart';
import 'inbox.dart';
import 'l10n.dart';
import 'notification_router.dart';
import 'push.dart';

class AuthStore extends ChangeNotifier {
  final _storage = const FlutterSecureStorage();
  String? accessToken;
  String? refreshToken;
  Map<String, dynamic>? user;
  Map<String, dynamic>? subscription;
  bool warningOpen = false;
  String? logoutReason;
  bool refreshRejected = false;

  Future<bool>? _refreshing;
  bool _closing = false;
  DateTime _lastActivity = DateTime.now();
  DateTime _lastHeartbeat = DateTime.fromMillisecondsSinceEpoch(0);
  int inactivityMinutes = 30;
  int warningMinutes = 2;
  Timer? _checkTimer;

  bool get isLoggedIn => accessToken != null && user != null;

  late final ApiClient api = ApiClient(this);
  late final MessageInbox inbox = MessageInbox(this);

  List<dynamic> get roles {
    final value = user?['roles'];
    if (value is List) return value;
    final role = user?['role'];
    return role == null ? const [] : [role];
  }

  List<dynamic> get permissions {
    final value = user?['permissions'];
    return value is List ? value : const [];
  }

  bool hasRole(String role) => roles.contains(role);

  bool hasAnyRole(List<String> wanted) => wanted.any(hasRole);

  bool hasPermission(String permission) => permissions.contains(permission) || isSuperAdmin;

  bool get isSuperAdmin => hasRole('SUPER_ADMIN');

  bool get isStaff => hasAnyRole(const ['TENANT_OWNER', 'TENANT_ADMIN', 'VETERINARIAN', 'RECEPTIONIST']);

  bool get isOwner => hasRole('PET_OWNER') && !isStaff && !isSuperAdmin;

  bool get canBill => hasAnyRole(const ['TENANT_OWNER', 'TENANT_ADMIN']);

  bool get canWriteMedical => hasPermission('MEDICAL_RECORD_WRITE');

  bool get messagingEnabled {
    if (isOwner) return true;
    final limits = asMap(subscription?['limits']);
    if (limits.isEmpty) return true;
    return limits['messagingEnabled'] != false;
  }

  bool get laboratoryEnabled {
    final limits = asMap(subscription?['limits']);
    if (limits.isEmpty) return true;
    return limits['laboratoryEnabled'] != false;
  }

  bool get reportsEnabled {
    final limits = asMap(subscription?['limits']);
    if (limits.isEmpty) return true;
    return limits['reportsEnabled'] != false;
  }

  bool get suspended {
    final status = '${user?['tenantStatus'] ?? subscription?['status'] ?? ''}';
    return status == 'SUSPENDED' || status == 'PAUSED' || subscription?['suspended'] == true;
  }

  bool get gracePeriod => subscription?['gracePeriod'] == true;

  bool get clinicalLocked {
    if (isOwner || isSuperAdmin) return false;
    if (suspended) return true;
    if (gracePeriod) return false;
    return user?['accessGranted'] == false;
  }

  Future<void> restore() async {
    await I18n.instance.load('es');
    accessToken = await _storage.read(key: 'access');
    refreshToken = await _storage.read(key: 'refresh');
    final raw = await _storage.read(key: 'user');
    if (raw != null) {
      user = jsonDecode(raw) as Map<String, dynamic>;
      await I18n.instance.load((user?['locale'] as String?) ?? 'es');
    }
    final activity = await _storage.read(key: 'lastActivity');
    if (activity != null) {
      _lastActivity = DateTime.fromMillisecondsSinceEpoch(int.tryParse(activity) ?? 0);
    }
    notifyListeners();
    if (accessToken != null) {
      await _hydrate();
    }
  }

  Future<void> login(String email, String password) async {
    final data = await api.post('/auth/login', {'email': email, 'password': password});
    await _persist(asMap(data), resetActivity: true);
    await afterLogin();
  }

  Future<void> register(Map<String, String> payload) async {
    final data = await api.post('/auth/register', payload);
    await _persist(asMap(data), resetActivity: true);
    await afterLogin();
  }

  Future<void> forgot(String email) async {
    await api.post('/auth/forgot-password', {'email': email});
  }

  Future<void> resetPassword(String token, String password) async {
    await api.post('/auth/reset-password', {'token': token, 'password': password});
  }

  Future<void> changePassword(String currentPassword, String newPassword) async {
    await api.post('/auth/change-password', {
      'currentPassword': currentPassword,
      'newPassword': newPassword,
    });
  }

  Future<void> updateProfile(Map<String, dynamic> payload) async {
    final data = await api.patch('/auth/me', payload);
    await applyProfile(asMap(data));
  }

  Future<void> uploadAvatar(List<int> bytes, String filename) async {
    await applyProfile(asMap(await api.upload('/auth/me/avatar', bytes, filename)));
  }

  Future<void> clearAvatar() async {
    await applyProfile(asMap(await api.delete('/auth/me/avatar')));
  }

  Future<void> applyProfile(Map<String, dynamic> data) async {
    if (data.isEmpty) return;
    user = data;
    await _storage.write(key: 'user', value: jsonEncode(user));
    notifyListeners();
  }

  Future<bool> refreshAccessToken() {
    if (_refreshing != null) return _refreshing!;
    _refreshing = _doRefresh();
    return _refreshing!.whenComplete(() => _refreshing = null);
  }

  Future<bool> _doRefresh() async {
    if (refreshToken == null) {
      refreshRejected = true;
      return false;
    }
    try {
      final res = await http.post(
        Uri.parse('${api.baseUrl}/auth/refresh'),
        headers: {'Content-Type': 'application/json', 'X-Lunaveta-Client': 'mobile'},
        body: jsonEncode({'refreshToken': refreshToken}),
      );
      if (res.statusCode == 401 && parseApiCode(res.body) == 'SESSION_INACTIVE') {
        refreshRejected = true;
        await expire('INACTIVITY');
        return false;
      }
      if (res.statusCode == 401) {
        refreshRejected = true;
        return false;
      }
      if (res.statusCode >= 400) {
        refreshRejected = false;
        return false;
      }
      refreshRejected = false;
      await _persist(asMap(jsonDecode(res.body)), resetActivity: false);
      return true;
    } catch (_) {
      refreshRejected = false;
      return false;
    }
  }

  Future<void> setTheme(String theme) async {
    if (isLoggedIn) {
      await updateProfile({'theme': theme});
    }
  }

  Future<void> setLocale(String locale) async {
    await I18n.instance.load(locale);
    if (isLoggedIn) {
      await updateProfile({'locale': locale});
    }
  }

  Future<void> logout() async {
    await expire('MANUAL');
  }

  Future<void> expire([String reason = 'MANUAL']) async {
    if (_closing) return;
    _closing = true;
    logoutReason = reason;
    inbox.stop();
    _checkTimer?.cancel();
    final refresh = refreshToken;
    final keepDestination = reason == 'UNAUTHORIZED' || reason == 'INACTIVITY';
    final installationId = await _storage.read(key: 'installationId');
    final permissionAsked = await _storage.read(key: 'notificationsPermissionRequested');
    final pendingConversation = keepDestination ? await _storage.read(key: NotificationRouter.pendingConversationKey) : null;
    final pendingMessage = keepDestination ? await _storage.read(key: NotificationRouter.pendingMessageKey) : null;
    if (refresh != null && reason != 'REMOTE' && reason != 'ACCOUNT_DELETED') {
      try {
        await http.post(
          Uri.parse('${api.baseUrl}/auth/logout'),
          headers: {'Content-Type': 'application/json', 'X-Lunaveta-Client': 'mobile'},
          body: jsonEncode({'refreshToken': refresh, 'reason': reason}),
        );
      } catch (_) {}
    }
    await PushService.unregister(api);
    if (!keepDestination) {
      await NotificationRouter.instance.clearDestination();
    }
    accessToken = null;
    refreshToken = null;
    user = null;
    subscription = null;
    warningOpen = false;
    refreshRejected = false;
    NotificationRouter.instance.loggedIn = false;
    await _storage.deleteAll();
    if (installationId != null && installationId.isNotEmpty) {
      await _storage.write(key: 'installationId', value: installationId);
    }
    if (permissionAsked != null && permissionAsked.isNotEmpty) {
      await _storage.write(key: 'notificationsPermissionRequested', value: permissionAsked);
    }
    if (keepDestination && pendingConversation != null && pendingConversation.isNotEmpty) {
      await _storage.write(key: NotificationRouter.pendingConversationKey, value: pendingConversation);
      if (pendingMessage != null && pendingMessage.isNotEmpty) {
        await _storage.write(key: NotificationRouter.pendingMessageKey, value: pendingMessage);
      }
    }
    _closing = false;
    notifyListeners();
  }

  Future<void> afterLogin() async {
    markActivity(true);
    await _loadSessionConfig();
    await _loadSubscription();
    inbox.start();
    await PushService.register(api);
    NotificationRouter.instance.setLoggedIn(true);
    _startWatchdog();
  }

  void markActivity([bool force = false]) {
    if (!isLoggedIn) return;
    final now = DateTime.now();
    if (!force && now.difference(_lastActivity).inSeconds < 10) return;
    _lastActivity = now;
    warningOpen = false;
    _storage.write(key: 'lastActivity', value: '${now.millisecondsSinceEpoch}');
    if (force || now.difference(_lastHeartbeat).inMinutes >= 5) {
      _lastHeartbeat = now;
      Future(() async {
        try {
          await api.post('/auth/activity', {});
        } catch (_) {}
      });
    }
    notifyListeners();
  }

  bool get isIdleExpired {
    if (!isLoggedIn) return false;
    return DateTime.now().difference(_lastActivity).inMinutes >= inactivityMinutes;
  }

  void _startWatchdog() {
    _checkTimer?.cancel();
    _checkTimer = Timer.periodic(const Duration(seconds: 15), (_) => _evaluate());
  }

  void _evaluate() {
    if (!isLoggedIn) return;
    final idle = DateTime.now().difference(_lastActivity);
    if (idle.inMinutes >= inactivityMinutes) {
      expire('INACTIVITY');
      return;
    }
    final remaining = Duration(minutes: inactivityMinutes) - idle;
    final shouldWarn = remaining.inMinutes <= warningMinutes;
    if (shouldWarn != warningOpen) {
      warningOpen = shouldWarn;
      notifyListeners();
    }
  }

  Future<void> continueSession() async {
    if (isIdleExpired) {
      await expire('INACTIVITY');
      return;
    }
    markActivity(true);
  }

  Future<void> _hydrate() async {
    try {
      final me = await api.get('/auth/me');
      user = asMap(me);
      await _storage.write(key: 'user', value: jsonEncode(user));
      await I18n.instance.load((user?['locale'] as String?) ?? 'es');
      notifyListeners();
      await afterLogin();
      if (isIdleExpired) {
        await expire('INACTIVITY');
      }
    } catch (error) {
      if (error is UnauthorizedException) {
        await expire(error.reason);
      }
    }
  }

  Future<void> _loadSessionConfig() async {
    try {
      final data = asMap(await api.get('/public/session-config', null, true));
      inactivityMinutes = asInt(data['inactivityTimeoutMinutes'], 30);
      warningMinutes = asInt(data['warningBeforeMinutes'], 2);
    } catch (_) {}
  }

  Future<void> _loadSubscription() async {
    if (!isStaff || isSuperAdmin) {
      subscription = null;
      return;
    }
    try {
      subscription = asMap(await api.get('/billing/subscription'));
      notifyListeners();
    } catch (_) {
      subscription = null;
    }
  }

  Future<void> _persist(Map<String, dynamic> data, {required bool resetActivity}) async {
    accessToken = asString(data['accessToken'], accessToken ?? '');
    if (accessToken != null && accessToken!.isEmpty) accessToken = null;
    final nextRefresh = asString(data['refreshToken']);
    if (nextRefresh.isNotEmpty) refreshToken = nextRefresh;
    final profile = asMap(data['user']);
    if (profile.isNotEmpty) user = profile;
    if (accessToken != null) await _storage.write(key: 'access', value: accessToken);
    if (refreshToken != null) await _storage.write(key: 'refresh', value: refreshToken);
    if (user != null) await _storage.write(key: 'user', value: jsonEncode(user));
    if (user?['locale'] is String) {
      await I18n.instance.load(user!['locale'] as String);
    }
    if (resetActivity) {
      _lastActivity = DateTime.now();
      await _storage.write(key: 'lastActivity', value: '${_lastActivity.millisecondsSinceEpoch}');
    }
    refreshRejected = false;
    notifyListeners();
  }

}
