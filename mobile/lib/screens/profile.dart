import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';
import '../core/auth.dart';
import '../core/config.dart';
import '../core/format.dart';
import '../core/l10n.dart';
import 'billing.dart';
import 'clinics.dart';
import 'notifications.dart';
import 'owners.dart';

class ProfileScreen extends StatefulWidget {
  const ProfileScreen({super.key, required this.auth});
  final AuthStore auth;
  @override
  State<ProfileScreen> createState() => _ProfileScreenState();
}

class _ProfileScreenState extends State<ProfileScreen> {
  final firstName = TextEditingController();
  final lastName = TextEditingController();
  final phone = TextEditingController();
  final currentPassword = TextEditingController();
  final newPassword = TextEditingController();
  bool saving = false;
  List pets = [];
  bool _notifyEmail = true;
  bool _notifyPush = true;
  bool _savingPrefs = false;

  @override
  void initState() {
    super.initState();
    firstName.text = asString(widget.auth.user?['firstName']);
    lastName.text = asString(widget.auth.user?['lastName']);
    phone.text = asString(widget.auth.user?['phone']);
    if (widget.auth.isOwner) {
      widget.auth.api.get('/pets/mine').then((value) {
        if (mounted) setState(() => pets = asList(value));
      }).catchError((_) {});
    }
    if (widget.auth.isStaff) {
      widget.auth.api.get('/settings').then((value) {
        final settings = asMap(value);
        if (mounted) {
          setState(() {
            _notifyEmail = settings['notifyEmail'] != false;
            _notifyPush = settings['notifyPush'] != false;
          });
        }
      }).catchError((_) {});
    }
  }

  @override
  void dispose() {
    firstName.dispose();
    lastName.dispose();
    phone.dispose();
    currentPassword.dispose();
    newPassword.dispose();
    super.dispose();
  }

