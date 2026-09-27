package co.churchyouth.pos.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

public class SettingsDao {

    private final DbHelper dbHelper;

    public SettingsDao(Context context) {
        this.dbHelper = DbHelper.getInstance(context);
    }

    public String get(String key, String fallback) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT value FROM settings WHERE key = ?", new String[]{key})) {
            return c.moveToFirst() ? c.getString(0) : fallback;
        }
    }

    public void set(String key, String value) {
        ContentValues cv = new ContentValues();
        cv.put("key", key);
        cv.put("value", value);
        dbHelper.getWritableDatabase().insertWithOnConflict("settings", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }
}
