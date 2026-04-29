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
        // Honor pluginResult.getKeepCallback() and the status. The
        // 2-argument BluetoothCallbackRegistry.sendResult defaults to
        // success=true, keepCallback=false, which silently drops the
        // callback after the first event delivered through this path.
        // That broke every operation that legitimately fires multiple
        // events through the same callback (startScan -> scanStarted
        // followed by scanResult, subscribe -> subscribed followed by
        // subscribedResult, connect -> connected followed by
        // disconnected, etc): only the first event reached the user's
        // listener, all subsequent events found the callback already
        // removed from the registry and went nowhere. Found by the
        // device-test layer running scan against an in-process
        // peripheral on a real Android device.
        boolean success = pluginResult.getStatus() == PluginResult.Status.OK.ordinal();
        boolean keepCallback = pluginResult.getKeepCallback();
        BluetoothCallbackRegistry.sendResult(action, pluginResult.getMessage(), success, keepCallback);
    }
}
