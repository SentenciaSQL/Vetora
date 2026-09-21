import 'package:flutter/material.dart';
import '../core/auth.dart';
import '../core/l10n.dart';
import '../core/notification_router.dart';
import 'appointments.dart';
import 'home.dart';
import 'messages.dart';
import 'pets.dart';
import 'profile.dart';

class ShellScreen extends StatefulWidget {
  const ShellScreen({super.key, required this.auth});
  final AuthStore auth;

  @override
  State<ShellScreen> createState() => _ShellScreenState();
}

class _ShellScreenState extends State<ShellScreen> with WidgetsBindingObserver {
  int index = 0;
  bool _dialogOpen = false;

  @override
  void initState() {
    super.initState();
    widget.auth.addListener(_onAuth);
    NotificationRouter.instance.addListener(_onPushRoute);
    WidgetsBinding.instance.addObserver(this);
    WidgetsBinding.instance.addPostFrameCallback((_) => _onPushRoute());
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    NotificationRouter.instance.removeListener(_onPushRoute);
    widget.auth.removeListener(_onAuth);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed && widget.auth.isLoggedIn) {
      widget.auth.inbox.refresh();
    }
  }

  int? _messagesTabIndex() {
    final auth = widget.auth;
    if (auth.clinicalLocked || !auth.messagingEnabled) return null;
    var tab = 1;
    if (!auth.clinicalLocked) tab += 2;
    return tab;
  }

  void _onPushRoute() {
    if (!mounted || !widget.auth.isLoggedIn) return;
    if (NotificationRouter.instance.pendingConversationId == null) return;
    final tab = _messagesTabIndex();
    if (tab == null) {
      NotificationRouter.instance.consume();
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(I18n.instance.t('conversationUnavailable'))));
      return;
    }
    NotificationRouter.navigatorKey.currentState?.popUntil((route) => route.isFirst);
    if (index != tab) {
      setState(() => index = tab);
    }
  }

  void _onAuth() {
    if (!mounted) return;
    if (!widget.auth.isLoggedIn) return;
    if (widget.auth.warningOpen && !_dialogOpen) {
      _dialogOpen = true;
      showDialog<void>(
        context: context,
        barrierDismissible: false,
        builder: (ctx) => AlertDialog(
          title: Text(I18n.instance.t('sessionWarningTitle')),
          content: Text(I18n.instance.t('sessionWarningBody')),
          actions: [
            TextButton(
              onPressed: () {
                Navigator.pop(ctx);
                widget.auth.logout();
              },
              child: Text(I18n.instance.t('logout')),
            ),
            FilledButton(
              onPressed: () {
                Navigator.pop(ctx);
                widget.auth.continueSession();
              },
              child: Text(I18n.instance.t('continueSession')),
            ),
          ],
        ),
      ).whenComplete(() {
        _dialogOpen = false;
      });
    }
    setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    final i = I18n.instance;
    final auth = widget.auth;
    final locked = auth.clinicalLocked;
    final showMessages = !locked && auth.messagingEnabled;
    final pages = <Widget>[
      HomeScreen(auth: auth),
      if (!locked) PetsScreen(auth: auth),
      if (!locked) AppointmentsScreen(auth: auth),
      if (showMessages) MessagesScreen(auth: auth),
      ProfileScreen(auth: auth),
    ];
    if (index >= pages.length) index = pages.length - 1;
    return Listener(
      onPointerDown: (_) => auth.markActivity(),
      child: Scaffold(
        body: Column(
          children: [
            if (auth.gracePeriod)
              Material(
                color: const Color(0xFFFFFBEB),
                child: SafeArea(
                  bottom: false,
                  child: Padding(
                    padding: const EdgeInsets.all(8),
                    child: Text(i.t('graceBanner'), textAlign: TextAlign.center, style: const TextStyle(color: Color(0xFF92400E))),
                  ),
                ),
              ),
            if (auth.suspended)
              Material(
                color: const Color(0xFFFFF1F2),
                child: SafeArea(
                  bottom: false,
                  child: Padding(
                    padding: const EdgeInsets.all(8),
                    child: Text(i.t('suspendedBanner'), textAlign: TextAlign.center, style: const TextStyle(color: Color(0xFF9F1239))),
                  ),
                ),
              ),
            Expanded(child: pages[index]),
          ],
        ),
        bottomNavigationBar: NavigationBar(
          selectedIndex: index,
          onDestinationSelected: (value) {
            auth.markActivity();
            setState(() => index = value);
          },
          destinations: [
            NavigationDestination(icon: const Icon(Icons.home_outlined), selectedIcon: const Icon(Icons.home), label: i.t('home')),
            if (!locked)
              NavigationDestination(icon: const Icon(Icons.pets_outlined), selectedIcon: const Icon(Icons.pets), label: i.t('pets')),
            if (!locked)
              NavigationDestination(
                icon: Icon(auth.isStaff ? Icons.calendar_month_outlined : Icons.calendar_today_outlined),
                label: auth.isStaff ? i.t('agenda') : i.t('appointments'),
              ),
            if (showMessages)
              NavigationDestination(
                icon: Badge(
                  isLabelVisible: auth.inbox.unreadMessages > 0,
                  label: Text(auth.inbox.badge(auth.inbox.unreadMessages)),
                  child: const Icon(Icons.chat_bubble_outline),
                ),
                label: i.t('messages'),
              ),
            NavigationDestination(
              icon: Badge(
                isLabelVisible: auth.inbox.unreadNotifications > 0,
                label: Text(auth.inbox.badge(auth.inbox.unreadNotifications)),
                child: const Icon(Icons.person_outline),
              ),
              label: i.t('profile'),
            ),
          ],
        ),
      ),
    );
  }
}
