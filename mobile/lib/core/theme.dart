import 'package:flutter/material.dart';

class VetoraTheme {
  static const brand = Color(0xFF0F766E);
  static const sand = Color(0xFFFAF7F2);

  static ThemeData get light => ThemeData(
        useMaterial3: true,
        colorScheme: ColorScheme.fromSeed(seedColor: brand, brightness: Brightness.light),
        scaffoldBackgroundColor: sand,
      );

  static ThemeData get dark => ThemeData(
        useMaterial3: true,
        colorScheme: ColorScheme.fromSeed(seedColor: brand, brightness: Brightness.dark),
      );
}
