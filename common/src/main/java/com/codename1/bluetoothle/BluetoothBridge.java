package com.codename1.bluetoothle;

import com.codename1.system.NativeLookup;
import java.io.IOException;
import java.util.Map;

final class BluetoothBridge {

    private final BluetoothNativeBridge bridge;

    private interface BridgeCall {
        boolean invoke();
    }

    BluetoothBridge() {
        bridge = NativeLookup.create(BluetoothNativeBridge.class);
    }

    private boolean call(final String methodKey, final BluetoothCallback callback, final BridgeCall call) throws IOException {
        if (bridge == null || !bridge.isSupported()) {
            throw new RuntimeException("Not supported on this platform");
        }
        BluetoothCallbackRegistry.setMethodCallback(methodKey, callback);
        boolean executed = call.invoke();
        if (executed && callback.isError()) {
            Map response = callback.getResponse();
            if (response != null && response.containsKey("message")) {
                throw new IOException((String) response.get("message"));
            }
            throw new IOException("Err");
        }
        return executed;
    }

    boolean initialize(final BluetoothCallback callback, final boolean request, final boolean statusReceiver, final String restoreKey) throws IOException {
        return call("initialize", callback, () -> bridge.initialize(request, statusReceiver, restoreKey));
    }

    boolean enable(final BluetoothCallback callback) throws IOException {
        return call("enable", callback, bridge::enable);
    }

    boolean disable(final BluetoothCallback callback) throws IOException {
        return call("disable", callback, bridge::disable);
    }

    boolean startScan(final BluetoothCallback callback, final String servicesJson, final boolean allowDuplicates, final int scanMode, final int matchMode, final int matchNum, final int callbackType) throws IOException {
        return call("startScan", callback, () -> bridge.startScan(servicesJson, allowDuplicates, scanMode, matchMode, matchNum, callbackType));
    }

    boolean stopScan(final BluetoothCallback callback) throws IOException {
        return call("stopScan", callback, bridge::stopScan);
    }

    boolean retrieveConnected(final BluetoothCallback callback, final String servicesJson) throws IOException {
        return call("retrieveConnected", callback, () -> bridge.retrieveConnected(servicesJson));
    }

    boolean connect(final BluetoothCallback callback, final String address) throws IOException {
        return call("connect", callback, () -> bridge.connect(address));
    }

    boolean reconnect(final BluetoothCallback callback, final String address) throws IOException {
        return call("reconnect", callback, () -> bridge.reconnect(address));
    }

    boolean disconnect(final BluetoothCallback callback, final String address) throws IOException {
        return call("disconnect", callback, () -> bridge.disconnect(address));
    }

    boolean close(final BluetoothCallback callback, final String address) throws IOException {
        return call("close", callback, () -> bridge.close(address));
    }

    boolean discover(final BluetoothCallback callback, final String address) throws IOException {
        return call("discover", callback, () -> bridge.discover(address));
    }

    boolean services(final BluetoothCallback callback, final String address, final String servicesJson) throws IOException {
        return call("services", callback, () -> bridge.services(address, servicesJson));
    }

    boolean characteristics(final BluetoothCallback callback, final String address, final String service, final String characteristicsJson) throws IOException {
        return call("characteristics", callback, () -> bridge.characteristics(address, service, characteristicsJson));
    }

    boolean descriptors(final BluetoothCallback callback, final String address, final String service, final String characteristic) throws IOException {
        return call("descriptors", callback, () -> bridge.descriptors(address, service, characteristic));
    }

    boolean read(final BluetoothCallback callback, final String address, final String service, final String characteristic) throws IOException {
        return call("read", callback, () -> bridge.read(address, service, characteristic));
    }

    boolean subscribe(final BluetoothCallback callback, final String address, final String service, final String characteristic) throws IOException {
        return call("subscribe", callback, () -> bridge.subscribe(address, service, characteristic));
    }

    boolean unsubscribe(final BluetoothCallback callback, final String address, final String service, final String characteristic) throws IOException {
        return call("unsubscribe", callback, () -> bridge.unsubscribe(address, service, characteristic));
    }

    boolean write(final BluetoothCallback callback, final String address, final String service, final String characteristic, final String value, final boolean noResponse) throws IOException {
        return call("write", callback, () -> bridge.write(address, service, characteristic, value, noResponse));
    }

    boolean writeQ(final BluetoothCallback callback, final String address, final String service, final String characteristic, final String value, final boolean noResponse) throws IOException {
        return call("writeQ", callback, () -> bridge.writeQ(address, service, characteristic, value, noResponse));
    }

    boolean readDescriptor(final BluetoothCallback callback, final String address, final String service, final String characteristic, final String descriptor) throws IOException {
        return call("readDescriptor", callback, () -> bridge.readDescriptor(address, service, characteristic, descriptor));
    }

    boolean writeDescriptor(final BluetoothCallback callback, final String address, final String service, final String characteristic, final String descriptor, final String value) throws IOException {
        return call("writeDescriptor", callback, () -> bridge.writeDescriptor(address, service, characteristic, descriptor, value));
    }

    boolean rssi(final BluetoothCallback callback, final String address) throws IOException {
        return call("rssi", callback, () -> bridge.rssi(address));
    }

    boolean mtu(final BluetoothCallback callback, final String address, final int mtu) throws IOException {
        return call("mtu", callback, () -> bridge.mtu(address, mtu));
    }

    boolean requestConnectionPriority(final BluetoothCallback callback, final String address, final String priority) throws IOException {
        return call("requestConnectionPriority", callback, () -> bridge.requestConnectionPriority(address, priority));
    }

    boolean isInitialized(final BluetoothCallback callback) throws IOException {
        return call("isInitialized", callback, bridge::isInitialized);
    }

    boolean isEnabled(final BluetoothCallback callback) throws IOException {
        return call("isEnabled", callback, bridge::isEnabled);
    }

    boolean isScanning(final BluetoothCallback callback) throws IOException {
        return call("isScanning", callback, bridge::isScanning);
    }

    boolean wasConnected(final BluetoothCallback callback, final String address) throws IOException {
        return call("wasConnected", callback, () -> bridge.wasConnected(address));
    }

    boolean isConnected(final BluetoothCallback callback, final String address) throws IOException {
        return call("isConnected", callback, () -> bridge.isConnected(address));
    }

    boolean isDiscovered(final BluetoothCallback callback, final String address) throws IOException {
        return call("isDiscovered", callback, () -> bridge.isDiscovered(address));
    }

    boolean hasPermission(final BluetoothCallback callback) throws IOException {
        return call("hasPermission", callback, bridge::hasPermission);
    }

    boolean requestPermission(final BluetoothCallback callback) throws IOException {
        return call("requestPermission", callback, bridge::requestPermission);
    }

    boolean isLocationEnabled(final BluetoothCallback callback) throws IOException {
        return call("isLocationEnabled", callback, bridge::isLocationEnabled);
    }

    boolean requestLocation(final BluetoothCallback callback) throws IOException {
        return call("requestLocation", callback, bridge::requestLocation);
    }
}
