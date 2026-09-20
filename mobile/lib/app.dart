import 'package:flutter/material.dart';
import 'core/auth.dart';
import 'core/l10n.dart';
import 'core/theme.dart';
import 'screens/login.dart';
import 'screens/shell.dart';

class VetoraApp extends StatefulWidget {
  const VetoraApp({super.key});

  @override
  State<VetoraApp> createState() => _VetoraAppState();
}

class _VetoraAppState extends State<VetoraApp> {
  final auth = AuthStore();
  bool ready = false;

  @override
  void initState() {
    super.initState();
    auth.restore().then((_) {
      if (mounted) setState(() => ready = true);
    });
  }

  ThemeMode _themeMode(String? theme) {
    switch (theme) {
      case 'light':
        return ThemeMode.light;
      case 'dark':
        return ThemeMode.dark;
      default:
        return ThemeMode.system;
    }
  }

  @override
  Widget build(BuildContext context) {
    if (!ready) {
      return MaterialApp(
        title: 'Vetora',
        debugShowCheckedModeBanner: false,
        home: Scaffold(
          backgroundColor: const Color(0xFF0F766E),
          body: Center(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                ClipRRect(
                  borderRadius: BorderRadius.circular(28),
                  child: Image.asset('assets/branding/logo.png', width: 112, height: 112),
                ),
                const SizedBox(height: 20),
                const Text(
                  'Vetora',
                  style: TextStyle(color: Colors.white, fontSize: 28, fontWeight: FontWeight.w700, letterSpacing: 0.4),
                ),
              ],
            ),
          ),
        ),
      );
    }
    return AnimatedBuilder(
      animation: Listenable.merge([auth, I18n.instance]),
      builder: (context, _) {
        return MaterialApp(
          title: 'Vetora',
          debugShowCheckedModeBanner: false,
          locale: Locale(I18n.instance.locale),
          theme: VetoraTheme.light,
          darkTheme: VetoraTheme.dark,
          themeMode: _themeMode(auth.user?['theme'] as String?),
          home: auth.isLoggedIn ? ShellScreen(auth: auth) : LoginScreen(auth: auth),
        );
      },
    );
  }
}
