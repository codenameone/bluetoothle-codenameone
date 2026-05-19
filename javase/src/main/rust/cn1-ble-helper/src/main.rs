// cn1-ble-helper — cross-platform BLE bridge for the Codename One bluetoothle
// cn1lib's JavaSE backend. See ../PROTOCOL.md for the exact JSON-line
// command/event format we share with the Java side.
//
// Architecture
// ------------
// One tokio runtime, three concurrent tasks:
//   • stdin reader → parses JSON-lines into Command values, hands them to the
//     command dispatcher;
//   • adapter event listener → translates btleplug's CentralEvent stream into
//     stateChanged / scanResult / connected / disconnected wire events;
//   • per-peripheral notification listeners (one per active subscription) →
//     translate ValueNotification streams into "notification" events.
//
// All wire output flows through a single mpsc channel drained by a writer
// task; that keeps every line written atomically without locking stdout.

use base64::engine::general_purpose::STANDARD as B64;
use base64::Engine;
use btleplug::api::{
    BDAddr, Central, CentralEvent, CharPropFlags, Manager as _, Peripheral as _, ScanFilter,
    WriteType,
};
use btleplug::platform::{Adapter, Manager, Peripheral, PeripheralId};
use futures::stream::StreamExt;
use serde_json::{json, Value};
use std::collections::HashMap;
use std::sync::Arc;
use tokio::io::{AsyncBufReadExt, AsyncWriteExt, BufReader};
use tokio::sync::{mpsc, Mutex};
use tokio::task::JoinHandle;
use uuid::Uuid;

// ---------------- protocol helpers ----------------

/// Single sink for everything the helper writes. Wire writes happen through
/// the corresponding receiver task so no two events ever interleave.
type EventSink = mpsc::UnboundedSender<Value>;

fn emit(sink: &EventSink, event: Value) {
    // If the channel is closed the writer task has exited and we're tearing
    // down; dropping a stray event is harmless.
    let _ = sink.send(event);
}

fn emit_event(sink: &EventSink, name: &str, payload: Value) {
    let mut obj = serde_json::Map::new();
    obj.insert("event".into(), Value::String(name.into()));
    if let Value::Object(extra) = payload {
        for (k, v) in extra {
            obj.insert(k, v);
        }
    }
    emit(sink, Value::Object(obj));
}

fn ack(sink: &EventSink, id: Option<u64>, ok: bool, error: Option<&str>) {
    let Some(id) = id else { return };
    let mut obj = serde_json::Map::new();
    obj.insert("ack".into(), Value::from(id));
    obj.insert("ok".into(), Value::from(ok));
    if let Some(e) = error {
        obj.insert("error".into(), Value::from(e));
    }
    emit(sink, Value::Object(obj));
}

/// btleplug exposes a [`Uuid`]; the cn1lib's wire format is always lowercase
/// dashed 128-bit, which is exactly what `Uuid::to_string` produces. Kept as
/// a helper so the call sites read uniformly.
fn fmt_uuid(u: &Uuid) -> String {
    u.to_string()
}

/// Normalize whatever string the Java side sends — could be the 128-bit
/// dashed form ("0000180a-…"), the bare 16-bit form ("180a"), or the BlueZ
/// MAC-with-dashes that some examples use — into a [`Uuid`] btleplug accepts.
fn parse_uuid(raw: &str) -> Option<Uuid> {
    let s = raw.trim();
    if let Ok(u) = Uuid::parse_str(s) {
        return Some(u);
    }
    // 16-bit assigned number: expand using the Bluetooth Base UUID.
    if s.len() == 4 {
        let padded = format!("0000{}-0000-1000-8000-00805f9b34fb", s.to_lowercase());
        return Uuid::parse_str(&padded).ok();
    }
    None
}

/// On macOS btleplug's `PeripheralId` is a UUID; on Linux it's a BDAddr; on
/// Windows it's a `BluetoothAddress`. The `Debug`/`Display` impls all yield
/// canonical strings, which is what we use as the wire-protocol address.
fn id_to_address(id: &PeripheralId) -> String {
    format!("{}", id).to_lowercase()
}

// ---------------- state ----------------

struct State {
    adapter: Adapter,
    peripherals: HashMap<String, Peripheral>,
    /// Subscribers per `address|service|char` so unsubscribe can cancel them.
    notif_listeners: HashMap<String, JoinHandle<()>>,
    scanning: bool,
}

