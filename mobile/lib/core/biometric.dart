import 'package:flutter/services.dart';
import 'package:local_auth/local_auth.dart';
import 'l10n.dart';

enum BiometricOutcome { success, cancelled, unavailable, notEnrolled, locked, failed }

class BiometricAuth {
  static final LocalAuthentication _auth = LocalAuthentication();

  static Future<BiometricOutcome> availability() async {
    try {
      final supported = await _auth.isDeviceSupported();
      if (!supported) return BiometricOutcome.unavailable;
      final enrolled = await _auth.canCheckBiometrics;
      final types = await _auth.getAvailableBiometrics();
      if (!enrolled && types.isEmpty) return BiometricOutcome.notEnrolled;
      return BiometricOutcome.success;
    } catch (_) {
      return BiometricOutcome.unavailable;
    }
  }

  static Future<BiometricOutcome> authenticate() async {
    final ready = await availability();
    if (ready != BiometricOutcome.success) return ready;
    try {
      final accepted = await _auth.authenticate(
        localizedReason: I18n.instance.t('biometricReason'),
        options: const AuthenticationOptions(
          biometricOnly: true,
          stickyAuth: true,
          sensitiveTransaction: true,
        ),
      );
      return accepted ? BiometricOutcome.success : BiometricOutcome.cancelled;
    } on PlatformException catch (error) {
      return _fromCode(error.code);
    } catch (_) {
      return BiometricOutcome.failed;
    }
  }

  static BiometricOutcome _fromCode(String code) {
    switch (code) {
      case 'NotAvailable':
      case 'biometricOnlyNotSupported':
      case 'no_fragment_activity':
        return BiometricOutcome.unavailable;
      case 'NotEnrolled':
      case 'PasscodeNotSet':
        return BiometricOutcome.notEnrolled;
      case 'LockedOut':
      case 'PermanentlyLockedOut':
        return BiometricOutcome.locked;
      default:
        return BiometricOutcome.failed;
    }
  }
}

String? biometricMessageKey(BiometricOutcome outcome) {
  switch (outcome) {
    case BiometricOutcome.success:
    case BiometricOutcome.cancelled:
      return null;
    case BiometricOutcome.unavailable:
      return 'biometricUnavailable';
    case BiometricOutcome.notEnrolled:
      return 'biometricNotEnrolled';
    case BiometricOutcome.locked:
      return 'biometricLocked';
    case BiometricOutcome.failed:
      return 'biometricLoginFailed';
  }
}
