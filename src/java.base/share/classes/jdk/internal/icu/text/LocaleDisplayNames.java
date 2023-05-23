// Copyright 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 *******************************************************************************
 * Copyright (C) 2009-2016, International Business Machines Corporation and    *
 * others. All Rights Reserved.                                                *
 *******************************************************************************
 */
package jdk.internal.icu.text;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import jdk.internal.icu.impl.ICUConfig;
import jdk.internal.icu.lang.UScript;
import jdk.internal.icu.text.DisplayContext.Type;
import jdk.internal.icu.util.IllformedLocaleException;
import jdk.internal.icu.util.ULocale;

/**
 * Returns display names of ULocales and components of ULocales. For
 * more information on language, script, region, variant, key, and
 * values, see {@link jdk.internal.icu.util.ULocale}.
 * @stable ICU 4.4
 */
public abstract class LocaleDisplayNames {
    /**
     * Enum used in {@link #getInstance(ULocale, DialectHandling)}.
     * @stable ICU 4.4
     */
    public enum DialectHandling {
        /**
         * Use standard names when generating a locale name,
         * e.g. en_GB displays as 'English (United Kingdom)'.
         * @stable ICU 4.4
         */
        STANDARD_NAMES,
        /**
         * Use dialect names when generating a locale name,
         * e.g. en_GB displays as 'British English'.
         * @stable ICU 4.4
         */
        DIALECT_NAMES
    }

    // factory methods
    /**
     * Convenience overload of {@link #getInstance(ULocale, DialectHandling)} that specifies
     * STANDARD dialect handling.
     * @param locale the display locale
     * @return a LocaleDisplayNames instance
     * @stable ICU 4.4
     */
    public static LocaleDisplayNames getInstance(ULocale locale) {
        return getInstance(locale, DialectHandling.STANDARD_NAMES);
    };

    /**
     * Convenience overload of {@link #getInstance(Locale, DisplayContext...)} that specifies
     * {@link DisplayContext#STANDARD_NAMES}.
     * @param locale the display {@link java.util.Locale}
     * @return a LocaleDisplayNames instance
     * @stable ICU 54
     */
    public static LocaleDisplayNames getInstance(Locale locale) {
        return getInstance(ULocale.forLocale(locale));
    };

    /**
     * Returns an instance of LocaleDisplayNames that returns names formatted for the provided locale,
     * using the provided dialectHandling.
     * @param locale the display locale
     * @param dialectHandling how to select names for locales
     * @return a LocaleDisplayNames instance
     * @stable ICU 4.4
     */
    public static LocaleDisplayNames getInstance(ULocale locale, DialectHandling dialectHandling) {
        LocaleDisplayNames result = null;
        if (FACTORY_DIALECTHANDLING != null) {
            try {
                result = (LocaleDisplayNames) FACTORY_DIALECTHANDLING.invoke(null,
                        locale, dialectHandling);
            } catch (InvocationTargetException e) {
                // fall through
            } catch (IllegalAccessException e) {
                // fall through
            }
        }
        if (result == null) {
            result = new LastResortLocaleDisplayNames(locale, dialectHandling);
        }
        return result;
    }

    /**
     * Returns an instance of LocaleDisplayNames that returns names formatted for the provided locale,
     * using the provided DisplayContext settings
     * @param locale the display locale
     * @param contexts one or more context settings (e.g. for dialect
     *              handling, capitalization, etc.
     * @return a LocaleDisplayNames instance
     * @stable ICU 51
     */
    public static LocaleDisplayNames getInstance(ULocale locale, DisplayContext... contexts) {
        LocaleDisplayNames result = null;
        if (FACTORY_DISPLAYCONTEXT != null) {
            try {
                result = (LocaleDisplayNames) FACTORY_DISPLAYCONTEXT.invoke(null,
                        locale, contexts);
            } catch (InvocationTargetException e) {
                // fall through
            } catch (IllegalAccessException e) {
                // fall through
            }
        }
        if (result == null) {
            result = new LastResortLocaleDisplayNames(locale, contexts);
        }
        return result;
    }

    /**
     * Returns an instance of LocaleDisplayNames that returns names formatted for the provided
     * {@link java.util.Locale}, using the provided DisplayContext settings
     * @param locale the display {@link java.util.Locale}
     * @param contexts one or more context settings (e.g. for dialect
     *              handling, capitalization, etc.
     * @return a LocaleDisplayNames instance
     * @stable ICU 54
     */
    public static LocaleDisplayNames getInstance(Locale locale, DisplayContext... contexts) {
        return getInstance(ULocale.forLocale(locale), contexts);
    }