impl State {
    fn lookup_peripheral(&self, address: &str) -> Option<&Peripheral> {
        self.peripherals.get(address)
    }
}

type SharedState = Arc<Mutex<State>>;

// ---------------- command dispatch ----------------

async fn handle_command(state: SharedState, sink: EventSink, cmd: Value) {
    let cmd_name = cmd.get("cmd").and_then(|v| v.as_str()).unwrap_or("").to_string();
    let id = cmd.get("id").and_then(|v| v.as_u64());

    match cmd_name.as_str() {
        "initialize" => {
            // No state re-broadcast on initialize — the startup-time
            // stateChanged is the single source of truth. The Java side sets
            // initialized=true before launching the helper, so the startup
            // event already lands with full context.
            ack(&sink, id, true, None);
        }
        "startScan" => {
            let filter = parse_scan_filter(&cmd);
            let mut s = state.lock().await;
            match s.adapter.start_scan(filter).await {
                Ok(()) => {
                    s.scanning = true;
                    drop(s);
                    ack(&sink, id, true, None);
                }
                Err(e) => {
                    drop(s);
                    ack(&sink, id, false, Some(&e.to_string()));
                }
            }
        }
        "stopScan" => {
            let mut s = state.lock().await;
            let res = s.adapter.stop_scan().await;
            s.scanning = false;
            drop(s);
            match res {
                Ok(()) => {
                    ack(&sink, id, true, None);
                    emit_event(&sink, "scanStopped", json!({}));
                }
                Err(e) => ack(&sink, id, false, Some(&e.to_string())),
            }
        }
        "connect" => {
            let Some(address) = cmd.get("address").and_then(|v| v.as_str()) else {
                ack(&sink, id, false, Some("missingAddress"));
                return;
            };
            let s = state.lock().await;
            let Some(p) = s.lookup_peripheral(address).cloned() else {
                drop(s);
                ack(&sink, id, false, Some("unknownPeripheral"));
                return;
            };
            drop(s);
            match p.connect().await {
                Ok(()) => ack(&sink, id, true, None),
                Err(e) => {
                    ack(&sink, id, false, Some(&e.to_string()));
                    emit_event(
                        &sink,
                        "error",
                        json!({"command": "connect", "address": address, "message": e.to_string()}),
                    );
                }
            }
        }
        "disconnect" => {
            let Some(address) = cmd.get("address").and_then(|v| v.as_str()) else {
                ack(&sink, id, false, Some("missingAddress"));
                return;
            };
            let s = state.lock().await;
            let Some(p) = s.lookup_peripheral(address).cloned() else {
                drop(s);
                ack(&sink, id, false, Some("unknownPeripheral"));
                return;
            };
            drop(s);
            match p.disconnect().await {
                Ok(()) => ack(&sink, id, true, None),
                Err(e) => ack(&sink, id, false, Some(&e.to_string())),
            }
        }
        "discover" => {
            let Some(address) = cmd.get("address").and_then(|v| v.as_str()) else {
                ack(&sink, id, false, Some("missingAddress"));
                return;
            };
            let s = state.lock().await;
            let Some(p) = s.lookup_peripheral(address).cloned() else {
                drop(s);
                ack(&sink, id, false, Some("unknownPeripheral"));
                return;
            };
            drop(s);
            ack(&sink, id, true, None);
            if let Err(e) = p.discover_services().await {
                emit_event(
                    &sink,
                    "error",
                    json!({"command": "discover", "address": address, "message": e.to_string()}),
                );
                return;
            }
            emit_event(&sink, "discovered", build_discovered_payload(&p, address).await);
        }
        "read" => {
            let Some((p, ch, addr, svc_uuid, ch_uuid)) =
                resolve_characteristic(&state, &cmd).await
            else {
                ack(&sink, id, false, Some("unknownCharacteristic"));
                return;
            };
            ack(&sink, id, true, None);
            match p.read(&ch).await {
                Ok(value) => emit_event(
                    &sink,
                    "readResult",
                    json!({
                        "address": addr,
                        "service": svc_uuid,
                        "characteristic": ch_uuid,
                        "value": B64.encode(&value),
                    }),
                ),
                Err(e) => emit_event(
                    &sink,
                    "error",
                    json!({"command": "read", "address": addr, "message": e.to_string()}),
                ),
            }
        }
        "write" => {
            let Some((p, ch, addr, svc_uuid, ch_uuid)) =
                resolve_characteristic(&state, &cmd).await
            else {
                ack(&sink, id, false, Some("unknownCharacteristic"));
                return;
            };
            let value_b64 = cmd.get("value").and_then(|v| v.as_str()).unwrap_or("");
            let Ok(value) = B64.decode(value_b64) else {
                ack(&sink, id, false, Some("badBase64"));
                return;
            };
            let no_response = cmd.get("noResponse").and_then(|v| v.as_bool()).unwrap_or(false);
            let write_type = if no_response {
                WriteType::WithoutResponse
            } else {
                WriteType::WithResponse
            };
            ack(&sink, id, true, None);
            match p.write(&ch, &value, write_type).await {
                Ok(()) => emit_event(
                    &sink,
                    "writeResult",
                    json!({
                        "address": addr,
                        "service": svc_uuid,
                        "characteristic": ch_uuid,
                        "value": value_b64,
                    }),
                ),
                Err(e) => emit_event(
                    &sink,
                    "error",
                    json!({"command": "write", "address": addr, "message": e.to_string()}),
                ),
            }
        }
        "subscribe" => {
            let Some((p, ch, addr, svc_uuid, ch_uuid)) =
                resolve_characteristic(&state, &cmd).await
            else {
                ack(&sink, id, false, Some("unknownCharacteristic"));
                return;
            };
            ack(&sink, id, true, None);
            match p.subscribe(&ch).await {
                Ok(()) => {
                    start_notification_listener(
                        state.clone(),
                        sink.clone(),
                        p.clone(),
                        addr.clone(),
                        svc_uuid.clone(),
                        ch_uuid.clone(),
                    )
                    .await;
                    emit_event(
                        &sink,
                        "subscribed",
                        json!({
                            "address": addr,
                            "service": svc_uuid,
                            "characteristic": ch_uuid,
                        }),
                    );
                }
                Err(e) => emit_event(
                    &sink,
                    "error",
                    json!({"command": "subscribe", "address": addr, "message": e.to_string()}),
                ),
            }
        }
        "unsubscribe" => {
            let Some((p, ch, addr, svc_uuid, ch_uuid)) =
                resolve_characteristic(&state, &cmd).await
            else {
                ack(&sink, id, false, Some("unknownCharacteristic"));
                return;
            };
            let key = subscription_key(&addr, &svc_uuid, &ch_uuid);
            // Stop the listener task before the unsubscribe round-trip; the
            // stream we cancel is the one peripheral.notifications() handed
            // out, and dropping its handle ends the per-subscription task.
            let mut s = state.lock().await;
            if let Some(task) = s.notif_listeners.remove(&key) {
                task.abort();
            }
            drop(s);
            ack(&sink, id, true, None);
            match p.unsubscribe(&ch).await {
                Ok(()) => emit_event(
                    &sink,
                    "unsubscribed",
                    json!({
                        "address": addr,
                        "service": svc_uuid,
                        "characteristic": ch_uuid,
                    }),
                ),
                Err(e) => emit_event(
                    &sink,
                    "error",
                    json!({"command": "unsubscribe", "address": addr, "message": e.to_string()}),
                ),
            }
        }
        "shutdown" => {
            std::process::exit(0);
        }
        other => {
            eprintln!("cn1-ble-helper: unknown command '{}'", other);
            ack(&sink, id, false, Some("unknownCommand"));
        }
    }
}

