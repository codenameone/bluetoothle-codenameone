package com.codename1.btle;

import com.codename1.bluetoothle.Bluetooth;
import com.codename1.bluetoothle.BluetoothSimulator;
import com.codename1.bluetoothle.SimulatedCharacteristic;
import com.codename1.bluetoothle.SimulatedPeripheral;
import com.codename1.bluetoothle.SimulatedService;
import com.codename1.testing.AbstractTest;

/// Shared setup for the JavaSE-simulator-backed Bluetooth tests.
///
/// Each test gets a freshly reset simulator with one well-known peripheral
/// already registered so the tests don't repeat the same plumbing. Tests that
/// need different peripherals can call [BluetoothSimulator#clearPeripherals]
/// and add their own.
abstract class AbstractBluetoothSimulatorTest extends AbstractTest {

    static final String DEVICE_ADDRESS = "AA:BB:CC:DD:EE:01";
    static final String DEVICE_NAME = "SimulatedSensor";
    static final String SERVICE_UUID = "0000180a-0000-1000-8000-00805f9b34fb";
    static final String CHAR_READ_UUID = "00002a29-0000-1000-8000-00805f9b34fb";
    static final String CHAR_WRITE_UUID = "00002a30-0000-1000-8000-00805f9b34fb";
    static final String CHAR_NOTIFY_UUID = "00002a31-0000-1000-8000-00805f9b34fb";
    static final String CCCD_DESCRIPTOR_UUID = "00002902-0000-1000-8000-00805f9b34fb";

    static final byte[] INITIAL_READ_VALUE = new byte[]{0x42, 0x43, 0x44};

    protected Bluetooth bt;

    @Override
    public void prepare() {
        BluetoothSimulator.reset();
        BluetoothSimulator.setCallbackLatencyMillis(2);
        BluetoothSimulator.setHasPermission(true);
        BluetoothSimulator.addPeripheral(buildDefaultPeripheral());
        bt = new Bluetooth();
    }

    @Override
    public void cleanup() {
        BluetoothSimulator.reset();
    }

    static SimulatedPeripheral buildDefaultPeripheral() {
        return new SimulatedPeripheral(DEVICE_ADDRESS, DEVICE_NAME)
                .withRssi(-55)
                .withService(new SimulatedService(SERVICE_UUID)
                        .withCharacteristic(new SimulatedCharacteristic(CHAR_READ_UUID)
                                .withProperty(SimulatedCharacteristic.PROPERTY_READ)
                                .withValue(INITIAL_READ_VALUE))
                        .withCharacteristic(new SimulatedCharacteristic(CHAR_WRITE_UUID)
                                .withProperty(SimulatedCharacteristic.PROPERTY_WRITE))
                        .withCharacteristic(new SimulatedCharacteristic(CHAR_NOTIFY_UUID)
                                .withProperty(SimulatedCharacteristic.PROPERTY_NOTIFY)
                                .withDescriptor(CCCD_DESCRIPTOR_UUID, new byte[]{0, 0})));
    }

    /// Drives the public Bluetooth API into an enabled+permitted state. Used by
    /// tests that don't explicitly want to verify the initialize/enable flow.
    protected void initEnabled() throws Exception {
        BluetoothSimulator.setEnabled(true);
        bt.initialize(true, false, "test");
        if (!bt.isEnabled()) {
            bt.enable();
        }
    }

    /// Convenience: connect, then discover, returning when both complete.
    protected void connectAndDiscover() throws Exception {
        final java.util.concurrent.CountDownLatch connected = new java.util.concurrent.CountDownLatch(1);
        bt.connect(evt -> {
            java.util.Map m = (java.util.Map) evt.getSource();
            if ("connected".equals(m.get("status"))) {
                connected.countDown();
            }
        }, DEVICE_ADDRESS);
        if (!connected.await(2, java.util.concurrent.TimeUnit.SECONDS)) {
            throw new RuntimeException("connect listener never fired");
        }
        final java.util.concurrent.CountDownLatch discovered = new java.util.concurrent.CountDownLatch(1);
        bt.discover(evt -> discovered.countDown(), DEVICE_ADDRESS);
        if (!discovered.await(2, java.util.concurrent.TimeUnit.SECONDS)) {
            throw new RuntimeException("discover listener never fired");
        }
    }
}
