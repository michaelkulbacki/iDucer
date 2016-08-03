package com.example.android.bluetoothlegatt;

import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import java.util.UUID;

/**
 * For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display GATT services and characteristics supported by the device.  The Activity
 * communicates with {@code BluetoothLeService}, which in turn interacts with the
 * Bluetooth LE API.
 */
public class DeviceControlActivity extends Activity {
    private final static String TAG = DeviceControlActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    /* Battery Service */
    private static final UUID BATTERY_SERVICE = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb");      //
    private static final UUID BATTERY_DATA_CHAR = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb");    //

    /* Pressure Service */
    private static final UUID PRESSURE_SERVICE = UUID.fromString("a6322521-eb79-4b9f-9152-19daa4870418");      //
    private static final UUID PRESSURE_DATA_CHAR = UUID.fromString("f90ea017-f673-45b8-b00b-16a088a2ed61");    //
    private static final UUID PRESSURE_RANGE_DATA_CHAR = UUID.fromString("797b3a40-0ea1-11e4-9f5a-0002a5d5c51b");//
    private static final UUID FULL_SCALE_OUTPUT_CHAR = UUID.fromString("4ee0e280-230f-11e4-ad5b-0002a5d5c51b");  //
    private static final UUID ZERO_OUTPUT_CHAR = UUID.fromString("70189ba0-230f-11e4-bcf2-0002a5d5c51b");      //

    public static float final_pressR, final_fso, final_zero;

    /* Temperature */
    private static final UUID TEMPERATURE_DATA_CHAR = UUID.fromString("79ed3826-3cbb-4881-bbf9-410f1b18dc9c"); //

    /* Client Configuration Descriptor */
    private static final UUID CONFIG_DESCRIPTOR = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"); //

    private TextView mManufact, mModelNum, mSerial;
    private TextView mTemperature, mPressure, mBattery, mPressureR;
    private String mDeviceAddress;

    public final static String ACTION_GATT_CONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";

