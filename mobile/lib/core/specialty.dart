import 'l10n.dart';

/// Localized label for a stable specialty code. The stored code is never shown.
String specialtyLabel(dynamic code, {dynamic other}) {
  final value = (code ?? '').toString().trim();
  if (value.isEmpty) {
    return '';
  }
  final custom = (other ?? '').toString().trim();
  if (value == 'OTHER' && custom.isNotEmpty) {
    return custom;
  }
  final translated = I18n.instance.t('specialty_$value');
  if (translated != 'specialty_$value') {
    return translated;
  }
  if (RegExp(r'^[A-Z0-9_]+$').hasMatch(value)) {
    return custom;
  }
  return value;
}

String personInitials(dynamic name) {
  final parts = (name ?? '').toString().trim().split(RegExp(r'\s+')).where((part) => part.isNotEmpty).toList();
  if (parts.isEmpty) {
    return '?';
  }
  if (parts.length == 1) {
    return parts.first.substring(0, 1).toUpperCase();
  }
  return (parts.first.substring(0, 1) + parts[1].substring(0, 1)).toUpperCase();
}
