package com.codename1.bluetoothle;

import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public class PluginResult {
    private int status;
    private String message;
    private boolean keepCallback;
    private List<PluginResult> multipartMessages;

    public PluginResult(Status status) {
        this(status, PluginResult.StatusMessages[status.ordinal()]);
    }

    public PluginResult(Status status, String message) {
        this.status = status.ordinal();
        this.message = message;
    }

    public PluginResult(Status status, JSONArray message) {
        this.status = status.ordinal();
        this.message = message.toString();
    }

    public PluginResult(Status status, JSONObject message) {
        this.status = status.ordinal();
        this.message = message.toString();
    }

    public PluginResult(Status status, int i) {
        this.status = status.ordinal();
        this.message = Integer.toString(i);
    }

    public PluginResult(Status status, float f) {
        this.status = status.ordinal();
        this.message = Float.toString(f);
    }

    public PluginResult(Status status, boolean b) {
        this.status = status.ordinal();
        this.message = Boolean.toString(b);
    }

    public PluginResult(Status status, byte[] data) {
        this(status, data, false);
    }

    public PluginResult(Status status, byte[] data, boolean binaryString) {
        this.status = status.ordinal();
        this.message = new String(data);
    }

    public PluginResult(Status status, List<PluginResult> multipartMessages) {
        this.status = status.ordinal();
        this.multipartMessages = multipartMessages;
        this.message = this.getMultipartMessagesSize() > 0 ? this.getMultipartMessage(0).getMessage() : "";
    }

    public int getStatus() {
        return this.status;
    }

    public String getMessage() {
        return this.message;
    }

    public List<PluginResult> getMultipartMessages() {
        return multipartMessages;
    }

    public int getMultipartMessagesSize() {
        return multipartMessages == null ? 0 : multipartMessages.size();
    }

    public PluginResult getMultipartMessage(int index) {
        return multipartMessages.get(index);
    }

    public void setKeepCallback(boolean b) {
        this.keepCallback = b;
    }

    public boolean getKeepCallback() {
        return this.keepCallback;
    }

    public enum Status {
        NO_RESULT,
        OK,
        CLASS_NOT_FOUND_EXCEPTION,
        ILLEGAL_ACCESS_EXCEPTION,
        INSTANTIATION_EXCEPTION,
        MALFORMED_URL_EXCEPTION,
        IO_EXCEPTION,
        INVALID_ACTION,
        JSON_EXCEPTION,
        ERROR
    }

    public static final String[] StatusMessages = {
        "No result",
        "OK",
        "Class not found",
        "Illegal access",
        "Instantiation error",
        "Malformed url",
        "IO error",
        "Invalid action",
        "JSON error",
        "Error"
    };
}