fn parse_scan_filter(cmd: &Value) -> ScanFilter {
    let mut services = Vec::new();
    if let Some(arr) = cmd.get("services").and_then(|v| v.as_array()) {
        for v in arr {
            if let Some(s) = v.as_str() {
                if let Some(u) = parse_uuid(s) {
                    services.push(u);
                }
            }
        }
    }
    ScanFilter { services }
}

async fn resolve_characteristic(
    state: &SharedState,
    cmd: &Value,
) -> Option<(Peripheral, btleplug::api::Characteristic, String, String, String)> {
    let address = cmd.get("address").and_then(|v| v.as_str())?.to_string();
    let svc_raw = cmd.get("service").and_then(|v| v.as_str())?;
    let ch_raw = cmd.get("characteristic").and_then(|v| v.as_str())?;
    let svc_uuid = parse_uuid(svc_raw)?;
    let ch_uuid = parse_uuid(ch_raw)?;

    let s = state.lock().await;
    let peripheral = s.lookup_peripheral(&address)?.clone();
    drop(s);

    let characteristic = peripheral
        .characteristics()
        .into_iter()
        .find(|c| c.service_uuid == svc_uuid && c.uuid == ch_uuid)?;
    Some((
        peripheral,
        characteristic,
        address,
        fmt_uuid(&svc_uuid),
        fmt_uuid(&ch_uuid),
    ))
}

