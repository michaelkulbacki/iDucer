/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.bluetoothlegatt;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
public class BluetoothLeService extends Service {
    private final static String TAG = BluetoothLeService.class.getSimpleName();

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;
    private BluetoothGattCallback callback;
    private int mConnectionState = STATE_DISCONNECTED;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    public final static String ACTION_GATT_CONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.example.bluetooth.le.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA =
            "com.example.bluetooth.le.EXTRA_DATA";

    public final static UUID UUID_HEART_RATE_MEASUREMENT = UUID.fromString(SampleGattAttributes.HEART_RATE_MEASUREMENT);

    // 2 bit hex characteristics
    public final static UUID UUID_TEMPERATURE_AB = UUID.fromString(SampleGattAttributes.TEMPERATURE_DATA_CHAR);
    public final static UUID UUID_PRESSURE_AB = UUID.fromString(SampleGattAttributes.PRESSURE_DATA_CHAR);
    public final static UUID UUID_PRESSURE_R_AB = UUID.fromString(SampleGattAttributes.PRESSURE_RANGE_DATA_CHAR);
    public final static UUID UUID_FSO_AB = UUID.fromString(SampleGattAttributes.FULL_SCALE_OUTPUT_CHAR);
    public final static UUID UUID_ZERO_AB = UUID.fromString(SampleGattAttributes.ZERO_OUTPUT_CHAR);
    public final static UUID UUID_BATTERY_LIFE = UUID.fromString(SampleGattAttributes.BATTERY_VALUE);
    public final static UUID UUID_MANUFACT_NAME = UUID.fromString(SampleGattAttributes.MANUFACT_NAME_CHAR);

