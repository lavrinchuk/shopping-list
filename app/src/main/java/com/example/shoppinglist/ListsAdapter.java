package com.example.shoppinglist;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import java.text.DateFormat;
import java.util.Date;
import java.util.Objects;

final class ListsAdapter extends ListAdapter<ShoppingList, ListsAdapter.Holder> {
    interface Actions {
        void open(ShoppingList list);
        void menu(View anchor, ShoppingList list);
    }
    private final Actions actions;
    private final DateFormat dates = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
    ListsAdapter(Actions actions) {
        super(new DiffUtil.ItemCallback<ShoppingList>() {
            @Override public boolean areItemsTheSame(@NonNull ShoppingList a, @NonNull ShoppingList b) { return a.id == b.id; }
            @Override public boolean areContentsTheSame(@NonNull ShoppingList a, @NonNull ShoppingList b) {
                return a.name.equals(b.name) && a.updatedAt == b.updatedAt && Objects.equals(a.archivedAt, b.archivedAt)
                        && a.total == b.total && a.purchased == b.purchased;
            }
        });
        this.actions = actions;
    }
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_list, parent, false));
    }
    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        ShoppingList list = getItem(position);
        holder.name.setText(list.name);
        holder.date.setText(holder.itemView.getContext().getString(list.archivedAt == null ? R.string.updated_date : R.string.archived_date,
                dates.format(new Date(list.archivedAt == null ? list.updatedAt : list.archivedAt))));
        holder.counts.setText(holder.itemView.getContext().getString(R.string.item_counts, list.purchased, list.total));
        holder.progress.setVisibility(list.total == 0 ? View.GONE : View.VISIBLE);
        holder.progress.setMax(Math.max(1, list.total)); holder.progress.setProgressCompat(list.purchased, false);
        holder.itemView.setOnClickListener(view -> actions.open(list));
        holder.menu.setContentDescription(holder.itemView.getContext().getString(R.string.list_actions, list.name));
        holder.menu.setOnClickListener(view -> actions.menu(view, list));
    }
    static final class Holder extends RecyclerView.ViewHolder {
        final TextView name, date, counts;
        final View menu;
        final LinearProgressIndicator progress;
        Holder(View view) {
            super(view); name = view.findViewById(R.id.list_name); date = view.findViewById(R.id.list_date);
            counts = view.findViewById(R.id.list_counts); menu = view.findViewById(R.id.list_menu); progress = view.findViewById(R.id.list_progress);
        }
    }
}
