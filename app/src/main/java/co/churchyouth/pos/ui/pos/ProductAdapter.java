package co.churchyouth.pos.ui.pos;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import co.churchyouth.pos.R;
import co.churchyouth.pos.model.Product;
import co.churchyouth.pos.util.Money;

public class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.ViewHolder> {

    public interface OnTapListener { void onTap(Product product); }

    private static final int[] SPINE_DRAWABLES = {
        R.drawable.tile_spine_ember, R.drawable.tile_spine_maize, R.drawable.tile_spine_leaf,
        R.drawable.tile_spine_brick, R.drawable.tile_spine_soft, R.drawable.tile_spine_navy
    };

    private final List<Product> products;
    private final OnTapListener listener;

    public ProductAdapter(List<Product> products, OnTapListener listener) {
        this.products = products;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_product, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Product p = products.get(position);
        holder.name.setText(p.name);
        holder.price.setText(Money.format(p.priceCents, "KSh") + " / " + p.unit);
        holder.root.setBackgroundResource(SPINE_DRAWABLES[position % SPINE_DRAWABLES.length]);
        holder.root.setOnClickListener(v -> listener.onTap(p));
    }

    @Override
    public int getItemCount() { return products.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        View root;
        TextView name;
        TextView price;
        ViewHolder(View itemView) {
            super(itemView);
            root = itemView;
            name = itemView.findViewById(R.id.tileName);
            price = itemView.findViewById(R.id.tilePrice);
        }
    }
}