fn subscription_key(address: &str, service: &str, characteristic: &str) -> String {
    format!("{}|{}|{}", address, service, characteristic)
}

async fn start_notification_listener(
    state: SharedState,
    sink: EventSink,
    peripheral: Peripheral,
    address: String,
    service: String,
    characteristic: String,
) {
    let key = subscription_key(&address, &service, &characteristic);
    let mut s = state.lock().await;
    if let Some(prev) = s.notif_listeners.remove(&key) {
        prev.abort();
    }
    let task = tokio::spawn(async move {
        let mut notif_stream = match peripheral.notifications().await {
            Ok(stream) => stream,
            Err(e) => {
                emit_event(
                    &sink,
                    "error",
                    json!({"command": "subscribe", "address": address, "message": e.to_string()}),
                );
                return;
            }
        };
        while let Some(notif) = notif_stream.next().await {
            // notifications() yields ALL chars on the peripheral; filter to
            // the one we subscribed to.
            if fmt_uuid(&notif.uuid) != characteristic {
                continue;
            }
            emit_event(
                &sink,
                "notification",
                json!({
                    "address": address,
                    "service": service,
                    "characteristic": characteristic,
                    "value": B64.encode(&notif.value),
                }),
            );
        }
    });
    s.notif_listeners.insert(key, task);
}

async fn build_discovered_payload(p: &Peripheral, address: &str) -> Value {
    let props = p.properties().await.ok().flatten();
    let name = props.and_then(|p| p.local_name).unwrap_or_default();
    let mut svc_arr: Vec<Value> = Vec::new();
    for svc in p.services() {
        let mut ch_arr: Vec<Value> = Vec::new();
        for ch in svc.characteristics {
            let mut props = Vec::new();
            let flags = ch.properties;
            if flags.contains(CharPropFlags::READ) {
                props.push("read");
            }
            if flags.contains(CharPropFlags::WRITE) {
                props.push("write");
            }
            if flags.contains(CharPropFlags::WRITE_WITHOUT_RESPONSE) {
                props.push("writeWithoutResponse");
            }
            if flags.contains(CharPropFlags::NOTIFY) {
                props.push("notify");
            }
            if flags.contains(CharPropFlags::INDICATE) {
                props.push("indicate");
            }
            ch_arr.push(json!({
                "uuid": fmt_uuid(&ch.uuid),
                "properties": props,
            }));
        }
        svc_arr.push(json!({
            "uuid": fmt_uuid(&svc.uuid),
            "characteristics": ch_arr,
        }));
    }
    json!({
        "address": address,
        "name": name,
        "services": svc_arr,
    })
}

// ---------------- central-event listener ----------------

async fn central_event_loop(state: SharedState, sink: EventSink, adapter: Adapter) {
    let mut events = match adapter.events().await {
        Ok(e) => e,
        Err(err) => {
            emit_event(
                &sink,
                "error",
                json!({"command": "adapter", "message": err.to_string()}),
            );
            return;
        }
    };
    while let Some(event) = events.next().await {
        match event {
            CentralEvent::DeviceDiscovered(id) | CentralEvent::DeviceUpdated(id) => {
                let address = id_to_address(&id);
                let Ok(p) = adapter.peripheral(&id).await else { continue };
                let props = p.properties().await.ok().flatten();
                let (name, rssi, manuf) = match props {
                    Some(prop) => (
                        prop.local_name.unwrap_or_default(),
                        prop.rssi.unwrap_or(-127),
                        prop.manufacturer_data
                            .values()
                            .next()
                            .cloned()
                            .unwrap_or_default(),
                    ),
                    None => (String::new(), -127, Vec::new()),
                };
                state
                    .lock()
                    .await
                    .peripherals
                    .insert(address.clone(), p);
                emit_event(
                    &sink,
                    "scanResult",
                    json!({
                        "address": address,
                        "name": name,
                        "rssi": rssi,
                        "advertisement": B64.encode(&manuf),
                    }),
                );
            }
            CentralEvent::DeviceConnected(id) => {
                let address = id_to_address(&id);
                let Ok(p) = adapter.peripheral(&id).await else { continue };
                let name = p
                    .properties()
                    .await
                    .ok()
                    .flatten()
                    .and_then(|p| p.local_name)
                    .unwrap_or_default();
                emit_event(
                    &sink,
                    "connected",
                    json!({"address": address, "name": name}),
                );
            }
            CentralEvent::DeviceDisconnected(id) => {
                let address = id_to_address(&id);
                // Tear down any notification listeners tied to this address.
                let mut s = state.lock().await;
                s.notif_listeners
                    .retain(|key, task| {
                        if key.starts_with(&format!("{}|", address)) {
                            task.abort();
                            false
                        } else {
                            true
                        }
                    });
                drop(s);
                emit_event(
                    &sink,
                    "disconnected",
                    json!({"address": address, "reason": ""}),
                );
            }
            _ => {}
        }
    }
}

