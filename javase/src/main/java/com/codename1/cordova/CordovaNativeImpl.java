package com.codename1.cordova;

import java.util.Map;
import com.codename1.ui.Display;

public class CordovaNativeImpl implements com.codename1.cordova.CordovaNative{
    public boolean execute(String param, String param1) {
        installBuildHints();
        return false;
    }

    public boolean isSupported() {
        installBuildHints();
        return false;
    }
    
    private boolean bluetoothUsageDescriptionChecked;
    
    private void checkBluetoothUsageDescription() {
        if (!bluetoothUsageDescriptionChecked) {
            bluetoothUsageDescriptionChecked = true;
            
            Map<String, String> m = Display.getInstance().getProjectBuildHints();
            if(m != null) {
                if(!m.containsKey("ios.NSBluetoothPeripheralUsageDescription")) {
                    Display.getInstance().setProjectBuildHint("ios.NSBluetoothPeripheralUsageDescription", "Some functionality of the application requires Bluetooth functionality");
                }
                if(!m.containsKey("ios.NSBluetoothAlwaysUsageDescription")) {
                    Display.getInstance().setProjectBuildHint("ios.NSBluetoothAlwaysUsageDescription", "Some functionality of the application requires Bluetooth functionality");
                }
            }
        }
    }
    
    private void installBuildHints() {
        checkBluetoothUsageDescription();
    }

}
