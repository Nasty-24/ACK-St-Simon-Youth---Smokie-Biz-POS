package co.churchyouth.pos.ui.pos;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import co.churchyouth.pos.R;
import co.churchyouth.pos.data.ProductDao;
import co.churchyouth.pos.model.CartLine;
import co.churchyouth.pos.model.Product;
import co.churchyouth.pos.ui.checkout.CheckoutActivity;
import co.churchyouth.pos.ui.common.BaseActivity;
import co.churchyouth.pos.util.Money;

public class PosActivity extends BaseActivity {

    public static final String EXTRA_CART = "cart";

    private final List<CartLine> cart = new ArrayList<>();
    private LinearLayout cartHost;
    private TextView emptyCartText;
    private TextView totalText;
    private Button checkoutButton;

    @Override
    protected int contentLayoutId() { return R.layout.content_pos; }

    @Override
    protected String activeNav() { return "pos"; }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (currentUser == null) return;

        RecyclerView grid = findViewById(R.id.productGrid);
        grid.setLayoutManager(new GridLayoutManager(this, 2));

        cartHost = findViewById(R.id.cartHost);
        emptyCartText = findViewById(R.id.emptyCartText);
        totalText = findViewById(R.id.totalText);
        checkoutButton = findViewById(R.id.checkoutButton);

        checkoutButton.setOnClickListener(v -> goToCheckout());

        new AsyncTask<Void, Void, List<Product>>() {
            @Override protected List<Product> doInBackground(Void... voids) {
                return new ProductDao(PosActivity.this).listActive();
            }
            @Override protected void onPostExecute(List<Product> products) {
                if (products.isEmpty()) {
                    findViewById(R.id.noProductsText).setVisibility(View.VISIBLE);
                    grid.setVisibility(View.GONE);
                    return;
                }
                grid.setAdapter(new ProductAdapter(products, PosActivity.this::addToCart));
            }
        }.execute();

        renderCart();
    }

    private void addToCart(Product product) {
        for (CartLine line : cart) {
            if (line.productId == product.id) {
                line.quantity += 1;
                renderCart();
                return;
            }
        }
        cart.add(new CartLine(product.id, product.name, product.priceCents, 1));
        renderCart();
    }

    private void changeQty(CartLine line, double delta) {
        line.quantity += delta;
        if (line.quantity <= 0) cart.remove(line);
        renderCart();
    }

    private void renderCart() {
        cartHost.removeAllViews();
        long total = 0;
        for (CartLine line : cart) {
            total += line.lineTotalCents();
            cartHost.addView(buildCartRow(line));
        }
        boolean hasItems = !cart.isEmpty();
        emptyCartText.setVisibility(hasItems ? View.GONE : View.VISIBLE);
        totalText.setVisibility(hasItems ? View.VISIBLE : View.GONE);
        totalText.setText("Total: " + Money.format(total, "KSh"));
        checkoutButton.setEnabled(hasItems);
    }

    private View buildCartRow(CartLine line) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 14, 0, 14);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView name = new TextView(this);
        name.setText(line.name);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        name.setLayoutParams(nameParams);

        Button minus = smallStepperButton("−");
        minus.setOnClickListener(v -> changeQty(line, -1));

        TextView qty = new TextView(this);
        qty.setText(trimQty(line.quantity));
        qty.setPadding(16, 0, 16, 0);

        Button plus = smallStepperButton("+");
        plus.setOnClickListener(v -> changeQty(line, 1));

        TextView lineTotal = new TextView(this);
        lineTotal.setText(Money.format(line.lineTotalCents(), "KSh"));
        lineTotal.setPadding(16, 0, 0, 0);

        row.addView(name);
        row.addView(minus);
        row.addView(qty);
        row.addView(plus);
        row.addView(lineTotal);
        return row;
    }

    private Button smallStepperButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setBackgroundResource(R.drawable.bg_stepper_btn);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(84, 84);
        b.setLayoutParams(params);
        b.setPadding(0, 0, 0, 0);
        b.setMinWidth(0);
        b.setMinHeight(0);
        b.setMinimumWidth(0);
        b.setMinimumHeight(0);
        return b;
    }

    private String trimQty(double qty) {
        if (qty == Math.floor(qty)) return String.valueOf((long) qty);
        return String.valueOf(qty);
    }

    private void goToCheckout() {
        if (cart.isEmpty()) return;
        Intent intent = new Intent(this, CheckoutActivity.class);
        intent.putExtra(EXTRA_CART, new ArrayList<>(cart));
        startActivityForResult(intent, 1001);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1001 && resultCode == RESULT_OK) {
            // Sale completed — clear the cart for the next customer.
            cart.clear();
            renderCart();
        }
    }
}
