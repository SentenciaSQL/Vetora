import 'package:flutter/material.dart';
import '../core/auth.dart';
import '../core/format.dart';
import '../core/l10n.dart';
import '../core/widgets.dart';
import 'pet_detail.dart';
import 'pet_form.dart';

class PetsScreen extends StatefulWidget {
  const PetsScreen({super.key, required this.auth});
  final AuthStore auth;
  @override
  State<PetsScreen> createState() => _PetsScreenState();
}

class _PetsScreenState extends State<PetsScreen> {
  List pets = [];
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
      final data = widget.auth.isStaff
          ? await widget.auth.api.get('/pets', {'q': query, 'size': '50'})
          : await widget.auth.api.get('/pets/mine');
      if (!mounted) return;
      setState(() {
        pets = asList(data);
        loading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        loading = false;
        error = userMessage(e);
      });
    }
  }

  Future<void> _openForm([Map<String, dynamic>? pet]) async {
    if (widget.auth.isOwner) {
      final created = await Navigator.push<bool>(
        context,
        MaterialPageRoute(builder: (_) => RegisterPetScreen(auth: widget.auth)),
      );
      if (created == true) await _load();
      return;
    }
    if (!widget.auth.hasPermission('PET_CREATE') && !widget.auth.hasPermission('PET_UPDATE')) return;
    final saved = await Navigator.push<bool>(
      context,
      MaterialPageRoute(builder: (_) => PetFormScreen(auth: widget.auth, pet: pet)),
    );
    if (saved == true) await _load();
  }

  @override
  Widget build(BuildContext context) {
    final i = I18n.instance;
    final canCreate = widget.auth.isOwner || widget.auth.hasPermission('PET_CREATE');
    return SafeArea(
      child: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 20, 20, 8),
            child: Row(
              children: [
                Expanded(child: Text(i.t('pets'), style: Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.w700))),
                if (canCreate) FilledButton(onPressed: () => _openForm(), child: Text(i.t('registerPet'))),
              ],
            ),
          ),
          if (widget.auth.isStaff)
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 20),
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
              child: loading || error != null || pets.isEmpty
                  ? ListView(children: [StatusView(loading: loading, error: error, empty: loading ? null : i.t('emptyPets'), onRetry: _load)])
                  : ListView(
                      padding: const EdgeInsets.all(20),
                      children: [
                        for (final pet in pets)
                          Card(
                            child: ListTile(
                              leading: RemoteCircleAvatar(
                                url: asString(pet['photoUrl']),
                                fallbackText: asString(pet['name'], '?'),
                              ),
                              title: Text(asString(pet['name'])),
                              subtitle: Text('${pet['breed'] ?? pet['species'] ?? ''} · ${pet['tenantName'] ?? pet['ownerName'] ?? ''}'),
                              onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => PetDetailScreen(auth: widget.auth, pet: asMap(pet)))).then((_) {
                                if (mounted) _load();
                              }),
                            ),
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

class RegisterPetScreen extends StatefulWidget {
  const RegisterPetScreen({super.key, required this.auth});
  final AuthStore auth;
  @override
  State<RegisterPetScreen> createState() => _RegisterPetScreenState();
}

class _RegisterPetScreenState extends State<RegisterPetScreen> {
  final name = TextEditingController();
  final breed = TextEditingController();
  final microchip = TextEditingController();
  final speciesOptions = const ['DOG', 'CAT', 'BIRD', 'RABBIT', 'RODENT', 'REPTILE', 'HORSE', 'OTHER'];
  List clinics = [];
  String? tenantSlug;
  String species = 'DOG';
  bool loading = false;
  String? error;
  I18n get i => I18n.instance;

  @override
  void initState() {
    super.initState();
    _loadClinics();
  }

  @override
  void dispose() {
    name.dispose();
    breed.dispose();
    microchip.dispose();
    super.dispose();
  }

  Future<void> _loadClinics() async {
    try {
      final list = asList(await widget.auth.api.get('/clinics'));
      if (!mounted) return;
      setState(() {
        clinics = list;
        if (list.length == 1) tenantSlug = list.first['slug'] as String?;
      });
    } catch (e) {
      if (mounted) setState(() => error = userMessage(e));
    }
  }

  Future<void> _submit() async {
    if (name.text.trim().isEmpty || tenantSlug == null || tenantSlug!.isEmpty) {
      setState(() => error = i.t('requiredFields'));
      return;
    }
    setState(() {
      loading = true;
      error = null;
    });
    try {
      await widget.auth.api.post('/pets/mine', {
        'tenantSlug': tenantSlug,
        'name': name.text.trim(),
        'species': species,
        'breed': breed.text.trim(),
        'microchip': microchip.text.trim(),
        'sex': 'UNKNOWN',
      });
      if (mounted) Navigator.pop(context, true);
    } catch (e) {
      if (mounted) setState(() => error = userMessage(e));
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(i.t('registerPet'))),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          DropdownButtonFormField<String>(
            // ignore: deprecated_member_use
            value: tenantSlug,
            decoration: InputDecoration(labelText: i.t('clinic')),
            items: [
              for (final clinic in clinics)
                DropdownMenuItem(value: clinic['slug'] as String, child: Text('${clinic['commercialName'] ?? clinic['name']}')),
            ],
            onChanged: (value) => setState(() => tenantSlug = value),
          ),
          const SizedBox(height: 12),
          TextField(controller: name, decoration: InputDecoration(labelText: i.t('name'))),
          const SizedBox(height: 12),
          DropdownButtonFormField<String>(
            // ignore: deprecated_member_use
            value: species,
            decoration: InputDecoration(labelText: i.t('species')),
            items: [for (final code in speciesOptions) DropdownMenuItem(value: code, child: Text(i.t('species_$code')))],
            onChanged: (value) => setState(() => species = value ?? 'DOG'),
          ),
          const SizedBox(height: 12),
          TextField(controller: breed, decoration: InputDecoration(labelText: i.t('breed'))),
          const SizedBox(height: 12),
          TextField(controller: microchip, decoration: InputDecoration(labelText: i.t('microchip'))),
          if (error != null) Padding(padding: const EdgeInsets.only(top: 12), child: Text(error!, style: const TextStyle(color: Colors.red))),
          const SizedBox(height: 20),
          FilledButton(onPressed: loading ? null : _submit, child: Text(loading ? '…' : i.t('save'))),
        ],
      ),
    );
  }
}
