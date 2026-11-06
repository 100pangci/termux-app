package com.termux.app.ssh;

import android.content.Context;

import com.termux.R;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
public class SshLocaleResourcesTest {

    @Test
    @Config(qualifiers = "zh-rCN")
    public void simplifiedChineseResources() {
        Context context = RuntimeEnvironment.getApplication();
        Assert.assertEquals("SSH 主机", context.getString(R.string.action_ssh_hosts));
        Assert.assertEquals("设置", context.getString(R.string.action_open_settings));
        Assert.assertEquals("Termux 设置", context.getString(R.string.title_activity_termux_settings));
        Assert.assertEquals("调试", context.getString(R.string.termux_debugging_preferences_title));
        Assert.assertEquals("调试（默认）", context.getString(
            com.termux.shared.R.string.log_level_default, "调试"));
        Assert.assertEquals("未安装 OpenSSH", context.getString(R.string.ssh_host_openssh_missing_title));
    }

    @Test
    @Config(qualifiers = "zh-rTW")
    public void traditionalChineseResources() {
        Context context = RuntimeEnvironment.getApplication();
        Assert.assertEquals("SSH 主機", context.getString(R.string.action_ssh_hosts));
        Assert.assertEquals("設定", context.getString(R.string.action_open_settings));
        Assert.assertEquals("Termux 設定", context.getString(R.string.title_activity_termux_settings));
        Assert.assertEquals("偵錯", context.getString(R.string.termux_debugging_preferences_title));
        Assert.assertEquals("偵錯（預設）", context.getString(
            com.termux.shared.R.string.log_level_default, "偵錯"));
        Assert.assertEquals("尚未安裝 OpenSSH", context.getString(R.string.ssh_host_openssh_missing_title));
        Assert.assertEquals("簡體中文", context.getString(R.string.language_simplified_chinese));
    }

    @Test
    @Config(qualifiers = "en")
    public void englishResources() {
        Context context = RuntimeEnvironment.getApplication();
        Assert.assertEquals("SSH Hosts", context.getString(R.string.action_ssh_hosts));
        Assert.assertEquals("Settings", context.getString(R.string.action_open_settings));
        Assert.assertEquals("Termux Settings", context.getString(R.string.title_activity_termux_settings));
        Assert.assertEquals("Debugging", context.getString(R.string.termux_debugging_preferences_title));
        Assert.assertEquals("Debug (default)", context.getString(
            com.termux.shared.R.string.log_level_default, "Debug"));
    }

}
