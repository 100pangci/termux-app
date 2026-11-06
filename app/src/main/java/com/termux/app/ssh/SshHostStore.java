package com.termux.app.ssh;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.logger.Logger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Persists SSH host metadata in an app-private SharedPreferences file.
 *
 * <p>This store is intentionally independent of {@code ~/.ssh/config}; the app never writes or
 * rewrites the user's OpenSSH configuration. No secrets are stored here.</p>
 */
public final class SshHostStore {

    private static final String PREFERENCES_NAME = "termux_ssh_hosts";
    private static final String KEY_HOSTS = "hosts";
    private static final int FORMAT_VERSION = 1;

    private static final String LOG_TAG = "SshHostStore";

    private final SharedPreferences mPreferences;

    private SshHostStore(@NonNull Context context) {
        mPreferences = context.getApplicationContext()
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    public static SshHostStore get(@NonNull Context context) {
        return new SshHostStore(context);
    }

    @NonNull
    public synchronized List<SshHost> loadHosts() {
        String json = mPreferences.getString(KEY_HOSTS, null);
        if (json == null || json.isEmpty()) return new ArrayList<>();

        try {
            JSONObject root = new JSONObject(json);
            JSONArray hostsArray = root.optJSONArray(KEY_HOSTS);
            if (hostsArray == null) return new ArrayList<>();

            List<SshHost> hosts = new ArrayList<>();
            for (int i = 0; i < hostsArray.length(); i++) {
                JSONObject object = hostsArray.optJSONObject(i);
                if (object == null) continue;

                SshHost host = new SshHost();
                host.id = object.optString("id", "");
                host.name = object.optString("name", "");
                host.hostname = object.optString("hostname", "");
                host.port = object.optInt("port", SshHost.UNSET_PORT);
                host.username = object.optString("username", "");
                host.identityFile = object.optString("identityFile", "");
                host.configAlias = object.optString("configAlias", "");
                host.notes = object.optString("notes", "");

                if (!host.id.isEmpty()) hosts.add(host);
            }

            return hosts;
        } catch (JSONException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to parse saved SSH hosts", e);
            return new ArrayList<>();
        }
    }

    public synchronized boolean saveHosts(@NonNull List<SshHost> hosts) {
        JSONObject root = new JSONObject();
        JSONArray hostsArray = new JSONArray();

        try {
            root.put("version", FORMAT_VERSION);
            for (SshHost host : hosts) {
                if (host == null || host.id == null || host.id.isEmpty()) continue;

                JSONObject object = new JSONObject();
                object.put("id", host.id);
                object.put("name", nullToEmpty(host.name));
                object.put("hostname", nullToEmpty(host.hostname));
                object.put("port", host.port);
                object.put("username", nullToEmpty(host.username));
                object.put("identityFile", nullToEmpty(host.identityFile));
                object.put("configAlias", nullToEmpty(host.configAlias));
                object.put("notes", nullToEmpty(host.notes));
                hostsArray.put(object);
            }
            root.put(KEY_HOSTS, hostsArray);
        } catch (JSONException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to serialize SSH hosts", e);
            return false;
        }

        return mPreferences.edit().putString(KEY_HOSTS, root.toString()).commit();
    }

    public synchronized boolean upsert(@NonNull SshHost host) {
        List<SshHost> hosts = loadHosts();
        boolean replaced = false;

        for (int i = 0; i < hosts.size(); i++) {
            if (hosts.get(i).id.equals(host.id)) {
                hosts.set(i, host);
                replaced = true;
                break;
            }
        }

        if (!replaced) hosts.add(host);
        return saveHosts(hosts);
    }

    public synchronized boolean delete(@NonNull String hostId) {
        List<SshHost> hosts = loadHosts();
        for (int i = 0; i < hosts.size(); i++) {
            if (hosts.get(i).id.equals(hostId)) {
                hosts.remove(i);
                return saveHosts(hosts);
            }
        }
        return true;
    }

    @Nullable
    public synchronized SshHost findById(@NonNull String hostId) {
        for (SshHost host : loadHosts()) {
            if (host.id.equals(hostId)) return host;
        }
        return null;
    }

    @Nullable
    public synchronized SshHost findByConfigAlias(@NonNull String alias) {
        String trimmedAlias = alias.trim();
        for (SshHost host : loadHosts()) {
            String savedAlias = trimOrNull(host.configAlias);
            if (savedAlias != null && trimmedAlias.equalsIgnoreCase(savedAlias)) return host;
        }
        return null;
    }

    @Nullable
    private static String trimOrNull(@Nullable String value) {
        return value == null ? null : value.trim();
    }

    @NonNull
    private static String nullToEmpty(@Nullable String value) {
        return value == null ? "" : value;
    }

}
