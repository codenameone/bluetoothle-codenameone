/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.codename1.cordova;

import ca.weblite.codename1.json.JSONException;
import ca.weblite.codename1.json.JSONObject;
import com.codename1.io.Util;
import com.codename1.ui.CN;
import com.codename1.ui.Display;
import com.codename1.ui.events.ActionEvent;
import com.codename1.ui.events.ActionListener;
import com.codename1.util.AsyncResource;

/**
 *
 * @author Chen
 */
public class CordovaCallback {
    
    private JSONObject response;
    private boolean complete;
    private Object completeLock = new Object();
    
    private ActionListener listener;

    public CordovaCallback() {
    }

    public CordovaCallback(ActionListener listener) {
        this.listener = listener;
    }
    
    public void onError(JSONObject json){
        complete = true;
        this.response = json;
        synchronized(completeLock) {
            completeLock.notifyAll();
        }
    }

    public void onSuccess(JSONObject json){
        complete = true;
        this.response = json;
        if(listener != null){
            Display.getInstance().callSerially(new Runnable(){
                public void run(){
                    listener.actionPerformed(new ActionEvent(json));
                }
            });
        }
        synchronized(completeLock) {
            completeLock.notifyAll();
        }
    }

    public void sendResult(JSONObject json){
        complete = true;
        this.response = json;
        if(listener != null){
            Display.getInstance().callSerially(new Runnable(){
                public void run(){
                    listener.actionPerformed(new ActionEvent(json));
                }
            });
        }
        synchronized(completeLock) {
            completeLock.notifyAll();
        }
    }

    public JSONObject getResponse() {
        return response;
    }
    
    public AsyncResource<JSONObject> getResponseAsync(int timeout) {
        
        long absTimeout = System.currentTimeMillis() + timeout;
        AsyncResource<JSONObject> out = new AsyncResource<JSONObject>();
        if (!complete) {
            new Thread(()->{
                while (!complete) {

                    if (timeout > 0 && absTimeout <= System.currentTimeMillis()) {
                        out.error(new RuntimeException("Callback timeout reached in getResponseAsync()"));
                        return;
                    }
                    synchronized(completeLock) {
                        Util.wait(completeLock, (int)Math.max(1, absTimeout - System.currentTimeMillis()));
                    }
                }
                out.complete(response);
            }).start();

        } else {
            out.complete(response);
        }
        return out;
        
    }
    
    public JSONObject getResponseAndWait(int timeout) {
        return getResponseAsync(timeout).get();
    }
    
    public boolean isError(){
        try {
            return response != null && response.getString("error") != null;
        } catch (JSONException ex) {
        }
        return false;
    }
    
}
