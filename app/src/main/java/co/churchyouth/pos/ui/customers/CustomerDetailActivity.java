package co.churchyouth.pos.ui.customers;

import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import co.churchyouth.pos.R;
import co.churchyouth.pos.data.CustomerDao;
import co.churchyouth.pos.data.PaymentDao;
import co.churchyouth.pos.data.SaleDao;
import co.churchyouth.pos.model.Customer;
import co.churchyouth.pos.model.Payment;
import co.churchyouth.pos.model.Sale;
import co.churchyouth.pos.util.Money;
import co.churchyouth.pos.util.SessionManager;

public class CustomerDetailActivity extends AppCompatActivity {

    public static final String EXTRA_CUSTOMER_ID = "customer_id";

    private long customerId;
    private Customer customer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_detail);

        customerId = getIntent().getLongExtra(EXTRA_CUSTOMER_ID, -1);
        findViewById(R.id.backButton).setOnClickListener(v -> finish());
        findViewById(R.id.recordPaymentButton).setOnClickListener(v -> attemptRecordPayment());

        reload();
    }

    private void reload() {
        new AsyncTask<Void, Void, Object[]>() {
            @Override protected Object[] doInBackground(Void... voids) {
                Customer c = new CustomerDao(CustomerDetailActivity.this).findById(customerId);
                List<Sale> sales = new SaleDao(CustomerDetailActivity.this).salesForCustomer(customerId, 50);
                List<Payment> payments = new PaymentDao(CustomerDetailActivity.this).forCustomer(customerId, 50);
                return new Object[]{c, sales, payments};
            }
            @SuppressWarnings("unchecked")
            @Override protected void onPostExecute(Object[] result) {
                customer = (Customer) result[0];
                if (customer == null) {
                    Toast.makeText(CustomerDetailActivity.this, "That customer was not found.", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                render((List<Sale>) result[1], (List<Payment>) result[2]);
            }
        }.execute();
    }

    private void render(List<Sale> sales, List<Payment> payments) {
        ((TextView) findViewById(R.id.custName)).setText(customer.fullName);
        ((TextView) findViewById(R.id.custPhone)).setText(customer.phone != null ? customer.phone : "No phone on file");

        TextView balanceLabel = findViewById(R.id.balanceLabel);
        TextView balanceValue = findViewById(R.id.balanceValue);
        if (customer.balanceCents > 0) {
            balanceLabel.setText("Owes the kiosk");
            balanceValue.setText(Money.format(customer.balanceCents, "KSh"));
            balanceValue.setTextColor(ContextCompat.getColor(this, R.color.brick));
        } else if (customer.balanceCents < 0) {
            balanceLabel.setText("Credit balance (prepaid)");
            balanceValue.setText(Money.format(-customer.balanceCents, "KSh"));
            balanceValue.setTextColor(ContextCompat.getColor(this, R.color.leaf));
        } else {
            balanceLabel.setText("Account status");
            balanceValue.setText("Settled up");
            balanceValue.setTextColor(ContextCompat.getColor(this, R.color.ink));
        }

        List<Object[]> events = new ArrayList<>(); // [epoch(Long), isSale(Boolean), object]
        for (Sale s : sales) events.add(new Object[]{s.saleAtEpoch, true, s});
        for (Payment p : payments) events.add(new Object[]{p.paymentAtEpoch, false, p});
        events.sort((a, b) -> Long.compare((Long) b[0], (Long) a[0]));

        LinearLayout host = findViewById(R.id.eventListHost);
        host.removeAllViews();
        if (events.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No sales or payments recorded for this customer yet.");
            empty.setTextAppearance(R.style.AppText_Muted);
            empty.setPadding(0, 20, 0, 20);
            host.addView(empty);
            return;
        }
        SimpleDateFormat fmt = new SimpleDateFormat("dd MMM, h:mma", Locale.US);
        for (Object[] event : events) {
            boolean isSale = (Boolean) event[1];
            long epoch = (Long) event[0];
            String dateStr = fmt.format(new Date(epoch * 1000L));
            android.widget.LinearLayout row = new android.widget.LinearLayout(this);
            row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            row.setPadding(0, 14, 0, 14);

            android.widget.LinearLayout left = new android.widget.LinearLayout(this);
            left.setOrientation(android.widget.LinearLayout.VERTICAL);
            left.setLayoutParams(new android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView dateView = new TextView(this);
            dateView.setText(dateStr);
            dateView.setTextAppearance(R.style.AppText_Muted);
            left.addView(dateView);

            TextView amountView = new TextView(this);
            amountView.setGravity(android.view.Gravity.END);

            if (isSale) {
                Sale s = (Sale) event[2];
                long due = s.balanceDueCents();
                TextView label = new TextView(this);
                label.setText("Sale " + s.receiptNo + (due > 0 ? " (put on account)" : ""));
                left.addView(label);
                amountView.setText((due > 0 ? "+" : "") + Money.format(due > 0 ? due : s.totalCents, "KSh"));
                amountView.setTextColor(ContextCompat.getColor(this, due > 0 ? R.color.brick : R.color.ink));
            } else {
                Payment p = (Payment) event[2];
                TextView label = new TextView(this);
                label.setText("Payment received (" + p.paymentMethod + ") — " + p.receivedByName
                    + (p.notes != null && !p.notes.isEmpty() ? "\n" + p.notes : ""));
                left.addView(label);
                amountView.setText("−" + Money.format(p.amountCents, "KSh"));
                amountView.setTextColor(ContextCompat.getColor(this, R.color.leaf_dark));
            }

            row.addView(left);
            row.addView(amountView);
            host.addView(row);
        }
    }

    private void attemptRecordPayment() {
        EditText amountField = findViewById(R.id.paymentAmount);
        RadioGroup methodGroup = findViewById(R.id.paymentMethodGroup);
        EditText notesField = findViewById(R.id.paymentNotes);

        long amountCents = Money.parseToCents(amountField.getText().toString());
        if (amountCents <= 0) {
            Toast.makeText(this, "Enter a payment amount greater than zero.", Toast.LENGTH_SHORT).show();
            return;
        }
        String method = methodGroup.getCheckedRadioButtonId() == R.id.payMethodMpesa ? "mpesa" : "cash";
        String notes = notesField.getText().toString().trim();
        long userId = new SessionManager(this).currentUserId();

        Button button = findViewById(R.id.recordPaymentButton);
        button.setEnabled(false);

        new AsyncTask<Void, Void, Boolean>() {
            @Override protected Boolean doInBackground(Void... voids) {
                return new PaymentDao(CustomerDetailActivity.this).recordPayment(customerId, amountCents, method, userId, notes);
            }
            @Override protected void onPostExecute(Boolean ok) {
                button.setEnabled(true);
                if (ok) {
                    amountField.setText("");
                    notesField.setText("");
                    Toast.makeText(CustomerDetailActivity.this, Money.format(amountCents, "KSh") + " payment recorded.", Toast.LENGTH_SHORT).show();
                    reload();
                } else {
                    Toast.makeText(CustomerDetailActivity.this, "Could not record that payment.", Toast.LENGTH_SHORT).show();
                }
            }
        }.execute();
    }
}
