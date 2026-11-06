package com.termux.app.ssh;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.termux.R;
import com.termux.shared.activity.media.AppCompatActivityUtils;
import com.termux.shared.theme.NightMode;

import java.util.UUID;

/** Add or edit a saved SSH host. Only connection metadata is stored. */
public final class SshHostEditActivity extends AppCompatActivity {

    private static final String EXTRA_HOST_ID = "com.termux.app.ssh.extra.HOST_ID";
    private static final String EXTRA_CONFIG_ALIAS = "com.termux.app.ssh.extra.CONFIG_ALIAS";

    private SshHostStore mHostStore;
    private SshHost mHost;
    private boolean mIsNewHost;
    private boolean mSaving;

    private TextInputEditText mNameInput;
    private TextInputEditText mHostnameInput;
    private TextInputEditText mPortInput;
    private TextInputEditText mUsernameInput;
    private TextInputEditText mIdentityInput;
    private TextInputEditText mAliasInput;
    private TextInputEditText mNotesInput;

    private TextInputLayout mNameLayout;
    private TextInputLayout mHostnameLayout;
    private TextInputLayout mPortLayout;
    private TextInputLayout mUsernameLayout;
    private TextInputLayout mIdentityLayout;
    private TextInputLayout mAliasLayout;

    public static Intent newInstance(Context context, @Nullable String hostId,
                                     @Nullable String configAlias) {
        Intent intent = new Intent(context, SshHostEditActivity.class);
        if (hostId != null) intent.putExtra(EXTRA_HOST_ID, hostId);
        if (configAlias != null) intent.putExtra(EXTRA_CONFIG_ALIAS, configAlias);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppCompatActivityUtils.setNightMode(this, NightMode.getAppNightMode().getName(), true);
        DynamicColors.applyToActivityIfAvailable(this);
        setContentView(R.layout.activity_ssh_host_edit);

        mHostStore = SshHostStore.get(this);

        MaterialToolbar toolbar = findViewById(R.id.ssh_host_edit_toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        mNameLayout = findViewById(R.id.ssh_host_name_layout);
        mHostnameLayout = findViewById(R.id.ssh_host_hostname_layout);
        mPortLayout = findViewById(R.id.ssh_host_port_layout);
        mUsernameLayout = findViewById(R.id.ssh_host_username_layout);
        mIdentityLayout = findViewById(R.id.ssh_host_identity_layout);
        mAliasLayout = findViewById(R.id.ssh_host_alias_layout);

        mNameInput = findViewById(R.id.ssh_host_name_input);
        mHostnameInput = findViewById(R.id.ssh_host_hostname_input);
        mPortInput = findViewById(R.id.ssh_host_port_input);
        mUsernameInput = findViewById(R.id.ssh_host_username_input);
        mIdentityInput = findViewById(R.id.ssh_host_identity_input);
        mAliasInput = findViewById(R.id.ssh_host_alias_input);
        mNotesInput = findViewById(R.id.ssh_host_notes_input);

        String hostId = getIntent().getStringExtra(EXTRA_HOST_ID);
        if (!TextUtils.isEmpty(hostId)) {
            mHost = mHostStore.findById(hostId);
            if (mHost == null) {
                Toast.makeText(this, R.string.ssh_host_not_found, Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            mIsNewHost = false;
            toolbar.setTitle(R.string.title_ssh_host_edit);
            setTitle(R.string.title_ssh_host_edit);
            bindHostToForm(mHost);
        } else {
            mIsNewHost = true;
            toolbar.setTitle(R.string.title_ssh_host_add);
            setTitle(R.string.title_ssh_host_add);
            String configAlias = getIntent().getStringExtra(EXTRA_CONFIG_ALIAS);
            if (!TextUtils.isEmpty(configAlias)) {
                mAliasInput.setText(configAlias);
                mNameInput.setText(configAlias);
            }
        }

        MaterialButton saveButton = findViewById(R.id.ssh_host_save_button);
        saveButton.setOnClickListener(v -> saveHost());
    }

    private void bindHostToForm(SshHost host) {
        mNameInput.setText(host.name);
        mHostnameInput.setText(host.hostname);
        if (host.port != SshHost.UNSET_PORT) mPortInput.setText(String.valueOf(host.port));
        mUsernameInput.setText(host.username);
        mIdentityInput.setText(host.identityFile);
        mAliasInput.setText(host.configAlias);
        mNotesInput.setText(host.notes);
    }

    private void saveHost() {
        if (mSaving || isFinishing()) return;
        clearFieldErrors();

        SshHost host = mHost == null ? new SshHost() : new SshHost(mHost);
        if (mIsNewHost) host.id = UUID.randomUUID().toString();

        host.name = textOf(mNameInput);
        host.hostname = textOf(mHostnameInput);
        host.username = textOf(mUsernameInput);
        host.identityFile = textOf(mIdentityInput);
        host.configAlias = textOf(mAliasInput);
        host.notes = textOf(mNotesInput);

        String portText = textOf(mPortInput);
        if (portText.isEmpty()) {
            host.port = SshHost.UNSET_PORT;
        } else {
            try {
                host.port = Integer.parseInt(portText);
            } catch (NumberFormatException e) {
                mPortLayout.setError(getString(R.string.ssh_host_error_port_invalid));
                mPortInput.requestFocus();
                return;
            }
            if (host.port < 1 || host.port > 65535) {
                mPortLayout.setError(getString(R.string.ssh_host_error_port_invalid));
                mPortInput.requestFocus();
                return;
            }
        }

        SshHostValidator.Error error = SshHostValidator.validate(host);
        if (error != SshHostValidator.Error.NONE) {
            showValidationError(error);
            return;
        }

        if (!mHostStore.upsert(host)) {
            Toast.makeText(this, R.string.ssh_host_error_save_failed, Toast.LENGTH_LONG).show();
            return;
        }

        mSaving = true;
        finish();
    }

    private void showValidationError(SshHostValidator.Error error) {
        switch (error) {
            case NAME_REQUIRED:
            case NAME_INVALID:
                mNameLayout.setError(getString(error == SshHostValidator.Error.NAME_REQUIRED
                    ? R.string.ssh_host_error_name_required
                    : R.string.ssh_host_error_name_invalid));
                mNameInput.requestFocus();
                break;
            case DESTINATION_REQUIRED:
                mHostnameLayout.setError(getString(R.string.ssh_host_error_destination_required));
                mHostnameInput.requestFocus();
                break;
            case HOST_INVALID:
                mHostnameLayout.setError(getString(R.string.ssh_host_error_host_invalid));
                mHostnameInput.requestFocus();
                break;
            case ALIAS_INVALID:
                mAliasLayout.setError(getString(R.string.ssh_host_error_alias_invalid));
                mAliasInput.requestFocus();
                break;
            case PORT_INVALID:
                mPortLayout.setError(getString(R.string.ssh_host_error_port_invalid));
                mPortInput.requestFocus();
                break;
            case USERNAME_INVALID:
                mUsernameLayout.setError(getString(R.string.ssh_host_error_username_invalid));
                mUsernameInput.requestFocus();
                break;
            case IDENTITY_FILE_INVALID:
                mIdentityLayout.setError(getString(R.string.ssh_host_error_identity_file_invalid));
                mIdentityInput.requestFocus();
                break;
            case NONE:
            default:
                break;
        }
    }

    private void clearFieldErrors() {
        mNameLayout.setError(null);
        mHostnameLayout.setError(null);
        mPortLayout.setError(null);
        mUsernameLayout.setError(null);
        mIdentityLayout.setError(null);
        mAliasLayout.setError(null);
    }

    private static String textOf(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

}
