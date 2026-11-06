package com.termux.app.ssh;

/**
 * Validation for {@link SshHost} fields that are later passed to OpenSSH as separate argv entries.
 *
 * <p>The command is never assembled into a shell string, so shell metacharacters inside a field
 * cannot execute commands. Validation still rejects control characters, whitespace where it would
 * make the OpenSSH command line ambiguous, and leading hyphens so a value can never be interpreted
 * as an option.</p>
 */
public final class SshHostValidator {

    public enum Error {
        NONE,
        NAME_REQUIRED,
        NAME_INVALID,
        DESTINATION_REQUIRED,
        HOST_INVALID,
        ALIAS_INVALID,
        PORT_INVALID,
        USERNAME_INVALID,
        IDENTITY_FILE_INVALID
    }

    private static final int MAX_NAME_LENGTH = 128;
    private static final int MAX_HOST_LENGTH = 255;
    private static final int MAX_USERNAME_LENGTH = 255;
    private static final int MAX_IDENTITY_FILE_LENGTH = 4096;

    private SshHostValidator() {
    }

    public static Error validate(SshHost host) {
        if (host == null) return Error.DESTINATION_REQUIRED;

        String name = trim(host.name);
        if (name.isEmpty()) return Error.NAME_REQUIRED;
        if (name.length() > MAX_NAME_LENGTH || containsControlCharacter(name)) return Error.NAME_INVALID;

        String hostname = trim(host.hostname);
        String alias = trim(host.configAlias);

        if (hostname.isEmpty() && alias.isEmpty()) return Error.DESTINATION_REQUIRED;

        if (!hostname.isEmpty()) {
            if (hostname.length() > MAX_HOST_LENGTH || !isSafeSshDestination(hostname)) {
                return Error.HOST_INVALID;
            }
        }

        if (!alias.isEmpty() && !isValidConfigAlias(alias)) {
            return Error.ALIAS_INVALID;
        }

        if (host.port < SshHost.UNSET_PORT || host.port > 65535) {
            return Error.PORT_INVALID;
        }

        String username = trim(host.username);
        if (!username.isEmpty()) {
            if (username.length() > MAX_USERNAME_LENGTH || !isSafeOptionValue(username)) {
                return Error.USERNAME_INVALID;
            }
        }

        String identityFile = trim(host.identityFile);
        if (!identityFile.isEmpty()) {
            if (identityFile.length() > MAX_IDENTITY_FILE_LENGTH || containsControlCharacter(identityFile)) {
                return Error.IDENTITY_FILE_INVALID;
            }
        }

        return Error.NONE;
    }

    /**
     * A host passed after {@code --} cannot be parsed as an option, but reject obvious command
     * separators and whitespace as defense in depth and to prevent confusing destinations.
     */
    public static boolean isSafeSshDestination(String value) {
        if (value == null) return false;
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.charAt(0) == '-') return false;

        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (Character.isWhitespace(c) || Character.isISOControl(c)) return false;
            if (c == '/' || c == '\\' || c == ';' || c == '|' || c == '&' || c == '$'
                || c == '`' || c == '<' || c == '>' || c == '\'' || c == '"') {
                return false;
            }
        }

        return true;
    }

    /**
     * A concrete OpenSSH config alias is a single token passed after {@code --}. OpenSSH itself
     * accepts non-ASCII and other characters in Host patterns, so only concrete wildcard/negation
     * patterns and unsafe tokens are rejected.
     */
    public static boolean isValidConfigAlias(String value) {
        String trimmed = trim(value);
        if (trimmed.isEmpty()) return false;
        if (trimmed.indexOf('*') >= 0 || trimmed.indexOf('?') >= 0
            || trimmed.indexOf('[') >= 0 || trimmed.indexOf(']') >= 0) {
            return false;
        }
        // OpenSSH treats "user@host" as a destination with an explicit user, so it cannot be a
        // config alias lookup. The username belongs in the dedicated field instead.
        if (trimmed.indexOf('@') >= 0) return false;
        if (trimmed.startsWith("!")) return false;
        return isSafeSshDestination(trimmed);
    }

    /** Values passed as the argument of an option such as {@code -l} or {@code -i}. */
    public static boolean isSafeOptionValue(String value) {
        if (value == null) return false;
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.charAt(0) == '-') return false;

        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (Character.isWhitespace(c) || Character.isISOControl(c)) return false;
        }

        return true;
    }

    public static boolean containsControlCharacter(String value) {
        if (value == null) return true;
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) return true;
        }
        return false;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

}
