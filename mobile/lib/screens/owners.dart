import 'package:flutter/material.dart';
import '../core/auth.dart';
import '../core/format.dart';
import '../core/l10n.dart';
import '../core/widgets.dart';
import 'pet_detail.dart';
import 'pet_form.dart';

class OwnersScreen extends StatefulWidget {
  const OwnersScreen({super.key, required this.auth});
  final AuthStore auth;
  @override
  State<OwnersScreen> createState() => _OwnersScreenState();
}

class _OwnersScreenState extends State<OwnersScreen> {
  List owners = [];
  bool loading = true;
  String? error;
  String query = '';

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      loading = true;
      error = null;
    });
    try {
      owners = asList(await widget.auth.api.get('/owners', {'q': query, 'size': '50'}));
      if (mounted) setState(() => loading = false);
    } catch (e) {
      if (mounted) {
        setState(() {
          loading = false;
          error = userMessage(e);
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final i = I18n.instance;
    return Scaffold(
      appBar: AppBar(title: Text(i.t('owners'))),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.all(16),
            child: TextField(
              decoration: InputDecoration(prefixIcon: const Icon(Icons.search), hintText: i.t('search')),
              onSubmitted: (value) {
                query = value.trim();
                _load();
              },
            ),
          ),
          Expanded(
            child: RefreshIndicator(
              onRefresh: _load,
              child: loading || error != null || owners.isEmpty
                  ? ListView(children: [StatusView(loading: loading, error: error, empty: loading ? null : i.t('empty'), onRetry: _load)])
                  : ListView(
                      children: [
                        for (final owner in owners)
                          ListTile(
                            title: Text(asString(owner['fullName'])),
                            subtitle: Text('${owner['email'] ?? owner['phone'] ?? ''}'),
                            onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => OwnerDetailScreen(auth: widget.auth, owner: asMap(owner)))),
                          ),
                      ],
                    ),
            ),
          ),
        ],
      ),
    );
  }
}

class OwnerDetailScreen extends StatefulWidget {
  const OwnerDetailScreen({super.key, required this.auth, required this.owner});
  final AuthStore auth;
  final Map owner;
  @override
  State<OwnerDetailScreen> createState() => _OwnerDetailScreenState();
}

class _OwnerDetailScreenState extends State<OwnerDetailScreen> {
  List pets = [];
  bool loading = true;
  String? error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      pets = asList(await widget.auth.api.get('/owners/${widget.owner['id']}/pets'));
      if (mounted) setState(() => loading = false);
    } catch (e) {
      if (mounted) {
        setState(() {
          loading = false;
          error = userMessage(e);
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final i = I18n.instance;
    return Scaffold(
      appBar: AppBar(title: Text(asString(widget.owner['fullName']))),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          ListTile(title: Text(i.t('email')), subtitle: Text(asString(widget.owner['email'], '—'))),
          ListTile(title: Text(i.t('phone')), subtitle: Text(asString(widget.owner['phone'], '—'))),
          const SizedBox(height: 12),
          Row(children: [
            Expanded(child: Text(i.t('pets'), style: Theme.of(context).textTheme.titleMedium)),
            if (widget.auth.hasPermission('PET_CREATE'))
              TextButton(
                onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => PetFormScreen(auth: widget.auth, ownerId: asInt(widget.owner['id'])))).then((_) => _load()),
                child: Text(i.t('registerPet')),
              ),
          ]),
          if (loading || error != null)
            StatusView(loading: loading, error: error, onRetry: _load)
          else if (pets.isEmpty)
            Text(i.t('empty'))
          else
            for (final pet in pets)
              ListTile(
                title: Text(asString(pet['name'])),
                subtitle: Text('${pet['species'] ?? ''}'),
                onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => PetDetailScreen(auth: widget.auth, pet: asMap(pet)))),
              ),
        ],
      ),
    );
  }
}
