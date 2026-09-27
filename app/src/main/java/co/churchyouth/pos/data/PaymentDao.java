package co.churchyouth.pos.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

import co.churchyouth.pos.model.Payment;

public class PaymentDao {

    private final DbHelper dbHelper;

    public PaymentDao(Context context) {
        this.dbHelper = DbHelper.getInstance(context);
    }

    /** Records a debt repayment and reduces the customer's balance, atomically. */
    public boolean recordPayment(long customerId, long amountCents, String method, long receivedBy, String notes) {
        if (amountCents <= 0) return false;
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues cv = new ContentValues();
            cv.put("customer_id", customerId);
            cv.put("amount_cents", amountCents);
            cv.put("payment_method", method);
            cv.put("received_by", receivedBy);
            cv.put("notes", notes);
            cv.put("payment_at", System.currentTimeMillis() / 1000L);
            db.insertOrThrow("payments", null, cv);

            db.execSQL("UPDATE customers SET balance_cents = balance_cents - ? WHERE id = ?",
                new Object[]{amountCents, customerId});

            ContentValues auditCv = new ContentValues();
            auditCv.put("user_id", receivedBy);
            auditCv.put("action", "payment_received");
            auditCv.put("details", "customer_id=" + customerId + " amount=" + amountCents);
            auditCv.put("created_at", System.currentTimeMillis() / 1000L);
            db.insert("audit_log", null, auditCv);

            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    public List<Payment> forCustomer(long customerId, int limit) {
        List<Payment> list = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor c = db.rawQuery(
                "SELECT p.*, u.full_name AS received_by_name FROM payments p " +
                "JOIN users u ON u.id = p.received_by WHERE p.customer_id = ? " +
                "ORDER BY p.payment_at DESC LIMIT ?",
                new String[]{String.valueOf(customerId), String.valueOf(limit)})) {
            while (c.moveToNext()) {
                Payment p = new Payment();
                p.id = c.getLong(c.getColumnIndexOrThrow("id"));
                p.customerId = c.getLong(c.getColumnIndexOrThrow("customer_id"));
                p.amountCents = c.getLong(c.getColumnIndexOrThrow("amount_cents"));
                p.paymentMethod = c.getString(c.getColumnIndexOrThrow("payment_method"));
                p.receivedBy = c.getLong(c.getColumnIndexOrThrow("received_by"));
                p.receivedByName = c.getString(c.getColumnIndexOrThrow("received_by_name"));
                p.notes = c.getString(c.getColumnIndexOrThrow("notes"));
                p.paymentAtEpoch = c.getLong(c.getColumnIndexOrThrow("payment_at"));
                list.add(p);
            }
        }
        return list;
    }
}
