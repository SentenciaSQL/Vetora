import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/material.dart';
import 'app.dart';
import 'core/push.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  if (await PushService.ensureFirebase()) {
    FirebaseMessaging.onBackgroundMessage(firebaseMessagingBackgroundHandler);
  }
  runApp(const VetoraApp());
}
