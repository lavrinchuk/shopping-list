package com.example.shoppinglist;

import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.DiffUtil;
import java.util.List;

final class ShoppingAdapter extends ListAdapter<ShoppingItem, ShoppingAdapter.Holder> {
    interface Actions {
        void purchased(ShoppingItem item, boolean checked);
        void delete(ShoppingItem item);
    }
    private final Actions actions;
    private boolean updating;
    private Runnable idleListener;
    private static final DiffUtil.ItemCallback<ShoppingItem> DIFF = new DiffUtil.ItemCallback<ShoppingItem>() {
        @Override public boolean areItemsTheSame(@NonNull ShoppingItem a, @NonNull ShoppingItem b) {
            return a.id == b.id;
        }
        @Override public boolean areContentsTheSame(@NonNull ShoppingItem a, @NonNull ShoppingItem b) {
            return a.name.equals(b.name) && a.quantity.equals(b.quantity) && a.purchased == b.purchased;
        }
    };
    ShoppingAdapter(Actions actions) { super(DIFF); this.actions = actions; }
    @Override public void submitList(List<ShoppingItem> items) {
        updating = true;
        super.submitList(items, () -> {
            updating = false;
            if (idleListener != null) idleListener.run();
        });
    }
    boolean isIdle() { return !updating; }
    void setIdleListener(Runnable listener) { idleListener = listener; }
    void restoreChecks() { notifyItemRangeChanged(0, getItemCount()); }
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_shopping, parent, false));
    }
    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        ShoppingItem item = getItem(position);
        holder.name.setOnCheckedChangeListener(null);
        holder.name.setText(item.name);
        holder.name.setChecked(item.purchased);
        holder.name.setPaintFlags(item.purchased ? holder.name.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG
                : holder.name.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
        holder.name.setOnCheckedChangeListener((button, checked) -> actions.purchased(item, checked));
        holder.quantity.setText(item.quantity);
        holder.quantity.setVisibility(item.quantity.isEmpty() ? View.GONE : View.VISIBLE);
        holder.delete.setContentDescription(holder.itemView.getContext().getString(R.string.delete_item, item.name));
        holder.delete.setOnClickListener(view -> actions.delete(item));
    }
    static final class Holder extends RecyclerView.ViewHolder {
        final CheckBox name;
        final TextView quantity;
        final View delete;
        Holder(View view) {
            super(view);
            name = view.findViewById(R.id.item_name);
            quantity = view.findViewById(R.id.item_quantity);
            delete = view.findViewById(R.id.delete_item);
        }
    }
}
