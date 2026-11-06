package com.termux.app;

import android.content.Context;
import android.content.res.XmlResourceParser;

import com.termux.BuildConfig;
import com.termux.R;
import com.termux.shared.termux.TermuxConstants;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.xmlpull.v1.XmlPullParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Guards the applicationId rename. The applicationId is "com.termux.ywpc" while the Java
 * package/namespace remains "com.termux", so applicationId and class-name usages must not be
 * conflated.
 */
@RunWith(RobolectricTestRunner.class)
public class TermuxPackageNameTest {

    @Test
    public void applicationIdMatchesConstantsAndRuntimePaths() {
        Context context = RuntimeEnvironment.getApplication();

        Assert.assertEquals("com.termux.ywpc", BuildConfig.APPLICATION_ID);
        Assert.assertEquals(BuildConfig.APPLICATION_ID, TermuxConstants.TERMUX_PACKAGE_NAME);
        Assert.assertEquals(BuildConfig.APPLICATION_ID, context.getPackageName());

        Assert.assertEquals("/data/data/com.termux.ywpc/files/usr",
            TermuxConstants.TERMUX_PREFIX_DIR_PATH);
        Assert.assertEquals("/data/data/com.termux.ywpc/files/home",
            TermuxConstants.TERMUX_HOME_DIR_PATH);
    }

    @Test
    public void javaClassNamesKeepTheNamespacePackage() {
        Assert.assertEquals("com.termux", TermuxConstants.TERMUX_JAVA_PACKAGE_NAME);
        Assert.assertEquals("com.termux.BuildConfig",
            TermuxConstants.TERMUX_APP.BUILD_CONFIG_CLASS_NAME);
        Assert.assertEquals("com.termux.app.TermuxActivity",
            TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY_NAME);
        Assert.assertEquals("com.termux.app.TermuxService",
            TermuxConstants.TERMUX_APP.TERMUX_SERVICE_NAME);
        Assert.assertEquals("com.termux.app.api.file.FileShareReceiverActivity",
            TermuxConstants.TERMUX_APP.FILE_SHARE_RECEIVER_ACTIVITY_CLASS_NAME);
        Assert.assertEquals("com.termux.app.api.file.FileViewReceiverActivity",
            TermuxConstants.TERMUX_APP.FILE_VIEW_RECEIVER_ACTIVITY_CLASS_NAME);

        // Official plugin APKs keep their own applicationIds and must not follow this fork's
        // applicationId; otherwise the in-app "install styling" link points to a 404.
        Assert.assertEquals("com.termux", TermuxConstants.TERMUX_PLUGIN_PACKAGE_NAME_PREFIX);
        Assert.assertEquals("com.termux.api", TermuxConstants.TERMUX_API_PACKAGE_NAME);
        Assert.assertEquals("com.termux.styling", TermuxConstants.TERMUX_STYLING_PACKAGE_NAME);
        Assert.assertEquals("com.termux.styling.TermuxStyleActivity",
            TermuxConstants.TERMUX_STYLING_APP.TERMUX_STYLING_ACTIVITY_NAME);
        Assert.assertEquals("https://f-droid.org/en/packages/com.termux",
            TermuxConstants.TERMUX_FDROID_PACKAGE_URL);
    }

    @Test
    public void sourceManifestUsesPackageNamePlaceholders() throws Exception {
        Assert.assertEquals("The manifest placeholder must resolve to the applicationId",
            BuildConfig.APPLICATION_ID,
            System.getProperty("termux.manifestPlaceholderPackageName"));

        String manifestPath = System.getProperty("termux.sourceManifest");
        Assert.assertNotNull("The test task must set termux.sourceManifest", manifestPath);
        String manifest = new String(Files.readAllBytes(Paths.get(manifestPath)),
            StandardCharsets.UTF_8);

        Assert.assertTrue(manifest.contains("android:sharedUserId=\"${TERMUX_PACKAGE_NAME}\""));
        Assert.assertTrue(manifest.contains("${TERMUX_PACKAGE_NAME}.permission.RUN_COMMAND"));
        Assert.assertTrue(manifest.contains("android:authorities=\"${TERMUX_PACKAGE_NAME}.documents\""));
        Assert.assertTrue(manifest.contains("android:authorities=\"${TERMUX_PACKAGE_NAME}.files\""));
        Assert.assertTrue(manifest.contains("<action android:name=\"${TERMUX_PACKAGE_NAME}.RUN_COMMAND\" />"));
    }

    @Test
    public void shortcutsTargetTheApplicationId() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        XmlResourceParser parser = context.getResources().getXml(R.xml.shortcuts);
        Set<String> expectedTargetClasses = new HashSet<>(Arrays.asList(
            "com.termux.app.TermuxActivity",
            "com.termux.app.activities.SettingsActivity"));
        int shortcutIntents = 0;
        try {
            int event;
            while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
                if (event != XmlPullParser.START_TAG || !"intent".equals(parser.getName())) {
                    continue;
                }
                String targetPackage = null;
                String targetClass = null;
                for (int i = 0; i < parser.getAttributeCount(); i++) {
                    if ("targetPackage".equals(parser.getAttributeName(i))) {
                        targetPackage = parser.getAttributeValue(i);
                    } else if ("targetClass".equals(parser.getAttributeName(i))) {
                        targetClass = parser.getAttributeValue(i);
                    }
                }
                Assert.assertEquals(BuildConfig.APPLICATION_ID, targetPackage);
                Assert.assertTrue("Unexpected shortcut targetClass: " + targetClass,
                    expectedTargetClasses.contains(targetClass));
                shortcutIntents++;
            }
        } finally {
            parser.close();
        }
        Assert.assertEquals(3, shortcutIntents);
    }

}
