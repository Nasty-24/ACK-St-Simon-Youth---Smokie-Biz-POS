package co.churchyouth.pos.ui.receipt;

import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import co.churchyouth.pos.R;
import co.churchyouth.pos.data.SaleDao;
import co.churchyouth.pos.data.SettingsDao;
import co.churchyouth.pos.model.Sale;
import co.churchyouth.pos.model.SaleItem;
import co.churchyouth.pos.util.Money;
import co.churchyouth.pos.util.PrinterHelper;

public class ReceiptActivity extends AppCompatActivity {

    public static final String EXTRA_RECEIPT_NO = "receipt_no";

    private final PrinterHelper printerHelper = new PrinterHelper();
    private Sale sale;
    private String businessName;
    private String currencySymbol;
    private String footer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receipt);
        printerHelper.bind(this);

        String receiptNo = getIntent().getStringExtra(EXTRA_RECEIPT_NO);
        findViewById(R.id.printButton).setOnClickListener(v -> doPrint());
        findViewById(R.id.newSaleButton).setOnClickListener(v -> finish());

        new AsyncTask<String, Void, Sale>() {
            @Override protected Sale doInBackground(String... params) {
                SettingsDao settings = new SettingsDao(ReceiptActivity.this);
                businessName = settings.get("business_name", "Youth Ministry Kiosk");
                currencySymbol = settings.get("currency_symbol", "KSh");
                footer = settings.get("receipt_footer", "Thank you!");
                return new SaleDao(ReceiptActivity.this).findByReceiptNo(params[0]);
            }
            @Override protected void onPostExecute(Sale result) {
                sale = result;
                if (sale != null) renderReceipt();
            }
        }.execute(receiptNo);
    }

    private void renderReceipt() {
        ((TextView) findViewById(R.id.receiptBusiness)).setText(businessName);
        String title = "Receipt " + sale.receiptNo + ("voided".equals(sale.status) ? " — VOIDED" : "");
        ((TextView) findViewById(R.id.receiptTitle)).setText(title);
        SimpleDateFormat fmt = new SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.US);
        ((TextView) findViewById(R.id.receiptMeta)).setText(
            fmt.format(new Date(sale.saleAtEpoch * 1000L)) + "   Till: " + sale.cashierName);
        ((TextView) findViewById(R.id.receiptCustomer)).setText(
            "Customer: " + (sale.customerName != null ? sale.customerName : "Walk-in"));

        LinearLayout itemsHost = findViewById(R.id.receiptItems);
        itemsHost.removeAllViews();
        for (SaleItem item : sale.items) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            TextView left = new TextView(this);
            left.setText(item.productName + " x" + trimQty(item.quantity));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            left.setLayoutParams(lp);
            TextView right = new TextView(this);
            right.setText(String.format(Locale.US, "%.2f", item.lineTotalCents / 100.0));
            row.addView(left);
            row.addView(right);
            itemsHost.addView(row);
        }

        ((TextView) findViewById(R.id.receiptTotal)).setText(
            "TOTAL " + Money.format(sale.totalCents, currencySymbol));
        ((TextView) findViewById(R.id.receiptPaid)).setText(
            "Paid (" + sale.paymentMethod.toUpperCase(Locale.US) + "): " + Money.format(sale.amountPaidCents, currencySymbol));

        long balance = sale.balanceDueCents();
        TextView balanceView = findViewById(R.id.receiptBalance);
        if (balance > 0) {
            balanceView.setVisibility(android.view.View.VISIBLE);
            balanceView.setText("Balance on account: " + Money.format(balance, currencySymbol));
        } else {
            balanceView.setVisibility(android.view.View.GONE);
        }

        ((TextView) findViewById(R.id.receiptFooter)).setText(footer);
    }

    private String trimQty(double qty) {
        if (qty == Math.floor(qty)) return String.valueOf((long) qty);
        return String.valueOf(qty);
    }

    private void doPrint() {
        if (sale == null) return;
        Button printButton = findViewById(R.id.printButton);
        printButton.setEnabled(false);
        printerHelper.printReceipt(sale, businessName, currencySymbol, footer, (success, message) -> runOnUiThread(() -> {
            printButton.setEnabled(true);
            Toast.makeText(this, success ? "Printed." : message, Toast.LENGTH_LONG).show();
        }));
    }

    @Override
    protected void onDestroy() {
        printerHelper.unbind(this);
        super.onDestroy();
    }
}
