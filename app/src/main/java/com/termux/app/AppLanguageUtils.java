package com.termux.app;

import android.content.Context;
import android.content.res.Configuration;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import java.util.Locale;

/**
 * Per-app language selection backed by AppCompat's locale APIs.
 *
 * <p>An empty locale list means "follow the system language". AppCompat persists the selection and
 * recreates activities, while terminal sessions live in {@link TermuxService} and therefore survive
 * the activity recreation.</p>
 *
 * <p>The selected tag is also kept in an app-private preference so non-Activity contexts, such as
 * the foreground service, can localize their own resources on API levels where AppCompat only
 * overrides Activity contexts.</p>
 */
public final class AppLanguageUtils {

    public static final String LANGUAGE_ENGLISH = "en";
    public static final String LANGUAGE_SIMPLIFIED_CHINESE = "zh-CN";
    public static final String LANGUAGE_TRADITIONAL_CHINESE = "zh-TW";

    private static final String PREFERENCES_NAME = "termux_app_language";
    private static final String KEY_LANGUAGE_TAG = "language_tag";

    private AppLanguageUtils() {
    }

    /** Returns the selected BCP 47 tag, or {@code null} when following the system language. */
    @Nullable
    public static String getCurrentLanguageTag() {
        LocaleListCompat locales = AppCompatDelegate.getApplicationLocales();
        if (locales.isEmpty()) return null;

        return locales.get(0).toLanguageTag();
    }

    /** Pass {@code null} or an empty string to follow the system language. */
    public static void setCurrentLanguageTag(@NonNull Context context,
                                             @Nullable String languageTag) {
        String tag = languageTag == null ? "" : languageTag.trim();
        if (tag.isEmpty()) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList());
        } else {
            AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags(tag));
        }

        context.getApplicationContext()
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE_TAG, tag)
            .apply();
    }

    /**
     * Returns a context whose resources use the selected app language. Returns the original context
     * when following the system language or when no selection is available.
     */
    @NonNull
    public static Context getLocalizedContext(@NonNull Context context) {
        Locale locale = getApplicationLocale(context);
        if (locale == null) return context;

        Configuration configuration = new Configuration(
            context.getResources().getConfiguration());
        configuration.setLocale(locale);
        return context.createConfigurationContext(configuration);
    }

    @Nullable
    private static Locale getApplicationLocale(@NonNull Context context) {
        LocaleListCompat locales = AppCompatDelegate.getApplicationLocales();
        if (!locales.isEmpty()) return locales.get(0);

        String storedTag = getStoredLanguageTag(context);
        if (storedTag == null) return null;

        Locale locale = Locale.forLanguageTag(storedTag);
        return locale.getLanguage().isEmpty() ? null : locale;
    }

    @Nullable
    private static String getStoredLanguageTag(@NonNull Context context) {
        String tag = context.getApplicationContext()
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE_TAG, null);
        return tag == null || tag.trim().isEmpty() ? null : tag.trim();
    }

    public static boolean isSimplifiedChinese(@Nullable String languageTag) {
        return languageTag != null
            && (languageTag.equalsIgnoreCase("zh-CN") || languageTag.equalsIgnoreCase("zh-Hans"));
    }

    public static boolean isTraditionalChinese(@Nullable String languageTag) {
        return languageTag != null
            && (languageTag.equalsIgnoreCase("zh-TW") || languageTag.equalsIgnoreCase("zh-HK")
            || languageTag.equalsIgnoreCase("zh-MO") || languageTag.equalsIgnoreCase("zh-Hant"));
    }

}
