package com.codename1.bluetoothle;

import java.util.HashMap;

public class BluetoothCallbackRegistry {

    private static final HashMap<String, BluetoothCallback> callbacks = new HashMap<String, BluetoothCallback>();

    public static void setMethodCallback(String method, BluetoothCallback callback) {
        callbacks.put(method, callback);
    }

    public static void removeMethodCallback(String method) {
        callbacks.remove(method);
    }

    public static void sendResult(String method, String result, boolean success, boolean keepCallback) {
        BluetoothCallback cb = callbacks.get(method);
        if (cb != null) {
            if (success) {
                cb.onSuccess(result);
            } else {
                cb.onError(result);
            }
            if (!keepCallback) {
                callbacks.remove(method);
            }
        }
    }

    public static void sendResult(String method, String result, boolean success) {
        sendResult(method, result, success, false);
    }

    public static void onSuccess(String method, String result) {
        sendResult(method, result, true);
    }

    public static void onError(String method, String result) {
        sendResult(method, result, false);
    }

    public static void sendResult(String method, String result) {
        sendResult(method, result, true);
    }
}
