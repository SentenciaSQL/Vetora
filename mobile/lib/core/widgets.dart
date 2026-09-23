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
      content = const Padding(padding: EdgeInsets.all(32), child: AppLoadingIndicator(size: 36));
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
      final bounded = constraints.hasBoundedHeight && constraints.maxHeight.isFinite;
      if (!bounded) {
        return content;
      }
      if (loading) {
        return Center(child: content);
      }
      return Center(child: SingleChildScrollView(child: content));
    });
  }
}

class AppLoadingIndicator extends StatelessWidget {
  const AppLoadingIndicator({super.key, this.size = 24, this.strokeWidth = 2.5, this.color});

  final double size;
  final double strokeWidth;
  final Color? color;

  @override
  Widget build(BuildContext context) {
    // A list or button passes a tight width equal to the screen. A plain
    // SizedBox cannot shrink below that minimum, so the indicator becomes a
    // full-width oval. Align loosens those constraints and keeps a square.
    return Align(
      alignment: Alignment.center,
      widthFactor: 1,
      heightFactor: 1,
      child: SizedBox.square(
        dimension: size,
        child: CircularProgressIndicator(strokeWidth: strokeWidth, color: color),
      ),
    );
  }
}

class ButtonLabel extends StatelessWidget {
  const ButtonLabel({super.key, required this.label, this.loading = false, this.color});

  final String label;
  final bool loading;
  final Color? color;

  @override
  Widget build(BuildContext context) {
    return Stack(
      alignment: Alignment.center,
      children: [
        Opacity(opacity: loading ? 0 : 1, child: Text(label)),
        if (loading) AppLoadingIndicator(size: 20, strokeWidth: 2.2, color: color),
      ],
    );
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
    final rawFallback = (fallbackText ?? '').trim();
    final shown = rawFallback.isEmpty
        ? ''
        : (rawFallback.length <= 2 ? rawFallback.toUpperCase() : rawFallback.substring(0, 1).toUpperCase());
    final fallback = shown.isNotEmpty
        ? Text(shown)
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

class LabeledOption<T> {
  const LabeledOption(this.value, this.label);

  final T value;
  final String label;
}

/// Full-width dropdown whose selected value ellipsizes and whose menu items wrap.
class AppDropdownField<T> extends StatelessWidget {
  const AppDropdownField({
    super.key,
    required this.value,
    required this.label,
    required this.options,
    required this.onChanged,
  });

  final T? value;
  final String label;
  final List<LabeledOption<T>> options;
  final ValueChanged<T?>? onChanged;

  @override
  Widget build(BuildContext context) {
    final selected = options.any((option) => option.value == value) ? value : null;
    return DropdownButtonFormField<T>(
      // ignore: deprecated_member_use
      value: selected,
      isExpanded: true,
      itemHeight: null,
      decoration: InputDecoration(labelText: label),
      items: [
        for (final option in options)
          DropdownMenuItem<T>(
            value: option.value,
            child: Text(option.label, maxLines: 2, overflow: TextOverflow.ellipsis),
          ),
      ],
      selectedItemBuilder: (context) => [
        for (final option in options)
          Text(option.label, maxLines: 1, overflow: TextOverflow.ellipsis),
      ],
      onChanged: onChanged,
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
