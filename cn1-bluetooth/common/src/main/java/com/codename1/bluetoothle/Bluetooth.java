/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.codename1.bluetoothle;

import com.codename1.cordova.CordovaCallback;
import com.codename1.cordova.Cordova;
import com.codename1.ui.CN;
import com.codename1.ui.events.ActionListener;
import com.codename1.util.JSONUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author Chen
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

    private Cordova plugin;

    public Bluetooth() {
        plugin = new Cordova();
    }

    private boolean getBoolean(Map m, String key) {
        if (m == null) return false;
        Object v = m.get(key);
        if (v instanceof Boolean) return (Boolean)v;
        if (v instanceof String) return "true".equalsIgnoreCase((String)v);
        return false;
    }

    public boolean initialize(boolean request, boolean statusReceiver, String restoreKey) throws IOException {
        HashMap p = new HashMap();
        p.put("request", request);
        p.put("statusReceiver", statusReceiver);
        p.put("restoreKey", restoreKey);
        CordovaCallback callack = new CordovaCallback();
        plugin.execute("initialize", JSONUtils.toJSON(p), callack);
        if ("ios".equalsIgnoreCase(CN.getPlatformName())) {
            // On iOS, it seems that initialize doesn't return.
            // Not sure why, but it seems to work fine in most cases without it.
            // Just assume that it worked.
            return true;
        }

        Map obj = callack.getResponseAndWait(2000);
        if (obj != null) {
            String status = (String) obj.get("status");
            return "enabled".equals(status);
        }
        return false;
    }

    /**
     * Not supported by iOS.  With throw an IOException if called on iOS.
     * @throws IOException 
     */
    public void enable() throws IOException {
        CordovaCallback callack = new CordovaCallback();
        plugin.execute("enable", "", callack);
    }

    /**
     * Not supported by iOS.  With throw an IOException if called on iOS.
     * @throws IOException 
     */
    public void disable() throws IOException {
        CordovaCallback callack = new CordovaCallback();
        plugin.execute("disable", "", callack);
    }

    public void startScan(ActionListener callback, ArrayList services, boolean allowDuplicates, int scanMode, int matchMode, int matchNum, int callbackType) throws IOException {
        HashMap p = new HashMap();
        p.put("allowDuplicates", allowDuplicates);
        p.put("scanMode", scanMode);
        p.put("matchMode", matchMode);
        p.put("matchNum", matchNum);
        p.put("callbackType", callbackType);

        if (services != null && services.size() > 0) {
            p.put("services", services);
        }

        CordovaCallback callack = new CordovaCallback(callback);
        plugin.execute("startScan", JSONUtils.toJSON(p), callack);
    }

    public void stopScan() throws IOException {
        CordovaCallback callack = new CordovaCallback();
        plugin.execute("stopScan", "", callack);
    }

    public void retrieveConnected(ActionListener callback, ArrayList services) throws IOException {
        HashMap j = new HashMap();
        if (services != null && services.size() > 0) {
            j.put("services", services);
        }
        CordovaCallback callack = new CordovaCallback(callback);
        plugin.execute("retrieveConnected", JSONUtils.toJSON(j), callack);
    }

    public void connect(ActionListener callback, String address) throws IOException {
        HashMap p = new HashMap();
        p.put("address", address);

        CordovaCallback callack = new CordovaCallback(callback);
        plugin.execute("connect", JSONUtils.toJSON(p), callack);
    }

    public void reconnect(ActionListener callback, String address) throws IOException {
        HashMap p = new HashMap();
        p.put("address", address);

        CordovaCallback callack = new CordovaCallback(callback);
        plugin.execute("reconnect", JSONUtils.toJSON(p), callack);
    }

    public void disconnect(String address) throws IOException {
        HashMap p = new HashMap();
        p.put("address", address);

        CordovaCallback callack = new CordovaCallback();
        plugin.execute("disconnect", JSONUtils.toJSON(p), callack);
    }

    public void close(String address) throws IOException {
        HashMap p = new HashMap();
        p.put("address", address);

        CordovaCallback callack = new CordovaCallback();
        plugin.execute("close", JSONUtils.toJSON(p), callack);
    }

    /**
     * Not currently supported on iOS.  Currently does nothing if called on iOS.
     * @param callback
     * @param address
     * @throws IOException 
     */
    public void discover(ActionListener callback, String address) throws IOException {
        HashMap p = new HashMap();
        p.put("address", address);

        CordovaCallback callack = new CordovaCallback(callback);
        plugin.execute("discover", JSONUtils.toJSON(p), callack);
    }

    public void services(ActionListener callback, String address, ArrayList services) throws IOException {
        HashMap p = new HashMap();
        p.put("address", address);
        if (services != null && services.size() > 0) {
            p.put("services", services);
        }

        CordovaCallback callack = new CordovaCallback(callback);
        plugin.execute("services", JSONUtils.toJSON(p), callack);
    }

    public void characteristics(ActionListener callback, String address, String service, ArrayList characteristics) throws IOException {
        HashMap p = new HashMap();
        p.put("address", address);
        p.put("service", service);

        if (characteristics != null) {
            p.put("characteristics", characteristics);
        }

        CordovaCallback callack = new CordovaCallback(callback);
        plugin.execute("characteristics", JSONUtils.toJSON(p), callack);
    }

    public void descriptors(ActionListener callback, String address, String service, String characteristic) throws IOException {
        HashMap p = new HashMap();
        p.put("address", address);
        p.put("service", service);
        p.put("characteristic", characteristic);

        CordovaCallback callack = new CordovaCallback(callback);
        plugin.execute("descriptors", JSONUtils.toJSON(p), callack);
    }

    public void read(ActionListener callback, String address, String service, String characteristic) throws IOException {
        HashMap p = new HashMap();
        p.put("address", address);
        p.put("service", service);
        p.put("characteristic", characteristic);

        CordovaCallback callack = new CordovaCallback(callback);
        plugin.execute("read", JSONUtils.toJSON(p), callack);
    }

    public void subscribe(ActionListener callback, String address, String service, String characteristic) throws IOException {
        HashMap p = new HashMap();
        p.put("address", address);
        p.put("service", service);
        p.put("characteristic", characteristic);

        CordovaCallback callack = new CordovaCallback(callback);
        plugin.execute("subscribe", JSONUtils.toJSON(p), callack);
    }

    public void unsubscribe(ActionListener callback, String address, String service, String characteristic) throws IOException {
        HashMap p = new HashMap();
        p.put("address", address);
        p.put("service", service);
        p.put("characteristic", characteristic);

        CordovaCallback callack = new CordovaCallback(callback);
        plugin.execute("unsubscribe", JSONUtils.toJSON(p), callack);
    }

    public void write(ActionListener callback, String address, String service, String characteristic, String value, boolean noResponse) throws IOException {
        HashMap p = new HashMap();
        p.put("address", address);
        p.put("service", service);
        p.put("characteristic", characteristic);
        p.put("value", value);
        if (noResponse) {
            p.put("type", "noResponse");
        }

        CordovaCallback callack = new CordovaCallback(callback);
        plugin.execute("write", JSONUtils.toJSON(p), callack);
    }

    public void writeQ(ActionListener callback, String address, String service, String characteristic, String value, boolean noResponse) throws IOException {
        HashMap p = new HashMap();
        p.put("address", address);
        p.put("service", service);
        p.put("characteristic", characteristic);
        p.put("value", value);
        if (noResponse) {
            p.put("type", "noResponse");
        }

        CordovaCallback callack = new CordovaCallback(callback);
        plugin.execute("writeQ", JSONUtils.toJSON(p), callack);
    }

    public void readDescriptor(ActionListener callback, String address, String service, String characteristic, String descriptor) throws IOException {
        HashMap p = new HashMap();
        p.put("address", address);
        p.put("service", service);
        p.put("characteristic", characteristic);
        p.put("descriptor", descriptor);

        CordovaCallback callack = new CordovaCallback(callback);
        plugin.execute("readDescriptor", JSONUtils.toJSON(p), callack);
    }

    public void writeDescriptor(ActionListener callback, String address, String service, String characteristic, String descriptor, String value) throws IOException {
        HashMap p = new HashMap();
        p.put("address", address);
        p.put("service", service);
        p.put("characteristic", characteristic);
        p.put("descriptor", descriptor);
        p.put("value", value);

        CordovaCallback callack = new CordovaCallback(callback);
        plugin.execute("writeDescriptor", JSONUtils.toJSON(p), callack);
    }

    public void rssi(ActionListener callback, String address) throws IOException {
        HashMap p = new HashMap();
        p.put("address", address);

        CordovaCallback callack = new CordovaCallback(callback);
        plugin.execute("rssi", JSONUtils.toJSON(p), callack);
    }

    /**
     * Not supported by iOS.  With throw an IOException if called on iOS.
     * @param callback
     * @param address
     * @param mtu
     * @throws IOException 
     */
    public void mtu(ActionListener callback, String address, int mtu) throws IOException {
        HashMap p = new HashMap();
        p.put("address", address);
        p.put("mtu", mtu);

        CordovaCallback callack = new CordovaCallback(callback);
        plugin.execute("mtu", JSONUtils.toJSON(p), callack);
    }

    /**
     * Not supported by iOS.  With throw an IOException if called on iOS.
     * 
     * @param callback
     * @param address
     * @param priority
     * @throws IOException 
     */
    public void requestConnectionPriority(ActionListener callback, String address, int priority) throws IOException {
        HashMap p = new HashMap();
        p.put("address", address);
        if (priority == CONNECTION_PRIORITY_LOW) {
            p.put("connectionPriority", "low");
        } else if (priority == CONNECTION_PRIORITY_BALANCED) {
            p.put("connectionPriority", "balanced");
        } else if (priority == CONNECTION_PRIORITY_HIGH) {
            p.put("connectionPriority", "high");
        }

        CordovaCallback callack = new CordovaCallback(callback);
        plugin.execute("requestConnectionPriority", JSONUtils.toJSON(p), callack);
    }

    public boolean isInitialized() throws IOException {
        CordovaCallback callack = new CordovaCallback();
        plugin.execute("isInitialized", "", callack);
        Map response = callack.getResponseAndWait(500);
        return getBoolean(response, "isInitialized");
    }

    public boolean isEnabled() throws IOException {
        CordovaCallback callack = new CordovaCallback();
        plugin.execute("isEnabled", "", callack);
        System.out.println("Waiting for response in isEnabled()");
        Map response = callack.getResponseAndWait(500);
        return getBoolean(response, "isEnabled");
    }

    public boolean isScanning() throws IOException {
        CordovaCallback callack = new CordovaCallback();
        plugin.execute("isScanning", "", callack);
        Map response = callack.getResponseAndWait(500);
        return getBoolean(response, "isScanning");
    }

    public boolean wasConnected(String address) throws IOException {
        HashMap p = new HashMap();
        p.put("address", address);

        CordovaCallback callack = new CordovaCallback();
        plugin.execute("wasConnected", JSONUtils.toJSON(p), callack);
        Map response = callack.getResponseAndWait(500);
        return getBoolean(response, "wasConnected");
    }

    public boolean isConnected(String address) throws IOException {
        HashMap p = new HashMap();
        p.put("address", address);

        CordovaCallback callack = new CordovaCallback();
        plugin.execute("isConnected", JSONUtils.toJSON(p), callack);
        Map response = callack.getResponseAndWait(500);
        return getBoolean(response, "isConnected");
    }

    public boolean isDiscovered(String address) throws IOException {
        HashMap p = new HashMap();
        p.put("address", address);

        CordovaCallback callack = new CordovaCallback();
        plugin.execute("isDiscovered", JSONUtils.toJSON(p), callack);
        Map response = callack.getResponseAndWait(500);
        return getBoolean(response, "isDiscovered");
    }

    /**
     * Not supported on iOS.  Will throw IOException if called on iOS.
     * @return
     * @throws IOException 
     */
    public boolean hasPermission() throws IOException {

        CordovaCallback callack = new CordovaCallback();
        plugin.execute("hasPermission", "", callack);
        Map response = callack.getResponseAndWait(500);
        return getBoolean(response, "hasPermission");
    }

    /**
     * Not supported on iOS.  Will throw IOException if called on iOS.
     * @return
     * @throws IOException 
     */
    public boolean requestPermission() throws IOException {

        CordovaCallback callack = new CordovaCallback();
        plugin.execute("requestPermission", "", callack);
        Map response = callack.getResponseAndWait(500);
        return getBoolean(response, "requestPermission");
    }

    /**
     * Not supported on iOS. Will throw IOException if called on iOS.
     * @return
     * @throws IOException 
     */
    public boolean isLocationEnabled() throws IOException {

        CordovaCallback callack = new CordovaCallback();
        plugin.execute("isLocationEnabled", "", callack);
        Map response = callack.getResponseAndWait(500);
        return getBoolean(response, "isLocationEnabled");
    }

    /**
     * Not supported on iOS. Will throw IOException if called on iOS.
     * @return
     * @throws IOException 
     */
    public boolean requestLocation() throws IOException {

        CordovaCallback callack = new CordovaCallback();
        plugin.execute("requestLocation", "", callack);
        Map response = callack.getResponseAndWait(500);
        return getBoolean(response, "requestLocation");
    }

}
