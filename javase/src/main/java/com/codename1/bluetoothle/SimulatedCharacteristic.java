package com.codename1.bluetoothle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// Describes a single GATT characteristic on a [SimulatedPeripheral].
///
/// Values are byte arrays exactly as they would arrive from the native bridge
/// (the public Bluetooth API exposes them base64-encoded over the wire).
public final class SimulatedCharacteristic {

    public static final String PROPERTY_READ = "Read";
    public static final String PROPERTY_WRITE = "Write";
    public static final String PROPERTY_WRITE_NO_RESPONSE = "WriteWithoutResponse";
    public static final String PROPERTY_NOTIFY = "Notify";
    public static final String PROPERTY_INDICATE = "Indicate";

    private final String uuid;
    private final List<String> properties = new ArrayList<>();
    private final Map<String, byte[]> descriptors = new LinkedHashMap<>();
    private byte[] value;

    public SimulatedCharacteristic(String uuid) {
        if (uuid == null) {
            throw new IllegalArgumentException("uuid is required");
        }
        this.uuid = uuid.toLowerCase();
    }

    public String getUuid() {
        return uuid;
    }

    public SimulatedCharacteristic withProperty(String property) {
        properties.add(property);
        return this;
    }

    public SimulatedCharacteristic withReadWriteNotify() {
        return withProperty(PROPERTY_READ).withProperty(PROPERTY_WRITE).withProperty(PROPERTY_NOTIFY);
    }

    public List<String> getProperties() {
        return Collections.unmodifiableList(properties);
    }

    public SimulatedCharacteristic withValue(byte[] value) {
        this.value = value == null ? null : value.clone();
        return this;
    }

    public SimulatedCharacteristic withValue(String utf8Value) {
        if (utf8Value == null) {
            this.value = null;
            return this;
        }
        try {
            this.value = utf8Value.getBytes("UTF-8");
        } catch (java.io.UnsupportedEncodingException ex) {
            throw new RuntimeException(ex);
        }
        return this;
    }

    public byte[] getValue() {
        return value == null ? null : value.clone();
    }

    void setValueInternal(byte[] value) {
        this.value = value == null ? null : value.clone();
    }

    public SimulatedCharacteristic withDescriptor(String descriptorUuid, byte[] value) {
        if (descriptorUuid == null) {
            throw new IllegalArgumentException("descriptorUuid is required");
        }
        descriptors.put(descriptorUuid.toLowerCase(), value == null ? null : value.clone());
        return this;
    }

    public Map<String, byte[]> getDescriptors() {
        return Collections.unmodifiableMap(descriptors);
    }

    byte[] getDescriptorValue(String descriptorUuid) {
        if (descriptorUuid == null) {
            return null;
        }
        byte[] v = descriptors.get(descriptorUuid.toLowerCase());
        return v == null ? null : v.clone();
    }

    boolean hasDescriptor(String descriptorUuid) {
        return descriptorUuid != null && descriptors.containsKey(descriptorUuid.toLowerCase());
    }

    void setDescriptorValue(String descriptorUuid, byte[] value) {
        if (descriptorUuid == null) {
            return;
        }
        descriptors.put(descriptorUuid.toLowerCase(), value == null ? null : value.clone());
    }
}
