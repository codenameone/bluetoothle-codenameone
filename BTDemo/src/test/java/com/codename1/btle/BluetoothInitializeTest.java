package com.codename1.btle;

import com.codename1.bluetoothle.BluetoothSimulator;
import com.codename1.testing.TestUtils;

public class BluetoothInitializeTest extends AbstractBluetoothSimulatorTest {

    @Override
    public boolean runTest() throws Exception {
        BluetoothSimulator.setEnabled(false);

        TestUtils.assertFalse(bt.isInitialized(), "isInitialized should be false before initialize");
        TestUtils.assertFalse(bt.isEnabled(), "isEnabled should be false before initialize");

        bt.initialize(true, false, "testRestoreKey");

        TestUtils.assertTrue(bt.isInitialized(), "isInitialized should be true after initialize");
        TestUtils.assertTrue(bt.isEnabled(), "request=true should also enable bluetooth");
        TestUtils.assertTrue(bt.hasPermission(), "permission was granted in setup");
        TestUtils.assertFalse(bt.isScanning(), "no scan started");
        return true;
    }
}
