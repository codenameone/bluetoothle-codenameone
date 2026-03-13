package com.codename1.bluetoothle;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class BluetoothNativeBridgeImpl implements BluetoothNativeBridge {

    private final BluetoothLePlugin plugin;

    public BluetoothNativeBridgeImpl() {
        plugin = new BluetoothLePlugin();
    }

    private boolean execute(String action, JSONObject args) {
        return plugin.execute(action, args == null ? "" : args.toString());
    }

    private JSONArray parseArray(String jsonArray) throws JSONException {
        if (jsonArray == null || jsonArray.length() == 0) {
            return null;
        }
        return new JSONArray(jsonArray);
    }

    @Override
    public boolean initialize(boolean request, boolean statusReceiver, String restoreKey) {
        try {
            JSONObject args = new JSONObject();
            args.put("request", request);
            args.put("statusReceiver", statusReceiver);
            args.put("restoreKey", restoreKey);
            return execute("initialize", args);
        } catch (JSONException ex) {
            return false;
        }
    }

    @Override
    public boolean enable() {
        return execute("enable", null);
    }

    @Override
    public boolean disable() {
        return execute("disable", null);
    }

    @Override
    public boolean startScan(String servicesJson, boolean allowDuplicates, int scanMode, int matchMode, int matchNum, int callbackType) {
        try {
            JSONObject args = new JSONObject();
            args.put("allowDuplicates", allowDuplicates);
            args.put("scanMode", scanMode);
            args.put("matchMode", matchMode);
            args.put("matchNum", matchNum);
            args.put("callbackType", callbackType);
            JSONArray services = parseArray(servicesJson);
            if (services != null && services.length() > 0) {
                args.put("services", services);
            }
            return execute("startScan", args);
        } catch (JSONException ex) {
            return false;
        }
    }

    @Override
    public boolean stopScan() {
        return execute("stopScan", null);
    }

    @Override
    public boolean retrieveConnected(String servicesJson) {
        try {
            JSONObject args = new JSONObject();
            JSONArray services = parseArray(servicesJson);
            if (services != null && services.length() > 0) {
                args.put("services", services);
            }
            return execute("retrieveConnected", args);
        } catch (JSONException ex) {
            return false;
        }
    }

    @Override
    public boolean connect(String address) {
        try {
            JSONObject args = new JSONObject();
            args.put("address", address);
            return execute("connect", args);
        } catch (JSONException ex) {
            return false;
        }
    }

    @Override
    public boolean reconnect(String address) {
        try {
            JSONObject args = new JSONObject();
            args.put("address", address);
            return execute("reconnect", args);
        } catch (JSONException ex) {
            return false;
        }
    }

    @Override
    public boolean disconnect(String address) {
        try {
            JSONObject args = new JSONObject();
            args.put("address", address);
            return execute("disconnect", args);
        } catch (JSONException ex) {
            return false;
        }
    }

    @Override
    public boolean close(String address) {
        try {
            JSONObject args = new JSONObject();
            args.put("address", address);
            return execute("close", args);
        } catch (JSONException ex) {
            return false;
        }
    }

    @Override
    public boolean discover(String address) {
        try {
            JSONObject args = new JSONObject();
            args.put("address", address);
            return execute("discover", args);
        } catch (JSONException ex) {
            return false;
        }
    }

    @Override
    public boolean services(String address, String servicesJson) {
        try {
            JSONObject args = new JSONObject();
            args.put("address", address);
            JSONArray services = parseArray(servicesJson);
            if (services != null && services.length() > 0) {
                args.put("services", services);
            }
            return execute("services", args);
        } catch (JSONException ex) {
            return false;
        }
    }

    @Override
    public boolean characteristics(String address, String service, String characteristicsJson) {
        try {
            JSONObject args = new JSONObject();
            args.put("address", address);
            args.put("service", service);
            JSONArray characteristics = parseArray(characteristicsJson);
            if (characteristics != null && characteristics.length() > 0) {
                args.put("characteristics", characteristics);
            }
            return execute("characteristics", args);
        } catch (JSONException ex) {
            return false;
        }
    }

    @Override
    public boolean descriptors(String address, String service, String characteristic) {
        try {
            JSONObject args = new JSONObject();
            args.put("address", address);
            args.put("service", service);
            args.put("characteristic", characteristic);
            return execute("descriptors", args);
        } catch (JSONException ex) {
            return false;
        }
    }

    @Override
    public boolean read(String address, String service, String characteristic) {
        try {
            JSONObject args = new JSONObject();
            args.put("address", address);
            args.put("service", service);
            args.put("characteristic", characteristic);
            return execute("read", args);
        } catch (JSONException ex) {
            return false;
        }
    }

    @Override
    public boolean subscribe(String address, String service, String characteristic) {
        try {
            JSONObject args = new JSONObject();
            args.put("address", address);
            args.put("service", service);
            args.put("characteristic", characteristic);
            return execute("subscribe", args);
        } catch (JSONException ex) {
            return false;
        }
    }

    @Override
    public boolean unsubscribe(String address, String service, String characteristic) {
        try {
            JSONObject args = new JSONObject();
            args.put("address", address);
            args.put("service", service);
            args.put("characteristic", characteristic);
            return execute("unsubscribe", args);
        } catch (JSONException ex) {
            return false;
        }
    }

    @Override
    public boolean write(String address, String service, String characteristic, String value, boolean noResponse) {
        try {
            JSONObject args = new JSONObject();
            args.put("address", address);
            args.put("service", service);
            args.put("characteristic", characteristic);
            args.put("value", value);
            if (noResponse) {
                args.put("type", "noResponse");
            }
            return execute("write", args);
        } catch (JSONException ex) {
            return false;
        }
    }

    @Override
    public boolean writeQ(String address, String service, String characteristic, String value, boolean noResponse) {
        try {
            JSONObject args = new JSONObject();
            args.put("address", address);
            args.put("service", service);
            args.put("characteristic", characteristic);
            args.put("value", value);
            if (noResponse) {
                args.put("type", "noResponse");
            }
            return execute("writeQ", args);
        } catch (JSONException ex) {
            return false;
        }
    }

    @Override
    public boolean readDescriptor(String address, String service, String characteristic, String descriptor) {
        try {
            JSONObject args = new JSONObject();
            args.put("address", address);
            args.put("service", service);
            args.put("characteristic", characteristic);
            args.put("descriptor", descriptor);
            return execute("readDescriptor", args);
        } catch (JSONException ex) {
            return false;
        }
    }

    @Override
    public boolean writeDescriptor(String address, String service, String characteristic, String descriptor, String value) {
        try {
            JSONObject args = new JSONObject();
            args.put("address", address);
            args.put("service", service);
            args.put("characteristic", characteristic);
            args.put("descriptor", descriptor);
            args.put("value", value);
            return execute("writeDescriptor", args);
        } catch (JSONException ex) {
            return false;
        }
    }

    @Override
    public boolean rssi(String address) {
        try {
            JSONObject args = new JSONObject();
            args.put("address", address);
            return execute("rssi", args);
        } catch (JSONException ex) {
            return false;
        }
    }

    @Override
    public boolean mtu(String address, int mtu) {
        try {
            JSONObject args = new JSONObject();
            args.put("address", address);
            args.put("mtu", mtu);
            return execute("mtu", args);
        } catch (JSONException ex) {
            return false;
        }
    }

    @Override
    public boolean requestConnectionPriority(String address, String priority) {
        try {
            JSONObject args = new JSONObject();
            args.put("address", address);
            if (priority != null && priority.length() > 0) {
                args.put("connectionPriority", priority);
            }
            return execute("requestConnectionPriority", args);
        } catch (JSONException ex) {
            return false;
        }
    }

    @Override
    public boolean isInitialized() {
        return execute("isInitialized", null);
    }

    @Override
    public boolean isEnabled() {
        return execute("isEnabled", null);
    }

    @Override
    public boolean isScanning() {
        return execute("isScanning", null);
    }

    @Override
    public boolean wasConnected(String address) {
        try {
            JSONObject args = new JSONObject();
            args.put("address", address);
            return execute("wasConnected", args);
        } catch (JSONException ex) {
            return false;
        }
    }

    @Override
    public boolean isConnected(String address) {
        try {
            JSONObject args = new JSONObject();
            args.put("address", address);
            return execute("isConnected", args);
        } catch (JSONException ex) {
            return false;
        }
    }

    @Override
    public boolean isDiscovered(String address) {
        try {
            JSONObject args = new JSONObject();
            args.put("address", address);
            return execute("isDiscovered", args);
        } catch (JSONException ex) {
            return false;
        }
    }

    @Override
    public boolean hasPermission() {
        return execute("hasPermission", null);
    }

    @Override
    public boolean requestPermission() {
        return execute("requestPermission", null);
    }

    @Override
    public boolean isLocationEnabled() {
        return execute("isLocationEnabled", null);
    }

    @Override
    public boolean requestLocation() {
        return execute("requestLocation", null);
    }

    @Override
    public boolean isSupported() {
        return true;
    }
}
