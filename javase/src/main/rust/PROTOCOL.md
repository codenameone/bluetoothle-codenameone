# cn1-ble-helper JSON Protocol

Communication between the Java `NativeBleBackend` and the bundled Rust helper
executable (a btleplug-based binary built from `cn1-ble-helper/`). One JSON
object per line, UTF-8, on the helper's stdin (commands) / stdout (responses
and events). Helper diagnostics go to stderr.

## Identifiers

btleplug's `PeripheralId` is OS-specific (a UUID on macOS, a BDAddr on Linux,
a 64-bit address on Windows); the helper renders it as the lowercase string
form and the cn1lib treats that as the wire-protocol `address`. Service and
characteristic UUIDs are normalized to lowercase 128-bit form
(`0000180a-0000-1000-8000-00805f9b34fb`) before being sent in either direction.

## Commands (Java → helper)

Each command carries an `id` (numeric, monotonic) that the helper echoes back
in its `ack` and in `error` events tied to that command.

```
{"cmd":"initialize","id":1}
{"cmd":"startScan","id":2,"services":["uuid",...],"allowDuplicates":false}
{"cmd":"stopScan","id":3}
{"cmd":"connect","id":4,"address":"..."}
{"cmd":"disconnect","id":5,"address":"..."}
{"cmd":"discover","id":6,"address":"..."}
{"cmd":"read","id":7,"address":"...","service":"...","characteristic":"..."}
{"cmd":"write","id":8,"address":"...","service":"...","characteristic":"...","value":"<base64>","noResponse":false}
{"cmd":"subscribe","id":9,"address":"...","service":"...","characteristic":"..."}
{"cmd":"unsubscribe","id":10,"address":"...","service":"...","characteristic":"..."}
{"cmd":"shutdown"}
```

## Acknowledgements (helper → Java)

Immediate "command accepted by helper" response. Does **not** mean the BLE
operation completed — those land as events.

```
{"ack":2,"ok":true}
{"ack":2,"ok":false,"error":"isDisabled"}
```

## Events (helper → Java, async)

```
{"event":"stateChanged","state":"poweredOn|unsupported"}
{"event":"scanResult","address":"...","name":"...","rssi":-45,"advertisement":"<base64>"}
{"event":"scanStopped"}
{"event":"connected","address":"...","name":"..."}
{"event":"disconnected","address":"...","reason":"..."}
{"event":"discovered","address":"...","name":"...","services":[{"uuid":"...","characteristics":[{"uuid":"...","properties":["read","write","notify"]}]}]}
{"event":"readResult","address":"...","service":"...","characteristic":"...","value":"<base64>"}
{"event":"writeResult","address":"...","service":"...","characteristic":"...","value":"<base64>"}
{"event":"notification","address":"...","service":"...","characteristic":"...","value":"<base64>"}
{"event":"subscribed","address":"...","service":"...","characteristic":"..."}
{"event":"unsubscribed","address":"...","service":"...","characteristic":"..."}
{"event":"error","command":"read","address":"...","message":"unknown characteristic"}
```

`stateChanged` fires exactly once at startup with either `poweredOn` (helper
got an adapter from btleplug) or `unsupported` (no adapter present). btleplug
itself doesn't expose runtime state changes on most platforms, so the Java
side caches the startup state and treats `poweredOn` as enabled.

`error` events carry the originating command name in `command` so the Java
side can route the failure to the correct callback.

## Shutdown

When the JVM exits or `NativeBleBackend` is closed, Java sends
`{"cmd":"shutdown"}` and closes stdin. The helper exits with status 0. If the
JVM crashes, the helper detects the closed stdin (EOF) and exits with
status 0.
