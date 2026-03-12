/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.codename1.bluetoothle;

import com.codename1.ui.CN;
import com.codename1.ui.events.ActionListener;
import com.codename1.util.JSONUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

/**
 * Bluetooth LE API.
 *
 * Public API is intentionally stable while native internals evolve.
 */
public class Bluetooth {

    public static final int SCAN_MODE_BALANCED = 1;
    public static final int SCAN_MODE_LOW_LATENCY = 2;
    public static final int SCAN_MODE_LOW_POWER = 0;
    public static final int SCAN_MODE_OPPORTUNISTIC = -1;

    public static final int MATCH_MODE_AGGRESSIVE = 1;
    public static final int MATCH_MODE_STICKY = 2;

    public static final int MATCH_NUM_ONE_ADVERTISEMENT = 1;
    public static final int MATCH_NUM_FEW_ADVERTISEMENT = 2;
    public static final int MATCH_NUM_MAX_ADVERTISEMENT = 3;

    public static final int CALLBACK_TYPE_ALL_MATCHES = 1;
    public static final int CALLBACK_TYPE_FIRST_MATCH = 2;
    public static final int CALLBACK_TYPE_MATCH_LOST = 4;

    public static final int CONNECTION_PRIORITY_LOW = 0;
    public static final int CONNECTION_PRIORITY_BALANCED = 1;
    public static final int CONNECTION_PRIORITY_HIGH = 2;

    private final BluetoothBridge plugin;

    public Bluetooth() {
        plugin = new BluetoothBridge();
    }

    private boolean getBoolean(Map<?, ?> m, String key) {
        if (m == null) {
            return false;
        }
        Object v = m.get(key);
        if (v instanceof Boolean) {
            return (Boolean) v;
        }
        if (v instanceof String) {
            return "true".equalsIgnoreCase((String) v);
        }
        return false;
    }

    private String toJsonArray(ArrayList list) {
        if (list == null || list.isEmpty()) {
            return "";
        }
        return JSONUtils.toJSON(list);
    }

    public boolean initialize(boolean request, boolean statusReceiver, String restoreKey) throws IOException {
        BluetoothCallback callback = new BluetoothCallback();
        plugin.initialize(callback, request, statusReceiver, restoreKey);
        if ("ios".equalsIgnoreCase(CN.getPlatformName())) {
            return true;
        }

        Map response = callback.getResponseAndWait(2000);
        if (response != null) {
            String status = (String) response.get("status");
            return "enabled".equals(status);
        }
        return false;
    }

    public void enable() throws IOException {
        BluetoothCallback callback = new BluetoothCallback();
        plugin.enable(callback);
    }

    public void disable() throws IOException {
        BluetoothCallback callback = new BluetoothCallback();
        plugin.disable(callback);
    }

    public void startScan(ActionListener listener, ArrayList services, boolean allowDuplicates, int scanMode, int matchMode, int matchNum, int callbackType) throws IOException {
        BluetoothCallback callback = new BluetoothCallback(listener);
        plugin.startScan(callback, toJsonArray(services), allowDuplicates, scanMode, matchMode, matchNum, callbackType);
    }

    public void stopScan() throws IOException {
        BluetoothCallback callback = new BluetoothCallback();
        plugin.stopScan(callback);
    }

    public void retrieveConnected(ActionListener listener, ArrayList services) throws IOException {
        BluetoothCallback callback = new BluetoothCallback(listener);
        plugin.retrieveConnected(callback, toJsonArray(services));
    }

    public void connect(ActionListener listener, String address) throws IOException {
        BluetoothCallback callback = new BluetoothCallback(listener);
        plugin.connect(callback, address);
    }

    public void reconnect(ActionListener listener, String address) throws IOException {
        BluetoothCallback callback = new BluetoothCallback(listener);
        plugin.reconnect(callback, address);
    }

    public void disconnect(String address) throws IOException {
        BluetoothCallback callback = new BluetoothCallback();
        plugin.disconnect(callback, address);
    }

    public void close(String address) throws IOException {
        BluetoothCallback callback = new BluetoothCallback();
        plugin.close(callback, address);
    }

    public void discover(ActionListener listener, String address) throws IOException {
        BluetoothCallback callback = new BluetoothCallback(listener);
        plugin.discover(callback, address);
    }

    public void services(ActionListener listener, String address, ArrayList services) throws IOException {
        BluetoothCallback callback = new BluetoothCallback(listener);
        plugin.services(callback, address, toJsonArray(services));
    }

    public void characteristics(ActionListener listener, String address, String service, ArrayList characteristics) throws IOException {
        BluetoothCallback callback = new BluetoothCallback(listener);
        plugin.characteristics(callback, address, service, toJsonArray(characteristics));
    }

    public void descriptors(ActionListener listener, String address, String service, String characteristic) throws IOException {
        BluetoothCallback callback = new BluetoothCallback(listener);
        plugin.descriptors(callback, address, service, characteristic);
    }

