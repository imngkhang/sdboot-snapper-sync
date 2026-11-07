package org.limine.snapper.objects;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Loads the translated messages.
 * <p>
 * The catalogs are plain {@code .properties} files in {@code src/main/resources}.
 * {@code messages.properties} holds the English originals and is the fallback,
 * so a missing or broken translation never costs more than the English text.
 * The language comes from the locale of the environment, which means LC_ALL,
 * LC_MESSAGES or LANG.
 */
public final class Messages {

    private static final String BUNDLE_NAME = "messages";
    private static final ResourceBundle BUNDLE = loadBundle();

    private Messages() {
    }

    private static ResourceBundle loadBundle() {
        try {
            return ResourceBundle.getBundle(BUNDLE_NAME, Locale.getDefault());
        } catch (MissingResourceException e) {
            return null;
        }
    }

    /**
     * Returns the text for the key, or the key itself if the catalog has no such entry.
     */
    public static String get(String key) {
        if (BUNDLE == null) {
            return key;
        }
        try {
            return BUNDLE.getString(key);
        } catch (MissingResourceException e) {
            return key;
        }
    }

    /**
     * Returns the text for the key with the placeholders {0}, {1}, ... filled in.
     */
    public static String get(String key, Object... arguments) {
        String pattern = get(key);
        if (arguments == null || arguments.length == 0) {
            return pattern;
        }
        // The arguments are turned into plain strings first: a number formatted by the
        // locale would grow a thousand separator and print snapshot ID 1234 as "1 234".
        Object[] plain = new Object[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            plain[i] = String.valueOf(arguments[i]);
        }
        try {
            return new MessageFormat(pattern, Locale.ROOT).format(plain);
        } catch (IllegalArgumentException e) {
            return pattern;
        }
    }

    /**
     * Picks the plural form for a count and fills in the placeholders.
     * <p>
     * The catalog holds one key per form, suffixed with the form name, and names the
     * forms its language needs in {@code plural.forms}. English gets by with
     * {@code one} and {@code other}, Russian needs {@code one}, {@code few} and
     * {@code many}.
     */
    public static String plural(String key, long count, Object... arguments) {
        return get(key + "." + pluralForm(count), arguments);
    }

    private static String pluralForm(long count) {
        String forms = get("plural.forms");
        long n = Math.abs(count);
        if ("one-few-many".equals(forms)) {
            long mod10 = n % 10;
            long mod100 = n % 100;
            if (mod10 == 1 && mod100 != 11) {
                return "one";
            }
            if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) {
                return "few";
            }
            return "many";
        }
        return n == 1 ? "one" : "other";
    }
}
