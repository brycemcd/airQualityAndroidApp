package com.github.brycemcd.air_quality_2;


// NOTE: I found this _very_ helpful: https://github.com/android/connectivity/blob/master/BluetoothLeGatt
// and this: https://developer.android.com/guide/topics/connectivity/bluetooth-le#java

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ExpandableListView;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;

import java.util.Calendar;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import static com.github.brycemcd.air_quality_2.OnlineQueue.getCredProvider;
import static com.github.brycemcd.air_quality_2.OnlineQueue.getSQSClient;

public class DeviceControl extends AppCompatActivity {

    public final static String ACTION_GATT_CONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.example.bluetooth.le.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA = "com.example.bluetooth.le.EXTRA_DATA";

    public static String HEART_RATE_MEASUREMENT = "00002a37-0000-1000-8000-00805f9b34fb";
    private final static String DATA_CHARACTERISTIC_UUID = "0000ffe1-0000-1000-8000-00805f9b34fb";
    private final static String DATA_CHARACTERISTIC_NOTIFICATION_DESCRIPTOR_UUID = "0x2902";

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";
    public static final String MAGIC_DEVICE_ADDRESS = "18:93:D7:14:57:B9";

    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt bluetoothGatt;

    private int mConnectionState = STATE_DISCONNECTED;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    private BluetoothLeService mBluetoothLeService = new BluetoothLeService();
    private String airQualityBTAddress = "18:93:D7:14:57:B9";
    private String mDeviceName = "foo";
    private String mDeviceAddress = airQualityBTAddress;
    private boolean mConnected = false;

    private TextView mDataField;

    private ExpandableListView mGattServicesList;
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
            new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

    private BluetoothGattCharacteristic mNotifyCharacteristic;

    private final String LIST_NAME = "NAME";
    private final String LIST_UUID = "UUID";


    LocationManager locationManager;
    LocationTracker lt = new LocationTracker();
    LocationListener locationListener;

    LocalStorage db;

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        Log.i("locationListener", "locationListener Permissions Result");

        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                Log.d("locationListener", "locationListener Permissions Result locman");
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);
            }
        } else {
            Log.d("LOCATION", "YOU DO NOT HAVE PERMISSIONS");
        }
    }


    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        setContentView(R.layout.activity_device_control);

        final Intent intent = getIntent();
        intent.putExtra(EXTRAS_DEVICE_NAME, "foo_device");
        intent.putExtra(EXTRAS_DEVICE_ADDRESS, mDeviceAddress);

        // This is set statically above
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        Log.d("device_name", mDeviceName + " " + mDeviceAddress);

        // Sets up UI references.
        ((TextView) findViewById(R.id.device_address)).setText(mDeviceAddress);
//        mGattServicesList = (ExpandableListView) findViewById(R.id.gatt_services_list);
//        mGattServicesList.setOnChildClickListener(servicesListClickListner);
//        mConnectionState = (TextView) findViewById(R.id.connection_state);
//        mDataField = (TextView) findViewById(R.id.data_value);

//        getActionBar().setTitle("HELLO WORLD");
//        getActionBar().setDisplayHomeAsUpEnabled(true);
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
//        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);


        bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        initializeBTConnction();
        connect(mDeviceAddress);

        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

        locationListener = lt.locationListener;

        try {
            // NOTE: this is just for kicks. It's not required for anything
            Location gpsLoc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            Location netLoc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

            Log.d("LOCATION", "LAST KNOWN GPS: " + gpsLoc.toString());
            Log.d("LOCATION", "LAST KNOWN NET: " + netLoc.toString());
        } catch (SecurityException e) {
            Log.d("LOCATION", "caught security exception");
        } catch (NullPointerException e) {
            // NOTE: This happens if the last location is null
            Log.d("LOCATION", "Caught NPE");
        }



        // FIXME: I think this is redundant for somewhere else
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        } else {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);
        }

        db = new LocalStorage(this);
        // NOTE: this removes a bunch of testing CRAP data