  Future<void> _savePrefs({bool? email, bool? push}) async {
    setState(() {
      if (email != null) _notifyEmail = email;
      if (push != null) _notifyPush = push;
      _savingPrefs = true;
    });
    try {
      await widget.auth.api.put('/settings', {
        'notifyEmail': _notifyEmail,
        'notifyPush': _notifyPush,
      });
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(userMessage(e))));
    } finally {
      if (mounted) setState(() => _savingPrefs = false);
    }
  }

  Future<void> _saveProfile() async {
    setState(() => saving = true);
    try {
      await widget.auth.updateProfile({
        'firstName': firstName.text.trim(),
        'lastName': lastName.text.trim(),
        'phone': phone.text.trim(),
      });
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(I18n.instance.t('saved'))));
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(userMessage(e))));
    } finally {
      if (mounted) setState(() => saving = false);
    }
  }

  Future<void> _savePassword() async {
    if (newPassword.text.length < 8) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(I18n.instance.t('passwordRule'))));
      return;
    }
    setState(() => saving = true);
    try {
      await widget.auth.changePassword(currentPassword.text, newPassword.text);
      currentPassword.clear();
      newPassword.clear();
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(I18n.instance.t('saved'))));
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(userMessage(e))));
    } finally {
      if (mounted) setState(() => saving = false);
    }
  }

  bool get _canManageHours =>
      widget.auth.hasAnyRole(const ['TENANT_OWNER', 'TENANT_ADMIN'])
      || widget.auth.hasPermission('BRANCH_MANAGE')
      || widget.auth.hasPermission('SETTINGS_UPDATE');

  Future<void> _openWebHours() async {
    final uri = Uri.parse('${AppConfig.webUrl}/settings');
    final ok = await launchUrl(uri, mode: LaunchMode.externalApplication);
    if (!ok && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(I18n.instance.t('hoursManageWeb'))));
    }
  }

  Future<void> _deleteAccount() async {
    final i = I18n.instance;
    final password = TextEditingController();
    final confirmation = TextEditingController();
    String? error;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) {
        return StatefulBuilder(builder: (ctx, setDialog) {
          return AlertDialog(
            title: Text(i.t('deleteAccount')),
            content: SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(i.t('deleteWarning')),
                  const SizedBox(height: 12),
                  TextField(controller: password, obscureText: true, decoration: InputDecoration(labelText: i.t('currentPassword'))),
                  const SizedBox(height: 8),
                  TextField(controller: confirmation, decoration: InputDecoration(labelText: i.t('deleteType'))),
                  if (error != null) Padding(padding: const EdgeInsets.only(top: 8), child: Text(error!, style: const TextStyle(color: Colors.red))),
                ],
              ),
            ),
            actions: [
              TextButton(onPressed: () => Navigator.pop(ctx, false), child: Text(i.t('cancel'))),
              FilledButton(
                onPressed: saving ? null : () async {
                  setDialog(() => error = null);
                  try {
                    await widget.auth.api.post('/account/deletion', {
                      'currentPassword': password.text,
                      'confirmation': confirmation.text.trim(),
                    });
                    if (ctx.mounted) Navigator.pop(ctx, true);
                  } catch (e) {
                    setDialog(() => error = userMessage(e));
                  }
                },
                child: Text(i.t('deleteConfirm')),
              ),
            ],
          );
        });
      },
    );
    password.dispose();
    confirmation.dispose();
    if (confirmed == true) {
      await widget.auth.expire('ACCOUNT_DELETED');
    }
  }

  @override
  Widget build(BuildContext context) {
    final i = I18n.instance;
    final auth = widget.auth;
    final theme = (auth.user?['theme'] as String?) ?? 'system';
    return SafeArea(
      child: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          Text(i.t('profile'), style: Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.w700)),
          ListTile(title: Text(asString(auth.user?['fullName'])), subtitle: Text('${auth.user?['email'] ?? ''}\n${auth.user?['role'] ?? auth.roles.join(', ')}'), isThreeLine: true),
          ListTile(
            title: Text(i.t('notifications')),
            trailing: CountOrChevron(count: auth.inbox.unreadNotifications),
            onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => NotificationsScreen(auth: auth))),
          ),
          if (auth.isOwner)
            ListTile(
              title: Text(i.t('clinics')),
              onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => ClinicsScreen(auth: auth, pets: pets))),
            ),
          if (auth.isStaff && !auth.clinicalLocked)
            ListTile(
              title: Text(i.t('owners')),
              onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => OwnersScreen(auth: auth))),
            ),
          if (auth.canBill)
            ListTile(
              title: Text(i.t('billing')),
              subtitle: Text(i.t('billingReadOnly')),
              onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => BillingScreen(auth: auth))),
            ),
          const Divider(),
          TextField(controller: firstName, decoration: InputDecoration(labelText: i.t('firstName'))),
          const SizedBox(height: 8),
          TextField(controller: lastName, decoration: InputDecoration(labelText: i.t('lastName'))),
          const SizedBox(height: 8),
          TextField(controller: phone, decoration: InputDecoration(labelText: i.t('phone'))),
          const SizedBox(height: 8),
          FilledButton(onPressed: saving ? null : _saveProfile, child: Text(i.t('save'))),
          const Divider(),
          Text(i.t('changePassword'), style: Theme.of(context).textTheme.titleMedium),
          TextField(controller: currentPassword, obscureText: true, decoration: InputDecoration(labelText: i.t('currentPassword'))),
          const SizedBox(height: 8),
          TextField(controller: newPassword, obscureText: true, decoration: InputDecoration(labelText: i.t('newPassword'))),
          const SizedBox(height: 8),
          OutlinedButton(onPressed: saving ? null : _savePassword, child: Text(i.t('changePassword'))),
          const Divider(),
          ListTile(
            title: Text(i.t('language')),
            trailing: DropdownButton<String>(
              value: i.locale,
              items: const [DropdownMenuItem(value: 'es', child: Text('ES')), DropdownMenuItem(value: 'en', child: Text('EN'))],
              onChanged: (v) { if (v != null) auth.setLocale(v); },
            ),
          ),
          ListTile(
            title: Text(i.t('theme')),
            trailing: DropdownButton<String>(
              value: ['light', 'dark', 'system'].contains(theme) ? theme : 'system',
              items: [
                DropdownMenuItem(value: 'system', child: Text(i.t('themeSystem'))),
                DropdownMenuItem(value: 'light', child: Text(i.t('themeLight'))),
                DropdownMenuItem(value: 'dark', child: Text(i.t('themeDark'))),
              ],
              onChanged: (v) { if (v != null) auth.setTheme(v); },
            ),
          ),
          ListTile(title: Text(i.t('session')), subtitle: Text('${i.t('inactivity')}: ${auth.inactivityMinutes} min\n${auth.user?['email'] ?? ''}')),
          if (auth.isStaff && auth.hasPermission('SETTINGS_UPDATE')) ...[
            const Divider(),
            SwitchListTile(
              title: Text(i.t('notifyEmail')),
              value: _notifyEmail,
              onChanged: _savingPrefs ? null : (value) => _savePrefs(email: value),
            ),
            SwitchListTile(
              title: Text(i.t('notifyPush')),
              value: _notifyPush,
              onChanged: _savingPrefs ? null : (value) => _savePrefs(push: value),
            ),
          ],
          if (_canManageHours)
            ListTile(
              title: Text(i.t('hoursTitle')),
              subtitle: Text(i.t('hoursManageWeb')),
              onTap: _openWebHours,
            ),
          if (!auth.isSuperAdmin) ...[
            const Divider(),
            Text(i.t('securityPrivacy'), style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 8),
            Text(i.t('deleteWarning'), style: Theme.of(context).textTheme.bodySmall),
            const SizedBox(height: 8),
            OutlinedButton(
              onPressed: saving ? null : _deleteAccount,
              child: Text(i.t('deleteAccount')),
            ),
          ],
          const SizedBox(height: 12),
          FilledButton(onPressed: auth.logout, child: Text(i.t('logout'))),
        ],
      ),
    );
  }
}

class CountOrChevron extends StatelessWidget {
  const CountOrChevron({super.key, required this.count});
  final int count;
  @override
  Widget build(BuildContext context) {
    if (count <= 0) return const Icon(Icons.chevron_right);
    final text = count > 99 ? '99+' : '$count';
    return CircleAvatar(radius: 12, backgroundColor: const Color(0xFFE11D48), child: Text(text, style: const TextStyle(color: Colors.white, fontSize: 10)));
  }
}
