package com.codename1.bluetoothle;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/// Mutable shared state for the JavaSE Bluetooth simulator. Owned by
/// [BluetoothSimulator] and consumed by [BluetoothNativeBridgeImpl].
///
/// Synchronization: all mutations and reads happen under the instance monitor.
/// Callbacks are dispatched on a background scheduler thread (matching how
/// real native bridges behave) so client code is exercised exactly as it
/// would be on a device.
final class SimulatorState {

    private final Map<String, SimulatedPeripheral> peripherals = new LinkedHashMap<>();
    private final Map<String, ConnectionState> connections = new HashMap<>();
    private final Map<String, Deque<Failure>> failures = new HashMap<>();

    private boolean initialized;
    private boolean enabled;
    private boolean scanning;
    private boolean hasPermission = true;
    private boolean locationEnabled = true;
    private int callbackLatencyMillis = 10;

    static final class Failure {
        final String error;
        final String message;

        Failure(String error, String message) {
            this.error = error;
            this.message = message;
        }
    }

    static final class ConnectionState {
        boolean connected;
        boolean discovered;
        boolean wasConnected;
        final Set<String> subscriptions = new HashSet<>();
    }

    synchronized void addPeripheral(SimulatedPeripheral peripheral) {
        if (peripheral == null) {
            throw new IllegalArgumentException("peripheral is required");
        }
        peripherals.put(peripheral.getAddress(), peripheral);
    }

    synchronized void removePeripheral(String address) {
        if (address != null) {
            peripherals.remove(address);
            connections.remove(address);
        }
    }

    synchronized void clearPeripherals() {
        peripherals.clear();
        connections.clear();
    }

    synchronized SimulatedPeripheral getPeripheral(String address) {
        return address == null ? null : peripherals.get(address);
    }

    synchronized Collection<SimulatedPeripheral> snapshotPeripherals() {
        return new java.util.ArrayList<>(peripherals.values());
    }

    synchronized ConnectionState connectionFor(String address, boolean create) {
        if (address == null) {
            return null;
        }
        ConnectionState cs = connections.get(address);
        if (cs == null && create) {
            cs = new ConnectionState();
            connections.put(address, cs);
        }
        return cs;
    }

    synchronized void setInitialized(boolean v) { initialized = v; }
    synchronized boolean isInitialized() { return initialized; }
    synchronized void setEnabled(boolean v) { enabled = v; }
    synchronized boolean isEnabled() { return enabled; }
    synchronized void setScanning(boolean v) { scanning = v; }
    synchronized boolean isScanning() { return scanning; }
    synchronized void setHasPermission(boolean v) { hasPermission = v; }
    synchronized boolean hasPermission() { return hasPermission; }
    synchronized void setLocationEnabled(boolean v) { locationEnabled = v; }
    synchronized boolean isLocationEnabled() { return locationEnabled; }

    synchronized int getCallbackLatencyMillis() { return callbackLatencyMillis; }
    synchronized void setCallbackLatencyMillis(int millis) {
        callbackLatencyMillis = Math.max(0, millis);
    }

    synchronized void queueFailure(String operation, String error, String message) {
        if (operation == null) {
            return;
        }
        Deque<Failure> q = failures.get(operation);
        if (q == null) {
            q = new ArrayDeque<>();
            failures.put(operation, q);
        }
        q.addLast(new Failure(error == null ? operation : error, message == null ? "" : message));
    }

    synchronized Failure consumeFailure(String operation) {
        Deque<Failure> q = failures.get(operation);
        if (q == null || q.isEmpty()) {
            return null;
        }
        Failure f = q.pollFirst();
        if (q.isEmpty()) {
            failures.remove(operation);
        }
        return f;
    }

    void schedule(Runnable task) {
        scheduleAfter(0, task);
    }

    void scheduleAfter(final int extraMillis, final Runnable task) {
        final int latency = getCallbackLatencyMillis() + Math.max(0, extraMillis);
        Thread t = new Thread(() -> {
            if (latency > 0) {
                try {
                    Thread.sleep(latency);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            try {
                task.run();
            } catch (Throwable err) {
                err.printStackTrace();
            }
        }, "bluetoothle-sim-dispatch");
        t.setDaemon(true);
        t.start();
    }

    /// Runs the task on the calling thread immediately. Used for state queries
    /// (is*, hasPermission, etc) where the Android plugin also returns
    /// synchronously and the test thread relies on the response being ready
    /// before the bridge call returns.
    void runSync(Runnable task) {
        task.run();
    }

    synchronized void pushNotification(String address, String service, String characteristic, byte[] value) {
        ConnectionState cs = connections.get(address);
        if (cs == null || !cs.connected) {
            return;
        }
        String key = subscriptionKey(service, characteristic);
        if (!cs.subscriptions.contains(key)) {
            return;
        }
        SimulatedPeripheral p = peripherals.get(address);
        if (p == null) {
            return;
        }
        SimulatedService s = p.findService(service);
        if (s == null) {
            return;
        }
        SimulatedCharacteristic c = s.findCharacteristic(characteristic);
        if (c == null) {
            return;
        }
        c.setValueInternal(value);
        final byte[] copy = value == null ? new byte[0] : value.clone();
        schedule(() -> {
            String json = JsonBuilder.start()
                    .put("status", "subscribedResult")
                    .put("address", address)
                    .put("name", p.getName())
                    .put("service", service)
                    .put("characteristic", characteristic)
                    .put("value", Base64Util.encode(copy))
                    .end();
            BluetoothCallbackRegistry.sendResult("subscribe", json, true, true);
        });
    }

    synchronized void disconnectFromRemote(String address) {
        ConnectionState cs = connections.get(address);
        if (cs == null || !cs.connected) {
            return;
        }
        cs.connected = false;
        cs.subscriptions.clear();
        SimulatedPeripheral p = peripherals.get(address);
        if (p == null) {
            return;
        }
        schedule(() -> {
            String json = JsonBuilder.start()
                    .put("status", "disconnected")
                    .put("address", address)
                    .put("name", p.getName())
                    .end();
            BluetoothCallbackRegistry.sendResult("connect", json, true, true);
        });
    }

    static String subscriptionKey(String service, String characteristic) {
        return (service == null ? "" : service.toLowerCase())
                + "|" + (characteristic == null ? "" : characteristic.toLowerCase());
    }

    /// Snapshot of subscribers (used only by the bridge during cleanup).
    synchronized Map<String, ConnectionState> snapshotConnections() {
        return Collections.unmodifiableMap(new HashMap<>(connections));
    }
}
