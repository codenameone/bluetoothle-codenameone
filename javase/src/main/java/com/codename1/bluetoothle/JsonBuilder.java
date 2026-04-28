package com.codename1.bluetoothle;

/// Minimal JSON object builder used to format simulator callback payloads in
/// the same shape that the iOS/Android native bridges produce. Kept dependency
/// free so the JavaSE port has no third-party JSON library on the classpath.
final class JsonBuilder {

    private final StringBuilder sb = new StringBuilder();
    private boolean first = true;

    private JsonBuilder() {
        sb.append('{');
    }

    static JsonBuilder start() {
        return new JsonBuilder();
    }

    JsonBuilder put(String key, String value) {
        appendKey(key);
        if (value == null) {
            sb.append("null");
        } else {
            appendString(value);
        }
        return this;
    }

    JsonBuilder put(String key, boolean value) {
        appendKey(key);
        sb.append(value);
        return this;
    }

    JsonBuilder put(String key, int value) {
        appendKey(key);
        sb.append(value);
        return this;
    }

    /// Embeds a pre-formatted JSON fragment (object or array) as the value.
    JsonBuilder putRaw(String key, String json) {
        appendKey(key);
        sb.append(json == null ? "null" : json);
        return this;
    }

    String end() {
        sb.append('}');
        return sb.toString();
    }

    private void appendKey(String key) {
        if (!first) {
            sb.append(',');
        }
        first = false;
        appendString(key);
        sb.append(':');
    }

    private void appendString(String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                default:
                    if (c < 0x20) {
                        String t = "000" + Integer.toHexString(c);
                        sb.append("\\u").append(t.substring(t.length() - 4));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }
}
