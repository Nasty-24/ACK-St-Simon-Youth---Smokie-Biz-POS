package co.churchyouth.pos.ui.reports;

import android.app.DatePickerDialog;
import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import co.churchyouth.pos.R;
import co.churchyouth.pos.data.CustomerDao;
import co.churchyouth.pos.data.SaleDao;
import co.churchyouth.pos.model.Customer;
import co.churchyouth.pos.ui.common.BaseActivity;
import co.churchyouth.pos.ui.customers.CustomerDetailActivity;
import androidx.core.content.ContextCompat;

import co.churchyouth.pos.util.Money;

public class ReportsActivity extends BaseActivity {

    private final SimpleDateFormat isoFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private Calendar fromCal;
    private Calendar toCal;

    @Override
    protected int contentLayoutId() { return R.layout.content_reports; }

    @Override
    protected String activeNav() { return "reports"; }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (currentUser == null) return;

        toCal = Calendar.getInstance();
        fromCal = Calendar.getInstance();
        fromCal.add(Calendar.DAY_OF_MONTH, -6);

        Button fromButton = findViewById(R.id.fromDateButton);
        Button toButton = findViewById(R.id.toDateButton);
        updateDateButtons(fromButton, toButton);

        fromButton.setOnClickListener(v -> pickDate(fromCal, () -> updateDateButtons(fromButton, toButton)));
        toButton.setOnClickListener(v -> pickDate(toCal, () -> updateDateButtons(fromButton, toButton)));
        findViewById(R.id.applyRangeButton).setOnClickListener(v -> reload());

