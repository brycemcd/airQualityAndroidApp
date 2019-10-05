package com.github.brycemcd.air_quality_2;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.util.LinkedList;

import static android.content.Context.MODE_PRIVATE;

public class LocalStorage {
    public static final String DB_NAME = "air_quality";
    public static final String AIR_SAMPLE_TABLE_NAME = "air_samples";
    public SQLiteDatabase db;

    public LocalStorage(Context context) {
        db = context.openOrCreateDatabase(DB_NAME, MODE_PRIVATE, null);

        createDbTable();
    }

    private void createDbTable() {
        String createTable = "CREATE TABLE IF NOT EXISTS "+ AIR_SAMPLE_TABLE_NAME +" ( " +
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
        Cursor c = db.rawQuery("SELECT COUNT(*) as cnt FROM "+ AIR_SAMPLE_TABLE_NAME, null);
        c.moveToFirst();
        int cntCol = c.getColumnIndex("cnt");
        int cnt = c.getInt(cntCol);
        c.close();
        return cnt;
    }

    // NOTE: I don't like this because the caller needs to know what makes a row unique
    // and makes this a level of indirection rather than a convenience
    public void deleteRow(String whereClause, String[] whereClauseArgs) {
        db.delete(AIR_SAMPLE_TABLE_NAME, whereClause, whereClauseArgs);
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

    public LinkedList<AirQualityData> getBatchRecords(int limit) {
        LinkedList<AirQualityData> results = new LinkedList<>();

        Cursor c = db.rawQuery("SELECT * FROM "+ AIR_SAMPLE_TABLE_NAME+" LIMIT "+limit,
                null);

        c.moveToFirst();
        int i = 0;
        while (i < c.getCount()) {
            AirQualityData aqd = new AirQualityData(c);
            results.add(aqd);

            i++;
            c.moveToNext();
        }

        return results;

    }
}
