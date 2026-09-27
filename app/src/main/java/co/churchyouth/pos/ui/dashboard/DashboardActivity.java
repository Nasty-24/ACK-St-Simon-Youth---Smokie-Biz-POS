package co.churchyouth.pos.ui.dashboard;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Calendar;
import java.util.List;

import co.churchyouth.pos.R;
import co.churchyouth.pos.data.CustomerDao;
import co.churchyouth.pos.data.SaleDao;
import co.churchyouth.pos.model.Sale;
import co.churchyouth.pos.ui.common.BaseActivity;
import co.churchyouth.pos.ui.pos.PosActivity;
import co.churchyouth.pos.ui.receipt.ReceiptActivity;
import androidx.core.content.ContextCompat;

import co.churchyouth.pos.util.Money;

public class DashboardActivity extends BaseActivity {

    @Override
    protected int contentLayoutId() { return R.layout.content_dashboard; }

    @Override
    protected String activeNav() { return ""; }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (currentUser == null) return; // BaseActivity already redirected

        findViewById(R.id.newSaleButton).setOnClickListener(v ->
            startActivity(new Intent(this, PosActivity.class)));

        new LoadTask().execute();
    }

    private static class DashData {
        SaleDao.TodayStats stats;
        long totalOwed;
        List<Sale> recent;
    }

    private class LoadTask extends AsyncTask<Void, Void, DashData> {
        @Override
        protected DashData doInBackground(Void... voids) {
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0);
            long startOfDay = cal.getTimeInMillis() / 1000L;
            long now = System.currentTimeMillis() / 1000L;

            DashData data = new DashData();
            SaleDao saleDao = new SaleDao(DashboardActivity.this);
            data.stats = saleDao.statsForRange(startOfDay, now);
            data.recent = saleDao.recentSales(8);
            data.totalOwed = new CustomerDao(DashboardActivity.this).totalOwed();
            return data;
        }

        @Override
        protected void onPostExecute(DashData data) {
            String currency = "KSh";
            ((TextView) findViewById(R.id.statSalesCount)).setText(String.valueOf(data.stats.count));
            ((TextView) findViewById(R.id.statRevenue)).setText(Money.format(data.stats.revenueCents, currency));
            ((TextView) findViewById(R.id.statProfit)).setText(Money.format(data.stats.profitCents(), currency));
            ((TextView) findViewById(R.id.statOwed)).setText(Money.format(data.totalOwed, currency));

            LinearLayout list = findViewById(R.id.recentList);
            list.removeAllViews();
            if (data.recent.isEmpty()) {
                TextView empty = new TextView(DashboardActivity.this);
                empty.setText("No sales recorded yet. Tap \"New sale\" to ring up the first one.");
                empty.setTextAppearance(R.style.AppText_Muted);
                empty.setPadding(0, 24, 0, 24);
                list.addView(empty);
            } else {
                for (Sale sale : data.recent) {
                    list.addView(buildSaleRow(sale, currency));
                }
            }
        }
    }

    private View buildSaleRow(Sale sale, String currency) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 16, 0, 16);
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(v -> {
            Intent intent = new Intent(this, ReceiptActivity.class);
            intent.putExtra(ReceiptActivity.EXTRA_RECEIPT_NO, sale.receiptNo);
            startActivity(intent);
        });

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams leftParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        left.setLayoutParams(leftParams);

        TextView receipt = new TextView(this);
        receipt.setText(sale.receiptNo + (sale.customerName != null ? " — " + sale.customerName : " — Walk-in"));
        receipt.setTextColor(ContextCompat.getColor(this, R.color.ink));

        TextView method = new TextView(this);
        method.setText(sale.paymentMethod.toUpperCase() + (sale.status.equals("voided") ? " · VOIDED" : ""));
        method.setTextAppearance(R.style.AppText_Muted);

        left.addView(receipt);
        left.addView(method);

        TextView amount = new TextView(this);
        amount.setText(Money.format(sale.totalCents, currency));
        amount.setTextColor(ContextCompat.getColor(this, R.color.ink));

        row.addView(left);
        row.addView(amount);
        return row;
    }
}
