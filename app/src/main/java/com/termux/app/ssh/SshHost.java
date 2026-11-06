package com.termux.app.ssh;

/**
 * A saved SSH host configuration.
 *
 * <p>Only connection metadata is stored. Passwords, passphrases and private key contents are never
 * stored by the app. Authentication is always performed by OpenSSH inside the terminal session.</p>
 */
public class SshHost {

    /** Port value when the port is not overridden and OpenSSH should use its config/default (22). */
    public static final int UNSET_PORT = 0;

    public String id;
    public String name;
    public String hostname;
    public int port = UNSET_PORT;
    public String username;
    public String identityFile;
    public String configAlias;
    public String notes;

    public SshHost() {
    }

    public SshHost(SshHost other) {
        if (other == null) return;
        id = other.id;
        name = other.name;
        hostname = other.hostname;
        port = other.port;
        username = other.username;
        identityFile = other.identityFile;
        configAlias = other.configAlias;
        notes = other.notes;
    }

    public boolean hasConfigAlias() {
        return configAlias != null && !configAlias.trim().isEmpty();
    }

    public boolean hasHostname() {
        return hostname != null && !hostname.trim().isEmpty();
    }

    /** The destination passed to ssh. OpenSSH resolves it against ~/.ssh/config. */
    public String getDestination() {
        return hasConfigAlias() ? configAlias.trim() : hostname == null ? "" : hostname.trim();
    }

    /** A stable, human-readable fallback name for a host that has no display name. */
    public String getDefaultName() {
        if (hasHostname()) return hostname.trim();
        if (hasConfigAlias()) return configAlias.trim();
        return "";
    }

}
