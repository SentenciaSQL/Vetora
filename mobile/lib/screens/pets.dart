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
    widget.auth.api.get('/pets/mine').then((value) => setState(() => pets = value as List? ?? []));
  }

  @override
  Widget build(BuildContext context) {
    final i = I18n.instance;
    return SafeArea(
      child: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          Text(i.t('pets'), style: Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.w700)),
          const SizedBox(height: 12),
          if (pets.isEmpty) Text(i.t('empty')),
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
