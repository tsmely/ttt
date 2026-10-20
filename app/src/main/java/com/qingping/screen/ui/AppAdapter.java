package com.qingping.screen.ui;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;

import com.qingping.screen.R;
import com.qingping.screen.core.AppInventory;
import com.qingping.screen.core.Prefs;
import com.qingping.screen.core.StatsDb;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class AppAdapter extends BaseAdapter {

    interface Listener {
        void onGuardChanged();

        void onRowClick(AppInventory.AppEntry entry);
    }

    private final Context context;
    private final Prefs prefs;
    private final Listener listener;
    private final List<AppInventory.AppEntry> items = new ArrayList<>();
    private Map<String, StatsDb.AppStat> counts = new HashMap<>();
    private final Drawable fallbackIcon;

    AppAdapter(Context context, Prefs prefs, Listener listener) {
        this.context = context;
        this.prefs = prefs;
        this.listener = listener;
        this.fallbackIcon = context.getPackageManager().getDefaultActivityIcon();
    }

    void setItems(List<AppInventory.AppEntry> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    void setCounts(Map<String, StatsDb.AppStat> newCounts) {
        counts = newCounts == null ? new HashMap<>() : newCounts;
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public AppInventory.AppEntry getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_app, parent, false);
            holder = new ViewHolder();
            holder.icon = convertView.findViewById(R.id.app_icon);
            holder.name = convertView.findViewById(R.id.app_name);
            holder.meta = convertView.findViewById(R.id.app_meta);
            holder.count = convertView.findViewById(R.id.app_count);
            holder.toggle = convertView.findViewById(R.id.app_switch);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        final AppInventory.AppEntry entry = items.get(position);
        holder.name.setText(entry.label);
        holder.meta.setText(entry.pkg);
        holder.icon.setImageDrawable(entry.icon != null ? entry.icon : fallbackIcon);

        StatsDb.AppStat stat = counts.get(entry.pkg);
        long count = stat == null ? 0 : stat.count;
        holder.count.setText(String.valueOf(count));
        holder.count.setTextColor(context.getResources().getColor(
                count > 0 ? R.color.primary : R.color.faint));

        holder.toggle.setOnCheckedChangeListener(null);
        holder.toggle.setChecked(prefs.isGuarded(entry.pkg));
        holder.toggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.setGuarded(entry.pkg, isChecked);
            listener.onGuardChanged();
        });

        convertView.setOnClickListener(v -> listener.onRowClick(entry));
        return convertView;
    }

    private static final class ViewHolder {
        ImageView icon;
        TextView name;
        TextView meta;
        TextView count;
        Switch toggle;
    }
}
