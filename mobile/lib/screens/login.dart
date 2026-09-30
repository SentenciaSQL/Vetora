import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';
import '../core/auth.dart';
import '../core/config.dart';
import '../core/format.dart';
import '../core/l10n.dart';
import '../core/widgets.dart';

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
  bool _biometricPrompted = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _maybePromptBiometric());
  }

  I18n get i => I18n.instance;

  Future<void> _openAccountDeletion() async {
    final uri = Uri.parse('${AppConfig.webUrl}/eliminar-cuenta');
    try {
      final opened = await launchUrl(uri, mode: LaunchMode.externalApplication);
      if (!opened && mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(i.t('deleteAccountLinkError'))));
      }
    } catch (_) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(i.t('deleteAccountLinkError'))));
    }
  }

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
    } catch (e) {
      if (mounted) setState(() => error = userMessage(e));
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  Future<void> _maybePromptBiometric() async {
    if (!mounted || _biometricPrompted || register || forgot || !widget.auth.biometricUnlockAvailable) return;
    _biometricPrompted = true;
    await _biometric();
  }

  Future<void> _biometric() async {
    setState(() {
      loading = true;
      error = null;
    });
    final key = await widget.auth.unlockWithBiometrics();
    if (!mounted) return;
    setState(() {
      loading = false;
      if (key != null) error = i.t(key);
    });
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
            Align(
              alignment: Alignment.centerRight,
              child: SegmentedButton<String>(
                segments: const [
                  ButtonSegment(value: 'es', label: Text('ES')),
                  ButtonSegment(value: 'en', label: Text('EN')),
                ],
                selected: {i.locale == 'en' ? 'en' : 'es'},
                onSelectionChanged: loading ? null : (value) => widget.auth.setLocale(value.first),
              ),
            ),
            const SizedBox(height: 16),
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
            AutofillGroup(
              child: Column(
                children: [
                  TextField(
                    controller: email,
                    keyboardType: TextInputType.emailAddress,
                    autofillHints: const [AutofillHints.email],
                    textInputAction: TextInputAction.next,
                    enableInteractiveSelection: true,
                    decoration: InputDecoration(labelText: i.t('email')),
                  ),
                  if (!forgot) ...[
                    const SizedBox(height: 12),
                    _SecretField(controller: password, label: i.t('password'), onSubmitted: loading ? null : submit),
                  ],
                  if (forgot) ...[
                    const SizedBox(height: 12),
                    TextField(controller: token, decoration: InputDecoration(labelText: i.t('resetToken')), onChanged: (_) => setState(() {})),
                    const SizedBox(height: 12),
                    _SecretField(controller: password, label: i.t('password'), onSubmitted: loading ? null : submit),
                  ],
                ],
              ),
            ),
            if (error != null)
              Padding(
                padding: const EdgeInsets.only(top: 12),
                child: Text(error!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
              ),
            if (sent) Padding(padding: const EdgeInsets.only(top: 12), child: Text(i.t('forgotSent'))),
            if (widget.auth.logoutReason == 'ACCOUNT_DELETED')
              Padding(padding: const EdgeInsets.only(top: 12), child: Text(i.t('deleteDone'))),
            if (widget.auth.logoutReason == 'UNAUTHORIZED' || widget.auth.logoutReason == 'INACTIVITY')
              Padding(
                padding: const EdgeInsets.only(top: 12),
                child: Text(i.t('sessionExpired'), style: TextStyle(color: Theme.of(context).colorScheme.error)),
              ),
            const SizedBox(height: 20),
            FilledButton(
              onPressed: loading ? null : submit,
              child: ButtonLabel(label: _submitLabel, loading: loading, color: Theme.of(context).colorScheme.onPrimary),
            ),
            if (widget.auth.biometricUnlockAvailable && !register && !forgot) ...[
              const SizedBox(height: 16),
              Row(
                children: [
                  const Expanded(child: Divider()),
                  Padding(padding: const EdgeInsets.symmetric(horizontal: 12), child: Text(i.t('or'))),
                  const Expanded(child: Divider()),
                ],
              ),
              const SizedBox(height: 16),
              OutlinedButton.icon(
                onPressed: loading ? null : _biometric,
                icon: const Icon(Icons.fingerprint),
                label: ButtonLabel(
                  label: i.t('biometricLogin'),
                  loading: loading,
                  color: Theme.of(context).colorScheme.primary,
                ),
              ),
            ],
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
            TextButton(
              onPressed: _openAccountDeletion,
              child: Text(i.t('deleteAccountLink')),
            ),
          ],
        ),
      ),
    );
  }
}

class _SecretField extends StatefulWidget {
  const _SecretField({required this.controller, required this.label, this.onSubmitted});

  final TextEditingController controller;
  final String label;
  final VoidCallback? onSubmitted;

  @override
  State<_SecretField> createState() => _SecretFieldState();
}

class _SecretFieldState extends State<_SecretField> {
  bool _obscure = true;

  @override
  void initState() {
    super.initState();
    widget.controller.addListener(_keepSelectionInRange);
  }

  @override
  void didUpdateWidget(covariant _SecretField oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.controller != widget.controller) {
      oldWidget.controller.removeListener(_keepSelectionInRange);
      widget.controller.addListener(_keepSelectionInRange);
    }
  }

  @override
  void dispose() {
    widget.controller.removeListener(_keepSelectionInRange);
    super.dispose();
  }

  /// Paste, autofill and fast replacement can leave a selection or composing
  /// range past the new text. That throws inside the field on the next frame.
  void _keepSelectionInRange() {
    final value = widget.controller.value;
    final text = value.text;
    final selection = value.selection;
    final composing = value.composing;
    var nextSelection = selection;
    var nextComposing = composing;
    var dirty = false;
    if (selection.isValid) {
      final start = selection.start.clamp(0, text.length).toInt();
      final end = selection.end.clamp(0, text.length).toInt();
      if (start != selection.start || end != selection.end) {
        nextSelection = TextSelection(baseOffset: start, extentOffset: end);
        dirty = true;
      }
    }
    if (composing.isValid && (composing.start < 0 || composing.end > text.length)) {
      nextComposing = TextRange.empty;
      dirty = true;
    }
    if (!dirty) return;
    widget.controller.value = value.copyWith(selection: nextSelection, composing: nextComposing);
  }

  @override
  Widget build(BuildContext context) {
    final i = I18n.instance;
    return TextField(
      controller: widget.controller,
      obscureText: _obscure,
      enableSuggestions: false,
      autocorrect: false,
      smartDashesType: SmartDashesType.disabled,
      smartQuotesType: SmartQuotesType.disabled,
      enableInteractiveSelection: true,
      keyboardType: TextInputType.visiblePassword,
      autofillHints: const [AutofillHints.password],
      textInputAction: TextInputAction.done,
      onSubmitted: (_) => widget.onSubmitted?.call(),
      decoration: InputDecoration(
        labelText: widget.label,
        suffixIcon: IconButton(
          tooltip: _obscure ? i.t('showPassword') : i.t('hidePassword'),
          onPressed: () => setState(() => _obscure = !_obscure),
          icon: Icon(_obscure ? Icons.visibility_outlined : Icons.visibility_off_outlined),
        ),
      ),
    );
  }
}
