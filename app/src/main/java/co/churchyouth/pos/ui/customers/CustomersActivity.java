package co.churchyouth.pos.ui.customers;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

import co.churchyouth.pos.R;
import co.churchyouth.pos.data.CustomerDao;
import co.churchyouth.pos.model.Customer;
import co.churchyouth.pos.ui.common.BaseActivity;
import androidx.core.content.ContextCompat;

import co.churchyouth.pos.util.Money;

public class CustomersActivity extends BaseActivity {

    private EditText searchField;
    private LinearLayout listHost;
    private TextView totalOwedText;

    @Override
    protected int contentLayoutId() { return R.layout.content_customers; }

    @Override
    protected String activeNav() { return "customers"; }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (currentUser == null) return;

        searchField = findViewById(R.id.searchField);
        listHost = findViewById(R.id.customerListHost);
        totalOwedText = findViewById(R.id.totalOwedText);

        searchField.setOnEditorActionListener((v, actionId, event) -> {
            reload();
            return true;
        });

        findViewById(R.id.addCustomerButton).setOnClickListener(v -> attemptAdd());

        reload();
    }

    private void reload() {
        String query = searchField.getText().toString().trim();
        new AsyncTask<Void, Void, Object[]>() {
            @Override protected Object[] doInBackground(Void... voids) {
                CustomerDao dao = new CustomerDao(CustomersActivity.this);
                List<Customer> list = query.isEmpty() ? dao.listAll() : dao.search(query);
                long owed = dao.totalOwed();
                return new Object[]{list, owed};
            }
            @SuppressWarnings("unchecked")
            @Override protected void onPostExecute(Object[] result) {
                renderList((List<Customer>) result[0]);
                totalOwedText.setText("Total owed to the kiosk right now: " + Money.format((long) result[1], "KSh"));
            }
        }.execute();
    }

    private void renderList(List<Customer> customers) {
        listHost.removeAllViews();
        if (customers.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No customers yet — add the first one below.");
            empty.setTextAppearance(R.style.AppText_Muted);
            empty.setPadding(0, 20, 0, 20);
            listHost.addView(empty);
            return;
        }
        for (Customer c : customers) {
            listHost.addView(buildRow(c));
        }
    }

    private View buildRow(Customer c) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 16, 0, 16);
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(v -> {
            Intent intent = new Intent(this, CustomerDetailActivity.class);
            intent.putExtra(CustomerDetailActivity.EXTRA_CUSTOMER_ID, c.id);
            startActivity(intent);
        });

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView name = new TextView(this);
        name.setText(c.fullName);
        TextView phone = new TextView(this);
        phone.setText(c.phone != null ? c.phone : "—");
        phone.setTextAppearance(R.style.AppText_Muted);
        left.addView(name);
        left.addView(phone);

        TextView balance = new TextView(this);
        if (c.balanceCents == 0) {
            balance.setText("Settled");
        } else {
            balance.setText(Money.format(Math.abs(c.balanceCents), "KSh"));
            balance.setTextColor(ContextCompat.getColor(this, c.balanceCents > 0 ? R.color.brick : R.color.leaf));
        }

        row.addView(left);
        row.addView(balance);
        return row;
    }

    private void attemptAdd() {
        EditText nameField = findViewById(R.id.newCustName);
        EditText phoneField = findViewById(R.id.newCustPhone);
        EditText limitField = findViewById(R.id.newCustLimit);

        String name = nameField.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, "Enter the customer's name.", Toast.LENGTH_SHORT).show();
            return;
        }
        String phone = phoneField.getText().toString().trim();
        long limitCents = Money.parseToCents(limitField.getText().toString());

        new AsyncTask<Void, Void, Long>() {
            @Override protected Long doInBackground(Void... voids) {
                return new CustomerDao(CustomersActivity.this).insert(name, phone, limitCents);
            }
            @Override protected void onPostExecute(Long id) {
                if (id == -1) {
                    Toast.makeText(CustomersActivity.this, "That phone number is already on the account book.", Toast.LENGTH_LONG).show();
                } else {
                    nameField.setText(""); phoneField.setText(""); limitField.setText("0");
                    Toast.makeText(CustomersActivity.this, "Added " + name + " to the customer book.", Toast.LENGTH_SHORT).show();
                    reload();
                }
            }
        }.execute();
    }
}
