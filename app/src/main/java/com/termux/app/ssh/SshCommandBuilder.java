package com.termux.app.ssh;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the argv arrays used to start OpenSSH in a Termux terminal session.
 *
 * <p>No user supplied value is ever interpolated into a shell command. The login shell runs the
 * constant script {@code exec "$0" "$@"} and receives the ssh argv through positional parameters,
 * which lets it load the user's login environment (including SSH_AUTH_SOCK when configured) while
 * keeping every host, user, port and key path a separate argument.</p>
 */
public final class SshCommandBuilder {

    /** Constant shell script; user input is only ever passed as positional parameters. */
    static final String SHELL_EXEC_SCRIPT = "exec \"$0\" \"$@\"";

    private SshCommandBuilder() {
    }

    /**
     * Build the OpenSSH argv. The destination is placed after {@code --}, so even a malformed value
     * cannot be parsed as an option. Validation is still required before calling this method.
     */
    public static String[] buildSshArguments(SshHost host, String homeDirectory) {
        if (host == null) throw new IllegalArgumentException("host is null");

        SshHostValidator.Error error = SshHostValidator.validate(host);
        if (error != SshHostValidator.Error.NONE) {
            throw new IllegalArgumentException("invalid ssh host: " + error);
        }

        String destination = host.getDestination();
        if (destination.isEmpty()) throw new IllegalArgumentException("empty ssh destination");

        List<String> arguments = new ArrayList<>();

        String username = trim(host.username);
        if (!username.isEmpty()) {
            arguments.add("-l");
            arguments.add(username);
        }

        if (host.port != SshHost.UNSET_PORT) {
            arguments.add("-p");
            arguments.add(Integer.toString(host.port));
        }

        String identityFile = trim(host.identityFile);
        if (!identityFile.isEmpty()) {
            arguments.add("-i");
            arguments.add(expandHome(identityFile, homeDirectory));
        }

        arguments.add("--");
        arguments.add(destination);

        return arguments.toArray(new String[0]);
    }

    /**
     * Build the argv for {@code sh -l -c "exec \"$0\" \"$@\"" <sshPath> <sshArgs...>}.
     */
    public static String[] buildLoginShellArguments(String sshExecutablePath, String[] sshArguments) {
        if (sshExecutablePath == null || sshExecutablePath.isEmpty()) {
            throw new IllegalArgumentException("ssh executable path is empty");
        }
        if (sshArguments == null) sshArguments = new String[0];

        String[] result = new String[sshArguments.length + 4];
        result[0] = "-l";
        result[1] = "-c";
        result[2] = SHELL_EXEC_SCRIPT;
        result[3] = sshExecutablePath;
        System.arraycopy(sshArguments, 0, result, 4, sshArguments.length);
        return result;
    }

    /**
     * Expand a leading {@code ~/} or a bare {@code ~}. OpenSSH accepts some tilde paths itself, but
     * expanding here keeps the behavior identical whether the value comes from this app or a config
     * alias, and avoids depending on shell expansion (there is no shell between the app and ssh).
     */
    public static String expandHome(String path, String homeDirectory) {
        if (path == null) return "";
        if (homeDirectory == null) homeDirectory = "";

        if (path.equals("~")) return homeDirectory;
        if (path.startsWith("~/")) return homeDirectory + path.substring(1);
        return path;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

}
