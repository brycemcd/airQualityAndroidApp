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
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Handler;
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

import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.ListQueuesResult;
import com.amazonaws.services.sqs.model.SendMessageBatchRequestEntry;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageResult;
import com.google.gson.JsonObject;

import org.w3c.dom.Text;

import java.util.Arrays;
import java.util.Calendar;


import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

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
    LocationListener locationListener;
    Location lastLocation;

    SQLiteDatabase db;

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

        // NOTE: this works!
//        mBluetoothLeService.setBtMgr(bluetoothManager);
//        mBluetoothLeService.setItent(gattServiceIntent);
//        mBluetoothLeService.setCntxt(this);
//        mBluetoothLeService.initialize();
//        mBluetoothLeService.connect(mDeviceAddress);
        initializeBTConnction();
        connect(mDeviceAddress);

        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

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


        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                Log.d("LOCATION", "onLocationChanged");
                Log.d("LOCATION", location.toString());
                lastLocation = location;
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
                Log.d("LOCATION", "onStatusChanged");

            }

            @Override
            public void onProviderEnabled(String provider) {
                Log.d("LOCATION", "onProviderEnabled");

            }

            @Override
            public void onProviderDisabled(String provider) {

            }
        };

        // FIXME: I think this is redundant for somewhere else
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        } else {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);
        }

        db = this.openOrCreateDatabase("air_quality", MODE_PRIVATE, null);
        createDbTable();
        // NOTE: this removes a bunch of testing CRAP data
//        db.delete("air_samples", "provider = ? OR speed = ?", new String[]{
//                "network",
//                "0.0"
//        });


        // Initialize the Amazon Cognito credentials provider
