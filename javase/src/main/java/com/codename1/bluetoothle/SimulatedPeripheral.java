package com.codename1.bluetoothle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/// Describes one virtual BLE peripheral that the JavaSE [BluetoothNativeBridgeImpl]
/// will surface to scans and connect/discover/read/write/subscribe operations.
public final class SimulatedPeripheral {

    private final String address;
    private final String name;
    private final List<SimulatedService> services = new ArrayList<>();
    private int rssi = -60;
    private byte[] advertisementData;

    public SimulatedPeripheral(String address, String name) {
        if (address == null) {
            throw new IllegalArgumentException("address is required");
        }
        this.address = address;
        this.name = name == null ? "" : name;
    }

    public String getAddress() {
        return address;
    }

    public String getName() {
        return name;
    }

    public SimulatedPeripheral withRssi(int rssi) {
        this.rssi = rssi;
        return this;
    }

    public int getRssi() {
        return rssi;
    }

    public SimulatedPeripheral withAdvertisementData(byte[] data) {
        this.advertisementData = data == null ? null : data.clone();
        return this;
    }

    public byte[] getAdvertisementData() {
        return advertisementData == null ? null : advertisementData.clone();
    }

    public SimulatedPeripheral withService(SimulatedService service) {
        if (service == null) {
            throw new IllegalArgumentException("service is required");
        }
        services.add(service);
        return this;
    }

    public List<SimulatedService> getServices() {
        return Collections.unmodifiableList(services);
    }

    SimulatedService findService(String uuid) {
        if (uuid == null) {
            return null;
        }
        String lc = uuid.toLowerCase();
        for (SimulatedService s : services) {
            if (s.getUuid().equals(lc)) {
                return s;
            }
        }
        return null;
    }
}
