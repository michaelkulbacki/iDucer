package com.example.android.bluetoothlegatt;

import java.util.HashMap;

public class SampleGattAttributes {
    private static HashMap<String, String> attributes = new HashMap();
    public static String HEART_RATE_MEASUREMENT = "00002a37-0000-1000-8000-00805f9b34fb";
    public static String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";
    public static String PRESSURE_DATA_CHAR = "f90ea017-f673-45b8-b00b-16a088a2ed61";
    public static String TEMPERATURE_DATA_CHAR = "79ed3826-3cbb-4881-bbf9-410f1b18dc9c";
    public static String PRESSURE_RANGE_DATA_CHAR = "797b3a40-0ea1-11e4-9f5a-0002a5d5c51b";
    public static String FULL_SCALE_OUTPUT_CHAR = "4ee0e280-230f-11e4-ad5b-0002a5d5c51b";
    public static String ZERO_OUTPUT_CHAR = "70189ba0-230f-11e4-bcf2-0002a5d5c51b";
    public static String PRESSURE_UNITS_CHAR = "88d91836-188c-4944-a11a-3252d189b7ca";
    public static String MANUFACT_NAME_CHAR = "00002a29-0000-1000-8000-00805f9b34fb";
    public static String MODEL_NUM_CHAR = "00002a24-0000-1000-8000-00805f9b34fb";
    public static String BATTERY_VALUE = "00002a19-0000-1000-8000-00805f9b34fb";


    static {
        attributes.put("00001800-0000-1000-8000-00805f9b34fb", "Bluetooth Generic Access");
            attributes.put("00002a00-0000-1000-8000-00805f9b34fb", "Device Serial Number");

        attributes.put("0000180a-0000-1000-8000-00805f9b34fb", "Device Information");
            attributes.put(MANUFACT_NAME_CHAR, "Manufacturer Name");
            attributes.put(MODEL_NUM_CHAR, "Model Number");

        attributes.put("a6322521-eb79-4b9f-9152-19daa4870418", "GP:50 Pressure & Temperature Data");
            attributes.put(PRESSURE_DATA_CHAR, "Pressure");
            attributes.put(TEMPERATURE_DATA_CHAR, "Temperature");
            attributes.put(PRESSURE_RANGE_DATA_CHAR, "Pressure Range");
            attributes.put(FULL_SCALE_OUTPUT_CHAR, "Full Scale Output");
            attributes.put(ZERO_OUTPUT_CHAR, "Zero Output");
            attributes.put(PRESSURE_UNITS_CHAR, "Pressure Units");

        attributes.put("0000180f-0000-1000-8000-00805f9b34fb", "Battery Life");
            attributes.put(BATTERY_VALUE, "Percentage");

        attributes.put(HEART_RATE_MEASUREMENT, "Heart Rate");
    }

    public static String lookup(String uuid, String defaultName) {
        String name = attributes.get(uuid);
        return name == null ? defaultName : name;
    }
}
