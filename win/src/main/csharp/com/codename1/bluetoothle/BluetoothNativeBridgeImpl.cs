namespace com.codename1.bluetoothle {

using System;
using System.Windows;

public class BluetoothNativeBridgeImpl {
    public bool initialize(bool request, bool statusReceiver, String restoreKey) { return false; }
    public bool enable() { return false; }
    public bool disable() { return false; }
    public bool startScan(String servicesJson, bool allowDuplicates, int scanMode, int matchMode, int matchNum, int callbackType) { return false; }
    public bool stopScan() { return false; }
    public bool retrieveConnected(String servicesJson) { return false; }
    public bool connect(String address) { return false; }
    public bool reconnect(String address) { return false; }
    public bool disconnect(String address) { return false; }
    public bool close(String address) { return false; }
    public bool discover(String address) { return false; }
    public bool services(String address, String servicesJson) { return false; }
    public bool characteristics(String address, String service, String characteristicsJson) { return false; }
    public bool descriptors(String address, String service, String characteristic) { return false; }
    public bool read(String address, String service, String characteristic) { return false; }
    public bool subscribe(String address, String service, String characteristic) { return false; }
    public bool unsubscribe(String address, String service, String characteristic) { return false; }
    public bool write(String address, String service, String characteristic, String value, bool noResponse) { return false; }
    public bool writeQ(String address, String service, String characteristic, String value, bool noResponse) { return false; }
    public bool readDescriptor(String address, String service, String characteristic, String descriptor) { return false; }
    public bool writeDescriptor(String address, String service, String characteristic, String descriptor, String value) { return false; }
    public bool rssi(String address) { return false; }
    public bool mtu(String address, int mtu) { return false; }
    public bool requestConnectionPriority(String address, String priority) { return false; }
    public bool isInitialized() { return false; }
    public bool isEnabled() { return false; }
    public bool isScanning() { return false; }
    public bool wasConnected(String address) { return false; }
    public bool isConnected(String address) { return false; }
    public bool isDiscovered(String address) { return false; }
    public bool hasPermission() { return false; }
    public bool requestPermission() { return false; }
    public bool isLocationEnabled() { return false; }
    public bool requestLocation() { return false; }
    public bool isSupported() { return false; }
}
}
