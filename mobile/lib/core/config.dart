import 'package:flutter/foundation.dart';

class AppConfig {
  static const _apiOverride = String.fromEnvironment('API_URL', defaultValue: '');
  static const _webOverride = String.fromEnvironment('WEB_URL', defaultValue: '');

  static const productionApiHost = 'https://vetora-production-4eac.up.railway.app';
  static const defaultWebUrl = 'https://lunaveta.com';

  static String get apiUrl {
    final raw = _apiOverride.isNotEmpty
        ? _apiOverride
        : (kReleaseMode ? '$productionApiHost/api/v1' : 'http://localhost:8080/api/v1');
    if (raw.endsWith('/api/v1')) return raw;
    if (raw.endsWith('/api/v1/')) return raw.substring(0, raw.length - 1);
    return raw.endsWith('/') ? '${raw}api/v1' : '$raw/api/v1';
  }

  static String get webUrl {
    if (_webOverride.isNotEmpty) {
      return _webOverride.endsWith('/') ? _webOverride.substring(0, _webOverride.length - 1) : _webOverride;
    }
    return defaultWebUrl;
  }

  static String get billingWebUrl => '$webUrl/billing';
}
