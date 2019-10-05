package com.github.brycemcd.air_quality_2;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.location.Location;
import android.util.Log;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;

/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
public class BluetoothLeService {

    public static final int STATE_DISCONNECTED = 0;
    public static final int STATE_CONNECTING = 1;
    public static final int STATE_CONNECTED = 2;

    public final static String ACTION_GATT_CONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.example.bluetooth.le.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA = "com.example.bluetooth.le.EXTRA_DATA";

    // NOTE: this is the characteristic that publishes the sensor data
    private final static String DATA_CHARACTERISTIC_UUID = "0000ffe1-0000-1000-8000-00805f9b34fb";

    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt bluetoothGatt;

    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
            new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

    public BluetoothLeService(BluetoothManager bluetoothMan) {
        bluetoothManager = bluetoothMan;
        bluetoothAdapter = bluetoothManager.getAdapter();
    }

    public int connect(Context context, final String address) {
        context = context;
        Log.d("OH NOES", "address: " + address);
        if (bluetoothAdapter == null || address == null) {
            Log.w("OH NOES", "BluetoothAdapter not initialized or unspecified address.");
            return STATE_DISCONNECTED;
        }

        // Previously connected device.  Try to reconnect.
        if (address != null && bluetoothGatt != null) {
            Log.d("OH NOES", "Trying to use an existing mBluetoothGatt for connection.");
            if (bluetoothGatt.connect()) {
                return STATE_CONNECTING;
            } else {
                return STATE_DISCONNECTED;
            }
        }

        final BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w("OH NOES", "Device not found.  Unable to connect.");
            return STATE_DISCONNECTED;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        bluetoothGatt = device.connectGatt(context, false, mGattCallback);
        Log.d("OH NOES", "Trying to create a new connection.");
        return STATE_CONNECTING;
    }

    public void disconnect() {
        if (bluetoothGatt != null) {
            bluetoothGatt.disconnect();
        }
    }

    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.d("OH NOES", "onConnectionStateChange YOU ARE HERE");
            String intentAction;
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;

                DeviceControl.updateUIWithConnectionState(STATE_CONNECTED);

                broadcastUpdate(intentAction);
                Log.i("OH NOES", "Connected to GATT server.");
                // Attempts to discover services after successful connection.
                Log.i("OH NOES", "Attempting to start service discovery:" +
                        bluetoothGatt.discoverServices());

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                Log.i("OH NOES", "Disconnected from GATT server.");
                DeviceControl.updateUIWithConnectionState(STATE_DISCONNECTED);

                broadcastUpdate(intentAction);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);

                walkGattServices( getSupportedGattServices() );
            } else {
                Log.w("OH NOES", "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
//                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            final String airReading = characteristic.getStringValue(0);
            Log.d("BLE CHAR CHANGE", "airReading: " + airReading);

            // NOTE: the final string is because of threads
            final Location lastLoc = DeviceControl.lt.lastLocation;
            final Long currentTime = Calendar.getInstance().getTimeInMillis();

            if (lastLoc == null) {
                Log.d("BLE CHAR CHANGE","Location is Null. Avoiding NPE. Exiting");
                DeviceControl.updateUIWithLocationError();
                return;
            }

            final AirQualityData airQualityData = new AirQualityData(lastLoc, airReading, currentTime);
            DeviceControl.bluetoothCharacteristicChangedCallback(airQualityData);
        }
    };

    private void broadcastUpdate(final String action) {
//        final Intent intent = new Intent(action);
        Log.d("OH NOES", "broadcastUpdate 123 " + action);
        if (ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
//            walkGattServices( getSupportedGattServices() );
        }
    }

    private List<BluetoothGattService> getSupportedGattServices() {
        if (bluetoothGatt == null) return null;

        return bluetoothGatt.getServices();
    }

    /**
     * For each service, this code walks through the characteristics. If the characteristic
     * is the one that I know about then I make a call to get a notification when the
     * characteristic is updated.
     *
     * @param gattServices
     */
    private void walkGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;
        String uuid = null;

        ArrayList<HashMap<String, String>> gattServiceData =
                new ArrayList<HashMap<String, String>>();
        ArrayList<ArrayList<HashMap<String, String>>> gattCharacteristicData
                = new ArrayList<ArrayList<HashMap<String, String>>>();
        mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {
            HashMap<String, String> currentServiceData =
                    new HashMap<String, String>();
            uuid = gattService.getUuid().toString();
            Log.d("GattServiceDiscovery", "walkGattServices Service? " + uuid);
            List<BluetoothGattCharacteristic> gattCharacteristics =
                    gattService.getCharacteristics();
            ArrayList<BluetoothGattCharacteristic> charas =
                    new ArrayList<BluetoothGattCharacteristic>();

//            // Loops through available Characteristics.
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                HashMap<String, String> currentCharaData = new HashMap<String, String>();
                uuid = gattCharacteristic.getUuid().toString();
                Log.d("GattServiceDiscovery", "displayGattChars Characteristic? " + uuid);

                if (uuid.equals(DATA_CHARACTERISTIC_UUID)) {
                    bluetoothGatt.setCharacteristicNotification(gattCharacteristic, true);
                }
            }
        }
    }
}

