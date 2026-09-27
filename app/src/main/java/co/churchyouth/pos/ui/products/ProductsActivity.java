package co.churchyouth.pos.ui.products;

import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

import co.churchyouth.pos.R;
import co.churchyouth.pos.data.ProductDao;
import co.churchyouth.pos.model.Product;
import co.churchyouth.pos.ui.common.BaseActivity;
import co.churchyouth.pos.util.Money;

public class ProductsActivity extends BaseActivity {

    private LinearLayout listHost;

    @Override
    protected int contentLayoutId() { return R.layout.content_products; }

    @Override
    protected String activeNav() { return "products"; }

    @Override
    protected boolean requireAdmin() { return true; }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (currentUser == null || !currentUser.isAdmin()) return;

        listHost = findViewById(R.id.productListHost);
        findViewById(R.id.addProductButton).setOnClickListener(v -> attemptAdd());
        reload();
    }

    private void reload() {
        new AsyncTask<Void, Void, List<Product>>() {
            @Override protected List<Product> doInBackground(Void... voids) {
                return new ProductDao(ProductsActivity.this).listAll();
            }
            @Override protected void onPostExecute(List<Product> products) {
                listHost.removeAllViews();
                for (Product p : products) listHost.addView(buildEditRow(p));
            }
        }.execute();
    }

    private android.view.View buildEditRow(Product p) {
        android.view.View row = getLayoutInflater().inflate(R.layout.item_product_edit, listHost, false);
        TextView title = row.findViewById(R.id.editProductTitle);
        EditText nameField = row.findViewById(R.id.editName);
        EditText unitField = row.findViewById(R.id.editUnit);
        EditText costField = row.findViewById(R.id.editCost);
        EditText priceField = row.findViewById(R.id.editPrice);
        CheckBox activeBox = row.findViewById(R.id.editActive);
        TextView marginText = row.findViewById(R.id.editMargin);
        Button saveButton = row.findViewById(R.id.editSaveButton);

        title.setText(p.name);
        nameField.setText(p.name);
        unitField.setText(p.unit);
        costField.setText(String.valueOf(p.costCents / 100.0));
        priceField.setText(String.valueOf(p.priceCents / 100.0));
        activeBox.setChecked(p.isActive);
        marginText.setText("Margin: " + Money.format(Math.max(0, p.priceCents - p.costCents), "KSh") + " per " + p.unit);

        saveButton.setOnClickListener(v -> {
            String name = nameField.getText().toString().trim();
            String unit = unitField.getText().toString().trim();
            long cost = Money.parseToCents(costField.getText().toString());
            long price = Money.parseToCents(priceField.getText().toString());
            boolean active = activeBox.isChecked();

            if (name.isEmpty() || price <= 0) {
                Toast.makeText(this, "Name and selling price are required.", Toast.LENGTH_SHORT).show();
                return;
            }
            new AsyncTask<Void, Void, Void>() {
                @Override protected Void doInBackground(Void... voids) {
                    new ProductDao(ProductsActivity.this).update(p.id, name, unit.isEmpty() ? "piece" : unit, cost, price, active);
                    return null;
                }
                @Override protected void onPostExecute(Void v2) {
                    Toast.makeText(ProductsActivity.this, "Updated \"" + name + "\".", Toast.LENGTH_SHORT).show();
                    reload();
                }
            }.execute();
        });

        return row;
    }

    private void attemptAdd() {
        EditText nameField = findViewById(R.id.newProdName);
        EditText unitField = findViewById(R.id.newProdUnit);
        EditText costField = findViewById(R.id.newProdCost);
        EditText priceField = findViewById(R.id.newProdPrice);

        String name = nameField.getText().toString().trim();
        String unit = unitField.getText().toString().trim();
        long cost = Money.parseToCents(costField.getText().toString());
        long price = Money.parseToCents(priceField.getText().toString());

        if (name.isEmpty()) { Toast.makeText(this, "Enter a product name.", Toast.LENGTH_SHORT).show(); return; }
        if (price <= 0) { Toast.makeText(this, "Selling price must be greater than zero.", Toast.LENGTH_SHORT).show(); return; }

        new AsyncTask<Void, Void, Void>() {
            @Override protected Void doInBackground(Void... voids) {
                new ProductDao(ProductsActivity.this).insert(name, unit.isEmpty() ? "piece" : unit, cost, price);
                return null;
            }
            @Override protected void onPostExecute(Void v) {
                nameField.setText(""); unitField.setText("piece"); costField.setText("0"); priceField.setText("");
                Toast.makeText(ProductsActivity.this, "Added \"" + name + "\" to the menu.", Toast.LENGTH_SHORT).show();
                reload();
            }
        }.execute();
    }
}
