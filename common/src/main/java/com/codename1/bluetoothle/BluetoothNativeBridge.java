package com.codename1.bluetoothle;

import com.codename1.system.NativeInterface;

public interface BluetoothNativeBridge extends NativeInterface {
    boolean initialize(boolean request, boolean statusReceiver, String restoreKey);
    boolean enable();
    boolean disable();
    boolean startScan(String servicesJson, boolean allowDuplicates, int scanMode, int matchMode, int matchNum, int callbackType);
    boolean stopScan();
    boolean retrieveConnected(String servicesJson);
    boolean connect(String address);
    boolean reconnect(String address);
    boolean disconnect(String address);
    boolean close(String address);
    boolean discover(String address);
    boolean services(String address, String servicesJson);
    boolean characteristics(String address, String service, String characteristicsJson);
    boolean descriptors(String address, String service, String characteristic);
    boolean read(String address, String service, String characteristic);
    boolean subscribe(String address, String service, String characteristic);
    boolean unsubscribe(String address, String service, String characteristic);
    boolean write(String address, String service, String characteristic, String value, boolean noResponse);
    boolean writeQ(String address, String service, String characteristic, String value, boolean noResponse);
    boolean readDescriptor(String address, String service, String characteristic, String descriptor);
    boolean writeDescriptor(String address, String service, String characteristic, String descriptor, String value);
    boolean rssi(String address);
    boolean mtu(String address, int mtu);
    boolean requestConnectionPriority(String address, String priority);
    boolean isInitialized();
    boolean isEnabled();
    boolean isScanning();
    boolean wasConnected(String address);
    boolean isConnected(String address);
    boolean isDiscovered(String address);
    boolean hasPermission();
    boolean requestPermission();
    boolean isLocationEnabled();
    boolean requestLocation();
}
