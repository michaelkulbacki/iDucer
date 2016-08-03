package com.example.android.bluetoothlegatt;

import android.bluetooth.BluetoothGattCharacteristic;
import android.hardware.Sensor;
import android.util.Log;

import java.math.BigDecimal;
import java.util.Arrays;



public class SensorTagData {
    private final static String TAG = SensorTagData.class.getSimpleName();
    public static String extractTemperature(BluetoothGattCharacteristic characteristic) {
        final byte[] data = characteristic.getValue();
        String tempADC = "";
        if (data != null && data.length > 0) {
            tempADC = reverse(stringBuilder(characteristic));
            float f = stringToFloat(tempADC);
            String str = Float.toString((f - 1690) / 10);

            return str + " \u00b0" + "C";
        }
        return "Error getting Temperature";
    }

    public static String extractBattery(BluetoothGattCharacteristic c) {
        final byte[] data = c.getValue();
        String battery = "0";

        if (data != null && data.length > 0) {
            if (Arrays.toString(data).equals("[100]")) {
                battery = data.toString().substring(1, 4);
            } else {
                battery = Arrays.toString(data).substring(1, 3);
            }
        }
        return battery+ "\u0025";
    }

    public static String extractPressure(BluetoothGattCharacteristic characteristic) {
        DeviceControlActivity d = new DeviceControlActivity();
        final byte[] data = characteristic.getValue();
        String tempADC = "";
        if (data != null && data.length > 0) {
            tempADC = reverse(stringBuilder(characteristic));
            float f = stringToFloat(tempADC);
            f = calculatePressure(d.final_zero, d.final_pressR, d.final_fso, f);
            BigDecimal result = roundFloat(f, 2);     // rounds to 2 decimal points
            String str = result.toString();
            return str +" PSIg";
        }
        return "Error getting Pressure";
    }

    /* -----------------Personal Helper Methods------------------- */
    public static float calculatePressure(float zero_output, float pressure_range,
                                          float fs_output, float pressure_data){
        float GAIN = pressure_range /
                (fs_output - zero_output);
        float OFFSET = zero_output * GAIN;
        float PRESS_OUT = (pressure_data*GAIN)-OFFSET;
        return PRESS_OUT;
    }

    public static String reverse(String str){
        if(str.length() <= 1){
            return str;
        }
        String reverse1;
        String reverse2;
        reverse1 = str.substring(0,2);
        reverse2 = str.substring(3,5);
        return reverse2+reverse1;
    }

    public static String stringBuilder(BluetoothGattCharacteristic characteristic){
        String str;
        final byte[] data = characteristic.getValue();
        final StringBuilder stringBuilder = new StringBuilder(data.length);
        for(byte byteChar : data)
            stringBuilder.append(String.format("%02X ", byteChar));
        str = stringBuilder.toString();
        return str;
    }

    public static float stringToFloat(String str){
        return Float.valueOf(Long.parseLong(str, 16));
    }

    public static BigDecimal roundFloat(float d, int decimalPlace) {
        BigDecimal bd = new BigDecimal(Float.toString(d));
        bd = bd.setScale(decimalPlace, BigDecimal.ROUND_HALF_UP);
        return bd;
    }

    public static Float calculatePressureVars(BluetoothGattCharacteristic characteristic){
        final byte[] data = characteristic.getValue();
        String temp = "";
        if (data != null && data.length > 0) {
            temp = reverse(stringBuilder(characteristic));
            float f = stringToFloat(temp);
            //String str = Float.toString(f);

            return f;
        }
        return Float.valueOf(0);
    }

    /* ------------------END------------------- */
}
