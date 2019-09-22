package com.github.brycemcd.air_quality_2;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.content.pm.PackageManager;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    public int REQUEST_ENABLE_BT = 2;
    public static final int PERMISSION_REQUEST_FINE_LOCATION = 1;

    ListView listView;
    TextView statusTextView;
    Button searchButton;
    ArrayList<String> bluetoothDevices = new ArrayList<>();
    ArrayAdapter arrayAdapter;

    BluetoothManager bluetoothManager;
    BluetoothAdapter bluetoothAdapter;
    BluetoothLeScanner bluetoothLeScanner;

    private final long SCAN_PERIOD = 2_000; // in ms

    final private Handler handler = new Handler();


    private void resetDeviceList() {
        bluetoothDevices = new ArrayList<>();
        arrayAdapter.notifyDataSetChanged();
    }

    public void searchClicked(View view) {
//        resetDeviceList();

        statusTextView.setText("Searching ...");
        searchButton.setEnabled(false);
        startScanning();
    }

    public void resetUI() {
        statusTextView.setText("Ready to Scan");
        searchButton.setText("Scan");
        searchButton.setEnabled(true);
    }

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.i("AIR ACTION", action);

            if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                searchButton.setEnabled(true);
                statusTextView.setText("finished");
            } else if (BluetoothDevice.ACTION_FOUND.equals(action)) {
               BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

               String address = device.getAddress();
               String rssi = Integer.toString(intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE));

               Log.i("DEVICE FOUND", "ADDRESS: " + address);

               bluetoothDevices.add(address);

               arrayAdapter.notifyDataSetChanged();
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();

        listView = findViewById(R.id.listView);
        statusTextView = findViewById(R.id.statusTextView);
        searchButton = findViewById(R.id.searchButton);

        // Ensures Bluetooth is available on the device and it is enabled. If not,
        // displays a dialog requesting user permission to enable Bluetooth.
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        // FROM GH - make sure we have perms:
        // Make sure we have access coarse location enabled, if not, prompt the user to enable it
        if (this.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("This app needs location access");
            builder.setMessage("Please grant location access so this app can detect peripherals.");
            builder.setPositiveButton(android.R.string.ok, null);
            builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                            PERMISSION_REQUEST_FINE_LOCATION);
                }
            });
            builder.show();
        }


        arrayAdapter = new ArrayAdapter(this, android.R.layout.simple_list_item_1, bluetoothDevices);

        listView.setAdapter(arrayAdapter);



//        IntentFilter intentFilter = new IntentFilter();
//        intentFilter.addAction(BluetoothDevice.ACTION_FOUND);
//        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
//        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
//        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
//        registerReceiver(broadcastReceiver, intentFilter);

    }

    // Device scan callback.
    private BluetoothAdapter.LeScanCallback leScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
            String address = device.getAddress();

            Log.i("SCAN RESULT", device.getName() + " Address: " + address + " rssi: " + rssi);

            bluetoothDevices.add(address);
            arrayAdapter.notifyDataSetChanged();

            // Since I'm after only one device right now, we can stop as soon as it's found
            if (address.equals(DeviceControl.MAGIC_DEVICE_ADDRESS)) {
                magicDevice = device;
                Log.d("MainActivity leScanCallback", "Stopping early");
                bluetoothAdapter.stopLeScan(leScanCallback);
            }
        }
    };


    public void startScanning() {
        Log.i("SCAN:","start scanning");

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                bluetoothAdapter.stopLeScan(leScanCallback);
                resetUI();
            }
        }, SCAN_PERIOD);

//        ArrayList<UUID> service = new ArrayList<>();
//        // NOTE: you are here
//        UUID airQualityService = "";
//        service.add(airQualityService);

        bluetoothAdapter.startLeScan(leScanCallback);
    }

    private BluetoothDevice magicDevice;
    public void switchActivity(View view) {
        if (magicDevice == null) {
            Log.d("onListItemClick", "NO magicDevice. HALTING");
            return;
        }

        final BluetoothDevice device = magicDevice;

        if (device == null) return;

        final Intent intent = new Intent(this, DeviceControl.class);
        intent.putExtra(DeviceControl.EXTRAS_DEVICE_NAME, device.getName());
        intent.putExtra(DeviceControl.EXTRAS_DEVICE_ADDRESS, device.getAddress());

        startActivity(intent);
    }


//    public void listItemClick(ListView l, View v, int position, long id) {
    public void listItemClick(View view) {
//        final BluetoothDevice device = arrayAdapter.getDevice(position);
        if (magicDevice == null) {
            Log.d("onListItemClick", "NO magicDevice. HALTING");
            return;
        }

        final BluetoothDevice device = magicDevice;

        if (device == null) return;

        final Intent intent = new Intent(this, DeviceControl.class);
        intent.putExtra(DeviceControl.EXTRAS_DEVICE_NAME, device.getName());
        intent.putExtra(DeviceControl.EXTRAS_DEVICE_ADDRESS, device.getAddress());
//        if (mScanning) {
//            mBluetoothAdapter.stopLeScan(mLeScanCallback);
//            mScanning = false;
//        }
        startActivity(intent);
    }
}
