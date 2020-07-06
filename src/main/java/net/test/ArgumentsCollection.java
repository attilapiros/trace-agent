package net.test;

import java.util.HashMap;

public class ArgumentsCollection extends HashMap<String, String> {

    public int parseInt(String key, int defaultValue) {
        String valueStr = this.get(key);
        int valueInt = defaultValue;
        if (valueStr != null) {
            try {
                valueInt = Integer.valueOf(valueStr);
            } catch (NumberFormatException nfe) {
                System.err.println("TraceAgent (counter) invalid `" + key + "` param value: `" + valueStr + "` using the default: " + defaultValue);

            }
        }
        return valueInt;
    }

    public long parseLong(String key, long defaultValue) {
        String valueStr = this.get(key);
        long valueLong = defaultValue;
        if (valueStr != null) {
            try {
                valueLong = Long.valueOf(valueStr);
            } catch (NumberFormatException nfe) {
                System.err.println("TraceAgent (counter) invalid `" + key + "` param value: `" + valueStr + "` using the default: " + defaultValue);

            }
        }
        return valueLong;
    }

}