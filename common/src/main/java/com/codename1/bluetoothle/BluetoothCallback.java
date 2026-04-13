package com.codename1.bluetoothle;

import com.codename1.io.Util;
import com.codename1.ui.Display;
import com.codename1.ui.events.ActionEvent;
import com.codename1.ui.events.ActionListener;
import com.codename1.util.AsyncResource;
import com.codename1.io.JSONParser;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;

public class BluetoothCallback {

    private Map response;
    private boolean complete;
    private final Object completeLock = new Object();

    private final ActionListener listener;

    public BluetoothCallback() {
        this.listener = null;
    }

    public BluetoothCallback(ActionListener listener) {
        this.listener = listener;
    }

    private Map<String, Object> parseJson(String json) throws IOException {
        JSONParser p = new JSONParser();
        return p.parseJSON(new InputStreamReader(new ByteArrayInputStream(json.getBytes("UTF-8")), "UTF-8"));
    }

    public void onError(String jsonStr) {
        try {
            onError(parseJson(jsonStr));
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public void onError(final Map json) {
        complete = true;
        this.response = json;
        if (listener != null) {
            Display.getInstance().callSerially(() -> listener.actionPerformed(new ActionEvent(json)));
        }
        synchronized (completeLock) {
            completeLock.notifyAll();
        }
    }

    public void onSuccess(String jsonStr) {
        try {
            onSuccess(parseJson(jsonStr));
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public void onSuccess(final Map json) {
        complete = true;
        this.response = json;
        if (listener != null) {
            Display.getInstance().callSerially(() -> listener.actionPerformed(new ActionEvent(json)));
        }
        synchronized (completeLock) {
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
            new Thread(() -> {
                while (!complete) {
                    if (timeout > 0 && absTimeout <= System.currentTimeMillis()) {
                        out.error(new RuntimeException("Callback timeout reached in getResponseAsync()"));
                        return;
                    }
                    synchronized (completeLock) {
                        Util.wait(completeLock, (int) Math.max(1, absTimeout - System.currentTimeMillis()));
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

    public boolean isError() {
        return response != null && response.containsKey("error");
    }
}
