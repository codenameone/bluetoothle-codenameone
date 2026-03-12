package com.codename1.bluetoothle;

import java.util.Map;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

public class BluetoothCallbackRegistryTest {

    @After
    public void cleanup() {
        BluetoothCallbackRegistry.removeMethodCallback("testMethod");
        BluetoothCallbackRegistry.removeMethodCallback("keepMethod");
        BluetoothCallbackRegistry.removeMethodCallback("errorMethod");
    }

    @Test
    public void sendsSuccessPayloadAndRemovesCallbackByDefault() {
        BluetoothCallback callback = new BluetoothCallback();
        BluetoothCallbackRegistry.setMethodCallback("testMethod", callback);

        BluetoothCallbackRegistry.sendResult("testMethod", "{\"status\":\"enabled\"}", true);

        Map response = callback.getResponse();
        Assert.assertNotNull(response);
        Assert.assertEquals("enabled", response.get("status"));

        BluetoothCallbackRegistry.sendResult("testMethod", "{\"status\":\"disabled\"}", true);
        Assert.assertEquals("enabled", callback.getResponse().get("status"));
    }

    @Test
    public void keepsCallbackWhenKeepCallbackIsTrue() {
        BluetoothCallback callback = new BluetoothCallback();
        BluetoothCallbackRegistry.setMethodCallback("keepMethod", callback);

        BluetoothCallbackRegistry.sendResult("keepMethod", "{\"status\":\"scanStarted\"}", true, true);
        Map first = callback.getResponse();
        Assert.assertNotNull(first);
        Assert.assertEquals("scanStarted", first.get("status"));

        BluetoothCallbackRegistry.sendResult("keepMethod", "{\"status\":\"scanStopped\"}", true, false);
        Map second = callback.getResponse();
        Assert.assertNotNull(second);
        Assert.assertEquals("scanStopped", second.get("status"));
    }

    @Test
    public void marksCallbackAsErrorOnErrorResults() {
        BluetoothCallback callback = new BluetoothCallback();
        BluetoothCallbackRegistry.setMethodCallback("errorMethod", callback);

        BluetoothCallbackRegistry.sendResult("errorMethod", "{\"error\":\"read\",\"message\":\"failed\"}", false);

        Assert.assertTrue(callback.isError());
        Map response = callback.getResponse();
        Assert.assertNotNull(response);
        Assert.assertEquals("read", response.get("error"));
        Assert.assertEquals("failed", response.get("message"));
    }
}
