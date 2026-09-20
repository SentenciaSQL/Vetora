import 'package:flutter/material.dart';
import '../core/auth.dart';
import '../core/l10n.dart';
import 'pet_detail.dart';

class PetsScreen extends StatefulWidget {
  const PetsScreen({super.key, required this.auth});
  final AuthStore auth;
  @override
  State<PetsScreen> createState() => _PetsScreenState();
}

class _PetsScreenState extends State<PetsScreen> {
  List pets = [];

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final mine = await widget.auth.api.get('/pets/mine');
      if (!mounted) return;
      setState(() => pets = mine as List? ?? []);
    } catch (_) {}
  }

  Future<void> _openRegister() async {
    final created = await Navigator.push<bool>(
      context,
      MaterialPageRoute(builder: (_) => RegisterPetScreen(auth: widget.auth)),
    );
    if (created == true) {
      await _load();
    }
  }

  @override
  Widget build(BuildContext context) {
    final i = I18n.instance;
    return SafeArea(
      child: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          Row(
            children: [
              Expanded(child: Text(i.t('pets'), style: Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.w700))),
              FilledButton(onPressed: _openRegister, child: Text(i.t('registerPet'))),
            ],
          ),
          const SizedBox(height: 12),
          if (pets.isEmpty) Text(i.t('emptyPets')),
          for (final pet in pets)
            Card(
              child: ListTile(
                leading: CircleAvatar(child: Text('${pet['name']}'.substring(0, 1))),
                title: Text('${pet['name']}'),
                subtitle: Text('${pet['breed'] ?? pet['species']} · ${pet['tenantName'] ?? ''}'),
                onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => PetDetailScreen(auth: widget.auth, pet: pet))),
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
  final species = TextEditingController(text: 'DOG');
  final breed = TextEditingController();
  List clinics = [];
  String? tenantSlug;
  bool loading = false;
  String? error;

  I18n get i => I18n.instance;

  @override
  void initState() {
    super.initState();
    _loadClinics();
  }

  Future<void> _loadClinics() async {
    try {
      final list = await widget.auth.api.get('/public/clinics');
      final items = list as List? ?? [];
      if (!mounted) return;
      setState(() {
        clinics = items;
        if (items.length == 1) {
          tenantSlug = items.first['slug'] as String?;
        }
      });
    } catch (_) {
      if (mounted) setState(() => error = i.t('invalid'));
    }
  }

  Future<void> _submit() async {
    if (name.text.trim().isEmpty || tenantSlug == null || tenantSlug!.isEmpty) {
      setState(() => error = i.t('invalid'));
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
        'species': species.text.trim().isEmpty ? 'DOG' : species.text.trim(),
        'breed': breed.text.trim(),
        'sex': 'UNKNOWN',
      });
      if (mounted) Navigator.pop(context, true);
    } catch (_) {
      if (mounted) setState(() => error = i.t('invalid'));
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
            value: tenantSlug,
            decoration: InputDecoration(labelText: i.t('clinic')),
            items: [
              for (final clinic in clinics)
                DropdownMenuItem(
                  value: clinic['slug'] as String,
                  child: Text('${clinic['commercialName'] ?? clinic['name']}'),
                ),
            ],
            onChanged: (value) => setState(() => tenantSlug = value),
          ),
          const SizedBox(height: 12),
          TextField(controller: name, decoration: InputDecoration(labelText: i.t('name'))),
          const SizedBox(height: 12),
          TextField(controller: species, decoration: InputDecoration(labelText: i.t('species'))),
          const SizedBox(height: 12),
          TextField(controller: breed, decoration: InputDecoration(labelText: i.t('breed'))),
          if (error != null) Padding(padding: const EdgeInsets.only(top: 12), child: Text(error!, style: const TextStyle(color: Colors.red))),
          const SizedBox(height: 20),
          FilledButton(
            onPressed: loading ? null : _submit,
            child: Text(loading ? '...' : i.t('save')),
          ),
        ],
      ),
    );
  }
}
