package com.github.brycemcd.air_quality_2;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.util.Log;

import java.util.Calendar;

import static android.content.Context.MODE_PRIVATE;

public class LocalStorage {
    private static final String dbName = "air_quality";
    private static final String airSampleTableName = "air_samples";
    public SQLiteDatabase db;

    public LocalStorage(Context context) {
        db = context.openOrCreateDatabase(dbName, MODE_PRIVATE, null);

        createDbTable();
    }

    private void createDbTable() {
        String createTable = "CREATE TABLE IF NOT EXISTS "+airSampleTableName+" ( " +
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

    public int countRows() {
        Cursor c = db.rawQuery("SELECT COUNT(*) as cnt FROM "+airSampleTableName, null);
        c.moveToFirst();
        int cntCol = c.getColumnIndex("cnt");
        int cnt = c.getInt(cntCol);
        c.close();
        return cnt;
    }

    // NOTE: I don't like this because the caller needs to know what makes a row unique
    // and makes this a level of indirection rather than a convenience
    public void deleteRow(String whereClause, String[] whereClauseArgs) {

        db.delete(airSampleTableName, whereClause, whereClauseArgs);
    }

    public void insertAirSampleWithLocation(AirQualityData airQualityData) {

        Log.d("DATABASE", "attempting insert");

        String insertStmt = String.format("INSERT INTO air_samples " +
                        "(sensor_value, read_time, loc_time, lat, long, speed, bearing, altitude, accuracy, provider)" +
                        "VALUES ('%s', %d, %d, %f, %f, %f, %f, %f, %f, '%s')",
                airQualityData.getSensorData(),
                airQualityData.getAirQualityTime(),
                airQualityData.getLocationTime(),
                airQualityData.getLatitude(),
                airQualityData.getLongitude(),
                airQualityData.getSpeed(),
                airQualityData.getBearing(),
                airQualityData.getAltitude(),
                airQualityData.getAccuracy(),
                airQualityData.getProvider()
                );

        db.execSQL(insertStmt);

        Log.d("DATABASE", "inserted row?");
    }
}