    // getters for state
    /**
     * Returns the locale used to determine the display names. This is not necessarily the same
     * locale passed to {@link #getInstance}.
     * @return the display locale
     * @stable ICU 4.4
     */
    public abstract ULocale getLocale();

    /**
     * Returns the dialect handling used in the display names.
     * @return the dialect handling enum
     * @stable ICU 4.4
     */
    public abstract DialectHandling getDialectHandling();

    /**
     * Returns the current value for a specified DisplayContext.Type.
     * @param type the DisplayContext.Type whose value to return
     * @return the current DisplayContext setting for the specified type
     * @stable ICU 51
     */
    public abstract DisplayContext getContext(DisplayContext.Type type);

    // names for entire locales
    /**
     * Returns the display name of the provided ulocale.
     * When no display names are available for all or portions
     * of the original locale ID, those portions may be
     * used directly (possibly in a more canonical form) as
     * part of the  returned display name.
     * @param locale the locale whose display name to return
     * @return the display name of the provided locale
     * @stable ICU 4.4
     */
    public abstract String localeDisplayName(ULocale locale);

    /**
     * Returns the display name of the provided locale.
     * When no display names are available for all or portions
     * of the original locale ID, those portions may be
     * used directly (possibly in a more canonical form) as
     * part of the  returned display name.
     * @param locale the locale whose display name to return
     * @return the display name of the provided locale
     * @stable ICU 4.4
     */
    public abstract String localeDisplayName(Locale locale);

    /**
     * Returns the display name of the provided locale id.
     * When no display names are available for all or portions
     * of the original locale ID, those portions may be
     * used directly (possibly in a more canonical form) as
     * part of the  returned display name.
     * @param localeId the id of the locale whose display name to return
     * @return the display name of the provided locale
     * @stable ICU 4.4
     */
    public abstract String localeDisplayName(String localeId);

    // names for components of a locale id
    /**
     * Returns the display name of the provided language code.
     * @param lang the language code
     * @return the display name of the provided language code
     * @stable ICU 4.4
     */
    public abstract String languageDisplayName(String lang);

    /**
     * Returns the display name of the provided script code.
     * @param script the script code
     * @return the display name of the provided script code
     * @stable ICU 4.4
     */
    public abstract String scriptDisplayName(String script);

    /**
     * Returns the display name of the provided script code
     * when used in the context of a full locale name.
     * @param script the script code
     * @return the display name of the provided script code
     * @internal ICU 49
     * @deprecated This API is ICU internal only.
     */
    @Deprecated
    public String scriptDisplayNameInContext(String script) {
        return scriptDisplayName(script);
    }

    /**
     * Returns the display name of the provided script code.  See
     * {@link jdk.internal.icu.lang.UScript} for recognized script codes.
     * @param scriptCode the script code number
     * @return the display name of the provided script code
     * @stable ICU 4.4
     */
    public abstract String scriptDisplayName(int scriptCode);

    /**
     * Returns the display name of the provided region code.
     * @param region the region code
     * @return the display name of the provided region code
     * @stable ICU 4.4
     */
    public abstract String regionDisplayName(String region);

    /**
     * Returns the display name of the provided variant.
     * @param variant the variant string
     * @return the display name of the provided variant
     * @stable ICU 4.4
     */
    public abstract String variantDisplayName(String variant);

    /**
     * Returns the display name of the provided locale key.
     * @param key the locale key name
     * @return the display name of the provided locale key
     * @stable ICU 4.4
     */
    public abstract String keyDisplayName(String key);

    /**
     * Returns the display name of the provided value (used with the provided key).
     * @param key the locale key name
     * @param value the locale key's value
     * @return the display name of the provided value
     * @stable ICU 4.4
     */
    public abstract String keyValueDisplayName(String key, String value);


