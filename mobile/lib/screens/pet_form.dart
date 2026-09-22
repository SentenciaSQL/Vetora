import 'package:flutter/material.dart';
import '../core/auth.dart';
import '../core/format.dart';
import '../core/l10n.dart';

class PetFormScreen extends StatefulWidget {
  const PetFormScreen({super.key, required this.auth, this.pet, this.ownerId});
  final AuthStore auth;
  final Map<String, dynamic>? pet;
  final int? ownerId;

  @override
  State<PetFormScreen> createState() => _PetFormScreenState();
}

class _PetFormScreenState extends State<PetFormScreen> {
  final name = TextEditingController();
  final breed = TextEditingController();
  final microchip = TextEditingController();
  final ownerId = TextEditingController();
  String species = 'DOG';
  String sex = 'UNKNOWN';
  bool loading = false;
  bool loadingBranches = true;
  String? error;
  List owners = [];
  List branches = [];
  int? selectedOwner;
  int? branchId;
  I18n get i => I18n.instance;

  @override
  void initState() {
    super.initState();
    final pet = widget.pet;
    if (pet != null) {
      name.text = asString(pet['name']);
      breed.text = asString(pet['breed']);
      microchip.text = asString(pet['microchip']);
      species = asString(pet['species'], 'DOG');
      sex = asString(pet['sex'], 'UNKNOWN');
      const speciesOk = ['DOG', 'CAT', 'BIRD', 'RABBIT', 'RODENT', 'REPTILE', 'HORSE', 'OTHER'];
      const sexOk = ['FEMALE', 'MALE', 'UNKNOWN'];
      if (!speciesOk.contains(species)) species = 'DOG';
      if (!sexOk.contains(sex)) sex = 'UNKNOWN';
      selectedOwner = asInt(pet['ownerId'], 0) == 0 ? null : asInt(pet['ownerId']);
      branchId = asInt(pet['branchId'], 0) == 0 ? null : asInt(pet['branchId']);
    } else {
      selectedOwner = widget.ownerId;
    }
    _loadOwners();
    _loadBranches();
  }

  Future<void> _loadOwners() async {
    try {
      owners = asList(await widget.auth.api.get('/owners', {'size': '100'}));
      if (mounted) setState(() {});
    } catch (_) {}
  }

  Future<void> _loadBranches() async {
    try {
      branches = asList(await widget.auth.api.get('/branches'));
      if (branchId == null && branches.length == 1) {
        branchId = asInt(asMap(branches.first)['id']);
      }
    } catch (_) {
    } finally {
      loadingBranches = false;
      if (mounted) setState(() {});
    }
  }

  String _branchLabel(dynamic branch) {
    final name = asString(asMap(branch)['name']);
    final city = asString(asMap(branch)['city']);
    if (city.isEmpty) return name;
    return '$name · $city';
  }

  @override
  void dispose() {
    name.dispose();
    breed.dispose();
    microchip.dispose();
    ownerId.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    if (loadingBranches || name.text.trim().isEmpty || selectedOwner == null || (branches.length > 1 && (branchId == null || branchId == 0))) {
      setState(() => error = i.t('requiredFields'));
      return;
    }
    setState(() {
      loading = true;
      error = null;
    });
    final body = {
      'ownerId': selectedOwner,
      'name': name.text.trim(),
      'species': species,
      'breed': breed.text.trim(),
      'microchip': microchip.text.trim(),
      'sex': sex,
      if (branchId != null && branchId != 0) 'branchId': branchId,
    };
    try {
      if (widget.pet?['id'] != null) {
        await widget.auth.api.put('/pets/${widget.pet!['id']}', body);
      } else {
        await widget.auth.api.post('/pets', body);
      }
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
      appBar: AppBar(title: Text(widget.pet == null ? i.t('registerPet') : i.t('editPet'))),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          DropdownButtonFormField<int>(
            // ignore: deprecated_member_use
            value: owners.any((owner) => asInt(owner['id']) == selectedOwner) ? selectedOwner : null,
            decoration: InputDecoration(labelText: i.t('owners')),
            items: [
              for (final owner in owners)
                DropdownMenuItem(value: asInt(owner['id']), child: Text(asString(owner['fullName']))),
            ],
            onChanged: widget.pet == null ? (value) => setState(() => selectedOwner = value) : null,
          ),
          if (branches.isNotEmpty) ...[
            const SizedBox(height: 12),
            DropdownButtonFormField<int>(
              // ignore: deprecated_member_use
              value: branches.any((branch) => asInt(asMap(branch)['id']) == branchId) ? branchId : null,
              decoration: InputDecoration(labelText: i.t('branch')),
              items: [
                for (final branch in branches)
                  DropdownMenuItem(value: asInt(asMap(branch)['id']), child: Text(_branchLabel(branch))),
              ],
              onChanged: (value) => setState(() => branchId = value),
            ),
          ],
          const SizedBox(height: 12),
          TextField(controller: name, decoration: InputDecoration(labelText: i.t('name'))),
          const SizedBox(height: 12),
          DropdownButtonFormField<String>(
            // ignore: deprecated_member_use
            value: species,
            decoration: InputDecoration(labelText: i.t('species')),
            items: [
              for (final code in const ['DOG', 'CAT', 'BIRD', 'RABBIT', 'RODENT', 'REPTILE', 'HORSE', 'OTHER'])
                DropdownMenuItem(value: code, child: Text(i.t('species_$code'))),
            ],
            onChanged: (value) => setState(() => species = value ?? 'DOG'),
          ),
          const SizedBox(height: 12),
          DropdownButtonFormField<String>(
            // ignore: deprecated_member_use
            value: sex,
            decoration: InputDecoration(labelText: i.t('sex')),
            items: [
              for (final code in const ['FEMALE', 'MALE', 'UNKNOWN'])
                DropdownMenuItem(value: code, child: Text(i.t('sex_$code'))),
            ],
            onChanged: (value) => setState(() => sex = value ?? 'UNKNOWN'),
          ),
          const SizedBox(height: 12),
          TextField(controller: breed, decoration: InputDecoration(labelText: i.t('breed'))),
          const SizedBox(height: 12),
          TextField(controller: microchip, decoration: InputDecoration(labelText: i.t('microchip'))),
          if (error != null) Padding(padding: const EdgeInsets.only(top: 12), child: Text(error!, style: const TextStyle(color: Colors.red))),
          const SizedBox(height: 20),
          FilledButton(onPressed: loading || loadingBranches ? null : _save, child: Text(loading || loadingBranches ? '…' : i.t('save'))),
        ],
      ),
    );
  }
}
