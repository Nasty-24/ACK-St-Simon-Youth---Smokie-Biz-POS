package co.churchyouth.pos.ui.checkout;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

import co.churchyouth.pos.R;
import co.churchyouth.pos.data.CheckoutOutcome;
import co.churchyouth.pos.data.CustomerDao;
import co.churchyouth.pos.data.SaleDao;
import co.churchyouth.pos.model.CartLine;
import co.churchyouth.pos.model.Customer;
import co.churchyouth.pos.ui.pos.PosActivity;
import co.churchyouth.pos.ui.receipt.ReceiptActivity;
import co.churchyouth.pos.util.Money;
import co.churchyouth.pos.util.SessionManager;

public class CheckoutActivity extends AppCompatActivity {

    private List<CartLine> cart;
    private List<Customer> customers = new ArrayList<>();

    private Spinner customerSpinner;
    private LinearLayout newCustomerFields;
    private EditText newCustomerName;
    private EditText newCustomerPhone;
    private RadioGroup paymentGroup;
    private LinearLayout creditFields;
    private EditText amountPaidField;
    private Button completeButton;
    private TextView totalLabel;

    @SuppressWarnings("unchecked")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_checkout);

        cart = (List<CartLine>) getIntent().getSerializableExtra(PosActivity.EXTRA_CART);
        if (cart == null) cart = new ArrayList<>();

        long total = 0;
        for (CartLine line : cart) total += line.lineTotalCents();
        final long finalTotal = total;

        totalLabel = findViewById(R.id.checkoutTotal);
        totalLabel.setText("Total: " + Money.format(total, "KSh"));

        customerSpinner = findViewById(R.id.customerSpinner);
        newCustomerFields = findViewById(R.id.newCustomerFields);
        newCustomerName = findViewById(R.id.newCustomerName);
        newCustomerPhone = findViewById(R.id.newCustomerPhone);
        paymentGroup = findViewById(R.id.paymentGroup);
        creditFields = findViewById(R.id.creditFields);
        amountPaidField = findViewById(R.id.amountPaidField);
        completeButton = findViewById(R.id.completeButton);

        customerSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                boolean isNew = position == customers.size() + 1; // last item = "+ New customer..."
                newCustomerFields.setVisibility(isNew ? View.VISIBLE : View.GONE);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });

        paymentGroup.setOnCheckedChangeListener((group, checkedId) ->
            creditFields.setVisibility(checkedId == R.id.paymentCredit ? View.VISIBLE : View.GONE));

        completeButton.setOnClickListener(v -> attemptCheckout(finalTotal));

        loadCustomers();
    }

    private void loadCustomers() {
        new AsyncTask<Void, Void, List<Customer>>() {
            @Override protected List<Customer> doInBackground(Void... voids) {
                return new CustomerDao(CheckoutActivity.this).listAll();
            }
            @Override protected void onPostExecute(List<Customer> result) {
                customers = result;
                List<String> labels = new ArrayList<>();
                labels.add("Walk-in (cash customer)");
                for (Customer c : customers) {
                    String extra = c.balanceCents > 0 ? " (owes " + Money.format(c.balanceCents, "KSh") + ")" : "";
                    labels.add(c.fullName + (c.phone != null ? " — " + c.phone : "") + extra);
                }
                labels.add("+ New customer…");
                ArrayAdapter<String> adapter = new ArrayAdapter<>(CheckoutActivity.this, android.R.layout.simple_spinner_dropdown_item, labels);
                customerSpinner.setAdapter(adapter);
            }
        }.execute();
    }

    private void attemptCheckout(long total) {
        int position = customerSpinner.getSelectedItemPosition();
        Long existingCustomerId = null;
        String newName = null;
        String newPhone = null;

        if (position == 0) {
            // walk-in — leave everything null
        } else if (position == customers.size() + 1) {
            newName = newCustomerName.getText().toString().trim();
            newPhone = newCustomerPhone.getText().toString().trim();
        } else {
            existingCustomerId = customers.get(position - 1).id;
        }

        String paymentMethod = "cash";
        int checkedId = paymentGroup.getCheckedRadioButtonId();
        if (checkedId == R.id.paymentMpesa) paymentMethod = "mpesa";
        if (checkedId == R.id.paymentCredit) paymentMethod = "credit";

        if ("credit".equals(paymentMethod) && existingCustomerId == null && (newName == null || newName.isEmpty())) {
            Toast.makeText(this, "Credit sales need a customer — pick one or add a new one.", Toast.LENGTH_LONG).show();
            return;
        }

        long amountPaidRequested = "credit".equals(paymentMethod)
            ? Money.parseToCents(amountPaidField.getText().toString())
            : total;

        completeButton.setEnabled(false);
        completeButton.setText("Recording sale…");

        long userId = new SessionManager(this).currentUserId();
        Long finalExistingCustomerId = existingCustomerId;
        String finalNewName = newName;
        String finalNewPhone = newPhone;
        String finalPaymentMethod = paymentMethod;

        new AsyncTask<Void, Void, CheckoutOutcome>() {
            @Override protected CheckoutOutcome doInBackground(Void... voids) {
                SaleDao saleDao = new SaleDao(CheckoutActivity.this);
                return saleDao.checkout(cart, finalExistingCustomerId, finalNewName, finalNewPhone,
                    finalPaymentMethod, amountPaidRequested, userId);
            }
            @Override protected void onPostExecute(CheckoutOutcome outcome) {
                if (outcome.success) {
                    Intent intent = new Intent(CheckoutActivity.this, ReceiptActivity.class);
                    intent.putExtra(ReceiptActivity.EXTRA_RECEIPT_NO, outcome.receiptNo);
                    startActivity(intent);
                    setResult(Activity.RESULT_OK);
                    finish();
                } else {
                    completeButton.setEnabled(true);
                    completeButton.setText("Complete sale");
                    Toast.makeText(CheckoutActivity.this, outcome.errorMessage, Toast.LENGTH_LONG).show();
                }
            }
        }.execute();
    }
}
