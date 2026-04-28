package com.codename1.bluetoothle;

/// Self-contained Base64 encode/decode for simulator payloads. Avoids
/// java.util.Base64 (Java 8+) and javax.xml.bind (removed in Java 11) so the
/// helper compiles cleanly on every JDK the rest of the project supports.
final class Base64Util {

    private static final char[] ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();
    private static final int[] DECODE = new int[128];

    static {
        for (int i = 0; i < DECODE.length; i++) {
            DECODE[i] = -1;
        }
        for (int i = 0; i < ALPHABET.length; i++) {
            DECODE[ALPHABET[i]] = i;
        }
    }

    private Base64Util() {
    }

    static String encode(byte[] data) {
        if (data == null || data.length == 0) {
            return "";
        }
        StringBuilder out = new StringBuilder(((data.length + 2) / 3) * 4);
        int i = 0;
        while (i + 3 <= data.length) {
            int v = ((data[i] & 0xff) << 16) | ((data[i + 1] & 0xff) << 8) | (data[i + 2] & 0xff);
            out.append(ALPHABET[(v >>> 18) & 0x3f]);
            out.append(ALPHABET[(v >>> 12) & 0x3f]);
            out.append(ALPHABET[(v >>> 6) & 0x3f]);
            out.append(ALPHABET[v & 0x3f]);
            i += 3;
        }
        int remaining = data.length - i;
        if (remaining == 1) {
            int v = (data[i] & 0xff) << 16;
            out.append(ALPHABET[(v >>> 18) & 0x3f]);
            out.append(ALPHABET[(v >>> 12) & 0x3f]);
            out.append("==");
        } else if (remaining == 2) {
            int v = ((data[i] & 0xff) << 16) | ((data[i + 1] & 0xff) << 8);
            out.append(ALPHABET[(v >>> 18) & 0x3f]);
            out.append(ALPHABET[(v >>> 12) & 0x3f]);
            out.append(ALPHABET[(v >>> 6) & 0x3f]);
            out.append('=');
        }
        return out.toString();
    }

    static byte[] decode(String text) {
        if (text == null || text.isEmpty()) {
            return new byte[0];
        }
        StringBuilder cleaned = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\r' || c == '\n' || c == ' ' || c == '\t') {
                continue;
            }
            cleaned.append(c);
        }
        String s = cleaned.toString();
        int padding = 0;
        if (s.endsWith("==")) {
            padding = 2;
        } else if (s.endsWith("=")) {
            padding = 1;
        }
        int outputLen = (s.length() / 4) * 3 - padding;
        byte[] out = new byte[outputLen];
        int o = 0;
        for (int i = 0; i < s.length(); i += 4) {
            int v0 = decodeChar(s.charAt(i));
            int v1 = decodeChar(s.charAt(i + 1));
            int v2 = i + 2 < s.length() ? decodeChar(s.charAt(i + 2)) : 0;
            int v3 = i + 3 < s.length() ? decodeChar(s.charAt(i + 3)) : 0;
            int v = (v0 << 18) | (v1 << 12) | (v2 << 6) | v3;
            if (o < outputLen) out[o++] = (byte) ((v >>> 16) & 0xff);
            if (o < outputLen) out[o++] = (byte) ((v >>> 8) & 0xff);
            if (o < outputLen) out[o++] = (byte) (v & 0xff);
        }
        return out;
    }

    private static int decodeChar(char c) {
        if (c == '=') return 0;
        if (c >= 128 || DECODE[c] < 0) {
            throw new IllegalArgumentException("invalid base64 char: " + c);
        }
        return DECODE[c];
    }
}
