package com.termux.app.ssh;

import com.termux.shared.termux.TermuxConstants;

import org.junit.Assert;
import org.junit.Test;

public class SshCommandBuilderTest {

    private static SshHost completeHost() {
        SshHost host = new SshHost();
        host.name = "Production";
        host.hostname = "example.com";
        host.port = 2222;
        host.username = "alice";
        host.identityFile = "~/.ssh/id_ed25519";
        return host;
    }

    @Test
    public void buildsSeparateArguments() {
        String[] arguments = SshCommandBuilder.buildSshArguments(
            completeHost(), "/home/termux");

        Assert.assertArrayEquals(new String[]{
            "-l", "alice",
            "-p", "2222",
            "-i", "/home/termux/.ssh/id_ed25519",
            "--", "example.com"
        }, arguments);
    }

    @Test
    public void blankOptionalFieldsAreOmitted() {
        SshHost host = new SshHost();
        host.name = "Minimal";
        host.hostname = "example.com";

        Assert.assertArrayEquals(new String[]{"--", "example.com"},
            SshCommandBuilder.buildSshArguments(host, "/home/termux"));
    }

    @Test
    public void configAliasTakesPrecedence() {
        SshHost host = new SshHost();
        host.name = "Alias";
        host.hostname = "ignored.example.com";
        host.configAlias = "prod";

        Assert.assertArrayEquals(new String[]{"--", "prod"},
            SshCommandBuilder.buildSshArguments(host, "/home/termux"));
    }

    @Test
    public void userInputIsNeverInterpolatedIntoTheShellScript() {
        SshHost host = completeHost();
        host.username = "user$(id)";

        String[] arguments = SshCommandBuilder.buildSshArguments(host, "/home/termux");
        Assert.assertEquals("-l", arguments[0]);
        Assert.assertEquals("user$(id)", arguments[1]);

        String sshPath = TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/ssh";
        String[] loginArguments = SshCommandBuilder.buildLoginShellArguments(
            sshPath, arguments);

        Assert.assertEquals("exec \"$0\" \"$@\"", loginArguments[2]);
        Assert.assertEquals(sshPath, loginArguments[3]);
        Assert.assertEquals("user$(id)", loginArguments[5]);
    }

    @Test
    public void rejectsInvalidHost() {
        SshHost host = new SshHost();
        host.name = "Invalid";
        host.hostname = "-oProxyCommand=id";

        try {
            SshCommandBuilder.buildSshArguments(host, "/home/termux");
            Assert.fail("Expected invalid host to be rejected");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("HOST_INVALID"));
        }
    }

    @Test
    public void expandsHomeDirectory() {
        Assert.assertEquals("/home/termux/.ssh/key",
            SshCommandBuilder.expandHome("~/.ssh/key", "/home/termux"));
        Assert.assertEquals("/home/termux",
            SshCommandBuilder.expandHome("~", "/home/termux"));
        Assert.assertEquals("/absolute/key",
            SshCommandBuilder.expandHome("/absolute/key", "/home/termux"));
        Assert.assertEquals("relative/key",
            SshCommandBuilder.expandHome("relative/key", "/home/termux"));
    }

}
