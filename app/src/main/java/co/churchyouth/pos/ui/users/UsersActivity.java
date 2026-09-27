package co.churchyouth.pos.ui.users;

import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

import co.churchyouth.pos.R;
import co.churchyouth.pos.data.UserDao;
import co.churchyouth.pos.model.User;
import co.churchyouth.pos.ui.common.BaseActivity;

public class UsersActivity extends BaseActivity {

    private LinearLayout listHost;

    @Override
    protected int contentLayoutId() { return R.layout.content_users; }

    @Override
    protected String activeNav() { return "users"; }

    @Override
    protected boolean requireAdmin() { return true; }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (currentUser == null || !currentUser.isAdmin()) return;

        listHost = findViewById(R.id.userListHost);
        findViewById(R.id.addUserButton).setOnClickListener(v -> attemptAdd());
        reload();
    }

    private void reload() {
        new AsyncTask<Void, Void, List<User>>() {
            @Override protected List<User> doInBackground(Void... voids) {
                return new UserDao(UsersActivity.this).listAll();
            }
            @Override protected void onPostExecute(List<User> users) {
                listHost.removeAllViews();
                for (User u : users) listHost.addView(buildEditRow(u));
            }
        }.execute();
    }

    private android.view.View buildEditRow(User u) {
        android.view.View row = getLayoutInflater().inflate(R.layout.item_user_edit, listHost, false);
        TextView title = row.findViewById(R.id.editUserTitle);
        EditText nameField = row.findViewById(R.id.editFullName);
        RadioGroup roleGroup = row.findViewById(R.id.editRoleGroup);
        CheckBox activeBox = row.findViewById(R.id.editUserActive);
        EditText newPasswordField = row.findViewById(R.id.editNewPassword);
        TextView lockNote = row.findViewById(R.id.editLockNote);
        Button saveButton = row.findViewById(R.id.editUserSaveButton);

        title.setText(u.username);
        nameField.setText(u.fullName);
        ((android.widget.RadioButton) row.findViewById(u.isAdmin() ? R.id.editRoleAdmin : R.id.editRoleCashier)).setChecked(true);
        activeBox.setChecked(u.isActive);

        if (u.lockedUntilEpoch != null && u.lockedUntilEpoch > System.currentTimeMillis() / 1000L) {
            lockNote.setVisibility(android.view.View.VISIBLE);
            lockNote.setText("Locked after repeated failed logins.");
        } else {
            lockNote.setVisibility(android.view.View.GONE);
        }

        saveButton.setOnClickListener(v -> {
            String name = nameField.getText().toString().trim();
            String role = roleGroup.getCheckedRadioButtonId() == R.id.editRoleAdmin ? "admin" : "cashier";
            boolean active = activeBox.isChecked();
            String newPassword = newPasswordField.getText().toString();

            new AsyncTask<Void, Void, String>() {
                @Override protected String doInBackground(Void... voids) {
                    UserDao dao = new UserDao(UsersActivity.this);
                    boolean losingLastAdmin = u.isAdmin() && (!role.equals("admin") || !active) && dao.countActiveAdmins() <= 1;
                    if (losingLastAdmin) {
                        return "At least one active admin must remain — add another admin first.";
                    }
                    dao.updateProfile(u.id, name, role, active);
                    if (!newPassword.isEmpty()) {
                        if (newPassword.length() < 8) {
                            return "Staff details saved, but the password reset was skipped — it needs at least 8 characters.";
                        }
                        dao.resetPassword(u.id, newPassword);
                    }
                    return null;
                }
                @Override protected void onPostExecute(String warning) {
                    Toast.makeText(UsersActivity.this, warning != null ? warning : "Staff account updated.", Toast.LENGTH_LONG).show();
                    reload();
                }
            }.execute();
        });

        return row;
    }

    private void attemptAdd() {
        EditText nameField = findViewById(R.id.newUserFullName);
        EditText usernameField = findViewById(R.id.newUsername);
        EditText passwordField = findViewById(R.id.newUserPassword);
        RadioGroup roleGroup = findViewById(R.id.newUserRoleGroup);

        String name = nameField.getText().toString().trim();
        String username = usernameField.getText().toString().trim();
        String password = passwordField.getText().toString();
        String role = roleGroup.getCheckedRadioButtonId() == R.id.newRoleAdmin ? "admin" : "cashier";

        if (name.isEmpty() || username.isEmpty() || password.length() < 8) {
            Toast.makeText(this, "Full name, username, and a password of at least 8 characters are required.", Toast.LENGTH_LONG).show();
            return;
        }

        new AsyncTask<Void, Void, Long>() {
            @Override protected Long doInBackground(Void... voids) {
                return new UserDao(UsersActivity.this).insert(name, username, password, role);
            }
            @Override protected void onPostExecute(Long id) {
                if (id == -1) {
                    Toast.makeText(UsersActivity.this, "That username is already taken.", Toast.LENGTH_LONG).show();
                } else {
                    nameField.setText(""); usernameField.setText(""); passwordField.setText("");
                    Toast.makeText(UsersActivity.this, "Added " + name + " as " + role + ".", Toast.LENGTH_SHORT).show();
                    reload();
                }
            }
        }.execute();
    }
}
