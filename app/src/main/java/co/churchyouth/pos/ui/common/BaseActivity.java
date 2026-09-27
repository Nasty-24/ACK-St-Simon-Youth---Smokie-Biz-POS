package co.churchyouth.pos.ui.common;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import co.churchyouth.pos.R;
import co.churchyouth.pos.data.UserDao;
import co.churchyouth.pos.model.User;
import co.churchyouth.pos.ui.customers.CustomersActivity;
import co.churchyouth.pos.ui.dashboard.DashboardActivity;
import co.churchyouth.pos.ui.login.LoginActivity;
import co.churchyouth.pos.ui.pos.PosActivity;
import co.churchyouth.pos.ui.products.ProductsActivity;
import co.churchyouth.pos.ui.reports.ReportsActivity;
import co.churchyouth.pos.ui.users.UsersActivity;
import co.churchyouth.pos.util.SessionManager;

/**
 * Every screen except LoginActivity extends this. It enforces the session
 * (bounces to login if nobody's signed in or the 30-minute idle timeout
 * hit), and wires up the bottom navigation bar the same way header.php /
 * footer.php did in the original web version.
 */
public abstract class BaseActivity extends AppCompatActivity {

    protected SessionManager session;
    protected User currentUser;

    /** Subclasses return the content view to inflate above the bottom nav. */
    protected abstract int contentLayoutId();

    /** Which bottom-nav tab to highlight: "pos", "customers", "reports", "products", "users", or "" for none. */
    protected String activeNav() { return ""; }

    protected boolean requireAdmin() { return false; }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        session = new SessionManager(this);

        long userId = session.currentUserId();
        if (userId == -1) {
            goToLogin();
            return;
        }
        currentUser = new UserDao(this).findById(userId);
        if (currentUser == null || !currentUser.isActive) {
            session.logout();
            goToLogin();
            return;
        }
        if (requireAdmin() && !currentUser.isAdmin()) {
            setContentView(R.layout.activity_forbidden);
            return;
        }

        setContentView(R.layout.activity_base_shell);
        LinearLayout contentHost = findViewById(R.id.contentHost);
        getLayoutInflater().inflate(contentLayoutId(), contentHost, true);

        TextView who = findViewById(R.id.topbarWho);
        if (who != null) who.setText(currentUser.fullName + " (" + currentUser.role + ")");

        findViewById(R.id.topbarLogout).setOnClickListener(v -> {
            session.logout();
            goToLogin();
        });

        wireBottomNav();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (session != null) session.touch();
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        if (session != null) session.touch();
    }

    private void wireBottomNav() {
        String active = activeNav();
        setNavItem(R.id.navPos, "pos", active, PosActivity.class);
        setNavItem(R.id.navCustomers, "customers", active, CustomersActivity.class);
        setNavItem(R.id.navReports, "reports", active, ReportsActivity.class);

        View navProducts = findViewById(R.id.navProducts);
        View navUsers = findViewById(R.id.navUsers);
        boolean isAdmin = currentUser != null && currentUser.isAdmin();
        navProducts.setVisibility(isAdmin ? View.VISIBLE : View.GONE);
        navUsers.setVisibility(isAdmin ? View.VISIBLE : View.GONE);
        if (isAdmin) {
            setNavItem(R.id.navProducts, "products", active, ProductsActivity.class);
            setNavItem(R.id.navUsers, "users", active, UsersActivity.class);
        }

        findViewById(R.id.topbarBrand).setOnClickListener(v -> {
            if (!(this instanceof DashboardActivity)) {
                startActivity(new Intent(this, DashboardActivity.class));
            }
        });
    }

    private void setNavItem(int viewId, String tag, String active, Class<?> destination) {
        View item = findViewById(viewId);
        boolean isActive = tag.equals(active);
        item.setSelected(isActive);
        if (item instanceof LinearLayout) {
            LinearLayout row = (LinearLayout) item;
            int color = isActive ? ContextCompat.getColor(this, R.color.ember) : ContextCompat.getColor(this, R.color.ink_soft);
            for (int i = 0; i < row.getChildCount(); i++) {
                View child = row.getChildAt(i);
                if (child instanceof TextView) ((TextView) child).setTextColor(color);
            }
        }
        item.setOnClickListener(v -> {
            if (!tag.equals(active)) {
                startActivity(new Intent(this, destination));
            }
        });
    }

    private void goToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
