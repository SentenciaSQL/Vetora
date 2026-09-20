import 'package:flutter/material.dart';
import '../core/auth.dart';
import '../core/l10n.dart';

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key, required this.auth});
  final AuthStore auth;

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final email = TextEditingController();
  final password = TextEditingController();
  final token = TextEditingController();
  final firstName = TextEditingController();
  final lastName = TextEditingController();
  bool loading = false;
  String? error;
  bool register = false;
  bool forgot = false;
  bool sent = false;

  I18n get i => I18n.instance;

  @override
  void dispose() {
    email.dispose();
    password.dispose();
    token.dispose();
    firstName.dispose();
    lastName.dispose();
    super.dispose();
  }

  Future<void> submit() async {
    setState(() {
      loading = true;
      error = null;
    });
    try {
      if (forgot) {
        if (token.text.trim().isNotEmpty) {
          await widget.auth.resetPassword(token.text.trim(), password.text);
          setState(() {
            forgot = false;
            sent = false;
            token.clear();
            password.clear();
          });
        } else {
          await widget.auth.forgot(email.text.trim());
          setState(() => sent = true);
        }
      } else if (register) {
        await widget.auth.register({
          'firstName': firstName.text,
          'lastName': lastName.text,
          'email': email.text,
          'password': password.text,
          'locale': i.locale,
        });
      } else {
        await widget.auth.login(email.text.trim(), password.text);
      }
    } catch (_) {
      setState(() => error = i.t('invalid'));
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  String get _submitLabel {
    if (forgot && token.text.trim().isNotEmpty) return i.t('resetSubmit');
    if (forgot) return i.t('forgot');
    if (register) return i.t('register');
    return i.t('login');
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(24),
          children: [
            const SizedBox(height: 32),
            ClipRRect(
              borderRadius: BorderRadius.circular(18),
              child: Image.asset('assets/branding/logo.png', width: 72, height: 72),
            ),
            const SizedBox(height: 16),
            Text(i.t('appName'), style: Theme.of(context).textTheme.headlineMedium?.copyWith(fontWeight: FontWeight.w700)),
            Text(i.t('tagline'), style: Theme.of(context).textTheme.bodyMedium),
            const SizedBox(height: 32),
            if (register && !forgot) ...[
              TextField(controller: firstName, decoration: InputDecoration(labelText: i.t('firstName'))),
              const SizedBox(height: 12),
              TextField(controller: lastName, decoration: InputDecoration(labelText: i.t('lastName'))),
              const SizedBox(height: 12),
            ],
            TextField(controller: email, keyboardType: TextInputType.emailAddress, decoration: InputDecoration(labelText: i.t('email'))),
            if (!forgot) ...[
              const SizedBox(height: 12),
              TextField(controller: password, obscureText: true, decoration: InputDecoration(labelText: i.t('password'))),
            ],
            if (forgot) ...[
              const SizedBox(height: 12),
              TextField(controller: token, decoration: InputDecoration(labelText: i.t('resetToken')), onChanged: (_) => setState(() {})),
              const SizedBox(height: 12),
              TextField(controller: password, obscureText: true, decoration: InputDecoration(labelText: i.t('password'))),
            ],
            if (error != null) Padding(padding: const EdgeInsets.only(top: 12), child: Text(error!, style: const TextStyle(color: Colors.red))),
            if (sent) Padding(padding: const EdgeInsets.only(top: 12), child: Text(i.t('forgotSent'))),
            const SizedBox(height: 20),
            FilledButton(
              onPressed: loading ? null : submit,
              child: Text(_submitLabel),
            ),
            TextButton(
              onPressed: () => setState(() {
                forgot = !forgot;
                register = false;
                sent = false;
                error = null;
              }),
              child: Text(forgot ? i.t('login') : i.t('forgot')),
            ),
            if (!forgot)
              TextButton(
                onPressed: () => setState(() => register = !register),
                child: Text(register ? i.t('login') : i.t('register')),
              ),
          ],
        ),
      ),
    );
  }
}