    private BluetoothLeService mBluetoothLeService;
    private ProgressDialog mProgress;
    private SparseArray<BluetoothDevice> mDevices;
    private BluetoothGatt mConnectedGatt;
    private BluetoothManager mBluetoothManager;
    private String mBluetoothDeviceAddress;
    private BluetoothAdapter mBluetoothAdapter;
    private boolean mConnected = false;

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
    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                //updateConnectionState(R.string.connected);
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                //updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();
                clearDisplayValues();
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.gatt_services_characteristics);

        final Intent intent = getIntent();
        String mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        mTemperature = (TextView) findViewById(R.id.text_temperature);
        mBattery = (TextView) findViewById(R.id.text_battery);
        mPressure = (TextView) findViewById(R.id.text_pressure);
        mPressureR = (TextView) findViewById(R.id.text_pressureR);

        ((TextView) findViewById(R.id.device_address)).setText(mDeviceAddress);

        mManufact = (TextView) findViewById(R.id.manufactuer_value);
        mSerial = (TextView) findViewById(R.id.serial_value);
        mModelNum = (TextView) findViewById(R.id.model_num);

        BluetoothLeService service = new BluetoothLeService();
        mManufact.setText(mDeviceName.substring(0,5));
        mSerial.setText(mDeviceName.substring(6));
        mModelNum.setText(service.getModelNum("7200-BLEE"));

        getActionBar().setTitle("Measurements");
        getActionBar().setDisplayHomeAsUpEnabled(true);
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
    }

    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Previously connected device.  Try to reconnect.
        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
                && mConnectedGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");

            return mConnectedGatt.connect();
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        mConnectedGatt = device.connectGatt(this, false, mGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        return true;
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public void disconnect() {
        if (mBluetoothAdapter == null || mConnectedGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mConnectedGatt.disconnect();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt_services, menu);
        if (mConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_connect:
                connect(mDeviceAddress);
                return true;
            case R.id.menu_disconnect:
                disconnect();
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
            default:
                BluetoothDevice device = mDevices.get(item.getItemId());
                mConnectedGatt = device.connectGatt(this, false, mGattCallback);
        }
        return super.onOptionsItemSelected(item);
    }

    private void clearDisplayValues() {
        mTemperature.setText("---");
        mPressureR.setText("---");
        mBattery.setText("---");
        mPressure.setText("---");
        mManufact.setText("---");
        mModelNum.setText("---");
        mSerial.setText("---");
    }

    /*
 * In this callback, we've created a bit of a state machine to enforce that only
 * one characteristic be read or written at a time until all of our sensors
 * are enabled and we are registered to get notifications.
 */
    public BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

        /* State Machine Tracking */
        private int mState = 0;

        public void reset() { mState = 0; }

        private void advance() { mState++; }

        /*
         * Read the data characteristic's value for each sensor explicitly
         */
        private void readNextSensor(BluetoothGatt gatt) {
            BluetoothGattCharacteristic characteristic;
            switch (mState) {
                case 0:
                    //Log.d(TAG, "Reading temperature...");
                    characteristic = gatt.getService(PRESSURE_SERVICE)
                            .getCharacteristic(TEMPERATURE_DATA_CHAR);
                    break;
                case 1:
                    //Log.d(TAG, "Reading battery...");
                    characteristic = gatt.getService(BATTERY_SERVICE)
                            .getCharacteristic(BATTERY_DATA_CHAR);
                    break;
                case 2:
                    //Log.d(TAG, "Reading Range Data...");
                    characteristic = gatt.getService(PRESSURE_SERVICE)
                            .getCharacteristic(PRESSURE_RANGE_DATA_CHAR);
                    break;
                case 3:
                    //Log.d(TAG, "Reading FSO...");
                    characteristic = gatt.getService(PRESSURE_SERVICE)
                            .getCharacteristic(FULL_SCALE_OUTPUT_CHAR);
                    break;
                case 4:
                    //Log.d(TAG, "Reading Zero Output...");
                    characteristic = gatt.getService(PRESSURE_SERVICE)
                            .getCharacteristic(ZERO_OUTPUT_CHAR);
                    break;
                case 5:
                    //Log.d(TAG, "Reading pressure...");
                    characteristic = gatt.getService(PRESSURE_SERVICE)
                            .getCharacteristic(PRESSURE_DATA_CHAR);
                    break;
                default:
                    mHandler.sendEmptyMessage(MSG_DISMISS);
                    Log.i(TAG, "All Sensors Enabled");
                    return;
            }
            gatt.readCharacteristic(characteristic);
        }

        /*
         * Enable notification of changes on the data characteristic for each sensor
         * by writing the ENABLE_NOTIFICATION_VALUE flag to that characteristic's
         * configuration descriptor.
         */
        private void setNotifyNextSensor(BluetoothGatt gatt) {
            BluetoothGattCharacteristic characteristic, tChar, pChar;
            //int tpState = 0;
            switch (mState) {
                case 0:
                    //Log.d(TAG, "Set notify temperature..");
                    characteristic = gatt.getService(PRESSURE_SERVICE)
                            .getCharacteristic(TEMPERATURE_DATA_CHAR);
                    break;
                case 1:
                    //Log.d(TAG, "Set notify battery...");
                    characteristic = gatt.getService(BATTERY_SERVICE)
                            .getCharacteristic(BATTERY_DATA_CHAR);
                    break;
                case 2:
                    //Log.d(TAG, "Set notify Range Data...");
                    characteristic = gatt.getService(PRESSURE_SERVICE)
                            .getCharacteristic(PRESSURE_RANGE_DATA_CHAR);
                    final_pressR = SensorTagData.calculatePressureVars(characteristic);
                    break;
                case 3:
                    //Log.d(TAG, "Set notify Full Scale Output...");
                    characteristic = gatt.getService(PRESSURE_SERVICE)
                            .getCharacteristic(FULL_SCALE_OUTPUT_CHAR);
                    final_fso = SensorTagData.calculatePressureVars(characteristic);
                    break;
                case 4:
                    //Log.d(TAG, "Set notify Zero Output...");
                    characteristic = gatt.getService(PRESSURE_SERVICE)
                            .getCharacteristic(ZERO_OUTPUT_CHAR);
                    final_zero = SensorTagData.calculatePressureVars(characteristic);
                    break;
                case 5:
                    //Log.d(TAG, "Set notify pressure....");
                    characteristic = gatt.getService(PRESSURE_SERVICE)
                            .getCharacteristic(PRESSURE_DATA_CHAR);
                    break;
                default:
                    mHandler.sendEmptyMessage(MSG_DISMISS);
                    Log.i(TAG, "All Sensors Enabled");
                    return;
            }
            if(mState == 0 || mState == 5) {
                gatt.setCharacteristicNotification(characteristic, true);
                BluetoothGattDescriptor desc = characteristic.getDescriptor(CONFIG_DESCRIPTOR);
                desc.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE); // use notify or indicate
                gatt.writeDescriptor(desc);
                if(mState == 5){
                    /*try {
                        Thread.sleep(1000);                 //1000 milliseconds is one second.
                    } catch(InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }*/
                    reset();
                }
            }
            if(mState != 0 && mState != 5) {advance(); readNextSensor(gatt);}
        }

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.d(TAG, "Connection State Change: "+status+" -> "+connectionState(newState));
            String intentAction;
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;
                broadcastUpdate(intentAction);
                Log.i(TAG, "Connected to GATT server.");
                // Attempts to discover services after successful connection.
                Log.i(TAG, "Attempting to start service discovery:" +
                        mConnectedGatt.discoverServices());
                /*
                 * Once successfully connected, we must next discover all the services on the
                 * device before we can read and write their characteristics.
                 */
                gatt.discoverServices();
                mHandler.sendMessage(Message.obtain(null, MSG_PROGRESS, "Discovering Services..."));
            } else if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_DISCONNECTED) {
                /*
                 * If at any point we disconnect, send a message to clear the weather values
                 * out of the UI
                 */
                intentAction = ACTION_GATT_DISCONNECTED;
                //mConnectionState = STATE_DISCONNECTED;
                Log.i(TAG, "Disconnected from GATT server.");
                broadcastUpdate(intentAction);
                mHandler.sendEmptyMessage(MSG_CLEAR);
            } else if (status != BluetoothGatt.GATT_SUCCESS) {
                /*
                 * If there is a failure at any stage, simply disconnect
                 */
                gatt.disconnect();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                mHandler.sendMessage(Message.obtain(null, MSG_PROGRESS, "Enabling Sensors..."));
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
            /*
             * With services discovered, we are going to reset our state machine and start
             * working through the sensors we need to enable
             */
            reset();
            readNextSensor(gatt);
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            //For each read, pass the data up to the UI thread to update the display
            if (BATTERY_DATA_CHAR.equals(characteristic.getUuid())) {
                mHandler.sendMessage(Message.obtain(null, MSG_BATTERY, characteristic));
            }
            if (PRESSURE_DATA_CHAR.equals(characteristic.getUuid())) {
                mHandler.sendMessage(Message.obtain(null, MSG_PRESSURE, characteristic));
            }
            if (TEMPERATURE_DATA_CHAR.equals(characteristic.getUuid())) {
                mHandler.sendMessage(Message.obtain(null, MSG_TEMP, characteristic));
            }
            if (PRESSURE_RANGE_DATA_CHAR.equals(characteristic.getUuid())) {
                mHandler.sendMessage(Message.obtain(null, MSG_PRESS_R, characteristic));
            }

            //After reading the initial value, next we enable notifications
            setNotifyNextSensor(gatt);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            //After writing the enable flag, next we read the initial value
            readNextSensor(gatt);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            /*
             * After notifications are enabled, all updates from the device on characteristic
             * value changes will be posted here.  Similar to read, we hand these up to the
             * UI thread to update the display.

            if (BATTERY_DATA_CHAR.equals(characteristic.getUuid())) {
                mHandler.sendMessage(Message.obtain(null, MSG_BATTERY, characteristic));
            }*/
            if (PRESSURE_DATA_CHAR.equals(characteristic.getUuid())) {
                mHandler.sendMessage(Message.obtain(null, MSG_PRESSURE, characteristic));
                Log.d(TAG, "Pressure Update Received...");
            }
            if (TEMPERATURE_DATA_CHAR.equals(characteristic.getUuid())) {
                mHandler.sendMessage(Message.obtain(null, MSG_TEMP, characteristic));
                Log.d(TAG, "Temperature Update Received...");
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            //Once notifications are enabled, we move to the next sensor and start over with enable
            advance();
            readNextSensor(gatt);
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            Log.d(TAG, "Remote RSSI: "+rssi);
        }

        private String connectionState(int status) {
            switch (status) {
                case BluetoothProfile.STATE_CONNECTED:
                    return "Connected";
                case BluetoothProfile.STATE_DISCONNECTED:
                    return "Disconnected";
                case BluetoothProfile.STATE_CONNECTING:
                    return "Connecting";
                case BluetoothProfile.STATE_DISCONNECTING:
                    return "Disconnecting";
                default:
                    return String.valueOf(status);
            }
        }
    };

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    /*
     * We have a Handler to process event results on the main thread
     */
    private static final int MSG_BATTERY = 101;
    private static final int MSG_PRESSURE = 102;
    private static final int MSG_TEMP = 103;
    private static final int MSG_PRESS_R = 104;
    private static final int MSG_PROGRESS = 201;
    private static final int MSG_DISMISS = 202;
    private static final int MSG_CLEAR = 301;
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            BluetoothGattCharacteristic characteristic;
            switch (msg.what) {
                case MSG_BATTERY:
                    characteristic = (BluetoothGattCharacteristic) msg.obj;
                    if (characteristic.getValue() == null) {
                        Log.w(TAG, "Error obtaining Battery value");
                        return;
                    }
                    updateBatteryValue(characteristic);
                    break;
                case MSG_PRESSURE:
                    characteristic = (BluetoothGattCharacteristic) msg.obj;
                    if (characteristic.getValue() == null) {
                        Log.w(TAG, "Error obtaining pressure value");
                        return;
                    }
                    updatePressureValue(characteristic);
                    break;
                case MSG_PRESS_R:
                    characteristic = (BluetoothGattCharacteristic) msg.obj;
                    if (characteristic.getValue() == null) {
                        Log.w(TAG, "Error obtaining pressure R value");
                        return;
                    }
                    setPressureR(final_pressR);
                    break;
                case MSG_TEMP:
                    characteristic = (BluetoothGattCharacteristic) msg.obj;
                    if (characteristic.getValue() == null) {
                        Log.w(TAG, "Error obtaining pressure value");
                        return;
                    }
                    updateTempValue(characteristic);
                    break;
                case MSG_PROGRESS:
                    if(mProgress == null){
                        break;
                    }
                    mProgress.setMessage((String) msg.obj);
                    if (!mProgress.isShowing()) {
                        mProgress.show();
                    }
                    break;
                case MSG_DISMISS:
                    if(mProgress == null){
                        break;
                    }
                    mProgress.hide();
                    break;
                case MSG_CLEAR:
                    clearDisplayValues();
                    break;
            }
        }
    };

    /* Methods to extract sensor data and update the UI */

    private void setPressureR(float data){
        mPressureR.setText(Float.toString(data));
    }

    private void updateBatteryValue(BluetoothGattCharacteristic characteristic) {
        String battery = SensorTagData.extractBattery(characteristic);
        mBattery.setText(battery);
    }

    private void updateTempValue(BluetoothGattCharacteristic characteristic) {
        String temp = SensorTagData.extractTemperature(characteristic);
        mTemperature.setText(temp);
    }

    private void updatePressureValue(BluetoothGattCharacteristic characteristic) {
        String pressure = SensorTagData.extractPressure(characteristic);
        mPressure.setText(pressure);
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }
}
