import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:http/http.dart' as http;
import 'api.dart';
import 'l10n.dart';

class AuthStore extends ChangeNotifier {
  final _storage = const FlutterSecureStorage();
  String? accessToken;
  String? refreshToken;
  Map<String, dynamic>? user;

  bool get isLoggedIn => accessToken != null;

  late final ApiClient api = ApiClient(this);

  Future<void> restore() async {
    await I18n.instance.load('es');
    accessToken = await _storage.read(key: 'access');
    refreshToken = await _storage.read(key: 'refresh');
    final raw = await _storage.read(key: 'user');
    if (raw != null) {
      user = jsonDecode(raw) as Map<String, dynamic>;
      final locale = (user?['locale'] as String?) ?? 'es';
      await I18n.instance.load(locale);
    }
    notifyListeners();
  }

  Future<void> login(String email, String password) async {
    final data = await api.post('/auth/login', {'email': email, 'password': password});
    await _persist(data as Map<String, dynamic>);
  }

  Future<void> register(Map<String, String> payload) async {
    final data = await api.post('/auth/register', payload);
    await _persist(data as Map<String, dynamic>);
  }

  Future<void> forgot(String email) async {
    await api.post('/auth/forgot-password', {'email': email});
  }

  Future<void> resetPassword(String token, String password) async {
    await api.post('/auth/reset-password', {'token': token, 'password': password});
  }

  Future<bool> refreshAccessToken() async {
    if (refreshToken == null) return false;
    try {
      final res = await http.post(
        Uri.parse('${api.baseUrl}/auth/refresh'),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({'refreshToken': refreshToken}),
      );
      if (res.statusCode >= 400) return false;
      await _persist(jsonDecode(res.body) as Map<String, dynamic>);
      return true;
    } catch (_) {
      return false;
    }
  }

  Future<void> setTheme(String theme) async {
    if (isLoggedIn) {
      await api.patch('/auth/me', {'theme': theme});
      user = {...?user, 'theme': theme};
      await _storage.write(key: 'user', value: jsonEncode(user));
      notifyListeners();
    }
  }

  Future<void> logout() async {
    if (refreshToken != null) {
      try {
        await api.post('/auth/logout', {'refreshToken': refreshToken});
      } catch (_) {}
    }
    accessToken = null;
    refreshToken = null;
    user = null;
    await _storage.deleteAll();
    notifyListeners();
  }

  Future<void> setLocale(String locale) async {
    await I18n.instance.load(locale);
    if (isLoggedIn) {
      await api.patch('/auth/me', {'locale': locale});
    }
  }

  Future<void> _persist(Map<String, dynamic> data) async {
    accessToken = data['accessToken'] as String?;
    refreshToken = data['refreshToken'] as String?;
    user = data['user'] as Map<String, dynamic>?;
    await _storage.write(key: 'access', value: accessToken);
    await _storage.write(key: 'refresh', value: refreshToken);
    await _storage.write(key: 'user', value: jsonEncode(user));
    final locale = (user?['locale'] as String?) ?? 'es';
    await I18n.instance.load(locale);
    notifyListeners();
  }
}