    public void read(ActionListener listener, String address, String service, String characteristic) throws IOException {
        BluetoothCallback callback = new BluetoothCallback(listener);
        plugin.read(callback, address, service, characteristic);
    }

    public void subscribe(ActionListener listener, String address, String service, String characteristic) throws IOException {
        BluetoothCallback callback = new BluetoothCallback(listener);
        plugin.subscribe(callback, address, service, characteristic);
    }

    public void unsubscribe(ActionListener listener, String address, String service, String characteristic) throws IOException {
        BluetoothCallback callback = new BluetoothCallback(listener);
        plugin.unsubscribe(callback, address, service, characteristic);
    }

    public void write(ActionListener listener, String address, String service, String characteristic, String value, boolean noResponse) throws IOException {
        BluetoothCallback callback = new BluetoothCallback(listener);
        plugin.write(callback, address, service, characteristic, value, noResponse);
    }

    public void writeQ(ActionListener listener, String address, String service, String characteristic, String value, boolean noResponse) throws IOException {
        BluetoothCallback callback = new BluetoothCallback(listener);
        plugin.writeQ(callback, address, service, characteristic, value, noResponse);
    }

    public void readDescriptor(ActionListener listener, String address, String service, String characteristic, String descriptor) throws IOException {
        BluetoothCallback callback = new BluetoothCallback(listener);
        plugin.readDescriptor(callback, address, service, characteristic, descriptor);
    }

    public void writeDescriptor(ActionListener listener, String address, String service, String characteristic, String descriptor, String value) throws IOException {
        BluetoothCallback callback = new BluetoothCallback(listener);
        plugin.writeDescriptor(callback, address, service, characteristic, descriptor, value);
    }

    public void rssi(ActionListener listener, String address) throws IOException {
        BluetoothCallback callback = new BluetoothCallback(listener);
        plugin.rssi(callback, address);
    }

    public void mtu(ActionListener listener, String address, int mtu) throws IOException {
        BluetoothCallback callback = new BluetoothCallback(listener);
        plugin.mtu(callback, address, mtu);
    }

    public void requestConnectionPriority(ActionListener listener, String address, int priority) throws IOException {
        String priorityStr = "";
        if (priority == CONNECTION_PRIORITY_LOW) {
            priorityStr = "low";
        } else if (priority == CONNECTION_PRIORITY_BALANCED) {
            priorityStr = "balanced";
        } else if (priority == CONNECTION_PRIORITY_HIGH) {
            priorityStr = "high";
        }

        BluetoothCallback callback = new BluetoothCallback(listener);
        plugin.requestConnectionPriority(callback, address, priorityStr);
    }

    public boolean isInitialized() throws IOException {
        BluetoothCallback callback = new BluetoothCallback();
        plugin.isInitialized(callback);
        Map response = callback.getResponseAndWait(500);
        return getBoolean(response, "isInitialized");
    }

    public boolean isEnabled() throws IOException {
        BluetoothCallback callback = new BluetoothCallback();
        plugin.isEnabled(callback);
        Map response = callback.getResponseAndWait(500);
        return getBoolean(response, "isEnabled");
    }

    public boolean isScanning() throws IOException {
        BluetoothCallback callback = new BluetoothCallback();
        plugin.isScanning(callback);
        Map response = callback.getResponseAndWait(500);
        return getBoolean(response, "isScanning");
    }

    public boolean wasConnected(String address) throws IOException {
        BluetoothCallback callback = new BluetoothCallback();
        plugin.wasConnected(callback, address);
        Map response = callback.getResponseAndWait(500);
        return getBoolean(response, "wasConnected");
    }

    public boolean isConnected(String address) throws IOException {
        BluetoothCallback callback = new BluetoothCallback();
        plugin.isConnected(callback, address);
        Map response = callback.getResponseAndWait(500);
        return getBoolean(response, "isConnected");
    }

    public boolean isDiscovered(String address) throws IOException {
        BluetoothCallback callback = new BluetoothCallback();
        plugin.isDiscovered(callback, address);
        Map response = callback.getResponseAndWait(500);
        return getBoolean(response, "isDiscovered");
    }

    public boolean hasPermission() throws IOException {
        BluetoothCallback callback = new BluetoothCallback();
        plugin.hasPermission(callback);
        Map response = callback.getResponseAndWait(500);
        return getBoolean(response, "hasPermission");
    }

    public boolean requestPermission() throws IOException {
        BluetoothCallback callback = new BluetoothCallback();
        plugin.requestPermission(callback);
        Map response = callback.getResponseAndWait(500);
        return getBoolean(response, "requestPermission");
    }

    public boolean isLocationEnabled() throws IOException {
        BluetoothCallback callback = new BluetoothCallback();
        plugin.isLocationEnabled(callback);
        Map response = callback.getResponseAndWait(500);
        return getBoolean(response, "isLocationEnabled");
    }

    public boolean requestLocation() throws IOException {
        BluetoothCallback callback = new BluetoothCallback();
        plugin.requestLocation(callback);
        Map response = callback.getResponseAndWait(500);
        return getBoolean(response, "requestLocation");
    }
}
