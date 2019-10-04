package com.github.brycemcd.air_quality_2;

import android.location.Location;
import android.location.LocationListener;
import android.os.Bundle;
import android.util.Log;

public class LocationTracker {
    private String LOG_TAG = LocationListener.class.toString();
    public Location lastLocation;

    public LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            Log.d(LOG_TAG, "onLocationChanged");
            Log.d(LOG_TAG, location.toString());
            lastLocation = location;
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            Log.d(LOG_TAG, "onStatusChanged");

        }

        @Override
        public void onProviderEnabled(String provider) {
            Log.d(LOG_TAG, "onProviderEnabled");

        }

        @Override
        public void onProviderDisabled(String provider) {
            Log.d(LOG_TAG, "PROVIDER DISABLED");
        }
    };
}