//        db.delete("air_samples", "provider = ? OR speed = ?", new String[]{
//                "network",
//                "0.0"
//        });


        getCredProvider(this);
        getSQSClient(this);

    }

    protected class SendMessage extends AsyncTask<String, Void, Boolean> {
        protected Boolean doInBackground(String[] args) {
            String jsonMsg = args[1];
            String msgId = args[0];

            OnlineQueue.addEntry(msgId, jsonMsg);

            return true;
        }

    }

    public void syncDbToSQS(View v) {
        LinkedList<AirQualityData> recordsToSync = db.getBatchRecords(200);
        new OnlineQueue().syncRecordsToSQS(recordsToSync);

        for (AirQualityData aqd : recordsToSync) {
            String[] whereClauseArgs = {
                    Long.toString(aqd.getAirQualityTime()),
                    Long.toString(aqd.getLocationTime()),
                    aqd.getSensorData()
            };
            db.deleteRow( "read_time = ? AND loc_time = ? AND sensor_value = ?",
                    whereClauseArgs);
        }
    }


    public void logAllRows(View v) {

        String cnt = Integer.toString(db.countRows());

        TextView cntV = findViewById(R.id.dbCntText);
        cntV.setText( cnt );
    }

    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.d("updateConnectionState", Integer.toString(resourceId));
