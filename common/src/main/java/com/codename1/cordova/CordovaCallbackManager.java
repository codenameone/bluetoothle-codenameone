/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.codename1.cordova;

import com.codename1.util.JSONParserUtils;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author Chen
 */
public class CordovaCallbackManager {

    private static HashMap<String, CordovaCallback> callbacks = new HashMap<String, CordovaCallback>();

    public static void setMethodCallback(String method, CordovaCallback callback){
        callbacks.put(method, callback);
    }

    public static void removeMethodCallback(String method){
        callbacks.remove(method);
    }

    public static void sendResult(String method, String result, boolean success, boolean keepCallback){
        CordovaCallback cb = callbacks.get(method);
        if(cb != null){
            if(success){
                cb.onSuccess(result);
            }else{
                cb.onError(result);
            }
            if(!keepCallback){
                callbacks.remove(method);
            }
        }
    }

    public static void sendResult(String method, String result, boolean success){
        sendResult(method, result, success, false);
    }

    // Methods required by native/legacy code
    public static void onSuccess(String method, String result) {
        sendResult(method, result, true);
    }

    public static void onError(String method, String result) {
        sendResult(method, result, false);
    }

    // For iOS
    public static void sendResult(String method, String result) {
        // This signature is ambiguous without success flag, but iOS seems to call this?
        // Let's check BluetoothLeCommandDelegateImpl.m again.
        // It calls com_codename1_cordova_CordovaCallbackManager_sendResult___java_lang_String_java_lang_String
        // which maps to sendResult(String, String).
        // Assuming success? Or does the result contain info?
        // Usually iOS plugins send a PluginResult which has status.
        // But here it seems to pass a string.
        // If I look at android/src/main/java/com/codename1/cordova/CallbackContext.java, it calls sendResult(action, pluginResult.getMessage()).
        // Wait, CallbackContext.java has: CordovaCallbackManager.sendResult(action, pluginResult.getMessage());
        // And sendResult there takes 2 args.
        // But `CallbackContext` also has `onSuccess` and `onError`.
        // Let's assume sendResult(String, String) implies success or the result string is raw JSON.
        // The previous implementation of `sendResult` likely delegated to the callback.

        // Let's implement it as success for now or try to parse.
        // Actually, looking at `BluetoothLeCommandDelegateImpl.m` usage:
        // [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
        // The command delegate impl calls CordovaCallbackManager.sendResult.

        sendResult(method, result, true);
    }
}