//        CognitoCachingCredentialsProvider credentialsProvider = new CognitoCachingCredentialsProvider(
//                getApplicationContext(),
//                "us-east-1:c453ed2a-a5bf-418c-b0e2-ab34a81c8e0d", // Identity pool ID
//                Regions.US_EAST_1 // Region
//        );

        getCredProvider(this);
        getSQSClient(this);

    }
    public void syncDbToSQS(View v) {
        Cursor c = db.rawQuery("SELECT * FROM air_samples LIMIT 200", null);

        c.moveToFirst();

        Log.d("DATABASE", "ROW COUNT: " + Integer.toString(c.getCount()));

//        // NOTE: you are here
//        String[] dbCols = {"sensor_value", "read_time", "loc_time", "lat", "long", "speed",
//                "bearing", "altitude", "accuracy", "provider"};
//        HashMap<String, Class> dbCols = new HashMap<>();
//        dbCols.put("sensor_value", String.class);

        int i = 0;
        Long j = 0L;
        while (i < c.getCount()) {
            JsonObject msg = new JsonObject();
            msg.addProperty("sensor_value", c.getString(c.getColumnIndex("sensor_value")));
            msg.addProperty("read_time", c.getLong(c.getColumnIndex("read_time")));
            msg.addProperty("loc_time", c.getLong(c.getColumnIndex("loc_time")));
            msg.addProperty("lat", c.getDouble(c.getColumnIndex("lat")));
            msg.addProperty("long", c.getDouble(c.getColumnIndex("long")));
            msg.addProperty("speed", c.getDouble(c.getColumnIndex("speed")));
            msg.addProperty("bearing", c.getDouble(c.getColumnIndex("bearing")));
            msg.addProperty("altitude", c.getDouble(c.getColumnIndex("altitude")));
            msg.addProperty("accuracy", c.getDouble(c.getColumnIndex("accuracy")));
            msg.addProperty("provider", c.getString(c.getColumnIndex("provider")));

            Log.d("QUEUE", "Sending msg: " + msg.toString());




            // FIXME: there's a bug in here where the last batch of messages won't get sent
            // because the total count is not divisible by 10
            new SendMessage().execute(
                    Long.toString(++j),
                    msg.toString()
            );
//            new SendMessage().execute(msg.toString());

            String[] whereClauseArgs = {
                    Long.toString(c.getLong(c.getColumnIndex("read_time"))),
                    Long.toString(c.getLong(c.getColumnIndex("loc_time"))),
                    c.getString(c.getColumnIndex("sensor_value"))

            };
            db.delete("air_samples",
                    "read_time = ? AND loc_time = ? AND sensor_value = ?",
                    whereClauseArgs);
            i++;
            c.moveToNext();
        }
    }

    private static AmazonSQSClient sqsClient;
    private static CognitoCachingCredentialsProvider sCredProvider;
    private static String qURL = "https://sqs.us-east-1.amazonaws.com/304286125266/air_quality_dev";
    static List<SendMessageBatchRequestEntry> msgBatch = new LinkedList<>();

    private class SendMessage extends AsyncTask<String, Void, Boolean> {

//        protected SendMessageResult doInBackground(String[] args) {
        protected Boolean doInBackground(String[] args) {

            Log.d("QUEUE", "sending message");
            // NOTE: this works for single messages
//            return sqsClient.sendMessage(qURL, jsonMsg);
            String jsonMsg = args[1];
            String msgId = args[0];
            SendMessageBatchRequestEntry smbre = new SendMessageBatchRequestEntry(msgId, jsonMsg);
            msgBatch.add(smbre);

            if (msgBatch.size() == 10) {
                Log.i("BATCH QUEUE", "SENDING BATCH");
                sqsClient.sendMessageBatch(qURL, msgBatch);
                msgBatch = new LinkedList<>();
            }

            return true;

        }

        protected void onPostExecute() {
           return;
        }
    }

    /**
     * Gets an instance of CognitoCachingCredentialsProvider which is
     * constructed using the given Context.
     *
     * @param context An Context instance.
     * @return A default credential provider.
     */
    private static CognitoCachingCredentialsProvider getCredProvider(Context context) {
        if (sCredProvider == null) {
            sCredProvider = new CognitoCachingCredentialsProvider(
                    context.getApplicationContext(),
                    "us-east-1:c453ed2a-a5bf-418c-b0e2-ab34a81c8e0d", // Identity pool ID
                    Regions.US_EAST_1);
        }
        return sCredProvider;
    }

    /**
     * Gets an instance of a S3 client which is constructed using the given
     * Context.
     *
     * @param context An Context instance.
     * @return A default S3 client.
     */
    public static AmazonSQSClient getSQSClient(Context context) {
        if (sqsClient == null) {
            sqsClient = new AmazonSQSClient(getCredProvider(context.getApplicationContext()));
        }
        return sqsClient;
    }


    private void createDbTable() {
        String createTable = "CREATE TABLE IF NOT EXISTS air_samples( " +
                " sensor_value VARCHAR, " +
                " read_time INTEGER, " +
                " loc_time INTEGER, " +
                " lat DOUBLE, " +
                " long DOUBLE, " +
                " speed DOUBLE, " +
                " bearing DOUBLE, " +
                " altitude DOUBLE, " +
                " accuracy DOUBLE, " +
                " provider VARCHAR)";

        db.execSQL(createTable);
        Log.d("DATABASE", "db created");
    }

    public void logAllRows(View v) {
//        getAllDbRows();

        Cursor c = db.rawQuery("SELECT COUNT(*) as cnt FROM air_samples", null);

        int cntCol = c.getColumnIndex("cnt");

        c.moveToFirst();

        String cnt = Integer.toString(c.getInt(cntCol));

        TextView cntV = findViewById(R.id.dbCntText);
        cntV.setText( cnt );

        c.close();
    }

    private void getAllDbRows() {
        Cursor c = db.rawQuery("SELECT * FROM air_samples", null);

        c.moveToFirst();

        Log.d("DATABASE", "ROW COUNT: " + Integer.toString(c.getCount()));

        int rdTmIdx = c.getColumnIndex("read_time");
        int sensorIdx = c.getColumnIndex("sensor_value");

        int i = 0;
        while (i < c.getCount()) {
            Log.d("DATABASE", "ROW= " + c.getString(sensorIdx) + " " + Long.toString(c.getLong(rdTmIdx)));
            i++;
            c.moveToNext();
        }
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

        TextView charText;
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            final String airReading = characteristic.getStringValue(0);
            Log.d("OH NOES", "onCharacteristicChanged - airReading: " + airReading);

            // NOTE: the final string is because of threads
            final Location lastLoc = lastLocation;
            String output = airReading;
            output += ",";

            final Long currentTime = Calendar.getInstance().getTimeInMillis();
            output += currentTime;

            if (lastLoc != null) {
                output += ",";
                output += Double.toString( lastLoc.getLongitude() );

                output += ",";
                output += Double.toString( lastLoc.getLatitude() );

                output += ",";
                output += Float.toString( lastLoc.getBearing() );

                output += ",";
                output += lastLoc.getSpeed();

                output += ",";
                output += lastLoc.getAltitude();

                output += ",";
                output += lastLoc.getAccuracy();

                output += ",";
                output += lastLoc.getProvider();

                output += ",";
                output += lastLoc.getTime(); // This is the time the loc was created

                output += ",";
                output += currentTime - lastLoc.getTime();

                Log.d("OH NOES", "onCharacteristicChanged - lastLoc: " + lastLoc.toString());

                Log.d("DATABASE", "attempting insert");

                String insertStmt = String.format("INSERT INTO air_samples " +
                                "(sensor_value, read_time, loc_time, lat, long, speed, bearing, altitude, accuracy, provider)" +
                                "VALUES ('%s', %d, %d, %f, %f, %f, %f, %f, %f, '%s')",
                        airReading,
                        currentTime,
                        lastLoc.getTime(),
                        lastLoc.getLatitude(),
                        lastLoc.getLongitude(),
                        lastLoc.getSpeed(),
                        lastLoc.getBearing(),
                        lastLoc.getAltitude(),
                        lastLoc.getAccuracy(),
                        lastLoc.getProvider()
                );


                db.execSQL(insertStmt);

                Log.d("DATABASE", "inserted row?");
            }

            final String outputToo = output;


            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    charText = findViewById(R.id.rawData);
                    charText.setText(outputToo);

                    if (lastLoc != null) {
                        TextView locTime = findViewById(R.id.locTimeValue);
                        locTime.setText(Long.toString(lastLoc.getTime()));

                        TextView readingTime = findViewById(R.id.readingValue);
                        readingTime.setText(Long.toString(currentTime));

                        TextView latText = findViewById(R.id.latValue);
                        latText.setText(Double.toString(lastLoc.getLatitude()));

                        TextView longText = findViewById(R.id.longValue);
                        longText.setText(Double.toString(lastLoc.getLongitude()));

                        TextView speedText = findViewById(R.id.speedValue);
                        speedText.setText(Double.toString(lastLoc.getSpeed()));

                        TextView bearingText = findViewById(R.id.bearingValue);
                        bearingText.setText(Double.toString(lastLoc.getBearing()));
                    }

                    TextView sensorText = findViewById(R.id.sensorValue);
                    sensorText.setText(airReading);
                }
            });
//            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
        }
    };

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
