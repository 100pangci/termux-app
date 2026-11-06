package com.termux.app.ssh;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.IBinder;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.MenuItemCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.MaterialColors;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.TermuxService;
import com.termux.app.terminal.TermuxTerminalSessionActivityClient;
import com.termux.shared.activity.media.AppCompatActivityUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;
import com.termux.shared.theme.NightMode;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * List of saved SSH hosts. Opening this activity never creates an SSH session; a session is only
 * created from {@link #connectToHost(SshHost)} after an explicit row tap.
 */
public final class SshHostListActivity extends AppCompatActivity implements SshHostAdapter.Listener {

    private static final String LOG_TAG = "SshHostListActivity";

    private SshHostStore mHostStore;
    private SshHostAdapter mAdapter;
    private List<SshHost> mAllHosts = new ArrayList<>();
    private EditText mSearchInput;
    private View mEmptyView;
    private TextView mEmptyTitle;
    private TextView mEmptyMessage;
    private TermuxService mTermuxService;
    private boolean mBound;
    private boolean mConnecting;

    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mTermuxService = ((TermuxService.LocalBinder) service).service;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            // The binding itself is still active; only the remote service process died.
            mTermuxService = null;
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppCompatActivityUtils.setNightMode(this, NightMode.getAppNightMode().getName(), true);
        DynamicColors.applyToActivityIfAvailable(this);

        setContentView(R.layout.activity_ssh_hosts);

        mHostStore = SshHostStore.get(this);

        MaterialToolbar toolbar = findViewById(R.id.ssh_hosts_toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_import_ssh_config) {
                importFromOpenSshConfig();
                return true;
            }
            return false;
        });
        tintToolbarMenu(toolbar);

        mSearchInput = findViewById(R.id.ssh_hosts_search_input);
        mEmptyView = findViewById(R.id.ssh_hosts_empty_view);
        mEmptyTitle = findViewById(R.id.ssh_hosts_empty_title);
        mEmptyMessage = findViewById(R.id.ssh_hosts_empty_message);

        RecyclerView recyclerView = findViewById(R.id.ssh_hosts_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        mAdapter = new SshHostAdapter(this);
        recyclerView.setAdapter(mAdapter);

        FloatingActionButton addButton = findViewById(R.id.ssh_hosts_add_button);
        addButton.setOnClickListener(v ->
            startActivity(SshHostEditActivity.newInstance(this, null, null)));

        mSearchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                applyFilter(s == null ? "" : s.toString());
            }
        });

        bindTermuxService();
    }

    /**
     * AppCompat's SupportMenuInflater ignores {@code app:iconTint} in menu XML, so the import
     * icon is tinted here with the toolbar's content color to stay visible on light surfaces.
     */
    private void tintToolbarMenu(@NonNull MaterialToolbar toolbar) {
        MenuItem importItem = toolbar.getMenu().findItem(R.id.action_import_ssh_config);
        if (importItem == null || importItem.getIcon() == null) return;
        MenuItemCompat.setIconTintList(importItem, ColorStateList.valueOf(
            MaterialColors.getColor(toolbar, com.google.android.material.R.attr.colorOnSurface)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        reloadHosts();
    }

    @Override
    protected void onDestroy() {
        if (mBound) {
            unbindService(mServiceConnection);
            mBound = false;
        }
        mTermuxService = null;
        super.onDestroy();
    }

    private void bindTermuxService() {
        Intent serviceIntent = new Intent(this, TermuxService.class);
        try {
            // Keep the service running independently of this activity, like TermuxActivity does, so
            // a session created before TermuxActivity starts is not torn down when we unbind.
            startService(serviceIntent);
            mBound = bindService(serviceIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
            if (!mBound) {
                Logger.logError(LOG_TAG, "bindService() failed");
            }
        } catch (Exception e) {
            mBound = false;
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to start/bind TermuxService", e);
        }
    }

    private void reloadHosts() {
        List<SshHost> hosts = mHostStore.loadHosts();
        Collections.sort(hosts, (left, right) -> {
            String leftName = left.name == null ? "" : left.name;
            String rightName = right.name == null ? "" : right.name;
            return leftName.compareToIgnoreCase(rightName);
        });
        mAllHosts = hosts;
        applyFilter(mSearchInput.getText() == null ? "" : mSearchInput.getText().toString());
    }

    private void applyFilter(@NonNull String query) {
        List<SshHost> filtered = new ArrayList<>();
        for (SshHost host : mAllHosts) {
            if (SshHostAdapter.matchesQuery(host, query)) filtered.add(host);
        }

        mAdapter.setHosts(filtered);

        if (!filtered.isEmpty()) {
            mEmptyView.setVisibility(View.GONE);
        } else {
            boolean hasSavedHosts = !mAllHosts.isEmpty();
            mEmptyTitle.setText(hasSavedHosts
                ? R.string.ssh_hosts_no_results_title : R.string.ssh_hosts_empty_title);
            mEmptyMessage.setText(hasSavedHosts
                ? R.string.ssh_hosts_no_results_message : R.string.ssh_hosts_empty_message);
            mEmptyView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onHostClicked(@NonNull SshHost host) {
        connectToHost(host);
    }

    @Override
    public void onHostEdit(@NonNull SshHost host) {
        startActivity(SshHostEditActivity.newInstance(this, host.id, null));
    }

    @Override
    public void onHostDelete(@NonNull SshHost host) {
        String name = host.name == null || host.name.trim().isEmpty()
            ? host.getDefaultName() : host.name.trim();

        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.ssh_host_delete_title)
            .setMessage(getString(R.string.ssh_host_delete_message, name))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.ssh_host_delete_action, (dialog, which) -> {
                if (mHostStore.delete(host.id)) {
                    reloadHosts();
                } else {
                    showToast(R.string.ssh_host_error_save_failed);
                }
            })
            .show();
    }

    private void connectToHost(@NonNull SshHost host) {
        if (mConnecting) return;

        if (mTermuxService == null) {
            showToast(R.string.ssh_host_service_not_ready);
            return;
        }

        if (mTermuxService.getTermuxSessionsSize() >= TermuxTerminalSessionActivityClient.MAX_SESSIONS) {
            new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.title_max_terminals_reached)
                .setMessage(R.string.msg_max_terminals_reached)
                .setPositiveButton(android.R.string.ok, null)
                .show();
            return;
        }

        if (!SshSessionLauncher.isOpenSshAvailable()) {
            new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.ssh_host_openssh_missing_title)
                .setMessage(R.string.ssh_host_openssh_missing_message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
            return;
        }

        mConnecting = true;
        TermuxSession session = SshSessionLauncher.createSession(this, mTermuxService, host, host.name);
        if (session == null) {
            mConnecting = false;
            showToast(R.string.ssh_host_connect_failed);
            return;
        }

        TermuxActivity.startTermuxActivity(this);
        finish();
    }

    private void importFromOpenSshConfig() {
        File homeDirectory = new File(TermuxConstants.TERMUX_HOME_DIR_PATH);
        File sshDirectory = new File(homeDirectory, ".ssh");
        File configFile = new File(sshDirectory, "config");

        new Thread(() -> {
            List<String> aliases = SshConfigHosts.readAliases(configFile, sshDirectory, homeDirectory);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                showImportDialog(aliases);
            });
        }, "SshConfigReader").start();
    }

    private void showImportDialog(@NonNull List<String> aliases) {
        if (aliases.isEmpty()) {
            new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.ssh_host_import_title)
                .setMessage(R.string.ssh_host_import_empty)
                .setPositiveButton(android.R.string.ok, null)
                .show();
            return;
        }

        String[] items = aliases.toArray(new String[0]);
        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.ssh_host_import_choose)
            .setItems(items, (dialog, which) -> openImportedAlias(items[which]))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void openImportedAlias(@NonNull String alias) {
        SshHost existing = mHostStore.findByConfigAlias(alias);
        if (existing != null) {
            showToast(R.string.ssh_host_import_already_added);
            startActivity(SshHostEditActivity.newInstance(this, existing.id, null));
        } else {
            startActivity(SshHostEditActivity.newInstance(this, null, alias));
        }
    }

    private void showToast(int stringResId) {
        android.widget.Toast.makeText(this, stringResId, android.widget.Toast.LENGTH_LONG).show();
    }

}
