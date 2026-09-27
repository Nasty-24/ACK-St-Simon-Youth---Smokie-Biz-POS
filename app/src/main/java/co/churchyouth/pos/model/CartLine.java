package co.churchyouth.pos.model;

import java.io.Serializable;

/** One line of the in-progress sale, held in memory until checkout. */
public class CartLine implements Serializable {
    public long productId;
    public String name;
    public long unitPriceCents;
    public double quantity;

    public CartLine(long productId, String name, long unitPriceCents, double quantity) {
        this.productId = productId;
        this.name = name;
        this.unitPriceCents = unitPriceCents;
        this.quantity = quantity;
    }

    public long lineTotalCents() {
        return Math.round(unitPriceCents * quantity);
    }
}
