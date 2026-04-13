/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.codename1.bluetoothle;

import com.codename1.ui.CN;
import com.codename1.ui.events.ActionListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
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
        return toJSON(list);
    }

    private String toJSON(Object o) {
        if (o == null) {
            return "null";
        }
        if (o instanceof String) {
            return "\"" + escapeString((String)o) + "\"";
        }
        if (o instanceof Number || o instanceof Boolean) {
            return String.valueOf(o);
        }
        if (o instanceof Map) {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            Map m = (Map) o;
            Iterator it = m.keySet().iterator();
            boolean first = true;
            while (it.hasNext()) {
                if (!first) {
                    sb.append(",");
                }
                Object key = it.next();
                Object val = m.get(key);
                sb.append(toJSON(String.valueOf(key)));
                sb.append(":");
                sb.append(toJSON(val));
                first = false;
            }
            sb.append("}");
            return sb.toString();
        }
        if (o instanceof List) {
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            List l = (List) o;
            boolean first = true;
            for (Object item : l) {
                if (!first) {
                    sb.append(",");
                }
                sb.append(toJSON(item));
                first = false;
            }
            sb.append("]");
            return sb.toString();
        }
        if (o instanceof Object[]) {
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            Object[] l = (Object[]) o;
            boolean first = true;
            for (Object item : l) {
                if (!first) {
                    sb.append(",");
                }
                sb.append(toJSON(item));
                first = false;
            }
            sb.append("]");
            return sb.toString();
        }
        return "\"" + escapeString(String.valueOf(o)) + "\"";
    }

    private String escapeString(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < ' ' || (c >= '\u0080' && c < '\u00a0') || (c >= '\u2000' && c < '\u2100')) {
                        String t = "000" + Integer.toHexString(c);
                        sb.append("\\u" + t.substring(t.length() - 4));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
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
