import 'dart:convert';
import 'dart:math';

import 'package:app_badge_plus/app_badge_plus.dart';
import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:package_info_plus/package_info_plus.dart';

import 'api.dart';
import 'auth.dart';
import 'notification_router.dart';

const _messagesChannel = AndroidNotificationChannel(
  'lunaveta_messages',
  'Mensajes',
  description: 'Mensajes nuevos de LunaVeta',
  importance: Importance.high,
  playSound: true,
);

const _silentChannel = AndroidNotificationChannel(
  'lunaveta_messages_silent',
  'Mensajes sin sonido',
  description: 'Mensajes nuevos de LunaVeta sin sonido',
  importance: Importance.high,
  playSound: false,
);

final FlutterLocalNotificationsPlugin _localNotifications = FlutterLocalNotificationsPlugin();
bool _localReady = false;
bool _listenersReady = false;
final Set<String> _seenMessages = {};

@pragma('vm:entry-point')
Future<void> firebaseMessagingBackgroundHandler(RemoteMessage message) async {
  WidgetsFlutterBinding.ensureInitialized();
  try {
    if (Firebase.apps.isEmpty) {
      await Firebase.initializeApp();
    }
  } catch (_) {}
}

class PushService {
  static final _storage = const FlutterSecureStorage();
  static String? _token;
  static bool firebaseReady = false;
  static bool soundEnabled = true;
  static AuthStore? _auth;

  static Future<bool> ensureFirebase() async {
    if (firebaseReady) return true;
    try {
      if (Firebase.apps.isEmpty) {
        await Firebase.initializeApp();
      }
      firebaseReady = true;
      return true;
    } catch (_) {
      firebaseReady = false;
      return false;
    }
  }

  static Future<void> start(AuthStore auth) async {
    _auth = auth;
    await _initLocalNotifications();
    final ready = await ensureFirebase();
    if (ready) {
      await _listen();
      if (auth.isLoggedIn) {
        await register(auth.api);
      }
    }
    NotificationRouter.instance.markUiReady(isLoggedIn: auth.isLoggedIn);
  }

  static Future<void> register(ApiClient api) async {
    if (!firebaseReady || _auth == null || !_auth!.isLoggedIn) return;
    try {
      await _ensurePermission();
      final messaging = FirebaseMessaging.instance;
      await messaging.setForegroundNotificationPresentationOptions(alert: false, badge: true, sound: false);
      final token = await messaging.getToken();
      if (token == null || token.isEmpty) return;
      _token = token;
      final installationId = await _installationId();
      String appVersion = '1.0.0';
      try {
        appVersion = (await PackageInfo.fromPlatform()).version;
      } catch (_) {}
      await api.post('/devices', {
        'token': token,
        'platform': _platform(),
        'installationId': installationId,
        'appVersion': appVersion,
        'deviceName': _deviceName(),
      }, true);
      await _loadPreferences(api);
    } catch (_) {}
  }

  static Future<void> unregister(ApiClient api) async {
    try {
      if (_auth != null && _auth!.isLoggedIn) {
        final installationId = await _storage.read(key: 'installationId');
        await api.delete('/devices/current', {
          if (_token != null && _token!.isNotEmpty) 'token': _token,
          if (installationId != null && installationId.isNotEmpty) 'installationId': installationId,
        }, true);
      }
    } catch (_) {}
    _token = null;
    _seenMessages.clear();
  }

  static Future<void> syncBadge(int count) async {
    try {
      final badge = count < 0 ? 0 : (count > 99 ? 99 : count);
      if (await AppBadgePlus.isSupported()) {
        await AppBadgePlus.updateBadge(badge);
      }
    } catch (_) {}
  }

  static Future<void> _listen() async {
    if (_listenersReady) return;
    _listenersReady = true;
    FirebaseMessaging.onMessage.listen(_onForeground);
    FirebaseMessaging.onMessageOpenedApp.listen(_onOpened);
    FirebaseMessaging.instance.onTokenRefresh.listen((token) async {
      _token = token;
      final auth = _auth;
      if (auth != null && auth.isLoggedIn) {
        await register(auth.api);
      }
    });
    final initial = await FirebaseMessaging.instance.getInitialMessage();
    if (initial != null) {
      await _onOpened(initial);
    }
    final launch = await _localNotifications.getNotificationAppLaunchDetails();
    if (launch?.didNotificationLaunchApp == true) {
      await NotificationRouter.instance.acceptPayload(launch?.notificationResponse?.payload);
    }
  }