        reload();
    }

    private interface Runnable2 { void run(); }

    private void pickDate(Calendar target, Runnable2 after) {
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            target.set(year, month, dayOfMonth, 0, 0, 0);
            after.run();
        }, target.get(Calendar.YEAR), target.get(Calendar.MONTH), target.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void updateDateButtons(Button fromButton, Button toButton) {
        fromButton.setText(isoFmt.format(fromCal.getTime()));
        toButton.setText(isoFmt.format(toCal.getTime()));
    }

    private void reload() {
        boolean isAdmin = currentUser.isAdmin();
        findViewById(R.id.profitCard).setVisibility(isAdmin ? android.view.View.VISIBLE : android.view.View.GONE);

        Calendar start = (Calendar) fromCal.clone();
        start.set(Calendar.HOUR_OF_DAY, 0); start.set(Calendar.MINUTE, 0); start.set(Calendar.SECOND, 0);
        Calendar end = (Calendar) toCal.clone();
        end.set(Calendar.HOUR_OF_DAY, 23); end.set(Calendar.MINUTE, 59); end.set(Calendar.SECOND, 59);
        long fromEpoch = start.getTimeInMillis() / 1000L;
        long toEpoch = end.getTimeInMillis() / 1000L;

        new AsyncTask<Void, Void, Object[]>() {
            @Override protected Object[] doInBackground(Void... voids) {
                SaleDao saleDao = new SaleDao(ReportsActivity.this);
                SaleDao.TodayStats stats = saleDao.statsForRange(fromEpoch, toEpoch);
                List<SaleDao.MethodTotal> byMethod = saleDao.byPaymentMethod(fromEpoch, toEpoch);
                List<SaleDao.ProductTotal> byProduct = saleDao.byProduct(fromEpoch, toEpoch);
                List<SaleDao.DayTotal> daily = saleDao.dailyRevenue(fromEpoch, toEpoch);
                long totalOwed = new CustomerDao(ReportsActivity.this).totalOwed();
                List<Customer> topDebtors = new CustomerDao(ReportsActivity.this).topDebtors(10);
                return new Object[]{stats, byMethod, byProduct, daily, totalOwed, topDebtors};
            }
            @SuppressWarnings("unchecked")
            @Override protected void onPostExecute(Object[] r) {
                render((SaleDao.TodayStats) r[0], (List<SaleDao.MethodTotal>) r[1], (List<SaleDao.ProductTotal>) r[2],
                    (List<SaleDao.DayTotal>) r[3], (long) r[4], (List<Customer>) r[5], isAdmin);
            }
        }.execute();
    }

    private void render(SaleDao.TodayStats stats, List<SaleDao.MethodTotal> byMethod, List<SaleDao.ProductTotal> byProduct,
                         List<SaleDao.DayTotal> daily, long totalOwed, List<Customer> topDebtors, boolean isAdmin) {
        ((TextView) findViewById(R.id.statSales)).setText(String.valueOf(stats.count));
        ((TextView) findViewById(R.id.statRevenue)).setText(Money.format(stats.revenueCents, "KSh"));
        ((TextView) findViewById(R.id.statProfit)).setText(Money.format(stats.profitCents(), "KSh"));
        ((TextView) findViewById(R.id.statOwedAllTime)).setText(Money.format(totalOwed, "KSh"));

        LinearLayout barsHost = findViewById(R.id.dailyBarsHost);
        barsHost.removeAllViews();
        long max = 1;
        for (SaleDao.DayTotal d : daily) max = Math.max(max, d.revenueCents);
        for (SaleDao.DayTotal d : daily) {
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL);
            col.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));

            android.view.View bar = new android.view.View(this);
            int heightPx = (int) Math.max(6, (d.revenueCents / (float) max) * 220);
            LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(40, heightPx);
            bar.setLayoutParams(barParams);
            bar.setBackgroundColor(ContextCompat.getColor(this, R.color.maize));

            TextView label = new TextView(this);
            label.setText(d.dateLabel.substring(5)); // MM-DD
            label.setTextAppearance(R.style.AppText_Muted);
            label.setTextSize(9);

            col.addView(bar);
            col.addView(label);
            barsHost.addView(col);
        }

        LinearLayout methodHost = findViewById(R.id.methodListHost);
        methodHost.removeAllViews();
        for (SaleDao.MethodTotal m : byMethod) {
            methodHost.addView(simpleRow(m.method.toUpperCase(Locale.US) + " (" + m.count + ")", Money.format(m.totalCents, "KSh")));
        }

        LinearLayout productHost = findViewById(R.id.productListHost);
        productHost.removeAllViews();
        for (SaleDao.ProductTotal p : byProduct) {
            String right = Money.format(p.revenueCents, "KSh") + (isAdmin ? "  ·  profit " + Money.format(p.profitCents, "KSh") : "");
            productHost.addView(simpleRow(p.name + " (" + trimQty(p.qty) + ")", right));
        }

        LinearLayout debtorHost = findViewById(R.id.debtorListHost);
        debtorHost.removeAllViews();
        if (topDebtors.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Nobody currently owes the kiosk.");
            empty.setTextAppearance(R.style.AppText_Muted);
            debtorHost.addView(empty);
        } else {
            for (Customer c : topDebtors) {
                android.view.View row = simpleRow(c.fullName, Money.format(c.balanceCents, "KSh"));
                row.setClickable(true);
                row.setOnClickListener(v -> {
                    android.content.Intent intent = new android.content.Intent(this, CustomerDetailActivity.class);
                    intent.putExtra(CustomerDetailActivity.EXTRA_CUSTOMER_ID, c.id);
                    startActivity(intent);
                });
                debtorHost.addView(row);
            }
        }
    }

    private android.view.View simpleRow(String left, String right) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 12, 0, 12);
        TextView leftView = new TextView(this);
        leftView.setText(left);
        leftView.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView rightView = new TextView(this);
        rightView.setText(right);
        row.addView(leftView);
        row.addView(rightView);
        return row;
    }

    private String trimQty(double qty) {
        if (qty == Math.floor(qty)) return String.valueOf((long) qty);
        return String.valueOf(qty);
    }
}
