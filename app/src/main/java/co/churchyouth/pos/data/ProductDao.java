package co.churchyouth.pos.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import co.churchyouth.pos.model.Product;

public class ProductDao {

    private final DbHelper dbHelper;

    public ProductDao(Context context) {
        this.dbHelper = DbHelper.getInstance(context);
    }

    public List<Product> listActive() {
        return query("SELECT * FROM products WHERE is_active = 1 ORDER BY sort_order, name");
    }

    public List<Product> listAll() {
        return query("SELECT * FROM products ORDER BY sort_order, name");
    }

    /** Looks up several products by id at once (checkout re-pricing) — never trusts client-side prices. */
    public Map<Long, Product> findByIds(List<Long> ids) {
        Map<Long, Product> map = new LinkedHashMap<>();
        if (ids.isEmpty()) return map;
        StringBuilder placeholders = new StringBuilder();
        String[] args = new String[ids.size()];
        for (int i = 0; i < ids.size(); i++) {
            placeholders.append(i == 0 ? "?" : ",?");
            args[i] = String.valueOf(ids.get(i));
        }
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor c = db.rawQuery(
                "SELECT * FROM products WHERE id IN (" + placeholders + ") AND is_active = 1", args)) {
            while (c.moveToNext()) {
                Product p = fromCursor(c);
                map.put(p.id, p);
            }
        }
        return map;
    }

    public long insert(String name, String unit, long costCents, long priceCents) {
        long now = System.currentTimeMillis() / 1000L;
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        int maxSort = 0;
        try (Cursor c = db.rawQuery("SELECT COALESCE(MAX(sort_order),0) FROM products", null)) {
            if (c.moveToFirst()) maxSort = c.getInt(0);
        }
        ContentValues cv = new ContentValues();
        cv.put("name", name);
        cv.put("unit", unit);
        cv.put("cost_cents", costCents);
        cv.put("price_cents", priceCents);
        cv.put("sort_order", maxSort + 1);
        cv.put("created_at", now);
        cv.put("updated_at", now);
        return db.insert("products", null, cv);
    }

    public void update(long id, String name, String unit, long costCents, long priceCents, boolean isActive) {
        ContentValues cv = new ContentValues();
        cv.put("name", name);
        cv.put("unit", unit);
        cv.put("cost_cents", costCents);
        cv.put("price_cents", priceCents);
        cv.put("is_active", isActive ? 1 : 0);
        cv.put("updated_at", System.currentTimeMillis() / 1000L);
        dbHelper.getWritableDatabase().update("products", cv, "id = ?", new String[]{String.valueOf(id)});
    }

    private List<Product> query(String sql) {
        List<Product> list = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor c = db.rawQuery(sql, null)) {
            while (c.moveToNext()) list.add(fromCursor(c));
        }
        return list;
    }

    private Product fromCursor(Cursor c) {
        Product p = new Product();
        p.id = c.getLong(c.getColumnIndexOrThrow("id"));
        p.name = c.getString(c.getColumnIndexOrThrow("name"));
        p.unit = c.getString(c.getColumnIndexOrThrow("unit"));
        p.costCents = c.getLong(c.getColumnIndexOrThrow("cost_cents"));
        p.priceCents = c.getLong(c.getColumnIndexOrThrow("price_cents"));
        p.isActive = c.getInt(c.getColumnIndexOrThrow("is_active")) == 1;
        p.sortOrder = c.getInt(c.getColumnIndexOrThrow("sort_order"));
        return p;
    }
}
