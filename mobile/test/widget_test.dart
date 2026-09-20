import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:animalin/core/l10n.dart';
import 'package:animalin/core/theme.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('Spanish i18n loads owner-facing copy', (tester) async {
    await tester.pumpWidget(const MaterialApp(home: SizedBox.shrink()));
    await I18n.instance.load('es');
    expect(I18n.instance.t('appName'), 'Vetora');
    expect(I18n.instance.t('login'), 'Iniciar sesión');
    expect(I18n.instance.t('reschedule'), 'Reprogramar');
  });

  testWidgets('English i18n loads owner-facing copy', (tester) async {
    await tester.pumpWidget(const MaterialApp(home: SizedBox.shrink()));
    await I18n.instance.load('en');
    expect(I18n.instance.t('login'), 'Sign in');
    expect(I18n.instance.t('themeDark'), 'Dark');
  });

  test('brand color is teal', () {
    expect(VetoraTheme.brand, const Color(0xFF0F766E));
    expect(VetoraTheme.light.useMaterial3, isTrue);
    expect(VetoraTheme.dark.brightness, Brightness.dark);
  });
}
