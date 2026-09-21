import 'api.dart';

class PushService {
  static String? _token;

  static Future<void> register(ApiClient api) async {
    try {
      if (_token == null || _token!.isEmpty) return;
      await api.post('/notifications/push-token', {'token': _token, 'platform': 'mobile'}, true);
    } catch (_) {}
  }

  static Future<void> unregister(ApiClient api) async {
    try {
      await api.delete('/notifications/push-token', {
        if (_token != null && _token!.isNotEmpty) 'token': _token,
        'platform': 'mobile',
      }, true);
    } catch (_) {}
    _token = null;
  }
}
