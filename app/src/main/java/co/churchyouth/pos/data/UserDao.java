package co.churchyouth.pos.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

import co.churchyouth.pos.model.User;
import co.churchyouth.pos.util.PasswordUtil;

public class UserDao {

    private final DbHelper dbHelper;

    public UserDao(Context context) {
        this.dbHelper = DbHelper.getInstance(context);
    }

    public User findByUsername(String username) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT * FROM users WHERE username = ?", new String[]{username})) {
            return c.moveToFirst() ? fromCursor(c) : null;
        }
    }

    public User findById(long id) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT * FROM users WHERE id = ?", new String[]{String.valueOf(id)})) {
            return c.moveToFirst() ? fromCursor(c) : null;
        }
    }

    public List<User> listAll() {
        List<User> list = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT * FROM users ORDER BY role, full_name", null)) {
            while (c.moveToNext()) list.add(fromCursor(c));
        }
        return list;
    }

    public int countActiveAdmins() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT COUNT(*) FROM users WHERE role='admin' AND is_active=1", null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    /** Returns the new user's id, or -1 if the username is already taken. */
    public long insert(String fullName, String username, String plaintextPassword, String role) {
        PasswordUtil.Hashed h = PasswordUtil.hash(plaintextPassword);
        ContentValues cv = new ContentValues();
        cv.put("full_name", fullName);
        cv.put("username", username);
        cv.put("password_hash", h.hash);
        cv.put("password_salt", h.salt);
        cv.put("role", role);
        cv.put("created_at", nowEpoch());
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        try {
            return db.insertOrThrow("users", null, cv);
        } catch (Exception e) {
            return -1;
        }
    }

    public void updateProfile(long id, String fullName, String role, boolean isActive) {
        ContentValues cv = new ContentValues();
        cv.put("full_name", fullName);
        cv.put("role", role);
        cv.put("is_active", isActive ? 1 : 0);
        dbHelper.getWritableDatabase().update("users", cv, "id = ?", new String[]{String.valueOf(id)});
    }

    public void resetPassword(long id, String newPlaintextPassword) {
        PasswordUtil.Hashed h = PasswordUtil.hash(newPlaintextPassword);
        ContentValues cv = new ContentValues();
        cv.put("password_hash", h.hash);
        cv.put("password_salt", h.salt);
        cv.put("failed_logins", 0);
        cv.putNull("locked_until");
        dbHelper.getWritableDatabase().update("users", cv, "id = ?", new String[]{String.valueOf(id)});
    }

    public static final int MAX_FAILED_LOGINS = 5;
    public static final long LOCKOUT_SECONDS = 15 * 60;

    /** Call after a failed password check. Locks the account once the threshold is hit. */
    public void recordFailedLogin(User user) {
        int failed = user.failedLogins + 1;
        Long lockUntil = null;
        if (failed >= MAX_FAILED_LOGINS) {
            lockUntil = nowEpoch() + LOCKOUT_SECONDS;
            failed = 0;
        }
        ContentValues cv = new ContentValues();
        cv.put("failed_logins", failed);
        if (lockUntil != null) cv.put("locked_until", lockUntil); else cv.putNull("locked_until");
        dbHelper.getWritableDatabase().update("users", cv, "id = ?", new String[]{String.valueOf(user.id)});
    }

    public void recordSuccessfulLogin(long id) {
        ContentValues cv = new ContentValues();
        cv.put("failed_logins", 0);
        cv.putNull("locked_until");
        cv.put("last_login_at", nowEpoch());
        dbHelper.getWritableDatabase().update("users", cv, "id = ?", new String[]{String.valueOf(id)});
    }

    private long nowEpoch() {
        return System.currentTimeMillis() / 1000L;
    }

    private User fromCursor(Cursor c) {
        User u = new User();
        u.id = c.getLong(c.getColumnIndexOrThrow("id"));
        u.fullName = c.getString(c.getColumnIndexOrThrow("full_name"));
        u.username = c.getString(c.getColumnIndexOrThrow("username"));
        u.passwordHash = c.getString(c.getColumnIndexOrThrow("password_hash"));
        u.passwordSalt = c.getString(c.getColumnIndexOrThrow("password_salt"));
        u.role = c.getString(c.getColumnIndexOrThrow("role"));
        u.isActive = c.getInt(c.getColumnIndexOrThrow("is_active")) == 1;
        u.failedLogins = c.getInt(c.getColumnIndexOrThrow("failed_logins"));
        int lockedIdx = c.getColumnIndexOrThrow("locked_until");
        u.lockedUntilEpoch = c.isNull(lockedIdx) ? null : c.getLong(lockedIdx);
        int lastLoginIdx = c.getColumnIndexOrThrow("last_login_at");
        u.lastLoginEpoch = c.isNull(lastLoginIdx) ? null : c.getLong(lastLoginIdx);
        u.createdAtEpoch = c.getLong(c.getColumnIndexOrThrow("created_at"));
        return u;
    }
}
