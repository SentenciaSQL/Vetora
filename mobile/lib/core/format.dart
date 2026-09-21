import 'dart:convert';
import 'api.dart';
import 'l10n.dart';

List<dynamic> asList(dynamic value) {
  if (value is List) return value;
  if (value is Map && value['content'] is List) return value['content'] as List;
  return const [];
}

Map<String, dynamic> asMap(dynamic value) {
  if (value is Map<String, dynamic>) return value;
  if (value is Map) return Map<String, dynamic>.from(value);
  return <String, dynamic>{};
}

int asInt(dynamic value, [int fallback = 0]) {
  if (value is int) return value;
  if (value is num) return value.toInt();
  return int.tryParse('$value') ?? fallback;
}

String asString(dynamic value, [String fallback = '']) {
  if (value == null) return fallback;
  final text = '$value';
  return text == 'null' ? fallback : text;
}

String formatDate(dynamic value) {
  if (value == null) return '';
  final raw = '$value';
  if (raw.isEmpty || raw == 'null') return '';
  final parsed = DateTime.tryParse(raw);
  if (parsed == null) {
    return raw.length >= 16 ? raw.substring(0, 16).replaceFirst('T', ' ') : raw;
  }
  final local = parsed.toLocal();
  String two(int n) => n.toString().padLeft(2, '0');
  return '${two(local.day)}/${two(local.month)}/${local.year} ${two(local.hour)}:${two(local.minute)}';
}

String formatDay(dynamic value) {
  if (value == null) return '';
  final parsed = DateTime.tryParse('$value');
  if (parsed == null) return '$value';
  final local = parsed.toLocal();
  String two(int n) => n.toString().padLeft(2, '0');
  return '${two(local.day)}/${two(local.month)}/${local.year}';
}

String userMessage(Object error) {
  final i = I18n.instance;
  if (error is UnauthorizedException) return i.t('sessionExpired');
  if (error is ApiException) {
    if (error.status == 403) return error.message.isNotEmpty ? error.message : i.t('forbidden');
    if (error.message.isNotEmpty) return error.message;
    return i.t('requestError');
  }
  return i.t('requestError');
}

String parseApiMessage(int status, String body) {
  try {
    final decoded = jsonDecode(body);
    if (decoded is Map && decoded['message'] is String) {
      final message = (decoded['message'] as String).trim();
      if (_safe(message)) return message;
    }
  } catch (_) {}
  if (status == 403) return I18n.instance.t('forbidden');
  if (status == 401) return I18n.instance.t('invalid');
  if (status == 404) return I18n.instance.t('notFound');
  if (status == 409) return I18n.instance.t('conflict');
  return I18n.instance.t('requestError');
}

String parseApiCode(String body) {
  try {
    final decoded = jsonDecode(body);
    if (decoded is Map && decoded['code'] is String) {
      return decoded['code'] as String;
    }
  } catch (_) {}
  return '';
}

bool _safe(String message) {
  final lower = message.toLowerCase();
  return !lower.contains('bearer ')
      && !lower.contains('api key')
      && !lower.contains('jwt')
      && !lower.contains('stack')
      && !lower.contains('exception')
      && !lower.contains('sql');
}
