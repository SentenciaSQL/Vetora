import 'dart:convert';
import 'package:flutter/services.dart';
import 'package:flutter/foundation.dart';

class I18n extends ChangeNotifier {
  I18n._();
  static final instance = I18n._();

  String locale = 'es';
  Map<String, dynamic> _map = {};

  String t(String key) => (_map[key] ?? key).toString();

  Future<void> load(String code) async {
    locale = code;
    final raw = await rootBundle.loadString('assets/i18n/$code.json');
    _map = jsonDecode(raw) as Map<String, dynamic>;
    notifyListeners();
  }
}
