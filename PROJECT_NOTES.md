# Implementation notes

## ADB transport
AppMan embeds `libadb-android` rather than Shizuku. The library implements the Android Wireless Debugging pairing/TLS ADB protocol and exposes `pair(host, port, code)`, automatic mDNS discovery, and ADB shell streams. The project persists its own RSA key/certificate in the app-private files directory so re-pairing is not required after ordinary reconnects.

## Pairing notification
The foreground service owns a low-importance ongoing notification with an Android RemoteInput action. The user can type the current six-digit Wireless Debugging pairing code directly from the notification. This is intentionally different from claiming a fixed pairing code: Android generates that code and an ordinary app cannot force it to remain unchanged.

## UI command mapping
- Clear cache: `pm clear --cache-only <pkg>`
- Clear data: `pm clear <pkg>`
- Force stop: `am force-stop <pkg>`
- Uninstall for current user: `pm uninstall --user 0 <pkg>`
- All-network switch: `cmd connectivity set-package-networking-enabled <true|false> <pkg>`
- Background restriction: `cmd appops set <pkg> RUN_ANY_IN_BACKGROUND <ignore|allow>`

Wi-Fi-only vs mobile-only controls are deliberately not faked. Android OEM/API support varies and the stable command shown in the request controls package networking as a whole. The multi-select menu keeps Wi-Fi/Mobile/All as explicit slots so the command layer can be expanded per OEM/API without silently doing the wrong thing.
