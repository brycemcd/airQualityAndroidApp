package com.github.brycemcd.air_quality_2;


// NOTE: I found this _very_ helpful: https://github.com/android/connectivity/blob/master/BluetoothLeGatt
// and this: https://developer.android.com/guide/topics/connectivity/bluetooth-le#java

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import java.util.LinkedList;

import static com.github.brycemcd.air_quality_2.OnlineQueue.getCredProvider;
import static com.github.brycemcd.air_quality_2.OnlineQueue.getSQSClient;

public class DeviceControl extends AppCompatActivity {

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";
    public static final String MAGIC_DEVICE_ADDRESS = "18:93:D7:14:57:B9";

    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    BluetoothLeService bluetoothLeService;

    private String airQualityBTAddress = "18:93:D7:14:57:B9";
    private String mDeviceAddress = airQualityBTAddress;

    LocationManager locationManager;
    public static LocationTracker lt = new LocationTracker();
    LocationListener locationListener;

    public static LocalStorage db;
    private static Context persCont; // a persistent context so I can statically update Views

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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.main_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);
        switch (item.getItemId()) {

            case R.id.deleteAll:
                Log.d("MENU SELECT", "DELETE ALL");
                db.deleteAll();
                break;

            case R.id.syncDataOffDevice:
                Log.d("MENU SELECT", "Sync Data");
                syncDbToSQS();
                break;

            case R.id.disconnectBT:
                Log.d("MENU SELECT", "disconnect BT");
                if (bluetoothLeService != null) {
                    bluetoothLeService.disconnect();
                }
                break;

            case R.id.connectBT:
                Log.d("MENU SELECT", "connect BT");
                if (bluetoothLeService != null) {
                    bluetoothLeService.connect(this, airQualityBTAddress);
                }
                break;

            default:
                return false;
        }
        updateUIWithDBRowCount();
        return true;

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        setContentView(R.layout.activity_device_control);

        // BLUETOOTH STUFF
        // Sets up UI references.
        ((TextView) findViewById(R.id.device_address)).setText(mDeviceAddress);

        persCont = this;
        initializeBTConnction();
        bluetoothLeService = new BluetoothLeService(bluetoothManager);
        int result = bluetoothLeService.connect(this, airQualityBTAddress);
        Log.d("BLOOTOOTH", "CONNECTION RESULT: " + Integer.toString(result));

        // LOCATION STUFF
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


        // LOCAL STORAGE STUFF

        db = new LocalStorage(this);

        // SQS (OnlineQueue) Stuff
        getCredProvider(this);
        getSQSClient(this);

    }

    public void syncDbToSQS() {
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


    public void logDataPush(View v) { updateUIWithDBRowCount(); }

    public void updateUIWithDBRowCount() {
        String cnt = Integer.toString(db.countRows());

        TextView cntV = findViewById(R.id.dbCntText);
        cntV.setText( cnt );
    }

    public boolean initializeBTConnction() {

        bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter == null) {
            Log.e("OH NOES", "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }

    public static void updateUIWithNewAirQualityData(final AirQualityData airQualityData) {
        TextView charText = ((Activity)persCont).findViewById(R.id.rawData);
        charText.setText(airQualityData.toString());

        if (airQualityData.toString() != null) {
            TextView locTime = ((Activity)persCont).findViewById(R.id.locTimeValue);
            locTime.setText(Long.toString(airQualityData.getLocationTime()));

            TextView readingTime = ((Activity)persCont).findViewById(R.id.readingValue);
            readingTime.setText(Long.toString(airQualityData.getAirQualityTime()));

            TextView latText = ((Activity)persCont).findViewById(R.id.latValue);
            latText.setText(Double.toString(airQualityData.getLatitude()));

            TextView longText = ((Activity)persCont).findViewById(R.id.longValue);
            longText.setText(Double.toString(airQualityData.getLongitude()));

            TextView speedText = ((Activity)persCont).findViewById(R.id.speedValue);
            speedText.setText(Double.toString(airQualityData.getSpeed()));

            TextView bearingText = ((Activity)persCont).findViewById(R.id.bearingValue);
            bearingText.setText(Double.toString(airQualityData.getBearing()));
        }

        TextView sensorText = ((Activity)persCont).findViewById(R.id.sensorValue);
        sensorText.setText(airQualityData.getSensorData());

    }

    public static void updateUIWithConnectionState(int connectionState) {
        TextView t = ((Activity)persCont).findViewById(R.id.connection_state);

        switch (connectionState) {
            case BluetoothLeService.STATE_CONNECTED:
                t.setText("Connected!");
                break;
            case BluetoothLeService.STATE_DISCONNECTED:
                t.setText("BT NOT Connected!");
                break;
            default:
                t.setText("I HAVE NO IDEA! cs: " + Integer.toString(connectionState));
                break;
        }
    }

    public static void updateUIWithLocationError() {
        TextView t = ((Activity)persCont).findViewById(R.id.rawData);
        t.setText("LOCATION ERROR. WAITING FOR LOCATION UPDATE");
    }

    public static void bluetoothCharacteristicChangedCallback(AirQualityData airQualityData) {
        // NOTE: the final string is because of threads
        Log.d("DEVICe CONTROL", airQualityData.toString());
        db.insertAirSampleWithLocation(airQualityData);
        updateUIWithNewAirQualityData(airQualityData);
        Log.d("DEVICe CONTROL", "New Data Updated");
    }
}
