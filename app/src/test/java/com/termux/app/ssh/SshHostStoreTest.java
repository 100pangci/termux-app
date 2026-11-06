package com.termux.app.ssh;

import android.content.Context;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class SshHostStoreTest {

    private Context mContext;
    private SshHostStore mStore;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.getApplication();
        mContext.getSharedPreferences("termux_ssh_hosts", Context.MODE_PRIVATE)
            .edit().clear().commit();
        mStore = SshHostStore.get(mContext);
    }

    @After
    public void tearDown() {
        mContext.getSharedPreferences("termux_ssh_hosts", Context.MODE_PRIVATE)
            .edit().clear().commit();
    }

    private static SshHost host(String id, String name, String alias) {
        SshHost host = new SshHost();
        host.id = id;
        host.name = name;
        host.hostname = "example.com";
        host.port = 2222;
        host.username = "alice";
        host.identityFile = "~/.ssh/id_ed25519";
        host.configAlias = alias;
        host.notes = "notes";
        return host;
    }

    @Test
    public void roundTripsMetadataWithoutSecrets() {
        Assert.assertTrue(mStore.upsert(host("1", "Production", "prod")));

        List<SshHost> hosts = mStore.loadHosts();
        Assert.assertEquals(1, hosts.size());

        SshHost loaded = hosts.get(0);
        Assert.assertEquals("1", loaded.id);
        Assert.assertEquals("Production", loaded.name);
        Assert.assertEquals("example.com", loaded.hostname);
        Assert.assertEquals(2222, loaded.port);
        Assert.assertEquals("alice", loaded.username);
        Assert.assertEquals("~/.ssh/id_ed25519", loaded.identityFile);
        Assert.assertEquals("prod", loaded.configAlias);
        Assert.assertEquals("notes", loaded.notes);

        String raw = mContext.getSharedPreferences("termux_ssh_hosts", Context.MODE_PRIVATE)
            .getString("hosts", "");
        Assert.assertNotNull(raw);
        Assert.assertFalse(raw.toLowerCase().contains("password"));
        Assert.assertFalse(raw.toLowerCase().contains("privatekey"));
    }

    @Test
    public void upsertReplacesExistingAndDeleteRemoves() {
        mStore.upsert(host("1", "One", "one"));
        mStore.upsert(host("2", "Two", "two"));

        SshHost edited = host("1", "One edited", "one");
        Assert.assertTrue(mStore.upsert(edited));

        Assert.assertEquals(2, mStore.loadHosts().size());
        Assert.assertEquals("One edited", mStore.findById("1").name);
        Assert.assertEquals("Two", mStore.findByConfigAlias("two").name);
        Assert.assertEquals("Two", mStore.findByConfigAlias("TWO").name);

        Assert.assertTrue(mStore.delete("1"));
        Assert.assertNull(mStore.findById("1"));
        Assert.assertEquals(1, mStore.loadHosts().size());

        Assert.assertTrue(mStore.delete("missing"));
    }

    @Test
    public void corruptDataIsTreatedAsEmpty() {
        mContext.getSharedPreferences("termux_ssh_hosts", Context.MODE_PRIVATE)
            .edit().putString("hosts", "{not valid json").commit();

        Assert.assertTrue(mStore.loadHosts().isEmpty());
    }

    @Test
    public void entriesWithoutIdAreSkipped() {
        SshHost noId = host("", "No id", null);
        Assert.assertTrue(mStore.saveHosts(java.util.Collections.singletonList(noId)));
        Assert.assertTrue(mStore.loadHosts().isEmpty());
    }

}
