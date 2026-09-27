package co.churchyouth.pos.model;

public class Payment {
    public long id;
    public long customerId;
    public long amountCents;
    public String paymentMethod; // cash, mpesa
    public long receivedBy;
    public String receivedByName; // populated by joins
    public String notes;
    public long paymentAtEpoch;
}
