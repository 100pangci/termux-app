package com.termux.app.ssh;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileWriter;
import java.util.List;

public class SshConfigHostsTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private File write(File file, String content) throws Exception {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) Assert.assertTrue(parent.mkdirs());
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
        return file;
    }

    @Test
    public void readsConcreteHostAliases() throws Exception {
        File home = temporaryFolder.newFolder("home");
        File sshDirectory = new File(home, ".ssh");
        File config = write(new File(sshDirectory, "config"),
            "Host production staging\n" +
                "    User deploy\n" +
                "\n" +
                "Host=short\n" +
                "Host *.internal\n" +
                "Host web?\n" +
                "Host !excluded\n" +
                "# Host commented\n" +
                "Host inline # trailing comment\n" +
                "Host \"quoted\"");

        List<String> aliases = SshConfigHosts.readAliases(config, sshDirectory, home);

        Assert.assertTrue(aliases.contains("production"));
        Assert.assertTrue(aliases.contains("staging"));
        Assert.assertTrue(aliases.contains("short"));
        Assert.assertTrue(aliases.contains("inline"));
        Assert.assertTrue(aliases.contains("quoted"));
        Assert.assertFalse(aliases.contains("*.internal"));
        Assert.assertFalse(aliases.contains("web?"));
        Assert.assertFalse(aliases.contains("!excluded"));
        Assert.assertFalse(aliases.contains("commented"));
        Assert.assertFalse(aliases.contains("#"));
    }

    @Test
    public void followsIncludeFilesAndGlobsWithoutDuplicates() throws Exception {
        File home = temporaryFolder.newFolder("home");
        File sshDirectory = new File(home, ".ssh");
        File config = write(new File(sshDirectory, "config"),
            "Include config.d/*.conf\n" +
                "Include ~/.ssh/extra\n" +
                "Host main");
        write(new File(sshDirectory, "config.d/a.conf"), "Host from-a\n");
        write(new File(sshDirectory, "config.d/b.conf"), "Host from-b\nInclude a.conf\n");
        write(new File(sshDirectory, "extra"), "Host from-extra\nHost main\n");

        List<String> aliases = SshConfigHosts.readAliases(config, sshDirectory, home);

        Assert.assertTrue(aliases.contains("from-a"));
        Assert.assertTrue(aliases.contains("from-b"));
        Assert.assertTrue(aliases.contains("from-extra"));
        Assert.assertTrue(aliases.contains("main"));
        Assert.assertEquals(1, count(aliases, "main"));
    }

    @Test
    public void skipsIncludesInsideConditionalBlocks() throws Exception {
        File home = temporaryFolder.newFolder("home");
        File sshDirectory = new File(home, ".ssh");
        File config = write(new File(sshDirectory, "config"),
            "Host bastion\n" +
                "    Include conditional.conf\n" +
                "Host global\n");
        write(new File(sshDirectory, "conditional.conf"), "Host internal\n");

        List<String> aliases = SshConfigHosts.readAliases(config, sshDirectory, home);

        Assert.assertTrue(aliases.contains("bastion"));
        Assert.assertTrue(aliases.contains("global"));
        Assert.assertFalse(aliases.contains("internal"));
    }

    @Test
    public void includesUnderHostWildcardAreGlobal() throws Exception {
        File home = temporaryFolder.newFolder("home");
        File sshDirectory = new File(home, ".ssh");
        File config = write(new File(sshDirectory, "config"),
            "Host *\n" +
                "    Include extra.conf\n");
        write(new File(sshDirectory, "extra.conf"), "Host internal\n");

        List<String> aliases = SshConfigHosts.readAliases(config, sshDirectory, home);

        Assert.assertTrue(aliases.contains("internal"));
    }

    @Test
    public void acceptsSeparatedEqualsAfterHostKeyword() throws Exception {
        File home = temporaryFolder.newFolder("home");
        File sshDirectory = new File(home, ".ssh");
        File config = write(new File(sshDirectory, "config"),
            "Host = production\n" +
                "Host=compact\n" +
                "Host = *\n" +
                "    Include extra.conf\n");
        write(new File(sshDirectory, "extra.conf"), "Host included\n");

        List<String> aliases = SshConfigHosts.readAliases(config, sshDirectory, home);

        Assert.assertTrue(aliases.contains("production"));
        Assert.assertTrue(aliases.contains("compact"));
        Assert.assertTrue(aliases.contains("included"));
        Assert.assertFalse(aliases.contains("="));
    }

    @Test
    public void acceptsSeparatedEqualsAfterMatchKeyword() throws Exception {
        File home = temporaryFolder.newFolder("home");
        File sshDirectory = new File(home, ".ssh");
        File config = write(new File(sshDirectory, "config"),
            "Match = all\n" +
                "    Include extra.conf\n");
        write(new File(sshDirectory, "extra.conf"), "Host included\n");

        List<String> aliases = SshConfigHosts.readAliases(config, sshDirectory, home);

        Assert.assertTrue(aliases.contains("included"));
    }

    @Test
    public void acceptsNonAsciiAliases() throws Exception {
        File home = temporaryFolder.newFolder("home");
        File sshDirectory = new File(home, ".ssh");
        File config = write(new File(sshDirectory, "config"),
            "Host 服务器\n" +
                "Host _nas\n");

        List<String> aliases = SshConfigHosts.readAliases(config, sshDirectory, home);

        Assert.assertTrue(aliases.contains("服务器"));
        Assert.assertTrue(aliases.contains("_nas"));
    }

    @Test
    public void missingConfigReturnsEmptyList() throws Exception {
        File home = temporaryFolder.newFolder("home");
        File sshDirectory = new File(home, ".ssh");
        File config = new File(sshDirectory, "config");

        Assert.assertTrue(SshConfigHosts.readAliases(config, sshDirectory, home).isEmpty());
    }

    @Test
    public void includeCycleTerminates() throws Exception {
        File home = temporaryFolder.newFolder("home");
        File sshDirectory = new File(home, ".ssh");
        File config = write(new File(sshDirectory, "config"), "Include loop\nHost first");
        write(new File(sshDirectory, "loop"), "Include config\nHost looped");

        List<String> aliases = SshConfigHosts.readAliases(config, sshDirectory, home);

        Assert.assertTrue(aliases.contains("first"));
        Assert.assertTrue(aliases.contains("looped"));
    }

    private static int count(List<String> values, String value) {
        int count = 0;
        for (String item : values) {
            if (value.equals(item)) count++;
        }
        return count;
    }

}
