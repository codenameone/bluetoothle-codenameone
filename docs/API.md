# Bluetooth API Guide

This guide documents the public `com.codename1.bluetoothle.Bluetooth` API and expected call patterns.

## Lifecycle and State

- `initialize(request, statusReceiver, restoreKey)`: Initializes BLE stack and plugin state.
- `isInitialized()`: Returns current initialization state.
- `isEnabled()`: Returns whether Bluetooth is currently enabled.
- `enable()`, `disable()`: Android-only adapter toggles.

## Scanning and Discovery

- `startScan(listener, services, allowDuplicates, scanMode, matchMode, matchNum, callbackType)`: Starts scanning and streams results to `listener`.
- `stopScan()`: Stops active scan.
- `isScanning()`: Returns current scanning state.
- `retrieveConnected(listener, services)`: Returns already-connected peripherals matching services.

## Connection and GATT

- `connect(listener, address)`: Connects and streams connection events.
- `reconnect(listener, address)`: Reconnects to a known device.
- `disconnect(address)`, `close(address)`: Terminates connection state.
- `wasConnected(address)`, `isConnected(address)`: Connection status checks.
- `discover(listener, address)`, `isDiscovered(address)`: Discovery operations/status.
- `services(listener, address, services)`: Service discovery/filter.
- `characteristics(listener, address, service, characteristics)`: Characteristic discovery/filter.
- `descriptors(listener, address, service, characteristic)`: Descriptor discovery.

## Data Operations

- `read(listener, address, service, characteristic)`: Read characteristic value.
- `write(listener, address, service, characteristic, value, noResponse)`: Write characteristic value.
- `writeQ(listener, address, service, characteristic, value, noResponse)`: Queued write variant.
- `subscribe(listener, address, service, characteristic)`: Start notifications/indications.
- `unsubscribe(listener, address, service, characteristic)`: Stop notifications.
- `readDescriptor(listener, address, service, characteristic, descriptor)`
- `writeDescriptor(listener, address, service, characteristic, descriptor, value)`
- `rssi(listener, address)`: Read signal strength.

## Android-Specific Controls

- `mtu(listener, address, mtu)`: Request MTU.
- `requestConnectionPriority(listener, address, priority)`: Request low/balanced/high.
- `hasPermission()`, `requestPermission()`: Bluetooth/location runtime permissions.
- `isLocationEnabled()`, `requestLocation()`: Device location setting checks/requests.

## Callback and Payload Semantics

- Asynchronous methods deliver maps to `ActionListener` via `ActionEvent.getSource()`.
- Boolean state helpers (`isEnabled()`, `isConnected()`, etc.) block briefly and parse callback payload fields.
- Payload keys and status/error strings are preserved for backward compatibility with existing app code.

## Internal Native Contract

Internally, each public operation maps to a dedicated native bridge method (no generic `execute(action, json)` API).
This improves readability and maintainability while preserving existing public behavior.
