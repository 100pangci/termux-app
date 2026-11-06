package com.termux.app.ssh;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.TermuxService;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;

import java.io.File;

/**
 * Starts an OpenSSH client through the existing {@link TermuxService} session API.
 *
 * <p>This class never runs at app startup or when a list is opened. It is only called after the
 * user taps a saved host.</p>
 */
public final class SshSessionLauncher {

    private static final String LOG_TAG = "SshSessionLauncher";

    private SshSessionLauncher() {
    }

    public static String getSshExecutablePath() {
        return TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/ssh";
    }

    public static String getShellExecutablePath() {
        return TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/sh";
    }

    public static boolean isOpenSshAvailable() {
        File ssh = new File(getSshExecutablePath());
        File shell = new File(getShellExecutablePath());
        return ssh.isFile() && ssh.canExecute()
            && shell.isFile() && shell.canExecute();
    }

    /**
     * Create a foreground Termux session that runs ssh. The caller is responsible for checking
     * {@link #isOpenSshAvailable()} and for bringing {@code TermuxActivity} to the foreground.
     *
     * @return The created session, or {@code null} if it could not be created.
     */
    @Nullable
    public static TermuxSession createSession(@NonNull Context context, @NonNull TermuxService service,
                                              @NonNull SshHost host, @Nullable String sessionName) {
        if (!isOpenSshAvailable()) return null;

        String[] sshArguments;
        try {
            sshArguments = SshCommandBuilder.buildSshArguments(host,
                TermuxConstants.TERMUX_HOME_DIR_PATH);
        } catch (IllegalArgumentException e) {
            Logger.logError(LOG_TAG, "Refusing to start SSH session: " + e.getMessage());
            return null;
        }

        String[] shellArguments = SshCommandBuilder.buildLoginShellArguments(
            getSshExecutablePath(), sshArguments);

        String name = sessionName;
        if (name == null || name.trim().isEmpty()) name = host.getDefaultName();

        TermuxSession session = service.createTermuxSession(getShellExecutablePath(),
            shellArguments, null, TermuxConstants.TERMUX_HOME_DIR_PATH, false, name);
        if (session == null) {
            Logger.logError(LOG_TAG, "Failed to create SSH TermuxSession for host \"" + host.name + "\"");
            return null;
        }

        // Persist the current session so TermuxActivity selects it when it is brought to the
        // foreground. This is the same preference TermuxService uses for plugin-created sessions.
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context);
        if (preferences != null) {
            preferences.setCurrentSession(session.getTerminalSession().mHandle);
        }

        return session;
    }

}
