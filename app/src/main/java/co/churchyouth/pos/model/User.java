package co.churchyouth.pos.model;

public class User {
    public long id;
    public String fullName;
    public String username;
    public String passwordHash;
    public String passwordSalt;
    public String role; // "admin" or "cashier"
    public boolean isActive;
    public int failedLogins;
    public Long lockedUntilEpoch; // null if not locked
    public Long lastLoginEpoch;
    public long createdAtEpoch;

    public boolean isAdmin() { return "admin".equals(role); }
}
