import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/material.dart';
import 'app.dart';
import 'core/l10n.dart';
import 'core/push.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  ErrorWidget.builder = (details) {
    return Material(
      child: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Center(
            child: Text(
              _friendlyError(),
              textAlign: TextAlign.center,
            ),
          ),
        ),
      ),
    );
  };
  if (await PushService.ensureFirebase()) {
    FirebaseMessaging.onBackgroundMessage(firebaseMessagingBackgroundHandler);
  }
  runApp(const VetoraApp());
}

String _friendlyError() {
  final message = I18n.instance.t('requestError');
  if (message != 'requestError') return message;
  return I18n.instance.locale == 'en'
      ? 'Something went wrong. Please try again later.'
      : 'Algo salió mal. Inténtelo más tarde.';
}
