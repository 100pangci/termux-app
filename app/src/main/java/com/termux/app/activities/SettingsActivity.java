package com.termux.app.activities;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Environment;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.termux.R;
import com.termux.app.AppLanguageUtils;
import com.termux.shared.activities.ReportActivity;
import com.termux.shared.file.FileUtils;
import com.termux.shared.models.ReportInfo;
import com.termux.app.models.UserAction;
import com.termux.shared.interact.ShareUtils;
import com.termux.shared.android.PackageUtils;
import com.termux.shared.termux.settings.preferences.TermuxAPIAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxFloatAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxTaskerAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxWidgetAppSharedPreferences;
import com.termux.shared.android.AndroidUtils;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.TermuxUtils;
import com.termux.shared.activity.media.AppCompatActivityUtils;
import com.termux.shared.theme.NightMode;

import java.util.Locale;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AppCompatActivityUtils.setNightMode(this, NightMode.getAppNightMode().getName(), true);

        setContentView(R.layout.activity_settings);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings, new RootPreferencesFragment())
                .commit();
        }

        AppCompatActivityUtils.setToolbar(this, com.termux.shared.R.id.toolbar);
        AppCompatActivityUtils.setShowBackButtonInActionBar(this, true);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    public static class RootPreferencesFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            Context context = getContext();
            if (context == null) return;

            setPreferencesFromResource(R.xml.root_preferences, rootKey);

            configureLanguagePreference(context);

            new Thread() {
                @Override
                public void run() {
                    configureTermuxAPIPreference(context);
                    configureTermuxFloatPreference(context);
                    configureTermuxTaskerPreference(context);
                    configureTermuxWidgetPreference(context);
                    configureAboutPreference(context);
                    configureDonatePreference(context);
                }
            }.start();
        }

        private void configureLanguagePreference(@NonNull Context context) {
            Preference languagePreference = findPreference("language");
            if (languagePreference == null) return;

            updateLanguageSummary(languagePreference);
            languagePreference.setOnPreferenceClickListener(preference -> {
                showLanguageDialog(context, languagePreference);
                return true;
            });
        }

        private void updateLanguageSummary(@NonNull Preference preference) {
            String tag = AppLanguageUtils.getCurrentLanguageTag();
            if (tag == null || tag.isEmpty()) {
                preference.setSummary(R.string.language_system);
            } else if (AppLanguageUtils.isSimplifiedChinese(tag)) {
                preference.setSummary(R.string.language_simplified_chinese);
            } else if (AppLanguageUtils.isTraditionalChinese(tag)) {
                preference.setSummary(R.string.language_traditional_chinese);
            } else if (tag.equalsIgnoreCase(AppLanguageUtils.LANGUAGE_ENGLISH)
                || tag.toLowerCase(Locale.ROOT).startsWith("en-")) {
                preference.setSummary(R.string.language_english);
            } else {
                preference.setSummary(tag);
            }
        }

        private void showLanguageDialog(@NonNull Context context, @NonNull Preference preference) {
            String[] languageTags = {
                null,
                AppLanguageUtils.LANGUAGE_ENGLISH,
                AppLanguageUtils.LANGUAGE_SIMPLIFIED_CHINESE,
                AppLanguageUtils.LANGUAGE_TRADITIONAL_CHINESE
            };
            String[] labels = {
                context.getString(R.string.language_system),
                context.getString(R.string.language_english),
                context.getString(R.string.language_simplified_chinese),
                context.getString(R.string.language_traditional_chinese)
            };

            String currentTag = AppLanguageUtils.getCurrentLanguageTag();
            int checkedIndex = 0;
            if (currentTag != null) {
                if (AppLanguageUtils.isSimplifiedChinese(currentTag)) {
                    checkedIndex = 2;
                } else if (AppLanguageUtils.isTraditionalChinese(currentTag)) {
                    checkedIndex = 3;
                } else if (currentTag.equalsIgnoreCase(AppLanguageUtils.LANGUAGE_ENGLISH)
                    || currentTag.toLowerCase(Locale.ROOT).startsWith("en-")) {
                    checkedIndex = 1;
                }
            }

            new AlertDialog.Builder(context)
                .setTitle(R.string.language_preference_title)
                .setSingleChoiceItems(labels, checkedIndex, (dialog, which) -> {
                    dialog.dismiss();
                    AppLanguageUtils.setCurrentLanguageTag(context, languageTags[which]);
                    updateLanguageSummary(preference);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        }

        private void configureTermuxAPIPreference(@NonNull Context context) {
            Preference termuxAPIPreference = findPreference("termux_api");
            if (termuxAPIPreference != null) {
                TermuxAPIAppSharedPreferences preferences = TermuxAPIAppSharedPreferences.build(context, false);
                // If failed to get app preferences, then likely app is not installed, so do not show its preference
                termuxAPIPreference.setVisible(preferences != null);
            }
        }

        private void configureTermuxFloatPreference(@NonNull Context context) {
            Preference termuxFloatPreference = findPreference("termux_float");
            if (termuxFloatPreference != null) {
                TermuxFloatAppSharedPreferences preferences = TermuxFloatAppSharedPreferences.build(context, false);
                // If failed to get app preferences, then likely app is not installed, so do not show its preference
                termuxFloatPreference.setVisible(preferences != null);
            }
        }

        private void configureTermuxTaskerPreference(@NonNull Context context) {
            Preference termuxTaskerPreference = findPreference("termux_tasker");
            if (termuxTaskerPreference != null) {
                TermuxTaskerAppSharedPreferences preferences = TermuxTaskerAppSharedPreferences.build(context, false);
                // If failed to get app preferences, then likely app is not installed, so do not show its preference
                termuxTaskerPreference.setVisible(preferences != null);
            }
        }

        private void configureTermuxWidgetPreference(@NonNull Context context) {
            Preference termuxWidgetPreference = findPreference("termux_widget");
            if (termuxWidgetPreference != null) {
                TermuxWidgetAppSharedPreferences preferences = TermuxWidgetAppSharedPreferences.build(context, false);
                // If failed to get app preferences, then likely app is not installed, so do not show its preference
                termuxWidgetPreference.setVisible(preferences != null);
            }
        }

        private void configureAboutPreference(@NonNull Context context) {
            Preference aboutPreference = findPreference("about");
            if (aboutPreference != null) {
                aboutPreference.setOnPreferenceClickListener(preference -> {
                    new Thread() {
                        @Override
                        public void run() {
                            String title = context.getString(R.string.about_preference_title);

                            StringBuilder aboutString = new StringBuilder();
                            aboutString.append(TermuxUtils.getAppInfoMarkdownString(context, TermuxUtils.AppInfoMode.TERMUX_AND_PLUGIN_PACKAGES));
                            aboutString.append("\n\n").append(AndroidUtils.getDeviceInfoMarkdownString(context, true));
                            aboutString.append("\n\n").append(TermuxUtils.getImportantLinksMarkdownString(context));

                            String userActionName = UserAction.ABOUT.getName();

                            ReportInfo reportInfo = new ReportInfo(userActionName,
                                TermuxConstants.TERMUX_APP.TERMUX_SETTINGS_ACTIVITY_NAME, title);
                            reportInfo.setReportString(aboutString.toString());
                            reportInfo.setReportSaveFileLabelAndPath(userActionName,
                                Environment.getExternalStorageDirectory() + "/" +
                                    FileUtils.sanitizeFileName(TermuxConstants.TERMUX_APP_NAME + "-" + userActionName + ".log", true, true));

                            ReportActivity.startReportActivity(context, reportInfo);
                        }
                    }.start();

                    return true;
                });
            }
        }

        private void configureDonatePreference(@NonNull Context context) {
            Preference donatePreference = findPreference("donate");
            if (donatePreference != null) {
                String signingCertificateSHA256Digest = PackageUtils.getSigningCertificateSHA256DigestForPackage(context);
                if (signingCertificateSHA256Digest != null) {
                    // If APK is a Google Playstore release, then do not show the donation link
                    // since Termux isn't exempted from the playstore policy donation links restriction
                    // Check Fund solicitations: https://pay.google.com/intl/en_in/about/policy/
                    String apkRelease = TermuxUtils.getAPKRelease(signingCertificateSHA256Digest);
                    if (apkRelease == null || apkRelease.equals(TermuxConstants.APK_RELEASE_GOOGLE_PLAYSTORE_SIGNING_CERTIFICATE_SHA256_DIGEST)) {
                        donatePreference.setVisible(false);
                        return;
                    } else {
                        donatePreference.setVisible(true);
                    }
                }

                donatePreference.setOnPreferenceClickListener(preference -> {
                    ShareUtils.openUrl(context, TermuxConstants.TERMUX_DONATE_URL);
                    return true;
                });
            }
        }
    }

}