  static Future<void> _onForeground(RemoteMessage message) async {
    final data = message.data;
    if ('${data['type'] ?? ''}' != 'CHAT_MESSAGE') return;
    final messageId = '${data['messageId'] ?? ''}';
    if (messageId.isNotEmpty) {
      if (_seenMessages.length > 300) _seenMessages.clear();
      if (!_seenMessages.add(messageId)) return;
    }
    final conversationId = int.tryParse('${data['conversationId'] ?? ''}');
    final auth = _auth;
    if (auth != null) {
      await auth.inbox.refresh();
    }
    if (conversationId != null && NotificationRouter.instance.visibleConversationId == conversationId) {
      final refresh = NotificationRouter.instance.onVisibleChat;
      if (refresh != null) await refresh(conversationId);
      return;
    }
    await _showLocal(message);
  }

  static Future<void> _onOpened(RemoteMessage message) async {
    await NotificationRouter.instance.acceptData(message.data);
  }

  static Future<void> _showLocal(RemoteMessage message) async {
    if (!_localReady) return;
    final notification = message.notification;
    final title = notification?.title ?? 'LunaVeta';
    final body = notification?.body ?? 'Tienes un nuevo mensaje en LunaVeta';
    final conversationId = '${message.data['conversationId'] ?? ''}';
    final messageId = '${message.data['messageId'] ?? ''}';
    final payload = jsonEncode({
      'type': 'CHAT_MESSAGE',
      'conversationId': conversationId,
      'messageId': messageId,
      'route': '/messages',
    });
    final channel = soundEnabled ? _messagesChannel : _silentChannel;
    final id = messageId.isEmpty ? DateTime.now().millisecondsSinceEpoch.remainder(100000) : messageId.hashCode & 0x7fffffff;
    await _localNotifications.show(
      id,
      title,
      body,
      NotificationDetails(
        android: AndroidNotificationDetails(
          channel.id,
          channel.name,
          channelDescription: channel.description,
          importance: Importance.high,
          priority: Priority.high,
          icon: 'ic_stat_lunaveta',
          playSound: soundEnabled,
          enableVibration: true,
        ),
        iOS: DarwinNotificationDetails(
          presentAlert: true,
          presentBadge: true,
          presentSound: soundEnabled,
        ),
      ),
      payload: payload,
    );
  }

  static Future<void> _initLocalNotifications() async {
    if (_localReady) return;
    const android = AndroidInitializationSettings('@drawable/ic_stat_lunaveta');
    const ios = DarwinInitializationSettings(
      requestAlertPermission: false,
      requestBadgePermission: false,
      requestSoundPermission: false,
    );
    await _localNotifications.initialize(
      const InitializationSettings(android: android, iOS: ios),
      onDidReceiveNotificationResponse: (response) {
        NotificationRouter.instance.acceptPayload(response.payload);
      },
    );
    final plugin = _localNotifications.resolvePlatformSpecificImplementation<AndroidFlutterLocalNotificationsPlugin>();
    await plugin?.createNotificationChannel(_messagesChannel);
    await plugin?.createNotificationChannel(_silentChannel);
    _localReady = true;
  }

  static Future<void> _ensurePermission() async {
    final settings = await FirebaseMessaging.instance.getNotificationSettings();
    final status = settings.authorizationStatus;
    if (status == AuthorizationStatus.authorized || status == AuthorizationStatus.provisional) return;
    if (status == AuthorizationStatus.denied) return;
    final asked = await _storage.read(key: 'notificationsPermissionRequested');
    if (asked == '1') return;
    await _storage.write(key: 'notificationsPermissionRequested', value: '1');
    await FirebaseMessaging.instance.requestPermission(alert: true, badge: true, sound: true);
  }

  static Future<void> _loadPreferences(ApiClient api) async {
    try {
      final data = await api.get('/notifications/preferences', null, true);
      if (data is Map) {
        soundEnabled = data['messageSoundEnabled'] != false;
      }
    } catch (_) {}
  }

  static Future<String> _installationId() async {
    final existing = await _storage.read(key: 'installationId');
    if (existing != null && existing.isNotEmpty) return existing;
    final created = _uuid();
    await _storage.write(key: 'installationId', value: created);
    return created;
  }

  static String _platform() {
    if (defaultTargetPlatform == TargetPlatform.iOS) return 'IOS';
    return 'ANDROID';
  }

  static String _deviceName() {
    final name = defaultTargetPlatform.name;
    return name.length > 80 ? name.substring(0, 80) : name;
  }

  static String _uuid() {
    final random = Random.secure();
    final bytes = List<int>.generate(16, (_) => random.nextInt(256));
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    String hex(int value) => value.toRadixString(16).padLeft(2, '0');
    final text = bytes.map(hex).join();
    return '${text.substring(0, 8)}-${text.substring(8, 12)}-${text.substring(12, 16)}-${text.substring(16, 20)}-${text.substring(20)}';
  }
}
