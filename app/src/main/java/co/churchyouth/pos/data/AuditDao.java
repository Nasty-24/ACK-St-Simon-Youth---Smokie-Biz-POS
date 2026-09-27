package co.churchyouth.pos.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

public class AuditDao {

    public static class Entry {
        public long id;
        public String action;
        public String details;
        public long createdAtEpoch;
        public String userName; // may be null (system / deleted user)
    }

    private final DbHelper dbHelper;

    public AuditDao(Context context) {
        this.dbHelper = DbHelper.getInstance(context);
    }

    public void log(Long userId, String action, String details) {
        ContentValues cv = new ContentValues();
        if (userId != null) cv.put("user_id", userId); else cv.putNull("user_id");
        cv.put("action", action);
        cv.put("details", details);
        cv.put("created_at", System.currentTimeMillis() / 1000L);
        dbHelper.getWritableDatabase().insert("audit_log", null, cv);
    }

    public List<Entry> recent(int limit) {
        List<Entry> list = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor c = db.rawQuery(
                "SELECT a.id, a.action, a.details, a.created_at, u.full_name FROM audit_log a " +
                "LEFT JOIN users u ON u.id = a.user_id ORDER BY a.id DESC LIMIT ?",
                new String[]{String.valueOf(limit)})) {
            while (c.moveToNext()) {
                Entry e = new Entry();
                e.id = c.getLong(0);
                e.action = c.getString(1);
                e.details = c.getString(2);
                e.createdAtEpoch = c.getLong(3);
                e.userName = c.getString(4);
                list.add(e);
            }
        }
        return list;
    }
}
