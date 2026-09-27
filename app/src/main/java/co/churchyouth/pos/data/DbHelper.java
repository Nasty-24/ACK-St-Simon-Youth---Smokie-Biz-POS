package co.churchyouth.pos.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import co.churchyouth.pos.util.PasswordUtil;

/**
 * The entire database lives on the device — no server, no network, no
 * install step beyond the APK itself. Schema mirrors the one validated
 * with the sqlite3 CLI during development (same tables, same profit
 * math), translated to SQLite types: money as INTEGER cents (never REAL),
 * timestamps as INTEGER unix-epoch seconds.
 */
public class DbHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "church_pos.db";
    private static final int DB_VERSION = 1;

    private static DbHelper instance;

    public static synchronized DbHelper getInstance(Context context) {
        if (instance == null) {
            instance = new DbHelper(context.getApplicationContext());
        }
        return instance;
    }

    private DbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE users (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "full_name TEXT NOT NULL," +
            "username TEXT NOT NULL UNIQUE," +
            "password_hash TEXT NOT NULL," +
            "password_salt TEXT NOT NULL," +
            "role TEXT NOT NULL CHECK (role IN ('admin','cashier'))," +
            "is_active INTEGER NOT NULL DEFAULT 1," +
            "failed_logins INTEGER NOT NULL DEFAULT 0," +
            "locked_until INTEGER," +
            "last_login_at INTEGER," +
            "created_at INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE products (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "name TEXT NOT NULL," +
            "unit TEXT NOT NULL DEFAULT 'piece'," +
            "cost_cents INTEGER NOT NULL DEFAULT 0," +
            "price_cents INTEGER NOT NULL DEFAULT 0," +
            "is_active INTEGER NOT NULL DEFAULT 1," +
            "sort_order INTEGER NOT NULL DEFAULT 0," +
            "created_at INTEGER NOT NULL," +
            "updated_at INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE customers (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "full_name TEXT NOT NULL," +
            "phone TEXT UNIQUE," +
            "credit_limit_cents INTEGER NOT NULL DEFAULT 0," +
            "balance_cents INTEGER NOT NULL DEFAULT 0," +
            "notes TEXT," +
            "is_active INTEGER NOT NULL DEFAULT 1," +
            "created_at INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE sales (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "receipt_no TEXT NOT NULL UNIQUE," +
            "customer_id INTEGER REFERENCES customers(id)," +
            "user_id INTEGER NOT NULL REFERENCES users(id)," +
            "sale_at INTEGER NOT NULL," +
            "subtotal_cents INTEGER NOT NULL DEFAULT 0," +
            "discount_cents INTEGER NOT NULL DEFAULT 0," +
            "total_cents INTEGER NOT NULL DEFAULT 0," +
            "amount_paid_cents INTEGER NOT NULL DEFAULT 0," +
            "payment_method TEXT NOT NULL CHECK (payment_method IN ('cash','mpesa','credit'))," +
            "status TEXT NOT NULL DEFAULT 'completed' CHECK (status IN ('completed','voided'))," +
            "voided_reason TEXT," +
            "voided_by INTEGER REFERENCES users(id)," +
            "voided_at INTEGER," +
            "created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX idx_sales_date ON sales(sale_at)");
        db.execSQL("CREATE INDEX idx_sales_customer ON sales(customer_id)");

        db.execSQL("CREATE TABLE sale_items (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "sale_id INTEGER NOT NULL REFERENCES sales(id) ON DELETE CASCADE," +
            "product_id INTEGER REFERENCES products(id)," +
            "product_name TEXT NOT NULL," +
            "quantity REAL NOT NULL," +
            "unit_price_cents INTEGER NOT NULL," +
            "unit_cost_cents INTEGER NOT NULL DEFAULT 0," +
            "line_total_cents INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX idx_items_sale ON sale_items(sale_id)");

        db.execSQL("CREATE TABLE payments (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "customer_id INTEGER NOT NULL REFERENCES customers(id)," +
            "amount_cents INTEGER NOT NULL," +
            "payment_method TEXT NOT NULL CHECK (payment_method IN ('cash','mpesa'))," +
            "received_by INTEGER NOT NULL REFERENCES users(id)," +
            "notes TEXT," +
            "payment_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX idx_payments_customer ON payments(customer_id)");

        db.execSQL("CREATE TABLE audit_log (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "user_id INTEGER," +
            "action TEXT NOT NULL," +
            "details TEXT," +
            "created_at INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE settings (" +
            "key TEXT PRIMARY KEY," +
            "value TEXT NOT NULL)");

        seed(db);
    }

    private void seed(SQLiteDatabase db) {
        long now = System.currentTimeMillis() / 1000L;

        insertSetting(db, "business_name", "Youth Ministry Kiosk");
        insertSetting(db, "receipt_footer", "Thank you & God bless!");
        insertSetting(db, "currency_symbol", "KSh");

        insertProduct(db, "Smocha", "piece", 3000, 5000, 1, now);
        insertProduct(db, "Egg", "piece", 1200, 2000, 2, now);
        insertProduct(db, "Smokie", "piece", 2000, 3000, 3, now);

        PasswordUtil.Hashed hashed = PasswordUtil.hash("ChangeMe123!");
        ContentValues userCv = new ContentValues();
        userCv.put("full_name", "Chairman");
        userCv.put("username", "admin");
        userCv.put("password_hash", hashed.hash);
        userCv.put("password_salt", hashed.salt);
        userCv.put("role", "admin");
        userCv.put("created_at", now);
        db.insertOrThrow("users", null, userCv);
    }

    private void insertSetting(SQLiteDatabase db, String key, String value) {
        ContentValues cv = new ContentValues();
        cv.put("key", key);
        cv.put("value", value);
        db.insertOrThrow("settings", null, cv);
    }

    private void insertProduct(SQLiteDatabase db, String name, String unit, long costCents, long priceCents, int sortOrder, long now) {
        ContentValues cv = new ContentValues();
        cv.put("name", name);
        cv.put("unit", unit);
        cv.put("cost_cents", costCents);
        cv.put("price_cents", priceCents);
        cv.put("sort_order", sortOrder);
        cv.put("created_at", now);
        cv.put("updated_at", now);
        db.insertOrThrow("products", null, cv);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // No upgrades yet — first shipped version. When the schema changes,
        // add "if (oldVersion < N) { db.execSQL(...ALTER TABLE...); }" blocks
        // here rather than dropping tables, so nobody's sales history or
        // customer ledger gets wiped by an app update.
    }
}
