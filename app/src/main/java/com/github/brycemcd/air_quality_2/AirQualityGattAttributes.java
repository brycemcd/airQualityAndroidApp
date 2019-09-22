package com.github.brycemcd.air_quality_2;

import java.util.HashMap;

public class AirQualityGattAttributes {

    private static HashMap<String, String> attributes = new HashMap();

    static {
        // Sample Services.
        attributes.put("0000180a-0000-1000-8000-00805f9b34fb", "Device Information Service");
        attributes.put("00001801-0000-1000-8000-00805f9b34fb", "Custom Service");
        // Sample Characteristics.
        attributes.put("00002a29-0000-1000-8000-00805f9b34fb", "Manufacturer Name String");
    }

    public static String lookup(String uuid, String defaultName) {
        String name = attributes.get(uuid);
        return name == null ? defaultName : name;
    }
}
