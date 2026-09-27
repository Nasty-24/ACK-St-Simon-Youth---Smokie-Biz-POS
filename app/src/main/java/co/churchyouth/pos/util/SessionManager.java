package co.churchyouth.pos.util;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * There's no HTTP session here — it's one app on one device — so "session"
 * just means "which user id is currently logged in", held in
 * SharedPreferences so it survives the app being backgrounded, plus the
 * same 30-minute idle auto-logout the original web version had.
 */
public class SessionManager {

    private static final String PREFS = "church_pos_session";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_LAST_ACTIVITY = "last_activity";
    private static final long IDLE_LIMIT_SECONDS = 30 * 60;

    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void login(long userId) {
        prefs.edit()
            .putLong(KEY_USER_ID, userId)
            .putLong(KEY_LAST_ACTIVITY, nowEpoch())
            .apply();
    }

    public void logout() {
        prefs.edit().clear().apply();
    }

    public void touch() {
        prefs.edit().putLong(KEY_LAST_ACTIVITY, nowEpoch()).apply();
    }

    /** Returns the logged-in user id, or -1 if nobody is logged in or the session went idle too long. */
    public long currentUserId() {
        long userId = prefs.getLong(KEY_USER_ID, -1);
        if (userId == -1) return -1;
        long last = prefs.getLong(KEY_LAST_ACTIVITY, 0);
        if (nowEpoch() - last > IDLE_LIMIT_SECONDS) {
            logout();
            return -1;
        }
        return userId;
    }

    public boolean isLoggedIn() {
        return currentUserId() != -1;
    }

    private long nowEpoch() {
        return System.currentTimeMillis() / 1000L;
    }
}
