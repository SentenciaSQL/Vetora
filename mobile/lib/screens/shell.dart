import 'package:flutter/material.dart';
import '../core/auth.dart';
import '../core/l10n.dart';
import 'home.dart';
import 'pets.dart';
import 'appointments.dart';
import 'messages.dart';
import 'profile.dart';

class ShellScreen extends StatefulWidget {
  const ShellScreen({super.key, required this.auth});
  final AuthStore auth;

  @override
  State<ShellScreen> createState() => _ShellScreenState();
}

class _ShellScreenState extends State<ShellScreen> {
  int index = 0;

  @override
  Widget build(BuildContext context) {
    final i = I18n.instance;
    final pages = [
      HomeScreen(auth: widget.auth),
      PetsScreen(auth: widget.auth),
      AppointmentsScreen(auth: widget.auth),
      MessagesScreen(auth: widget.auth),
      ProfileScreen(auth: widget.auth),
    ];
    return Scaffold(
      body: pages[index],
      bottomNavigationBar: NavigationBar(
        selectedIndex: index,
        onDestinationSelected: (value) => setState(() => index = value),
        destinations: [
          NavigationDestination(icon: const Icon(Icons.home_outlined), label: i.t('home')),
          NavigationDestination(icon: const Icon(Icons.pets_outlined), label: i.t('pets')),
          NavigationDestination(icon: const Icon(Icons.calendar_today_outlined), label: i.t('appointments')),
          NavigationDestination(icon: const Icon(Icons.chat_bubble_outline), label: i.t('messages')),
          NavigationDestination(icon: const Icon(Icons.person_outline), label: i.t('profile')),
        ],
      ),
    );
  }
}
