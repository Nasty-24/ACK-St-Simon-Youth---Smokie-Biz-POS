package co.churchyouth.pos.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import co.churchyouth.pos.model.CartLine;
import co.churchyouth.pos.model.Sale;
import co.churchyouth.pos.model.SaleItem;

public class SaleDao {

    private final DbHelper dbHelper;
    private final Context appContext;

    public SaleDao(Context context) {
        this.appContext = context.getApplicationContext();
        this.dbHelper = DbHelper.getInstance(appContext);
    }

    /**
     * Runs the whole sale as one transaction: re-price every line straight
     * from the products table (never trust a price handed in from the UI
     * layer), optionally create a new customer, write the sale + line
     * items, and — for credit sales — grow the customer's balance by
     * exactly the unpaid portion. Either all of it lands, or none of it
     * does.
     */
    public CheckoutOutcome checkout(List<CartLine> requestedLines, Long existingCustomerId,
                                     String newCustomerName, String newCustomerPhone,
                                     String paymentMethod, long amountPaidRequestedCents,
                                     long userId) {
        CheckoutOutcome outcome = new CheckoutOutcome();

        if (requestedLines == null || requestedLines.isEmpty()) {
            outcome.success = false;
            outcome.errorMessage = "The cart is empty.";
            return outcome;
        }

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            // --- Re-price every line from the authoritative products table ---
            StringBuilder placeholders = new StringBuilder();
            String[] idArgs = new String[requestedLines.size()];
            for (int i = 0; i < requestedLines.size(); i++) {
                placeholders.append(i == 0 ? "?" : ",?");
                idArgs[i] = String.valueOf(requestedLines.get(i).productId);
            }

            java.util.Map<Long, long[]> priceCostById = new java.util.HashMap<>(); // id -> [priceCents, costCents]
            java.util.Map<Long, String> nameById = new java.util.HashMap<>();
            try (Cursor c = db.rawQuery(
                    "SELECT id, name, price_cents, cost_cents FROM products WHERE id IN (" + placeholders + ") AND is_active = 1",
                    idArgs)) {
                while (c.moveToNext()) {
                    long id = c.getLong(0);
                    priceCostById.put(id, new long[]{c.getLong(2), c.getLong(3)});
                    nameById.put(id, c.getString(1));
                }
            }

            List<SaleItem> items = new ArrayList<>();
            long subtotal = 0;
            for (CartLine line : requestedLines) {
                if (line.quantity <= 0 || line.quantity > 500) continue;
                long[] pc = priceCostById.get(line.productId);
                if (pc == null) continue; // deleted/deactivated between screen load and checkout
                SaleItem item = new SaleItem();
                item.productId = line.productId;
                item.productName = nameById.get(line.productId);
                item.quantity = line.quantity;
                item.unitPriceCents = pc[0];
                item.unitCostCents = pc[1];
                item.lineTotalCents = Math.round(pc[0] * line.quantity);
                items.add(item);
                subtotal += item.lineTotalCents;
            }

            if (items.isEmpty()) {
                outcome.success = false;
                outcome.errorMessage = "Those products are no longer available.";
                return outcome;
            }

            long total = subtotal;

            // --- Customer ---
            Long customerId = existingCustomerId;
            if (newCustomerName != null && !newCustomerName.trim().isEmpty()) {
                ContentValues cv = new ContentValues();
                cv.put("full_name", newCustomerName.trim());
                String phone = (newCustomerPhone == null || newCustomerPhone.trim().isEmpty()) ? null : newCustomerPhone.trim();
                cv.put("phone", phone);
                cv.put("created_at", System.currentTimeMillis() / 1000L);
                try {
                    customerId = db.insertOrThrow("customers", null, cv);
                } catch (Exception e) {
                    outcome.success = false;
                    outcome.errorMessage = "That phone number is already on the account book.";
                    return outcome;
                }
            }

            if ("credit".equals(paymentMethod) && customerId == null) {
                outcome.success = false;
                outcome.errorMessage = "Credit sales need a customer on the account.";
                return outcome;
            }

            long amountPaid = "credit".equals(paymentMethod)
                ? Math.max(0, Math.min(amountPaidRequestedCents, total))
                : total;

            String receiptNo = generateReceiptNo(db);

            ContentValues saleCv = new ContentValues();
            saleCv.put("receipt_no", receiptNo);
            if (customerId != null) saleCv.put("customer_id", customerId); else saleCv.putNull("customer_id");
            saleCv.put("user_id", userId);
            saleCv.put("sale_at", System.currentTimeMillis() / 1000L);
            saleCv.put("subtotal_cents", subtotal);
            saleCv.put("total_cents", total);
            saleCv.put("amount_paid_cents", amountPaid);
            saleCv.put("payment_method", paymentMethod);
            saleCv.put("created_at", System.currentTimeMillis() / 1000L);
            long saleId = db.insertOrThrow("sales", null, saleCv);

            for (SaleItem item : items) {
                ContentValues itemCv = new ContentValues();
                itemCv.put("sale_id", saleId);
                itemCv.put("product_id", item.productId);
                itemCv.put("product_name", item.productName);
                itemCv.put("quantity", item.quantity);
                itemCv.put("unit_price_cents", item.unitPriceCents);
                itemCv.put("unit_cost_cents", item.unitCostCents);
                itemCv.put("line_total_cents", item.lineTotalCents);
                db.insertOrThrow("sale_items", null, itemCv);
            }

            long balanceDue = total - amountPaid;
            if ("credit".equals(paymentMethod) && balanceDue > 0 && customerId != null) {
                db.execSQL("UPDATE customers SET balance_cents = balance_cents + ? WHERE id = ?",
                    new Object[]{balanceDue, customerId});
            }

            ContentValues auditCv = new ContentValues();
            auditCv.put("user_id", userId);
            auditCv.put("action", "sale_created");
            auditCv.put("details", "receipt=" + receiptNo + " total=" + total + " method=" + paymentMethod);
            auditCv.put("created_at", System.currentTimeMillis() / 1000L);
            db.insert("audit_log", null, auditCv);

            db.setTransactionSuccessful();
            outcome.success = true;
            outcome.receiptNo = receiptNo;
        } finally {
            db.endTransaction();
        }
        return outcome;
    }

    private String generateReceiptNo(SQLiteDatabase db) {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMdd", Locale.US);
        String datePart = fmt.format(new Date());
        String prefix = "RC-" + datePart + "-";
        int seq = 1;
        try (Cursor c = db.rawQuery("SELECT COUNT(*) FROM sales WHERE receipt_no LIKE ?", new String[]{prefix + "%"})) {
            if (c.moveToFirst()) seq = c.getInt(0) + 1;
        }
        return prefix + String.format(Locale.US, "%04d", seq);
    }

    public Sale findByReceiptNo(String receiptNo) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Sale sale;
        try (Cursor c = db.rawQuery(
                "SELECT s.*, u.full_name AS cashier, c.full_name AS customer_name " +
                "FROM sales s JOIN users u ON u.id = s.user_id " +
                "LEFT JOIN customers c ON c.id = s.customer_id WHERE s.receipt_no = ?",
                new String[]{receiptNo})) {
            if (!c.moveToFirst()) return null;
            sale = fromCursor(c);
        }
        sale.items = itemsForSale(sale.id);
        return sale;
    }

    public List<SaleItem> itemsForSale(long saleId) {
        List<SaleItem> items = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT * FROM sale_items WHERE sale_id = ? ORDER BY id", new String[]{String.valueOf(saleId)})) {
            while (c.moveToNext()) {
                SaleItem item = new SaleItem();
                item.id = c.getLong(c.getColumnIndexOrThrow("id"));
                item.saleId = c.getLong(c.getColumnIndexOrThrow("sale_id"));
                item.productName = c.getString(c.getColumnIndexOrThrow("product_name"));
                item.quantity = c.getDouble(c.getColumnIndexOrThrow("quantity"));
                item.unitPriceCents = c.getLong(c.getColumnIndexOrThrow("unit_price_cents"));
                item.unitCostCents = c.getLong(c.getColumnIndexOrThrow("unit_cost_cents"));
                item.lineTotalCents = c.getLong(c.getColumnIndexOrThrow("line_total_cents"));
                items.add(item);
            }
        }
        return items;
    }

    public List<Sale> recentSales(int limit) {
        List<Sale> list = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor c = db.rawQuery(
                "SELECT s.*, u.full_name AS cashier, c.full_name AS customer_name " +
                "FROM sales s JOIN users u ON u.id = s.user_id " +
                "LEFT JOIN customers c ON c.id = s.customer_id " +
                "ORDER BY s.sale_at DESC LIMIT ?", new String[]{String.valueOf(limit)})) {
            while (c.moveToNext()) list.add(fromCursor(c));
        }
        return list;
    }

    public List<Sale> salesForCustomer(long customerId, int limit) {
        List<Sale> list = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor c = db.rawQuery(
                "SELECT s.*, u.full_name AS cashier FROM sales s JOIN users u ON u.id = s.user_id " +
                "WHERE s.customer_id = ? ORDER BY s.sale_at DESC LIMIT ?",
                new String[]{String.valueOf(customerId), String.valueOf(limit)})) {
            while (c.moveToNext()) list.add(fromCursor(c));
        }
        return list;
    }

    public static class TodayStats {
        public int count;
        public long revenueCents;
        public long costCents;
        public long profitCents() { return revenueCents - costCents; }
    }

    public TodayStats statsForRange(long fromEpoch, long toEpoch) {
        TodayStats stats = new TodayStats();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor c = db.rawQuery(
                "SELECT COUNT(*), COALESCE(SUM(total_cents),0) FROM sales " +
                "WHERE status='completed' AND sale_at BETWEEN ? AND ?",
                new String[]{String.valueOf(fromEpoch), String.valueOf(toEpoch)})) {
            if (c.moveToFirst()) { stats.count = c.getInt(0); stats.revenueCents = c.getLong(1); }
        }
        try (Cursor c = db.rawQuery(
                "SELECT COALESCE(SUM(si.unit_cost_cents * si.quantity),0) FROM sale_items si " +
                "JOIN sales s ON s.id = si.sale_id " +
                "WHERE s.status='completed' AND s.sale_at BETWEEN ? AND ?",
                new String[]{String.valueOf(fromEpoch), String.valueOf(toEpoch)})) {
            if (c.moveToFirst()) stats.costCents = Math.round(c.getDouble(0));
        }
        return stats;
    }

    public static class MethodTotal { public String method; public int count; public long totalCents; }

    public List<MethodTotal> byPaymentMethod(long fromEpoch, long toEpoch) {
        List<MethodTotal> list = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor c = db.rawQuery(
                "SELECT payment_method, COUNT(*), COALESCE(SUM(total_cents),0) FROM sales " +
                "WHERE status='completed' AND sale_at BETWEEN ? AND ? GROUP BY payment_method",
                new String[]{String.valueOf(fromEpoch), String.valueOf(toEpoch)})) {
            while (c.moveToNext()) {
                MethodTotal m = new MethodTotal();
                m.method = c.getString(0); m.count = c.getInt(1); m.totalCents = c.getLong(2);
                list.add(m);
            }
        }
        return list;
    }

    public static class ProductTotal { public String name; public double qty; public long revenueCents; public long profitCents; }

    public List<ProductTotal> byProduct(long fromEpoch, long toEpoch) {
        List<ProductTotal> list = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor c = db.rawQuery(
                "SELECT si.product_name, SUM(si.quantity), SUM(si.line_total_cents), " +
                "SUM(si.line_total_cents - si.unit_cost_cents * si.quantity) " +
                "FROM sale_items si JOIN sales s ON s.id = si.sale_id " +
                "WHERE s.status='completed' AND s.sale_at BETWEEN ? AND ? " +
                "GROUP BY si.product_name ORDER BY 3 DESC",
                new String[]{String.valueOf(fromEpoch), String.valueOf(toEpoch)})) {
            while (c.moveToNext()) {
                ProductTotal p = new ProductTotal();
                p.name = c.getString(0);
                p.qty = c.getDouble(1);
                p.revenueCents = c.getLong(2);
                p.profitCents = Math.round(c.getDouble(3));
                list.add(p);
            }
        }
        return list;
    }

    public static class DayTotal { public String dateLabel; public long revenueCents; }

    /** One row per calendar day (device-local) between the two epochs, using SQLite's own date() function. */
    public List<DayTotal> dailyRevenue(long fromEpoch, long toEpoch) {
        List<DayTotal> list = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor c = db.rawQuery(
                "SELECT date(sale_at, 'unixepoch', 'localtime') AS d, COALESCE(SUM(total_cents),0) " +
                "FROM sales WHERE status='completed' AND sale_at BETWEEN ? AND ? " +
                "GROUP BY d ORDER BY d",
                new String[]{String.valueOf(fromEpoch), String.valueOf(toEpoch)})) {
            while (c.moveToNext()) {
                DayTotal d = new DayTotal();
                d.dateLabel = c.getString(0);
                d.revenueCents = c.getLong(1);
                list.add(d);
            }
        }
        return list;
    }

    private Sale fromCursor(Cursor c) {
        Sale s = new Sale();
        s.id = c.getLong(c.getColumnIndexOrThrow("id"));
        s.receiptNo = c.getString(c.getColumnIndexOrThrow("receipt_no"));
        int custIdx = c.getColumnIndexOrThrow("customer_id");
        s.customerId = c.isNull(custIdx) ? null : c.getLong(custIdx);
        int custNameIdx = c.getColumnIndex("customer_name");
        s.customerName = custNameIdx >= 0 ? c.getString(custNameIdx) : null;
        s.userId = c.getLong(c.getColumnIndexOrThrow("user_id"));
        int cashierIdx = c.getColumnIndex("cashier");
        s.cashierName = cashierIdx >= 0 ? c.getString(cashierIdx) : null;
        s.saleAtEpoch = c.getLong(c.getColumnIndexOrThrow("sale_at"));
        s.subtotalCents = c.getLong(c.getColumnIndexOrThrow("subtotal_cents"));
        s.discountCents = c.getLong(c.getColumnIndexOrThrow("discount_cents"));
        s.totalCents = c.getLong(c.getColumnIndexOrThrow("total_cents"));
        s.amountPaidCents = c.getLong(c.getColumnIndexOrThrow("amount_paid_cents"));
        s.paymentMethod = c.getString(c.getColumnIndexOrThrow("payment_method"));
        s.status = c.getString(c.getColumnIndexOrThrow("status"));
        return s;
    }
}
