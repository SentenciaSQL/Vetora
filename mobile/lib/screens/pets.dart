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
  List branches = [];
  String? tenantSlug;
  int? branchId;
  int _branchRequest = 0;
  bool loadingBranches = false;
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
      if (tenantSlug != null) await _loadBranches(tenantSlug);
    } catch (e) {
      if (mounted) setState(() => error = userMessage(e));
    }
  }

  Future<void> _loadBranches(String? slug) async {
    final request = ++_branchRequest;
    branches = [];
    branchId = null;
    loadingBranches = slug != null && slug.isNotEmpty;
    if (mounted) setState(() {});
    if (!loadingBranches) return;
    try {
      final list = asList(await widget.auth.api.get('/public/tenants/$slug/branches'));
      if (!mounted || request != _branchRequest) return;
      setState(() {
        branches = list;
        loadingBranches = false;
        if (list.length == 1) branchId = asInt(list.first['id']);
      });
    } catch (e) {
      if (mounted && request == _branchRequest) {
        setState(() {
          loadingBranches = false;
          error = userMessage(e);
        });
      }
    }
  }

  String _branchLabel(dynamic branch) {
    final name = asString(asMap(branch)['name']);
    final city = asString(asMap(branch)['city']);
    if (city.isEmpty) return name;
    return '$name · $city';
  }

  Future<void> _submit() async {
    if (loadingBranches || name.text.trim().isEmpty || tenantSlug == null || tenantSlug!.isEmpty || (branches.length > 1 && (branchId == null || branchId == 0))) {
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
        if (branchId != null && branchId != 0) 'branchId': branchId,
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
          AppDropdownField<String>(
            value: tenantSlug,
            label: i.t('clinic'),
            options: [
              for (final clinic in clinics)
                LabeledOption(clinic['slug'] as String, '${clinic['commercialName'] ?? clinic['name']}'),
            ],
            onChanged: (value) {
              setState(() => tenantSlug = value);
              _loadBranches(value);
            },
          ),
          if (loadingBranches)
            Padding(padding: const EdgeInsets.only(top: 12), child: Text(i.t('loading'))),
          if (branches.isNotEmpty) ...[
            const SizedBox(height: 12),
            AppDropdownField<int>(
              value: branches.any((branch) => asInt(asMap(branch)['id']) == branchId) ? branchId : null,
              label: i.t('branch'),
              options: [
                for (final branch in branches)
                  LabeledOption(asInt(asMap(branch)['id']), _branchLabel(branch)),
              ],
              onChanged: (value) => setState(() => branchId = value),
            ),
          ],
          const SizedBox(height: 12),
          TextField(controller: name, decoration: InputDecoration(labelText: i.t('name'))),
          const SizedBox(height: 12),
          AppDropdownField<String>(
            value: species,
            label: i.t('species'),
            options: [for (final code in speciesOptions) LabeledOption(code, i.t('species_$code'))],
            onChanged: (value) => setState(() => species = value ?? 'DOG'),
          ),
          const SizedBox(height: 12),
          TextField(controller: breed, decoration: InputDecoration(labelText: i.t('breed'))),
          const SizedBox(height: 12),
          TextField(controller: microchip, decoration: InputDecoration(labelText: i.t('microchip'))),
          if (error != null) Padding(padding: const EdgeInsets.only(top: 12), child: Text(error!, style: const TextStyle(color: Colors.red))),
          const SizedBox(height: 20),
          FilledButton(onPressed: loading || loadingBranches ? null : _submit, child: Text(loading || loadingBranches ? '…' : i.t('save'))),
        ],
      ),
    );
  }
}
