package com.github.brycemcd.air_quality_2;

import android.database.Cursor;
import android.location.Location;
import android.support.annotation.NonNull;

import com.google.gson.JsonObject;

import java.util.Calendar;

public class AirQualityData {
    public Double altitude;
    public Double latitude;
    public Double longitude;
    public String provider;
    public String sensorData;
    public float accuracy;
    public float bearing;
    public float speed;
    public long airQualityTime;
    public long locationTime;

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

    public AirQualityData(Cursor c) {

        setAirQualityTime(c.getLong(c.getColumnIndex("read_time")));
        setSensorData(c.getString(c.getColumnIndex("sensor_value")));

        setLocationTime(c.getLong(c.getColumnIndex("loc_time")));
        setLatitude(c.getDouble(c.getColumnIndex("lat")));
        setLongitude(c.getDouble(c.getColumnIndex("long")));
        setSpeed(c.getFloat(c.getColumnIndex("speed")));
        setBearing(c.getFloat(c.getColumnIndex("bearing")));
        setAltitude(c.getDouble(c.getColumnIndex("altitude")));
        setAccuracy(c.getFloat(c.getColumnIndex("accuracy")));
        setProvider(c.getString(c.getColumnIndex("provider")));
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Location location) {
        this.longitude = location.getLongitude();
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Location location) {
        this.latitude = location.getLatitude();
    }
    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public float getBearing() {
        return bearing;
    }

    public void setBearing(Location location) {
        this.bearing = location.getBearing();
    }

    public void setBearing(Float bearing) {
        this.bearing = bearing;
    }

    public float getSpeed() {
        return speed;
    }

    public void setSpeed(Location location) {
        this.speed = location.getSpeed();
    }

    public void setSpeed(Float speed) {
        this.speed = speed;
    }

    public Double getAltitude() {
        return altitude;
    }

    public void setAltitude(Location location) {
        this.altitude = location.getAltitude();
    }

    public void setAltitude(Double altitude) {
        this.altitude = altitude;
    }

    public float getAccuracy() {
        return accuracy;
    }

    public void setAccuracy(Location location) {
        this.accuracy = location.getAccuracy();
    }

    public void setAccuracy(Float accuracy) {
        this.accuracy = accuracy;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(Location location) {
        this.provider = location.getProvider();
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public long getLocationTime() {
        return locationTime;
    }

    public void setLocationTime(Location location) {
        this.locationTime = location.getTime();
    }

    public void setLocationTime(Long unixtime) {
        this.locationTime = unixtime;
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

    public JsonObject toJson() {
        JsonObject msg = new JsonObject();
        msg.addProperty("sensor_value", getSensorData());
        msg.addProperty("sensor_read_time", getAirQualityTime());
        msg.addProperty("loc_time", getLocationTime());
        msg.addProperty("latitude", getLatitude());
        msg.addProperty("longitude", getLongitude());
        msg.addProperty("speed", getSpeed());
        msg.addProperty("bearing", getBearing());
        msg.addProperty("altitude", getAltitude());
        msg.addProperty("accuracy", getAccuracy());
        msg.addProperty("provider", getProvider());

        return msg;
    }
}
