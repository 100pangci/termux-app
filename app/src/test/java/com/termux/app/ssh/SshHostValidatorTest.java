package com.termux.app.ssh;

import org.junit.Assert;
import org.junit.Test;

public class SshHostValidatorTest {

    private static SshHost host(String name, String hostname) {
        SshHost host = new SshHost();
        host.name = name;
        host.hostname = hostname;
        return host;
    }

    @Test
    public void validHostsAndDestinations() {
        SshHost host = host("Production", "example.com");
        Assert.assertEquals(SshHostValidator.Error.NONE, SshHostValidator.validate(host));

        host = host("IPv4", "192.0.2.10");
        Assert.assertEquals(SshHostValidator.Error.NONE, SshHostValidator.validate(host));

        host = host("IPv6", "2001:db8::1");
        Assert.assertEquals(SshHostValidator.Error.NONE, SshHostValidator.validate(host));

        host = new SshHost();
        host.name = "Alias only";
        host.configAlias = "prod-web";
        Assert.assertEquals(SshHostValidator.Error.NONE, SshHostValidator.validate(host));
    }

    @Test
    public void nameIsRequired() {
        SshHost host = host("", "example.com");
        Assert.assertEquals(SshHostValidator.Error.NAME_REQUIRED, SshHostValidator.validate(host));

        host.name = "   ";
        Assert.assertEquals(SshHostValidator.Error.NAME_REQUIRED, SshHostValidator.validate(host));

        host.name = "line\nbreak";
        Assert.assertEquals(SshHostValidator.Error.NAME_INVALID, SshHostValidator.validate(host));
    }

    @Test
    public void destinationIsRequired() {
        SshHost host = host("No destination", "");
        Assert.assertEquals(SshHostValidator.Error.DESTINATION_REQUIRED,
            SshHostValidator.validate(host));
    }

    @Test
    public void rejectsUnsafeDestinations() {
        String[] unsafe = {
            "-oProxyCommand=id",
            "host name",
            "host\nname",
            "host;id",
            "host|id",
            "host&id",
            "host$HOME",
            "host`id`",
            "host/name",
            "host\\name",
            "host<name",
            "host>name",
            "host'name",
            "host\"name",
            ""
        };

        for (String value : unsafe) {
            SshHost host = host("Unsafe", value);
            Assert.assertNotEquals("Should reject destination: " + value,
                SshHostValidator.Error.NONE, SshHostValidator.validate(host));
            Assert.assertFalse("Should reject destination: " + value,
                SshHostValidator.isSafeSshDestination(value));
        }
    }

    @Test
    public void aliasRules() {
        Assert.assertTrue(SshHostValidator.isValidConfigAlias("prod"));
        Assert.assertTrue(SshHostValidator.isValidConfigAlias("prod.web-1_2+"));
        Assert.assertTrue(SshHostValidator.isValidConfigAlias("_nas"));
        Assert.assertTrue(SshHostValidator.isValidConfigAlias(".foo"));
        Assert.assertTrue(SshHostValidator.isValidConfigAlias("服务器"));
        Assert.assertTrue(SshHostValidator.isValidConfigAlias("foo#bar"));
        Assert.assertFalse(SshHostValidator.isValidConfigAlias("-prod"));
        Assert.assertFalse(SshHostValidator.isValidConfigAlias("prod web"));
        Assert.assertFalse(SshHostValidator.isValidConfigAlias("prod;id"));
        Assert.assertFalse(SshHostValidator.isValidConfigAlias("user@host"));
        Assert.assertFalse(SshHostValidator.isValidConfigAlias("*"));
        Assert.assertFalse(SshHostValidator.isValidConfigAlias("!"));

        SshHost host = new SshHost();
        host.name = "Bad alias";
        host.configAlias = "bad alias";
        Assert.assertEquals(SshHostValidator.Error.ALIAS_INVALID,
            SshHostValidator.validate(host));
    }

    @Test
    public void portBounds() {
        SshHost host = host("Port", "example.com");

        host.port = 0;
        Assert.assertEquals(SshHostValidator.Error.NONE, SshHostValidator.validate(host));
        host.port = 22;
        Assert.assertEquals(SshHostValidator.Error.NONE, SshHostValidator.validate(host));
        host.port = 65535;
        Assert.assertEquals(SshHostValidator.Error.NONE, SshHostValidator.validate(host));
        host.port = -1;
        Assert.assertEquals(SshHostValidator.Error.PORT_INVALID, SshHostValidator.validate(host));
        host.port = 65536;
        Assert.assertEquals(SshHostValidator.Error.PORT_INVALID, SshHostValidator.validate(host));
    }

    @Test
    public void usernameAndIdentityRules() {
        SshHost host = host("User", "example.com");

        host.username = "alice";
        Assert.assertEquals(SshHostValidator.Error.NONE, SshHostValidator.validate(host));
        host.username = "-oProxyCommand=id";
        Assert.assertEquals(SshHostValidator.Error.USERNAME_INVALID,
            SshHostValidator.validate(host));
        host.username = "alice smith";
        Assert.assertEquals(SshHostValidator.Error.USERNAME_INVALID,
            SshHostValidator.validate(host));

        host.username = "";
        host.identityFile = "~/.ssh/id_ed25519";
        Assert.assertEquals(SshHostValidator.Error.NONE, SshHostValidator.validate(host));
        host.identityFile = "~/.ssh/id\ned25519";
        Assert.assertEquals(SshHostValidator.Error.IDENTITY_FILE_INVALID,
            SshHostValidator.validate(host));
    }

}
