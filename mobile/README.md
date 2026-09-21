# Lunaveta mobile

Aplicación Flutter para propietarios de mascotas y personal de veterinaria. Comparte la API REST `/api/v1` con el panel Angular.

La facturación en móvil es **solo consulta**. La compra, el cambio y la cancelación del plan se realizan en la aplicación web.

## Configuración

Las URLs se centralizan en `lib/core/config.dart` y se sobrescriben con `--dart-define`:

| Variable | Descripción | Producción |
| --- | --- | --- |
| `API_URL` | Backend (`/api/v1` se añade si falta) | `https://vetora-production-4eac.up.railway.app` |
| `WEB_URL` | Aplicación web | `https://lunaveta.com` |

En release, si no se pasa `API_URL`, se usa el backend de producción. En debug, el valor por defecto es `http://localhost:8080/api/v1`.

```bash
cd mobile
flutter pub get
flutter run \
  --dart-define=API_URL=http://10.0.2.2:8080/api/v1 \
  --dart-define=WEB_URL=https://lunaveta.com
```

Emulador Android: `10.0.2.2` apunta al `localhost` del equipo anfitrión.

Release:

```bash
flutter build apk --release \
  --dart-define=API_URL=https://vetora-production-4eac.up.railway.app \
  --dart-define=WEB_URL=https://lunaveta.com
```

La pantalla de facturación abre `https://lunaveta.com/billing` en el navegador externo, sin tokens en la URL.
