package com.codename1.bluetoothle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/// Describes a single GATT service on a [SimulatedPeripheral].
public final class SimulatedService {

    private final String uuid;
    private final List<SimulatedCharacteristic> characteristics = new ArrayList<>();

    public SimulatedService(String uuid) {
        if (uuid == null) {
            throw new IllegalArgumentException("uuid is required");
        }
        this.uuid = uuid.toLowerCase();
    }

    public String getUuid() {
        return uuid;
    }

    public SimulatedService withCharacteristic(SimulatedCharacteristic characteristic) {
        if (characteristic == null) {
            throw new IllegalArgumentException("characteristic is required");
        }
        characteristics.add(characteristic);
        return this;
    }

    public List<SimulatedCharacteristic> getCharacteristics() {
        return Collections.unmodifiableList(characteristics);
    }

    SimulatedCharacteristic findCharacteristic(String uuid) {
        if (uuid == null) {
            return null;
        }
        String lc = uuid.toLowerCase();
        for (SimulatedCharacteristic c : characteristics) {
            if (c.getUuid().equals(lc)) {
                return c;
            }
        }
        return null;
    }
}
