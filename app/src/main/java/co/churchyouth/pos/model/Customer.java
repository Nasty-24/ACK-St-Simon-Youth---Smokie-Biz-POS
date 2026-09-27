package co.churchyouth.pos.model;

public class Customer {
    public long id;
    public String fullName;
    public String phone; // nullable
    public long creditLimitCents;
    public long balanceCents; // positive = owes the kiosk; negative = prepaid credit
    public String notes;
    public boolean isActive = true;
    public long createdAtEpoch;
}
