/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.codename1.cordova;

import com.codename1.io.Util;
import com.codename1.ui.Display;
import com.codename1.ui.events.ActionEvent;
import com.codename1.ui.events.ActionListener;
import com.codename1.util.AsyncResource;
import com.codename1.util.JSONParserUtils;
import java.io.IOException;
import java.util.Map;

/**
 *
 * @author Chen
 */
public class CordovaCallback {
    
    private Map response;
    private boolean complete;
    private Object completeLock = new Object();
    
    private ActionListener listener;

    public CordovaCallback() {
    }

    public CordovaCallback(ActionListener listener) {
        this.listener = listener;
    }
    
    public void onError(String jsonStr){
        try {
            onError(JSONParserUtils.parse(jsonStr));
        } catch (IOException ex) {
            ex.printStackTrace();
            // If parsing fails, maybe pass raw string wrapped?
            // For now, print stack trace.
        }
    }

    public void onError(Map json){
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

    public void onSuccess(String jsonStr) {
        try {
            onSuccess(JSONParserUtils.parse(jsonStr));
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public void onSuccess(Map json){
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

    public void sendResult(String jsonStr) {
        try {
            sendResult(JSONParserUtils.parse(jsonStr));
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public void sendResult(Map json){
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

    public Map getResponse() {
        return response;
    }
    
    public AsyncResource<Map> getResponseAsync(int timeout) {
        
        long absTimeout = System.currentTimeMillis() + timeout;
        AsyncResource<Map> out = new AsyncResource<Map>();
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
    
    public Map getResponseAndWait(int timeout) {
        return getResponseAsync(timeout).get();
    }
    
    public boolean isError(){
        return response != null && response.containsKey("error");
    }
    
}
