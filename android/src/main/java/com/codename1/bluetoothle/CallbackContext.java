package com.codename1.bluetoothle;

import org.json.JSONObject;

public class CallbackContext {

    private final String action;

    public CallbackContext(String action) {
        this.action = action;
    }

    public void success(JSONObject res) {
        BluetoothCallbackRegistry.onSuccess(action, res.toString());
    }

    public void error(JSONObject res) {
        BluetoothCallbackRegistry.onError(action, res.toString());
    }

    public void sendPluginResult(PluginResult pluginResult) {
        BluetoothCallbackRegistry.sendResult(action, pluginResult.getMessage());
    }
}
