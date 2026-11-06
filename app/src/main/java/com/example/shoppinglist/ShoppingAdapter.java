package com.example.shoppinglist;

import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.example.shoppinglist.model.ShoppingItem;
import com.google.android.material.color.MaterialColors;
import java.util.List;

final class ShoppingAdapter extends ListAdapter<ShoppingItem, ShoppingAdapter.Holder> {
    interface Actions {
        void purchased(ShoppingItem item, boolean checked);

        void delete(ShoppingItem item);

        void search(ShoppingItem item);

        void editLink(ShoppingItem item);

        void openLink(ShoppingItem item);

        void barcode(ShoppingItem item);
    }

    private final Actions actions;
    private boolean updating;
    private boolean readOnly;

    void setReadOnly(boolean readOnly) {
        if (this.readOnly != readOnly) {
            this.readOnly = readOnly;
            notifyItemRangeChanged(0, getItemCount());
        }
    }

    private Runnable idleListener;
    private static final DiffUtil.ItemCallback<ShoppingItem> DIFF =
            new DiffUtil.ItemCallback<ShoppingItem>() {
                @Override
                public boolean areItemsTheSame(@NonNull ShoppingItem a, @NonNull ShoppingItem b) {
                    return a.id == b.id;
                }

                @Override
                public boolean areContentsTheSame(
                        @NonNull ShoppingItem a, @NonNull ShoppingItem b) {
                    return a.name.equals(b.name)
                            && a.quantity.equals(b.quantity)
                            && a.purchased == b.purchased
                            && java.util.Objects.equals(a.url, b.url)
                            && java.util.Objects.equals(a.barcode, b.barcode);
                }
            };

    ShoppingAdapter(Actions actions) {
        super(DIFF);
        this.actions = actions;
    }

    @Override
    public void submitList(List<ShoppingItem> items) {
        updating = true;
        super.submitList(
                items,
                () -> {
                    updating = false;
                    if (idleListener != null) {
                        idleListener.run();
                    }
                });
    }

    boolean isIdle() {
        return !updating;
    }

    void setIdleListener(Runnable listener) {
        idleListener = listener;
    }

    void restoreChecks() {
        notifyItemRangeChanged(0, getItemCount());
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new Holder(
                LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_shopping, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        ShoppingItem item = getItem(position);
        holder.name.setOnCheckedChangeListener(null);
        holder.name.setText(item.name);
        holder.name.setChecked(item.purchased);
        holder.name.setEnabled(!readOnly);
        holder.delete.setVisibility(readOnly ? View.GONE : View.VISIBLE);
        holder.search.setContentDescription(
                holder.itemView.getContext().getString(R.string.search_item, item.name));
        holder.search.setOnClickListener(view -> actions.search(item));
        holder.editLink.setVisibility(View.VISIBLE);
        holder.editLink.setContentDescription(
                holder.itemView.getContext().getString(R.string.edit_item_link, item.name));
        holder.editLink.setOnClickListener(view -> actions.editLink(item));
        holder.openLink.setVisibility(item.url == null ? View.GONE : View.VISIBLE);
        holder.openLink.setText(R.string.view_store_links);
        holder.openLink.setOnClickListener(view -> actions.openLink(item));
        holder.name.setTextColor(
                MaterialColors.getColor(
                        holder.name,
                        item.purchased
                                ? com.google.android.material.R.attr.colorOnSurfaceVariant
                                : com.google.android.material.R.attr.colorOnSurface));
        holder.name.setPaintFlags(
                item.purchased
                        ? holder.name.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG
                        : holder.name.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
        holder.name.setOnCheckedChangeListener(
                (button, checked) -> actions.purchased(item, checked));
        holder.quantity.setText(item.quantity);
        holder.quantity.setVisibility(item.quantity.isEmpty() ? View.GONE : View.VISIBLE);
        holder.barcode.setText(
                item.barcode == null ? R.string.barcode_scan : R.string.barcode_view);
        holder.barcode.setVisibility(readOnly && item.barcode == null ? View.GONE : View.VISIBLE);
        holder.barcode.setContentDescription(
                holder.itemView.getContext().getString(R.string.barcode_action_for, item.name));
        holder.barcode.setOnClickListener(view -> actions.barcode(item));
        holder.barcodeValue.setVisibility(item.barcode == null ? View.GONE : View.VISIBLE);
        holder.barcodeValue.setText(
                item.barcode == null
                        ? ""
                        : holder.itemView
                                .getContext()
                                .getString(R.string.barcode_value, item.barcode));
        holder.delete.setContentDescription(
                holder.itemView.getContext().getString(R.string.delete_item, item.name));
        holder.delete.setOnClickListener(view -> actions.delete(item));
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final CheckBox name;
        final TextView quantity;
        final View delete;
        final View search;
        final View editLink;
        final View openLink;
        final TextView barcode;
        final TextView barcodeValue;

        Holder(View view) {
            super(view);
            name = view.findViewById(R.id.item_name);
            quantity = view.findViewById(R.id.item_quantity);
            delete = view.findViewById(R.id.delete_item);
            search = view.findViewById(R.id.search_item);
            editLink = view.findViewById(R.id.edit_item_link);
            openLink = view.findViewById(R.id.open_item_link);
            barcode = view.findViewById(R.id.barcode_action);
            barcodeValue = view.findViewById(R.id.barcode_value);
        }
    }
}
