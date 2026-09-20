import 'dart:convert';
import 'package:http/http.dart' as http;
import 'auth.dart';

class ApiClient {
  ApiClient(this.auth, {String? baseUrl})
      : baseUrl = baseUrl ??
            const String.fromEnvironment('API_URL', defaultValue: 'http://localhost:8080/api/v1');

  final AuthStore auth;
  final String baseUrl;

  Future<dynamic> get(String path, [Map<String, String>? query]) async {
    return _send(() => http.get(Uri.parse('$baseUrl$path').replace(queryParameters: query), headers: _headers()));
  }

  Future<dynamic> post(String path, [Map<String, dynamic>? body]) async {
    return _send(() => http.post(Uri.parse('$baseUrl$path'), headers: _headers(), body: jsonEncode(body ?? {})));
  }

  Future<dynamic> put(String path, [Map<String, dynamic>? body]) async {
    return _send(() => http.put(Uri.parse('$baseUrl$path'), headers: _headers(), body: jsonEncode(body ?? {})));
  }

  Future<dynamic> patch(String path, Map<String, dynamic> body) async {
    return _send(() => http.patch(Uri.parse('$baseUrl$path'), headers: _headers(), body: jsonEncode(body)));
  }

  Future<List<int>> bytes(String path) async {
    final res = await _raw(() => http.get(Uri.parse('$baseUrl$path'), headers: _headers()));
    if (res.statusCode >= 400) {
      throw ApiException(res.statusCode, res.body);
    }
    return res.bodyBytes;
  }

  Map<String, String> _headers() {
    final headers = {'Content-Type': 'application/json', 'Accept': 'application/json'};
    if (auth.accessToken != null) {
      headers['Authorization'] = 'Bearer ${auth.accessToken}';
    }
    return headers;
  }

  Future<http.Response> _raw(Future<http.Response> Function() send) async {
    var res = await send();
    if (res.statusCode == 401 && await auth.refreshAccessToken()) {
      res = await send();
    }
    return res;
  }

  Future<dynamic> _send(Future<http.Response> Function() send) async {
    final res = await _raw(send);
    return _decode(res);
  }

  dynamic _decode(http.Response res) {
    if (res.statusCode == 401) {
      throw UnauthorizedException();
    }
    if (res.statusCode >= 400) {
      throw ApiException(res.statusCode, res.body);
    }
    if (res.body.isEmpty) return null;
    return jsonDecode(res.body);
  }
}

class ApiException implements Exception {
  ApiException(this.status, this.body);
  final int status;
  final String body;
  @override
  String toString() => body;
}

class UnauthorizedException implements Exception {}