// ---------------- stdin reader ----------------

async fn stdin_loop(state: SharedState, sink: EventSink) {
    let stdin = tokio::io::stdin();
    let mut reader = BufReader::new(stdin).lines();
    loop {
        match reader.next_line().await {
            Ok(Some(line)) => {
                if line.is_empty() {
                    continue;
                }
                match serde_json::from_str::<Value>(&line) {
                    Ok(cmd) => {
                        let state = state.clone();
                        let sink = sink.clone();
                        tokio::spawn(async move {
                            handle_command(state, sink, cmd).await;
                        });
                    }
                    Err(e) => eprintln!("cn1-ble-helper: malformed JSON: {} ({})", line, e),
                }
            }
            Ok(None) => {
                // stdin EOF — parent process gone; mirror the Swift helper's
                // clean exit so the JVM-side reader thread sees the pipe close.
                std::process::exit(0);
            }
            Err(e) => {
                eprintln!("cn1-ble-helper: stdin read error: {}", e);
                std::process::exit(0);
            }
        }
    }
}

// ---------------- stdout writer ----------------

async fn writer_loop(mut rx: mpsc::UnboundedReceiver<Value>) {
    let stdout = tokio::io::stdout();
    let mut stdout = stdout;
    while let Some(value) = rx.recv().await {
        let mut line = match serde_json::to_string(&value) {
            Ok(s) => s,
            Err(e) => {
                eprintln!("cn1-ble-helper: failed to serialize event: {}", e);
                continue;
            }
        };
        line.push('\n');
        if let Err(e) = stdout.write_all(line.as_bytes()).await {
            eprintln!("cn1-ble-helper: stdout write failed: {}", e);
            return;
        }
        if let Err(e) = stdout.flush().await {
            eprintln!("cn1-ble-helper: stdout flush failed: {}", e);
            return;
        }
    }
}

// ---------------- entry point ----------------

#[tokio::main(flavor = "current_thread")]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    let manager = Manager::new().await?;
    let adapters = manager.adapters().await?;
    let Some(adapter) = adapters.into_iter().next() else {
        // No adapter at all: emit an unsupported state so the Java side
        // doesn't hang waiting on initialize completion, then idle.
        let (tx, rx) = mpsc::unbounded_channel::<Value>();
        emit_event(&tx, "stateChanged", json!({"state": "unsupported"}));
        // Keep the helper alive so the parent's reader thread doesn't see an
        // early EOF that looks like a crash. Drain stdin so a shutdown
        // command or EOF still terminates us cleanly.
        tokio::spawn(writer_loop(rx));
        let stdin = tokio::io::stdin();
        let mut reader = BufReader::new(stdin).lines();
        loop {
            match reader.next_line().await {
                Ok(Some(_)) => continue,
                _ => return Ok(()),
            }
        }
    };

    let (tx, rx) = mpsc::unbounded_channel::<Value>();
    let state = Arc::new(Mutex::new(State {
        adapter: adapter.clone(),
        peripherals: HashMap::new(),
        notif_listeners: HashMap::new(),
        scanning: false,
    }));

    tokio::spawn(writer_loop(rx));
    tokio::spawn(central_event_loop(state.clone(), tx.clone(), adapter.clone()));

    // Emit poweredOn now — btleplug doesn't expose a true state-change stream;
    // having an adapter at all is our "ready" signal, and a missing adapter
    // is the `unsupported` branch above.
    emit_event(&tx, "stateChanged", json!({"state": "poweredOn"}));

    stdin_loop(state, tx).await;
    Ok(())
}

// Some platforms (Linux/macOS) need this import to compile; silence the unused
// warning when only some targets need it.
#[allow(dead_code)]
fn _force_bdaddr_link(_b: BDAddr) {}
