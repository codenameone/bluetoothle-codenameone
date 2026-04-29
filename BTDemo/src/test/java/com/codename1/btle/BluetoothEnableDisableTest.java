package com.codename1.btle;

import com.codename1.bluetoothle.BluetoothSimulator;
import com.codename1.testing.TestUtils;

public class BluetoothEnableDisableTest extends AbstractBluetoothSimulatorTest {

    @Override
    public boolean runTest() throws Exception {
        BluetoothSimulator.setEnabled(false);
        bt.initialize(false, false, "test");

        TestUtils.assertFalse(bt.isEnabled(), "starts disabled");

        bt.enable();
        TestUtils.assertTrue(bt.isEnabled(), "enable() flips state on");

        bt.disable();
        TestUtils.assertFalse(bt.isEnabled(), "disable() flips state off");
        return true;
    }
}
