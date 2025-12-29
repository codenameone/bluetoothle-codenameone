/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.codename1.cordova;

import com.codename1.system.NativeLookup;
import java.io.IOException;
import java.util.Map;

/**
 *
 * @author Chen
 */
public class Cordova {

    private CordovaNative cordova;

    public Cordova() {
        cordova = (CordovaNative) NativeLookup.create(CordovaNative.class);
    }
    
    public boolean execute(String action, String jsonArgs, CordovaCallback callback) throws IOException{
        if(cordova == null || !cordova.isSupported()){
            throw new RuntimeException("Not supported on this platform");
        }
        CordovaCallbackManager.setMethodCallback(action, callback);
        boolean executed = cordova.execute(action, jsonArgs);        
        if(executed){
            if(callback.isError()){
                Map response = callback.getResponse();
                if (response != null && response.containsKey("message")) {
                    throw new IOException((String)response.get("message"));
                } else {
                    throw new IOException("Err");
                }
            }
        }
        return executed;
    }

    
}
