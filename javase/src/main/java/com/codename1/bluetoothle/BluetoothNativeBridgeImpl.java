package com.codename1.bluetoothle;

import java.util.Map;
import com.codename1.ui.Display;

public class BluetoothNativeBridgeImpl implements BluetoothNativeBridge {
    private boolean bluetoothUsageDescriptionChecked;

    private boolean unsupported() {
        installBuildHints();
        return false;
    }

    @Override
    public boolean initialize(boolean request, boolean statusReceiver, String restoreKey) { return unsupported(); }
    @Override
    public boolean enable() { return unsupported(); }
    @Override
    public boolean disable() { return unsupported(); }
    @Override
    public boolean startScan(String servicesJson, boolean allowDuplicates, int scanMode, int matchMode, int matchNum, int callbackType) { return unsupported(); }
    @Override
    public boolean stopScan() { return unsupported(); }
    @Override
    public boolean retrieveConnected(String servicesJson) { return unsupported(); }
    @Override
    public boolean connect(String address) { return unsupported(); }
    @Override
    public boolean reconnect(String address) { return unsupported(); }
    @Override
    public boolean disconnect(String address) { return unsupported(); }
    @Override
    public boolean close(String address) { return unsupported(); }
    @Override
    public boolean discover(String address) { return unsupported(); }
    @Override
    public boolean services(String address, String servicesJson) { return unsupported(); }
    @Override
    public boolean characteristics(String address, String service, String characteristicsJson) { return unsupported(); }
    @Override
    public boolean descriptors(String address, String service, String characteristic) { return unsupported(); }
    @Override
    public boolean read(String address, String service, String characteristic) { return unsupported(); }
    @Override
    public boolean subscribe(String address, String service, String characteristic) { return unsupported(); }
    @Override
    public boolean unsubscribe(String address, String service, String characteristic) { return unsupported(); }
    @Override
    public boolean write(String address, String service, String characteristic, String value, boolean noResponse) { return unsupported(); }
    @Override
    public boolean writeQ(String address, String service, String characteristic, String value, boolean noResponse) { return unsupported(); }
    @Override
    public boolean readDescriptor(String address, String service, String characteristic, String descriptor) { return unsupported(); }
    @Override
    public boolean writeDescriptor(String address, String service, String characteristic, String descriptor, String value) { return unsupported(); }
    @Override
    public boolean rssi(String address) { return unsupported(); }
    @Override
    public boolean mtu(String address, int mtu) { return unsupported(); }
    @Override
    public boolean requestConnectionPriority(String address, String priority) { return unsupported(); }
    @Override
    public boolean isInitialized() { return unsupported(); }
    @Override
    public boolean isEnabled() { return unsupported(); }
    @Override
    public boolean isScanning() { return unsupported(); }
    @Override
    public boolean wasConnected(String address) { return unsupported(); }
    @Override
    public boolean isConnected(String address) { return unsupported(); }
    @Override
    public boolean isDiscovered(String address) { return unsupported(); }
    @Override
    public boolean hasPermission() { return unsupported(); }
    @Override
    public boolean requestPermission() { return unsupported(); }
    @Override
    public boolean isLocationEnabled() { return unsupported(); }
    @Override
    public boolean requestLocation() { return unsupported(); }

    @Override
    public boolean isSupported() {
        return unsupported();
    }

    private void checkBluetoothUsageDescription() {
        if (!bluetoothUsageDescriptionChecked) {
            bluetoothUsageDescriptionChecked = true;

            Map<String, String> m = Display.getInstance().getProjectBuildHints();
            if (m != null) {
                if (!m.containsKey("ios.NSBluetoothPeripheralUsageDescription")) {
                    Display.getInstance().setProjectBuildHint("ios.NSBluetoothPeripheralUsageDescription", "Some functionality of the application requires Bluetooth functionality");
                }
                if (!m.containsKey("ios.NSBluetoothAlwaysUsageDescription")) {
                    Display.getInstance().setProjectBuildHint("ios.NSBluetoothAlwaysUsageDescription", "Some functionality of the application requires Bluetooth functionality");
                }
            }
        }
    }

    private void installBuildHints() {
        checkBluetoothUsageDescription();
    }
}
