package co.churchyouth.pos.model;

public class Product {
    public long id;
    public String name;
    public String unit = "piece";
    public long costCents;
    public long priceCents;
    public boolean isActive = true;
    public int sortOrder;
}
