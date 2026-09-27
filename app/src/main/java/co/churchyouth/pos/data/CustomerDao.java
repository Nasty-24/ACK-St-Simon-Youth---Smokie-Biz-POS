package co.churchyouth.pos.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

import co.churchyouth.pos.model.Customer;

public class CustomerDao {

    private final DbHelper dbHelper;

    public CustomerDao(Context context) {
        this.dbHelper = DbHelper.getInstance(context);
    }

    public Customer findById(long id) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT * FROM customers WHERE id = ?", new String[]{String.valueOf(id)})) {
            return c.moveToFirst() ? fromCursor(c) : null;
        }
    }

    public List<Customer> listAll() {
        return query("SELECT * FROM customers WHERE is_active = 1 ORDER BY balance_cents DESC, full_name", null);
    }

    public List<Customer> search(String q) {
        String like = "%" + q + "%";
        return query("SELECT * FROM customers WHERE is_active = 1 AND (full_name LIKE ? OR phone LIKE ?) ORDER BY full_name",
            new String[]{like, like});
    }

    public long totalOwed() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT COALESCE(SUM(balance_cents),0) FROM customers WHERE balance_cents > 0", null)) {
            return c.moveToFirst() ? c.getLong(0) : 0;
        }
    }

    public List<Customer> topDebtors(int limit) {
        return query("SELECT * FROM customers WHERE balance_cents > 0 ORDER BY balance_cents DESC LIMIT " + limit, null);
    }

    /** Returns the new customer's id, or -1 if the phone number is already used by someone else. */
    public long insert(String fullName, String phone, long creditLimitCents) {
        ContentValues cv = new ContentValues();
        cv.put("full_name", fullName);
        cv.put("phone", (phone == null || phone.trim().isEmpty()) ? null : phone.trim());
        cv.put("credit_limit_cents", creditLimitCents);
        cv.put("created_at", System.currentTimeMillis() / 1000L);
        try {
            return dbHelper.getWritableDatabase().insertOrThrow("customers", null, cv);
        } catch (Exception e) {
            return -1;
        }
    }

    /** Adjusts the running balance by a signed amount (positive = customer now owes more). */
    public void adjustBalance(long customerId, long deltaCents) {
        dbHelper.getWritableDatabase().execSQL(
            "UPDATE customers SET balance_cents = balance_cents + ? WHERE id = ?",
            new Object[]{deltaCents, customerId});
    }

    private List<Customer> query(String sql, String[] args) {
        List<Customer> list = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor c = db.rawQuery(sql, args)) {
            while (c.moveToNext()) list.add(fromCursor(c));
        }
        return list;
    }

    private Customer fromCursor(Cursor c) {
        Customer cu = new Customer();
        cu.id = c.getLong(c.getColumnIndexOrThrow("id"));
        cu.fullName = c.getString(c.getColumnIndexOrThrow("full_name"));
        cu.phone = c.getString(c.getColumnIndexOrThrow("phone"));
        cu.creditLimitCents = c.getLong(c.getColumnIndexOrThrow("credit_limit_cents"));
        cu.balanceCents = c.getLong(c.getColumnIndexOrThrow("balance_cents"));
        cu.notes = c.getString(c.getColumnIndexOrThrow("notes"));
        cu.isActive = c.getInt(c.getColumnIndexOrThrow("is_active")) == 1;
        cu.createdAtEpoch = c.getLong(c.getColumnIndexOrThrow("created_at"));
        return cu;
    }
}
