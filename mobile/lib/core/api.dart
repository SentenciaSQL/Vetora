import 'dart:convert';
import 'package:http/http.dart' as http;
import 'auth.dart';
import 'config.dart';
import 'format.dart';

class ApiClient {
  ApiClient(this.auth, {String? baseUrl}) : baseUrl = baseUrl ?? AppConfig.apiUrl;

  final AuthStore auth;
  final String baseUrl;

  Future<dynamic> get(String path, [Map<String, String>? query, bool background = false]) {
    return _send(
      () => http.get(Uri.parse('$baseUrl$path').replace(queryParameters: query), headers: _headers()),
      background: background,
    );
  }

  Future<dynamic> post(String path, [Map<String, dynamic>? body, bool background = false]) {
    return _send(
      () => http.post(Uri.parse('$baseUrl$path'), headers: _headers(), body: jsonEncode(body ?? {})),
      background: background,
    );
  }

  Future<dynamic> put(String path, [Map<String, dynamic>? body]) {
    return _send(() => http.put(Uri.parse('$baseUrl$path'), headers: _headers(), body: jsonEncode(body ?? {})));
  }

  Future<dynamic> patch(String path, Map<String, dynamic> body) {
    return _send(() => http.patch(Uri.parse('$baseUrl$path'), headers: _headers(), body: jsonEncode(body)));
  }

  Future<dynamic> delete(String path, [Map<String, dynamic>? body, bool background = false]) {
    return _send(
      () => http.delete(
        Uri.parse('$baseUrl$path'),
        headers: _headers(),
        body: body == null ? null : jsonEncode(body),
      ),
      background: background,
    );
  }

  Future<dynamic> upload(String path, List<int> bytes, String filename, {String field = 'file'}) async {
    final request = http.MultipartRequest('POST', Uri.parse('$baseUrl$path'));
    request.headers.addAll(_authHeaders());
    request.files.add(http.MultipartFile.fromBytes(field, bytes, filename: filename));
    var streamed = await request.send();
    var res = await http.Response.fromStream(streamed);
    if (res.statusCode == 401 && parseApiCode(res.body) != 'SESSION_INACTIVE' && await auth.refreshAccessToken()) {
      final retry = http.MultipartRequest('POST', Uri.parse('$baseUrl$path'));
      retry.headers.addAll(_authHeaders());
      retry.files.add(http.MultipartFile.fromBytes(field, bytes, filename: filename));
      res = await http.Response.fromStream(await retry.send());
    }
    return _decode(res, background: false);
  }

  Future<List<int>> bytes(String path) async {
    final res = await _raw(() => http.get(Uri.parse('$baseUrl$path'), headers: _headers()), background: false);
    if (res.statusCode >= 400) {
      throw ApiException(res.statusCode, parseApiCode(res.body), parseApiMessage(res.statusCode, res.body));
    }
    return res.bodyBytes;
  }

  Map<String, String> _headers() {
    return {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
      ..._authHeaders(),
    };
  }

  Map<String, String> _authHeaders() {
    final headers = <String, String>{
      'Accept': 'application/json',
      'X-Lunaveta-Client': 'mobile',
    };
    if (auth.accessToken != null) {
      headers['Authorization'] = 'Bearer ${auth.accessToken}';
    }
    return headers;
  }

  bool _isAuthPath(String url) {
    return url.contains('/auth/login')
        || url.contains('/auth/register')
        || url.contains('/auth/refresh')
        || url.contains('/auth/logout')
        || url.contains('/auth/forgot-password')
        || url.contains('/auth/reset-password')
        || url.contains('/public/');
  }

  Future<http.Response> _raw(Future<http.Response> Function() send, {required bool background}) async {
    var res = await send();
    if (res.statusCode != 401) return res;
    final code = parseApiCode(res.body);
    if (code == 'SESSION_INACTIVE') {
      await auth.expire('INACTIVITY');
      return res;
    }
    if (_isAuthPath(res.request?.url.toString() ?? '')) return res;
    if (await auth.refreshAccessToken()) {
      res = await send();
    }
    return res;
  }

  Future<dynamic> _send(Future<http.Response> Function() send, {bool background = false}) async {
    final res = await _raw(send, background: background);
    return _decode(res, background: background);
  }

  dynamic _decode(http.Response res, {required bool background}) {
    if (res.statusCode == 401) {
      final code = parseApiCode(res.body);
      if (code == 'SESSION_INACTIVE') {
        throw UnauthorizedException('INACTIVITY');
      }
      if (_isAuthPath(res.request?.url.toString() ?? '')) {
        throw ApiException(res.statusCode, code.isEmpty ? 'UNAUTHORIZED' : code, parseApiMessage(res.statusCode, res.body));
      }
      if (auth.isLoggedIn && (auth.refreshRejected || !background)) {
        auth.expire('UNAUTHORIZED');
      }
      throw UnauthorizedException('UNAUTHORIZED');
    }
    if (res.statusCode >= 400) {
      throw ApiException(res.statusCode, parseApiCode(res.body), parseApiMessage(res.statusCode, res.body));
    }
    if (res.body.isEmpty) return null;
    return jsonDecode(res.body);
  }
}

class ApiException implements Exception {
  ApiException(this.status, this.code, this.message);
  final int status;
  final String code;
  final String message;
  @override
  String toString() => message;
}

class UnauthorizedException implements Exception {
  UnauthorizedException([this.reason = 'UNAUTHORIZED']);
  final String reason;
}
