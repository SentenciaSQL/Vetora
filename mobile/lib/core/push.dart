/// Prepared hook for Firebase Cloud Messaging.
/// Register the device token with POST /api/v1/notifications/push-token
/// once google-services.json / GoogleService-Info.plist are configured.
class PushService {
  static Future<void> register(dynamic api, String? token) async {
    if (token == null || token.isEmpty) return;
    await api.post('/notifications/push-token', {'token': token, 'platform': 'mobile'});
  }
}
