import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import 'l10n.dart';

class StatusView extends StatelessWidget {
  const StatusView({super.key, this.loading = false, this.error, this.empty, this.onRetry, this.child});

  final bool loading;
  final String? error;
  final String? empty;
  final VoidCallback? onRetry;
  final Widget? child;

  @override
  Widget build(BuildContext context) {
    Widget content;
    if (loading) {
      content = const Padding(padding: EdgeInsets.all(32), child: CircularProgressIndicator());
    } else if (error != null && error!.isNotEmpty) {
      content = Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(error!, textAlign: TextAlign.center, style: TextStyle(color: Theme.of(context).colorScheme.error)),
            if (onRetry != null) ...[
              const SizedBox(height: 12),
              FilledButton(onPressed: onRetry, child: Text(I18n.instance.t('retry'))),
            ],
          ],
        ),
      );
    } else if (empty != null) {
      content = Padding(padding: const EdgeInsets.all(24), child: Text(empty!, textAlign: TextAlign.center));
    } else {
      content = child ?? const SizedBox.shrink();
    }
    return LayoutBuilder(builder: (context, constraints) {
      if (constraints.hasBoundedHeight && constraints.maxHeight.isFinite) {
        return Center(child: SingleChildScrollView(child: content));
      }
      return content;
    });
  }
}

class RemoteImage extends StatelessWidget {
  const RemoteImage(this.url, {super.key, this.height, this.width, this.fit = BoxFit.cover, this.fallback});

  final String url;
  final double? height;
  final double? width;
  final BoxFit fit;
  final Widget? fallback;

  @override
  Widget build(BuildContext context) {
    if (url.trim().isEmpty) {
      return fallback ?? const SizedBox.shrink();
    }
    return Image.network(
      url,
      height: height,
      width: width,
      fit: fit,
      errorBuilder: (_, __, ___) => fallback ?? const SizedBox.shrink(),
    );
  }
}

Future<XFile?> pickProfilePhoto(BuildContext context) {
  final i = I18n.instance;
  return showModalBottomSheet<ImageSource>(
    context: context,
    builder: (ctx) => SafeArea(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          ListTile(
            leading: const Icon(Icons.photo_library_outlined),
            title: Text(i.t('photoGallery')),
            onTap: () => Navigator.pop(ctx, ImageSource.gallery),
          ),
          ListTile(
            leading: const Icon(Icons.photo_camera_outlined),
            title: Text(i.t('photoCamera')),
            onTap: () => Navigator.pop(ctx, ImageSource.camera),
          ),
        ],
      ),
    ),
  ).then((source) {
    if (source == null) return null;
    return ImagePicker().pickImage(source: source, maxWidth: 1600, imageQuality: 85);
  });
}

bool isProfileImage(String name, int size) {
  final lower = name.toLowerCase();
  final allowed = lower.endsWith('.jpg') || lower.endsWith('.jpeg') || lower.endsWith('.png') || lower.endsWith('.webp');
  return allowed && size > 0 && size <= 5 * 1024 * 1024;
}

class RemoteCircleAvatar extends StatelessWidget {
  const RemoteCircleAvatar({super.key, this.url, this.radius = 20, this.fallbackIcon, this.fallbackText});

  final String? url;
  final double radius;
  final IconData? fallbackIcon;
  final String? fallbackText;

  @override
  Widget build(BuildContext context) {
    final imageUrl = (url ?? '').trim();
    final fallback = fallbackText != null && fallbackText!.isNotEmpty
        ? Text(fallbackText!.substring(0, 1).toUpperCase())
        : Icon(fallbackIcon ?? Icons.local_hospital_outlined);
    if (imageUrl.isEmpty) {
      return CircleAvatar(radius: radius, child: fallback);
    }
    return CircleAvatar(
      radius: radius,
      child: ClipOval(
        child: Image.network(
          imageUrl,
          width: radius * 2,
          height: radius * 2,
          fit: BoxFit.cover,
          errorBuilder: (_, __, ___) => SizedBox(
            width: radius * 2,
            height: radius * 2,
            child: Center(child: fallback),
          ),
        ),
      ),
    );
  }
}

class CountBadge extends StatelessWidget {
  const CountBadge({super.key, required this.count, this.label});
  final int count;
  final String? label;

  @override
  Widget build(BuildContext context) {
    if (count <= 0) return const SizedBox.shrink();
    final text = count > 99 ? '99+' : '$count';
    return Semantics(
      label: label ?? text,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
        constraints: const BoxConstraints(minWidth: 20),
        decoration: BoxDecoration(color: const Color(0xFFE11D48), borderRadius: BorderRadius.circular(999)),
        child: Text(text, textAlign: TextAlign.center, style: const TextStyle(color: Colors.white, fontSize: 10, fontWeight: FontWeight.w700)),
      ),
    );
  }
}
