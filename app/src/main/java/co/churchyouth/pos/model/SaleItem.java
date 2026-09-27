package co.churchyouth.pos.model;

public class SaleItem {
    public long id;
    public long saleId;
    public Long productId; // nullable — product may be deleted later
    public String productName;
    public double quantity;
    public long unitPriceCents;
    public long unitCostCents;
    public long lineTotalCents;
}
