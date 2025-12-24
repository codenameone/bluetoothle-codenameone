package com.codename1.util;

import java.util.Iterator;
import java.util.Map;
import java.util.List;

public class JSONUtils {
    public static String toJSON(Object o) {
        if (o == null) {
            return "null";
        }
        if (o instanceof String) {
            return "\"" + escapeString((String)o) + "\"";
        }
        if (o instanceof Number || o instanceof Boolean) {
            return String.valueOf(o);
        }
        if (o instanceof Map) {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            Map m = (Map)o;
            Iterator it = m.keySet().iterator();
            boolean first = true;
            while (it.hasNext()) {
                if (!first) {
                    sb.append(",");
                }
                Object key = it.next();
                Object val = m.get(key);
                sb.append(toJSON(String.valueOf(key)));
                sb.append(":");
                sb.append(toJSON(val));
                first = false;
            }
            sb.append("}");
            return sb.toString();
        }
        if (o instanceof List) {
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            List l = (List)o;
            boolean first = true;
            for (Object item : l) {
                if (!first) {
                    sb.append(",");
                }
                sb.append(toJSON(item));
                first = false;
            }
            sb.append("]");
            return sb.toString();
        }
        if (o instanceof Object[]) {
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            Object[] l = (Object[])o;
            boolean first = true;
            for (Object item : l) {
                if (!first) {
                    sb.append(",");
                }
                sb.append(toJSON(item));
                first = false;
            }
            sb.append("]");
            return sb.toString();
        }
        return "\"" + escapeString(String.valueOf(o)) + "\"";
    }

    private static String escapeString(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i=0; i<s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < ' ' || (c >= '\u0080' && c < '\u00a0') || (c >= '\u2000' && c < '\u2100')) {
                        String t = "000" + Integer.toHexString(c);
                        sb.append("\\u" + t.substring(t.length() - 4));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}