    /**
     * Return a list of information used to construct a UI list of locale names.
     * @param collator how to collate\u2014should normally be Collator.getInstance(getDisplayLocale())
     * @param inSelf if true, compares the nameInSelf, otherwise the nameInDisplayLocale.
     * Set depending on which field (displayLocale vs self) is to show up in the UI.
     * If both are to show up in the UI, then it should be the one used for the primary sort order.
     * @param localeSet a list of locales to present in a UI list. The casing uses the settings in the LocaleDisplayNames instance.
     * @return an ordered list of UiListItems.
     * @throws IllformedLocaleException if any of the locales in localeSet are malformed.
     * @stable ICU 55
     */
    public List<UiListItem> getUiList(Set<ULocale> localeSet, boolean inSelf, Comparator<Object> collator) {
        return getUiListCompareWholeItems(localeSet, UiListItem.getComparator(collator, inSelf));
    }

    /**
     * Return a list of information used to construct a UI list of locale names, providing more access to control the sorting.
     * Normally use getUiList instead.
     * @param comparator how to sort the UiListItems in the result.
     * @param localeSet a list of locales to present in a UI list. The casing uses the settings in the LocaleDisplayNames instance.
     * @return an ordered list of UiListItems.
     * @throws IllformedLocaleException if any of the locales in localeSet are malformed.
     * @stable ICU 55
     */
    public abstract List<UiListItem> getUiListCompareWholeItems(Set<ULocale> localeSet, Comparator<UiListItem> comparator);

    /**
     * Struct-like class used to return information for constructing a UI list, each corresponding to a locale.
     * @stable ICU 55
     */
    public static class UiListItem {
        /**
         * Returns the minimized locale for an input locale, such as sr-Cyrl \u2192 sr
         * @stable ICU 55
         */
        public final ULocale minimized;
        /**
         * Returns the modified locale for an input locale, such as sr \u2192 sr-Cyrl, where there is also an sr-Latn in the list
         * @stable ICU 55
         */
        public final ULocale modified;
        /**
         * Returns the name of the modified locale in the display locale, such as "Englisch (VS)" (for 'en-US', where the display locale is 'de').
         * @stable ICU 55
         */
        public final String nameInDisplayLocale;
        /**
         * Returns the name of the modified locale in itself, such as "English (US)" (for 'en-US').
         * @stable ICU 55
         */
        public final String nameInSelf;

        /**
         * Constructor, normally only called internally.
         * @param minimized locale for an input locale
         * @param modified modified for an input locale
         * @param nameInDisplayLocale name of the modified locale in the display locale
         * @param nameInSelf name of the modified locale in itself
         * @stable ICU 55
         */
        public UiListItem(ULocale minimized, ULocale modified, String nameInDisplayLocale, String nameInSelf) {
            this.minimized = minimized;
            this.modified = modified;
            this.nameInDisplayLocale = nameInDisplayLocale;
            this.nameInSelf = nameInSelf;
        }

        /**
         * {@inheritDoc}
         *
         * @stable ICU 55
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || !(obj instanceof UiListItem)) {
                return false;
            }
            UiListItem other = (UiListItem)obj;
            return nameInDisplayLocale.equals(other.nameInDisplayLocale)
                    && nameInSelf.equals(other.nameInSelf)
                    && minimized.equals(other.minimized)
                    && modified.equals(other.modified);
        }

        /**
         * {@inheritDoc}
         *
         * @stable ICU 55
         */
        @Override
        public int hashCode() {
            return modified.hashCode() ^ nameInDisplayLocale.hashCode();
        }

        /**
         * {@inheritDoc}
         *
         * @stable ICU 55
         */
        @Override
        public String toString() {
            return "{" + minimized + ", " + modified + ", " + nameInDisplayLocale + ", " + nameInSelf  + "}";
        }

        /**
         * Return a comparator that compares the locale names for the display locale or the in-self names,
         * depending on an input parameter.
         * @param inSelf if true, compares the nameInSelf, otherwise the nameInDisplayLocale
         * @param comparator (meant for strings, but because Java Collator doesn't have &lt;String&gt;...)
         * @return UiListItem comparator
         * @stable ICU 55
         */
        public static Comparator<UiListItem> getComparator(Comparator<Object> comparator, boolean inSelf) {
            return new UiListItemComparator(comparator, inSelf);
        }

