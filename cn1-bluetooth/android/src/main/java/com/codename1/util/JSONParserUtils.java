package com.codename1.util;

import com.codename1.io.JSONParser;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.Map;
import java.util.List;

public class JSONParserUtils {
    public static Map<String, Object> parse(String json) throws IOException {
        JSONParser p = new JSONParser();
        return p.parseJSON(new InputStreamReader(new ByteArrayInputStream(json.getBytes("UTF-8")), "UTF-8"));
    }
}