    // 2 bit converted hex
    public float final_temp;
    public float final_press;
    public String final_pressR;
    public String final_fso;
    public String final_zero;
    public String final_battery;
    public String tempADC, pressADC;


    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;
                mConnectionState = STATE_CONNECTED;
                broadcastUpdate(intentAction);
                Log.i(TAG, "Connected to GATT server.");
                // Attempts to discover services after successful connection.
                Log.i(TAG, "Attempting to start service discovery:" +
                        mBluetoothGatt.discoverServices());

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;
                Log.i(TAG, "Disconnected from GATT server.");
                broadcastUpdate(intentAction);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
        }
    };

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);

        if (UUID_TEMPERATURE_AB.equals(characteristic.getUuid())) {         // temperature----------
            final byte[] data = characteristic.getValue();
            if (data != null && data.length > 0) {
                tempADC = reverse(stringBuilder(characteristic));
                float f = stringToFloat(tempADC);
                String str = Float.toString((f-1690)/10);
                //readCharacteristic(characteristic);  // how to keep updating

                Log.d(TAG, String.format("Received Temperature: "+ str));
                intent.putExtra(EXTRA_DATA, str +  " \u00b0"+"C");
            }

        } else if (UUID_PRESSURE_AB.equals(characteristic.getUuid())) {     // pressure------------
            final byte[] data = characteristic.getValue();
            if (data != null && data.length > 0) {
                pressADC = reverse(stringBuilder(characteristic));
                float f = stringToFloat(pressADC);
                final_press = calculatePressure(final_zero, final_pressR, final_fso,f);
                BigDecimal result = roundFloat(final_press, 2);     // rounds to 2 decimal points
                String str = result.toString();
                //readCharacteristic(characteristic);

                Log.d(TAG, String.format("Received Pressure: "+ str));
                intent.putExtra(EXTRA_DATA, str + " PSIg");
            }
        } else if (UUID_PRESSURE_R_AB.equals(characteristic.getUuid())) {   // pressure range-------
            final byte[] data = characteristic.getValue();
            if (data != null && data.length > 0) {
                final_pressR = reverse(stringBuilder(characteristic));
                float f = stringToFloat(final_pressR);
                String str = Float.toString(f);

                Log.d(TAG, String.format("Received Pressure Range: "+ str));
                intent.putExtra(EXTRA_DATA, str);
            }
        } else if (UUID_FSO_AB.equals(characteristic.getUuid())) {   // full scale output-----------
            final byte[] data = characteristic.getValue();
            if (data != null && data.length > 0) {
                final_fso = reverse(stringBuilder(characteristic));
                float f = stringToFloat(final_fso);
                String str = Float.toString(f);

                Log.d(TAG, String.format("Received Full Scale Output: "+ str));
                intent.putExtra(EXTRA_DATA, str);
            }
        } else if (UUID_ZERO_AB.equals(characteristic.getUuid())) {   // zero output----------------
            final byte[] data = characteristic.getValue();
            if (data != null && data.length > 0) {
                final_zero = reverse(stringBuilder(characteristic));
                float f = stringToFloat(final_zero);
                String str = Float.toString(f);

                Log.d(TAG, String.format("Received Zero Output: "+ str));
                intent.putExtra(EXTRA_DATA, str);
            }
        } else if (UUID_BATTERY_LIFE.equals(characteristic.getUuid())) {   // battery life----------
            final byte[] data = characteristic.getValue();
            if (data != null && data.length > 0) {

                Log.d(TAG, String.format("Received Battery Value: "+ data));
                if(Arrays.toString(data) == "[100]") {
                    final_battery = Arrays.toString(data).substring(1,4);
                }else{
                    final_battery = Arrays.toString(data).substring(1,3);
                }
                intent.putExtra(EXTRA_DATA, final_battery + " \u0025");
            }
        } else {
            // For all other profiles, writes the data formatted in HEX.----------------------------
            final byte[] data = characteristic.getValue();
            if (data != null && data.length > 0) {
                intent.putExtra(EXTRA_DATA, new String(data));
            }
        }
        sendBroadcast(intent);
    }

    public float calculatePressure(String zero_output, String pressure_range,
                                   String fs_output, float pressure_data){
        float GAIN = stringToFloat(pressure_range) /
                (stringToFloat(fs_output) - stringToFloat(zero_output));
        float OFFSET = stringToFloat(zero_output)*GAIN;
        float PRESS_OUT = (pressure_data*GAIN)-OFFSET;
        return PRESS_OUT;
    }

    public String reverse(String str){
        if(str.length() <= 1){
            return str;
        }
        String reverse1;
        String reverse2;
        reverse1 = str.substring(0,2);
        reverse2 = str.substring(3,5);
        return reverse2+reverse1;
    }

    public String stringBuilder(BluetoothGattCharacteristic characteristic){
        String str;
        final byte[] data = characteristic.getValue();
        final StringBuilder stringBuilder = new StringBuilder(data.length);
        for(byte byteChar : data)
            stringBuilder.append(String.format("%02X ", byteChar));
        str = stringBuilder.toString();
        return str;
    }

    public float stringToFloat(String str){
        return Float.valueOf(Long.parseLong(str, 16));
    }

    public static BigDecimal roundFloat(float d, int decimalPlace) {
        BigDecimal bd = new BigDecimal(Float.toString(d));
        bd = bd.setScale(decimalPlace, BigDecimal.ROUND_HALF_UP);
        return bd;
    }

    public class LocalBinder extends Binder {
        BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }

    public String getManufact(String str){

        return str.substring(0,5);
    }
    public String getModelNum(String str){

        return str;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
        close();
        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();

    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     *
     * @return Return true if the connection is initiated successfully. The connection result
     *         is reported asynchronously through the
     *         {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     *         callback.
     */
    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Previously connected device.  Try to reconnect.
        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
                && mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            if (mBluetoothGatt.connect()) {
                mConnectionState = STATE_CONNECTING;
                return true;
            } else {
                return false;
            }
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;
        return true;
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    /**
     * Request a read on a given {@code BluetoothGattCharacteristic}. The read result is reported
     * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     *
     * @param characteristic The characteristic to read from.
     */
    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.readCharacteristic(characteristic);
    }

    /**
     * Enables or disables notification on a give characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param enabled If true, enable notification.  False otherwise.
     */
    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
                                              boolean enabled) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);

        if (UUID_PRESSURE_AB.equals(characteristic.getUuid())) {
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                    UUID.fromString(SampleGattAttributes.CLIENT_CHARACTERISTIC_CONFIG));
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
            mBluetoothGatt.writeDescriptor(descriptor);
        }
        if (UUID_TEMPERATURE_AB.equals(characteristic.getUuid())) {
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                    UUID.fromString(SampleGattAttributes.CLIENT_CHARACTERISTIC_CONFIG));
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
            mBluetoothGatt.writeDescriptor(descriptor);
        }
    }

    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after {@code BluetoothGatt#discoverServices()} completes successfully.
     *
     * @return A {@code List} of supported services.
     */
    public List<BluetoothGattService> getSupportedGattServices() {
        if (mBluetoothGatt == null) return null;

        return mBluetoothGatt.getServices();
    }
}