        private static class UiListItemComparator implements Comparator<UiListItem> {
            private final Comparator<Object> collator;
            private final boolean useSelf;
            UiListItemComparator(Comparator<Object> collator, boolean useSelf) {
                this.collator = collator;
                this.useSelf = useSelf;
            }
            @Override
            public int compare(UiListItem o1, UiListItem o2) {
                int result = useSelf ? collator.compare(o1.nameInSelf, o2.nameInSelf)
                        : collator.compare(o1.nameInDisplayLocale, o2.nameInDisplayLocale);
                return result != 0 ? result : o1.modified.compareTo(o2.modified); // just in case
            }
        }
    }
    /**
     * Sole constructor.  (For invocation by subclass constructors,
     * typically implicit.)
     * @internal
     * @deprecated This API is ICU internal only.
     */
    @Deprecated
    protected LocaleDisplayNames() {
    }

    private static final Method FACTORY_DIALECTHANDLING;
    private static final Method FACTORY_DISPLAYCONTEXT;

    static {
        String implClassName = ICUConfig.get("jdk.internal.icu.text.LocaleDisplayNames.impl", "jdk.internal.icu.impl.LocaleDisplayNamesImpl");

        Method factoryDialectHandling = null;
        Method factoryDisplayContext = null;

        try {
            Class<?> implClass = Class.forName(implClassName);
            try {
                factoryDialectHandling = implClass.getMethod("getInstance",
                        ULocale.class, DialectHandling.class);
            } catch (NoSuchMethodException e) {
            }
            try {
                factoryDisplayContext = implClass.getMethod("getInstance",
                        ULocale.class, DisplayContext[].class);
            } catch (NoSuchMethodException e) {
            }

        } catch (ClassNotFoundException e) {
            // fallback to last resort impl
        }

        FACTORY_DIALECTHANDLING = factoryDialectHandling;
        FACTORY_DISPLAYCONTEXT = factoryDisplayContext;
    }

    /**
     * Minimum implementation of LocaleDisplayNames
     */
    private static class LastResortLocaleDisplayNames extends LocaleDisplayNames {

        private ULocale locale;
        private DisplayContext[] contexts;

        private LastResortLocaleDisplayNames(ULocale locale, DialectHandling dialectHandling) {
            this.locale = locale;
            DisplayContext context = (dialectHandling == DialectHandling.DIALECT_NAMES) ?
                    DisplayContext.DIALECT_NAMES : DisplayContext.STANDARD_NAMES;
            this.contexts = new DisplayContext[] {context};
        }

        private LastResortLocaleDisplayNames(ULocale locale, DisplayContext... contexts) {
            this.locale = locale;
            this.contexts = new DisplayContext[contexts.length];
            System.arraycopy(contexts, 0, this.contexts, 0, contexts.length);
        }

        @Override
        public ULocale getLocale() {
            return locale;
        }

        @Override
        public DialectHandling getDialectHandling() {
            DialectHandling result = DialectHandling.STANDARD_NAMES;
            for (DisplayContext context : contexts) {
                if (context.type() == DisplayContext.Type.DIALECT_HANDLING) {
                    if (context.value() == DisplayContext.DIALECT_NAMES.ordinal()) {
                        result = DialectHandling.DIALECT_NAMES;
                        break;
                    }
                }
            }
            return result;
        }

        @Override
        public DisplayContext getContext(Type type) {
            DisplayContext result = DisplayContext.STANDARD_NAMES;  // final fallback
            for (DisplayContext context : contexts) {
                if (context.type() == type) {
                    result = context;
                    break;
                }
            }
            return result;
        }

        @Override
        public String localeDisplayName(ULocale locale) {
            return locale.getName();
        }

        @Override
        public String localeDisplayName(Locale locale) {
            return ULocale.forLocale(locale).getName();
        }

        @Override
        public String localeDisplayName(String localeId) {
            return new ULocale(localeId).getName();
        }

        @Override
        public String languageDisplayName(String lang) {
            return lang;
        }

        @Override
        public String scriptDisplayName(String script) {
            return script;
        }

        @Override
        public String scriptDisplayName(int scriptCode) {
            return UScript.getShortName(scriptCode);
        }

        @Override
        public String regionDisplayName(String region) {
            return region;
        }

        @Override
        public String variantDisplayName(String variant) {
            return variant;
        }

        @Override
        public String keyDisplayName(String key) {
            return key;
        }

        @Override
        public String keyValueDisplayName(String key, String value) {
            return value;
        }

        @Override
        public List<UiListItem> getUiListCompareWholeItems(Set<ULocale> localeSet, Comparator<UiListItem> comparator) {
            return Collections.emptyList();
        }
    }
}