//                mConnectionState.setText(resourceId);
            }
        });
    }

    private void clearUI() {
        mGattServicesList.setAdapter((SimpleExpandableListAdapter) null);
//        mDataField.setText(R.string.no_data);
    }

    private void displayData(String data) {
        if (data != null) {
//            mDataField.setText(data);
            Log.d("displayData", data);
        }
    }

    public boolean initializeBTConnction() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (bluetoothManager == null) {
            bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (bluetoothManager == null) {
                Log.e("OH NOES", "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter == null) {
            Log.e("OH NOES", "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }

    public boolean connect(final String address) {
        Log.d("OH NOES", "address: " + address);
        if (bluetoothAdapter == null || address == null) {
            Log.w("OH NOES", "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Previously connected device.  Try to reconnect.
        if (mDeviceAddress != null && address.equals(mDeviceAddress)
                && bluetoothGatt != null) {
            Log.d("OH NOES", "Trying to use an existing mBluetoothGatt for connection.");
            if (bluetoothGatt.connect()) {
                mConnectionState = STATE_CONNECTING;
                return true;
            } else {
                return false;
            }
        }

        final BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w("OH NOES", "Device not found.  Unable to connect.");
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
//        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        bluetoothGatt = device.connectGatt(this, false, mGattCallback);
        Log.d("OH NOES", "Trying to create a new connection.");
        mConnectionState = STATE_CONNECTING;
        return true;
    }

    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.d("OH NOES", "onConnectionStateChange YOU ARE HERE");
            String intentAction;
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;
                mConnectionState = STATE_CONNECTED;

                TextView t = findViewById(R.id.connection_state);
                t.setText("Connected!");

                broadcastUpdate(intentAction);
                Log.i("OH NOES", "Connected to GATT server.");
                // Attempts to discover services after successful connection.
                Log.i("OH NOES", "Attempting to start service discovery:" +
                        bluetoothGatt.discoverServices());

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;
                Log.i("OH NOES", "Disconnected from GATT server.");

                TextView t = findViewById(R.id.connection_state);
                t.setText("NOT NOT Connected!");

                broadcastUpdate(intentAction);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
            } else {
                Log.w("OH NOES", "onServicesDiscovered received: " + status);
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
            final String airReading = characteristic.getStringValue(0);
            Log.d("BLE CHAR CHANGE", "airReading: " + airReading);

            // NOTE: the final string is because of threads
            final Location lastLoc = lt.lastLocation;
            final Long currentTime = Calendar.getInstance().getTimeInMillis();

            if (lastLoc == null) {
                Log.d("BLE CHAR CHANGE","Location is Null. Avoiding NPE. Exiting");
                return;
            }

            final AirQualityData airQualityData = new AirQualityData(lastLoc, airReading, currentTime);

            db.insertAirSampleWithLocation(airQualityData);

            Log.d("BLE CHAR CHANGE", "lastLoc: " + airQualityData.toString());

            updateUIWithNewAirQualityData(airQualityData);
        }
    };

    public void updateUIWithNewAirQualityData(final AirQualityData airQualityData) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TextView charText = findViewById(R.id.rawData);
                charText.setText(airQualityData.toString());

                if (airQualityData.toString() != null) {
                    TextView locTime = findViewById(R.id.locTimeValue);
                    locTime.setText(Long.toString(airQualityData.getLocationTime()));

                    TextView readingTime = findViewById(R.id.readingValue);
                    readingTime.setText(Long.toString(airQualityData.getAirQualityTime()));

                    TextView latText = findViewById(R.id.latValue);
                    latText.setText(Double.toString(airQualityData.getLatitude()));

                    TextView longText = findViewById(R.id.longValue);
                    longText.setText(Double.toString(airQualityData.getLongitude()));

                    TextView speedText = findViewById(R.id.speedValue);
                    speedText.setText(Double.toString(airQualityData.getSpeed()));

                    TextView bearingText = findViewById(R.id.bearingValue);
                    bearingText.setText(Double.toString(airQualityData.getBearing()));
                }

                TextView sensorText = findViewById(R.id.sensorValue);
                sensorText.setText(airQualityData.getSensorData());
            }
        });
    }

    private void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;
        String uuid = null;

        ArrayList<HashMap<String, String>> gattServiceData =
                new ArrayList<HashMap<String, String>>();
        ArrayList<ArrayList<HashMap<String, String>>> gattCharacteristicData
                = new ArrayList<ArrayList<HashMap<String, String>>>();
        mGattCharacteristics =
                new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {
            HashMap<String, String> currentServiceData =
                    new HashMap<String, String>();
            uuid = gattService.getUuid().toString();
            Log.d("OH NOES", "displayGattServices Service? " + uuid);
            List<BluetoothGattCharacteristic> gattCharacteristics =
                    gattService.getCharacteristics();
            ArrayList<BluetoothGattCharacteristic> charas =
                    new ArrayList<BluetoothGattCharacteristic>();
//            // Loops through available Characteristics.
            for (BluetoothGattCharacteristic gattCharacteristic :
                    gattCharacteristics) {
                HashMap<String, String> currentCharaData =
                        new HashMap<String, String>();
                uuid = gattCharacteristic.getUuid().toString();
                Log.d("OH NOES", "displayGattChars Characteristic? " + uuid);

                if (uuid.equals(DATA_CHARACTERISTIC_UUID)) {
                    bluetoothGatt.setCharacteristicNotification(gattCharacteristic, true);
                }
            }
        }
    }

    private void broadcastUpdate(final String action) {
//        final Intent intent = new Intent(action);
        Log.d("OH NOES", "broadcastUpdate 123 " + action);
//        sendBroadcast(itent);
        if (ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
            displayGattServices( getSupportedGattServices() );
        }
    }

    public List<BluetoothGattService> getSupportedGattServices() {
        if (bluetoothGatt == null) return null;

        return bluetoothGatt.getServices();
    }

    // NOTE: I don't think this is needed
    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);

        Log.d("OH NOES", "broadcastUpdate char-uuid: " + characteristic.getUuid().toString());

        // This is special handling for the Heart Rate Measurement profile.  Data parsing is
        // carried out as per profile specifications:
        // http://developer.bluetooth.org/gatt/characteristics/Pages/CharacteristicViewer.aspx?u=org.bluetooth.characteristic.heart_rate_measurement.xml
        if (HEART_RATE_MEASUREMENT.equals(characteristic.getUuid())) {
            int flag = characteristic.getProperties();
            int format = -1;
            if ((flag & 0x01) != 0) {
                format = BluetoothGattCharacteristic.FORMAT_UINT16;
                Log.d("OH NOES", "Heart rate format UINT16.");
            } else {
                format = BluetoothGattCharacteristic.FORMAT_UINT8;
                Log.d("OH NOES", "Heart rate format UINT8.");
            }
            final int heartRate = characteristic.getIntValue(format, 1);
            Log.d("OH NOES", String.format("Received heart rate: %d", heartRate));
            intent.putExtra(EXTRA_DATA, String.valueOf(heartRate));
        } else {
            // For all other profiles, writes the data formatted in HEX.
            final byte[] data = characteristic.getValue();
            if (data != null && data.length > 0) {
                final StringBuilder stringBuilder = new StringBuilder(data.length);
                for(byte byteChar : data)
                    stringBuilder.append(String.format("%02X ", byteChar));
                intent.putExtra(EXTRA_DATA, new String(data) + "\n" + stringBuilder.toString());
            }
        }
        sendBroadcast(intent);
    }
}
