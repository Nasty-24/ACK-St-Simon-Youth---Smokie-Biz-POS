package co.churchyouth.pos.ui.login;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import co.churchyouth.pos.R;
import co.churchyouth.pos.data.UserDao;
import co.churchyouth.pos.model.User;
import co.churchyouth.pos.ui.dashboard.DashboardActivity;
import co.churchyouth.pos.util.PasswordUtil;
import co.churchyouth.pos.util.SessionManager;

public class LoginActivity extends AppCompatActivity {

    private EditText usernameField;
    private EditText passwordField;
    private TextView errorText;
    private Button signInButton;
    private ProgressBar progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SessionManager session = new SessionManager(this);
        if (session.isLoggedIn()) {
            startActivity(new Intent(this, DashboardActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_login);
        usernameField = findViewById(R.id.usernameField);
        passwordField = findViewById(R.id.passwordField);
        errorText = findViewById(R.id.errorText);
        signInButton = findViewById(R.id.signInButton);
        progress = findViewById(R.id.progress);

        signInButton.setOnClickListener(v -> attemptLogin());
    }

    private void attemptLogin() {
        String username = usernameField.getText().toString().trim();
        String password = passwordField.getText().toString();

        if (username.isEmpty() || password.isEmpty()) {
            showError("Enter both your username and password.");
            return;
        }

        setLoading(true);
        // Password hashing takes a couple hundred milliseconds by design
        // (see PasswordUtil) — never do this on the UI thread.
        new LoginTask().execute(new String[]{username, password});
    }

    private class LoginTask extends AsyncTask<String, Void, LoginResult> {
        @Override
        protected LoginResult doInBackground(String... params) {
            String username = params[0];
            String password = params[1];
            UserDao userDao = new UserDao(LoginActivity.this);
            User user = userDao.findByUsername(username);

            LoginResult result = new LoginResult();

            if (user != null && user.isActive && user.lockedUntilEpoch != null
                    && user.lockedUntilEpoch > System.currentTimeMillis() / 1000L) {
                result.success = false;
                result.message = "This account is temporarily locked after too many attempts. Try again later.";
                return result;
            }

            boolean ok = user != null && user.isActive && PasswordUtil.verify(password, user.passwordHash, user.passwordSalt);

            if (!ok) {
                if (user != null) userDao.recordFailedLogin(user);
                result.success = false;
                result.message = "Incorrect username or password.";
                return result;
            }

            userDao.recordSuccessfulLogin(user.id);
            result.success = true;
            result.userId = user.id;
            return result;
        }

        @Override
        protected void onPostExecute(LoginResult result) {
            setLoading(false);
            if (result.success) {
                new SessionManager(LoginActivity.this).login(result.userId);
                Intent intent = new Intent(LoginActivity.this, DashboardActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            } else {
                showError(result.message);
            }
        }
    }

    private static class LoginResult {
        boolean success;
        String message;
        long userId;
    }

    private void setLoading(boolean loading) {
        signInButton.setEnabled(!loading);
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void showError(String message) {
        errorText.setText(message);
        errorText.setVisibility(View.VISIBLE);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
