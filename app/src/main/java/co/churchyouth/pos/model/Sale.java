package co.churchyouth.pos.model;

import java.util.List;

public class Sale {
    public long id;
    public String receiptNo;
    public Long customerId; // nullable = walk-in
    public String customerName; // populated by joins, not stored here
    public long userId;
    public String cashierName; // populated by joins
    public long saleAtEpoch;
    public long subtotalCents;
    public long discountCents;
    public long totalCents;
    public long amountPaidCents;
    public String paymentMethod; // cash, mpesa, credit
    public String status = "completed"; // completed, voided
    public List<SaleItem> items;

    public long balanceDueCents() {
        return totalCents - amountPaidCents;
    }
}
