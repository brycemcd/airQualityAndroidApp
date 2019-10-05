package com.github.brycemcd.air_quality_2;

import android.location.Location;
import android.support.annotation.NonNull;

import java.util.Calendar;

public class AirQualityData {
    public Double longitude;
    public Double latitude;
    public float bearing;
    public float speed;
    public Double altitude;
    public float accuracy;
    public String provider;
    public long locationTime;
    public long airQualityTime;
    public String sensorData;

    public AirQualityData(@NonNull Location location, final String airReading, Long sensorTime) {
        setAirQualityTime(sensorTime);
        setSensorData(airReading);

        setLongitude(location);
        setLatitude(location);
        setBearing(location);
        setSpeed(location);
        setAltitude(location);
        setAccuracy(location);
        setProvider(location);
        setLocationTime(location);
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Location location) {
        this.longitude = location.getLongitude();
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Location location) {
        this.latitude = location.getLatitude();
    }

    public float getBearing() {
        return bearing;
    }

    public void setBearing(Location location) {
        this.bearing = location.getBearing();
    }

    public float getSpeed() {
        return speed;
    }

    public void setSpeed(Location location) {
        this.speed = location.getSpeed();
    }

    public Double getAltitude() {
        return altitude;
    }

    public void setAltitude(Location location) {
        this.altitude = location.getAltitude();
    }

    public float getAccuracy() {
        return accuracy;
    }

    public void setAccuracy(Location location) {
        this.accuracy = location.getAccuracy();
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(Location location) {
        this.provider = location.getProvider();
    }

    public long getLocationTime() {
        return locationTime;
    }

    public void setLocationTime(Location location) {
        this.locationTime = location.getTime();
    }

    /**
     * Unix timestamp when the data was collected
     *
     * @return Long
     */
    public long getAirQualityTime() {
        return airQualityTime;
    }

    public void setAirQualityTime(long airQualityTime) {
        this.airQualityTime = airQualityTime;
    }

    /**
    * A string of comma separated values read directly from the device and
    * transmitted via bluetooth. Example: 400,512,11,11
    **/
    public String getSensorData() {
        return sensorData;
    }

    public void setSensorData(String sensorData) {
        this.sensorData = sensorData;
    }

    public boolean hasLocation() {
        return (getLongitude() != null) && (getLatitude() != null);
    }

    public String toString() {
        String output = "";
        if (hasLocation()) {
            output += Double.toString(getLongitude());

            output += ",";
            output += Double.toString(getLatitude());

            output += ",";
            output += Float.toString(getBearing());

            output += ",";
            output += getSpeed();

            output += ",";
            output += getAltitude();

            output += ",";
            output += getAccuracy();

            output += ",";
            output += getProvider();

            output += ",";
            output += getLocationTime(); // This is the time the loc was created

            output += ",";
            output += getAirQualityTime() - getLocationTime();
        }

        return output;
    }
}
