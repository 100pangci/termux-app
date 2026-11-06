package com.termux.app.ssh;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.termux.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** RecyclerView adapter for the saved SSH host list. */
public class SshHostAdapter extends RecyclerView.Adapter<SshHostAdapter.HostViewHolder> {

    public interface Listener {
        void onHostClicked(@NonNull SshHost host);

        void onHostEdit(@NonNull SshHost host);

        void onHostDelete(@NonNull SshHost host);
    }

    private final Listener mListener;
    private final List<SshHost> mHosts = new ArrayList<>();

    public SshHostAdapter(@NonNull Listener listener) {
        mListener = listener;
    }

    public void setHosts(@NonNull List<SshHost> hosts) {
        int oldSize = mHosts.size();
        mHosts.clear();
        mHosts.addAll(hosts);
        if (oldSize > 0) notifyItemRangeRemoved(0, oldSize);
        if (!mHosts.isEmpty()) notifyItemRangeInserted(0, mHosts.size());
    }

    @NonNull
    @Override
    public HostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_ssh_host, parent, false);
        return new HostViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HostViewHolder holder, int position) {
        SshHost host = mHosts.get(position);

        String name = host.name == null ? "" : host.name.trim();
        if (name.isEmpty()) name = host.getDefaultName();
        holder.name.setText(name);

        if (host.hasConfigAlias()) {
            holder.target.setText(holder.itemView.getContext()
                .getString(R.string.ssh_host_target_alias, host.configAlias.trim()));
            holder.aliasBadge.setVisibility(View.VISIBLE);
        } else {
            holder.target.setText(formatHostTarget(host));
            holder.aliasBadge.setVisibility(View.GONE);
        }

        String notes = host.notes == null ? "" : host.notes.trim();
        if (notes.isEmpty()) {
            holder.notes.setVisibility(View.GONE);
        } else {
            holder.notes.setText(notes);
            holder.notes.setVisibility(View.VISIBLE);
        }

        holder.itemView.setContentDescription(name + ", " + holder.target.getText());
        holder.itemView.setOnClickListener(v -> mListener.onHostClicked(host));

        holder.menuButton.setOnClickListener(v -> showItemMenu(holder, host));
    }

    @Override
    public int getItemCount() {
        return mHosts.size();
    }

    private void showItemMenu(@NonNull HostViewHolder holder, @NonNull SshHost host) {
        PopupMenu popupMenu = new PopupMenu(holder.itemView.getContext(), holder.menuButton);
        popupMenu.inflate(R.menu.menu_ssh_host_item);
        popupMenu.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.action_edit_ssh_host) {
                mListener.onHostEdit(host);
                return true;
            } else if (itemId == R.id.action_delete_ssh_host) {
                mListener.onHostDelete(host);
                return true;
            }
            return false;
        });
        popupMenu.show();
    }

    @NonNull
    public static String formatHostTarget(@NonNull SshHost host) {
        String hostname = host.hostname == null ? "" : host.hostname.trim();
        String username = host.username == null ? "" : host.username.trim();

        String target = hostname;
        if (!username.isEmpty()) target = username + "@" + hostname;

        if (host.port != SshHost.UNSET_PORT) {
            if (hostname.indexOf(':') >= 0 && !hostname.startsWith("[")) {
                target = username.isEmpty()
                    ? "[" + hostname + "]:" + host.port
                    : username + "@[" + hostname + "]:" + host.port;
            } else {
                target += ":" + host.port;
            }
        }

        return target;
    }

    public static boolean matchesQuery(@NonNull SshHost host, @NonNull String query) {
        String normalizedQuery = query.trim().toLowerCase(Locale.ROOT);
        if (normalizedQuery.isEmpty()) return true;

        return contains(host.name, normalizedQuery)
            || contains(host.hostname, normalizedQuery)
            || contains(host.username, normalizedQuery)
            || contains(host.configAlias, normalizedQuery)
            || contains(host.notes, normalizedQuery);
    }

    private static boolean contains(String value, @NonNull String normalizedQuery) {
        return !TextUtils.isEmpty(value)
            && value.toLowerCase(Locale.ROOT).contains(normalizedQuery);
    }

    static class HostViewHolder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView target;
        final TextView notes;
        final TextView aliasBadge;
        final ImageButton menuButton;

        HostViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.ssh_host_name);
            target = itemView.findViewById(R.id.ssh_host_target);
            notes = itemView.findViewById(R.id.ssh_host_notes);
            aliasBadge = itemView.findViewById(R.id.ssh_host_alias_badge);
            menuButton = itemView.findViewById(R.id.ssh_host_menu);
        }
    }

}
