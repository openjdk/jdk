// Copyright 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 ******************************************************************************
 * Copyright (C) 2003-2016, International Business Machines Corporation and
 * others. All Rights Reserved.
 ******************************************************************************
 */

package jdk.internal.icu.util;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import jdk.internal.icu.impl.CacheBase;
import jdk.internal.icu.impl.ICUData;
import jdk.internal.icu.impl.ICUResourceBundle;
import jdk.internal.icu.impl.ICUResourceTableAccess;
import jdk.internal.icu.impl.LocaleIDParser;
import jdk.internal.icu.impl.LocaleIDs;
import jdk.internal.icu.impl.SoftCache;
import jdk.internal.icu.impl.Utility;
import jdk.internal.icu.impl.locale.AsciiUtil;
import jdk.internal.icu.impl.locale.BaseLocale;
import jdk.internal.icu.impl.locale.Extension;
import jdk.internal.icu.impl.locale.InternalLocaleBuilder;
import jdk.internal.icu.impl.locale.KeyTypeData;
import jdk.internal.icu.impl.locale.LanguageTag;
import jdk.internal.icu.impl.locale.LocaleExtensions;
import jdk.internal.icu.impl.locale.LocaleSyntaxException;
import jdk.internal.icu.impl.locale.ParseStatus;
import jdk.internal.icu.impl.locale.UnicodeLocaleExtension;
import jdk.internal.icu.lang.UScript;
import jdk.internal.icu.text.LocaleDisplayNames;
import jdk.internal.icu.text.LocaleDisplayNames.DialectHandling;

/**
 * {@icuenhanced java.util.Locale}.{@icu _usage_}
 *
 * A class analogous to {@link java.util.Locale} that provides additional
 * support for ICU protocol.  In ICU 3.0 this class is enhanced to support
 * RFC 3066 language identifiers.
 *
 * <p>Many classes and services in ICU follow a factory idiom, in
 * which a factory method or object responds to a client request with
 * an object.  The request includes a locale (the <i>requested</i>
 * locale), and the returned object is constructed using data for that
 * locale.  The system may lack data for the requested locale, in
 * which case the locale fallback mechanism will be invoked until a
 * populated locale is found (the <i>valid</i> locale).  Furthermore,
 * even when a populated locale is found (the <i>valid</i> locale),
 * further fallback may be required to reach a locale containing the
 * specific data required by the service (the <i>actual</i> locale).
 *
 * <p>ULocale performs <b>'normalization'</b> and <b>'canonicalization'</b> of locale ids.
 * Normalization 'cleans up' ICU locale ids as follows:
 * <ul>
 * <li>language, script, country, variant, and keywords are properly cased<br>
 * (lower, title, upper, upper, and lower case respectively)</li>
 * <li>hyphens used as separators are converted to underscores</li>
 * <li>three-letter language and country ids are converted to two-letter
 * equivalents where available</li>
 * <li>surrounding spaces are removed from keywords and values</li>
 * <li>if there are multiple keywords, they are put in sorted order</li>
 * </ul>
 * Canonicalization additionally performs the following:
 * <ul>
 * <li>POSIX ids are converted to ICU format IDs</li>
 * <li>Legacy language tags (marked as "Type: grandfathered" in BCP 47)
 * are converted to ICU standard form</li>
 * </ul>
 * All ULocale constructors automatically normalize the locale id.  To handle
 * POSIX ids, <code>canonicalize</code> can be called to convert the id
 * to canonical form, or the <code>canonicalInstance</code> factory method
 * can be called.
 *
 * <p>This class provides selectors {@link #VALID_LOCALE} and {@link
 * #ACTUAL_LOCALE} intended for use in methods named
 * <tt>getLocale()</tt>.  These methods exist in several ICU classes,
 * including {@link jdk.internal.icu.util.Calendar}, {@link
 * jdk.internal.icu.util.Currency}, {@link jdk.internal.icu.text.UFormat},
 * {@link jdk.internal.icu.text.BreakIterator},
 * {@link jdk.internal.icu.text.Collator},
 * {@link jdk.internal.icu.text.DateFormatSymbols}, and {@link
 * jdk.internal.icu.text.DecimalFormatSymbols} and their subclasses, if
 * any. Once an object of one of these classes has been created,
 * <tt>getLocale()</tt> may be called on it to determine the valid and
 * actual locale arrived at during the object's construction.
 *
 * <p>Note: The <i>actual</i> locale is returned correctly, but the <i>valid</i>
 * locale is not, in most cases.
 *
 * @see java.util.Locale
 * @author weiv
 * @author Alan Liu
 * @author Ram Viswanadha
 * @stable ICU 2.8
 */
@SuppressWarnings("deprecation")    // jdk.internal.icu.text.Collator is in another project
public final class ULocale implements Serializable, Comparable<ULocale> {
    // using serialver from jdk1.4.2_05
    private static final long serialVersionUID = 3715177670352309217L;

    private static CacheBase<String, String, Void> nameCache = new SoftCache<String, String, Void>() {
        @Override
        protected String createInstance(String tmpLocaleID, Void unused) {
            return new LocaleIDParser(tmpLocaleID).getName();
        }
    };

    /**
     * Types for {@link ULocale#getAvailableLocalesByType}
     *
     * @stable ICU 65
     */
    public static enum AvailableType {
        /**
         * Locales that return data when passed to ICU APIs,
         * but not including legacy or alias locales.
         *
         * @stable ICU 65
         */
        DEFAULT,

        /**
         * Legacy or alias locales that return data when passed to ICU APIs.
         * Examples of supported legacy or alias locales:
         *
         * <ul>
         * <li>iw (alias to he)
         * <li>mo (alias to ro)
         * <li>zh_CN (alias to zh_Hans_CN)
         * <li>sr_BA (alias to sr_Cyrl_BA)
         * <li>ars (alias to ar_SA)
         * </ul>
         *
         * The locales in this set are disjoint from the ones in
         * DEFAULT. To get both sets at the same time, use
         * WITH_LEGACY_ALIASES.
         *
         * @stable ICU 65
         */
        ONLY_LEGACY_ALIASES,

        /**
         * The union of the locales in DEFAULT and ONLY_LEGACY_ALIASES.
         *
         * @stable ICU 65
         */
        WITH_LEGACY_ALIASES,
    }

    /**
     * Useful constant for language.
     * @stable ICU 3.0
     */
    public static final ULocale ENGLISH = new ULocale("en", Locale.ENGLISH);

    /**
     * Useful constant for language.
     * @stable ICU 3.0
     */
    public static final ULocale FRENCH = new ULocale("fr", Locale.FRENCH);

    /**
     * Useful constant for language.
     * @stable ICU 3.0
     */
    public static final ULocale GERMAN = new ULocale("de", Locale.GERMAN);

    /**
     * Useful constant for language.
     * @stable ICU 3.0
     */
    public static final ULocale ITALIAN = new ULocale("it", Locale.ITALIAN);

    /**
     * Useful constant for language.
     * @stable ICU 3.0
     */
    public static final ULocale JAPANESE = new ULocale("ja", Locale.JAPANESE);

    /**
     * Useful constant for language.
     * @stable ICU 3.0
     */
    public static final ULocale KOREAN = new ULocale("ko", Locale.KOREAN);

    /**
     * Useful constant for language.
     * @stable ICU 3.0
     */
    public static final ULocale CHINESE = new ULocale("zh", Locale.CHINESE);


    // Special note about static initializer for
    //   - SIMPLIFIED_CHINESE
    //   - TRADTIONAL_CHINESE
    //   - CHINA
    //   - TAIWAN
    //
    // Equivalent JDK Locale for ULocale.SIMPLIFIED_CHINESE is different
    // by JRE version. JRE 7 or later supports a script tag "Hans", while
    // JRE 6 or older does not. JDK's Locale.SIMPLIFIED_CHINESE is actually
    // zh_CN, not zh_Hans. This is same in Java 7 or later versions.
    //
    // ULocale#toLocale() implementation create a Locale with a script tag.
    // When a new ULocale is constructed with the single arg
    // constructor, the volatile field 'Locale locale' is initialized by
    // #toLocale() method.
    //
    // Because we cannot hardcode corresponding JDK Locale representation below,
    // SIMPLIFIED_CHINESE is constructed without JDK Locale argument, and
    // #toLocale() is used for resolving the best matching JDK Locale at runtime.
    //
    // The same thing applies to TRADITIONAL_CHINESE.

    /**
     * Useful constant for language.
     * @stable ICU 3.0
     */
    public static final ULocale SIMPLIFIED_CHINESE = new ULocale("zh_Hans");


    /**
     * Useful constant for language.
     * @stable ICU 3.0
     */
    public static final ULocale TRADITIONAL_CHINESE = new ULocale("zh_Hant");

    /**
     * Useful constant for country/region.
     * @stable ICU 3.0
     */
    public static final ULocale FRANCE = new ULocale("fr_FR", Locale.FRANCE);

    /**
     * Useful constant for country/region.
     * @stable ICU 3.0
     */
    public static final ULocale GERMANY = new ULocale("de_DE", Locale.GERMANY);

    /**
     * Useful constant for country/region.
     * @stable ICU 3.0
     */
    public static final ULocale ITALY = new ULocale("it_IT", Locale.ITALY);

    /**
     * Useful constant for country/region.
     * @stable ICU 3.0
     */
    public static final ULocale JAPAN = new ULocale("ja_JP", Locale.JAPAN);

    /**
     * Useful constant for country/region.
     * @stable ICU 3.0
     */
    public static final ULocale KOREA = new ULocale("ko_KR", Locale.KOREA);

    /**
     * Useful constant for country/region.
     * @stable ICU 3.0
     */
    public static final ULocale CHINA = new ULocale("zh_Hans_CN");

    /**
     * Useful constant for country/region.
     * @stable ICU 3.0
     */
    public static final ULocale PRC = CHINA;

    /**
     * Useful constant for country/region.
     * @stable ICU 3.0
     */
    public static final ULocale TAIWAN = new ULocale("zh_Hant_TW");

    /**
     * Useful constant for country/region.
     * @stable ICU 3.0
     */
    public static final ULocale UK = new ULocale("en_GB", Locale.UK);

    /**
     * Useful constant for country/region.
     * @stable ICU 3.0
     */
    public static final ULocale US = new ULocale("en_US", Locale.US);

    /**
     * Useful constant for country/region.
     * @stable ICU 3.0
     */
    public static final ULocale CANADA = new ULocale("en_CA", Locale.CANADA);

    /**
     * Useful constant for country/region.
     * @stable ICU 3.0
     */
    public static final ULocale CANADA_FRENCH = new ULocale("fr_CA", Locale.CANADA_FRENCH);

    /**
     * Handy constant.
     */
    private static final String EMPTY_STRING = "";

    // Used in both ULocale and LocaleIDParser, so moved up here.
    private static final char UNDERSCORE            = '_';

    // default empty locale
    private static final Locale EMPTY_LOCALE = new Locale("", "");

    // special keyword key for Unicode locale attributes
    private static final String LOCALE_ATTRIBUTE_KEY = "attribute";

    /**
     * The root ULocale.
     * @stable ICU 2.8
     */
    public static final ULocale ROOT = new ULocale("", EMPTY_LOCALE);

    /**
     * Enum for locale categories. These locale categories are used to get/set the default locale for
     * the specific functionality represented by the category.
     * @stable ICU 49
     */
    public enum Category {
        /**
         * Category used to represent the default locale for displaying user interfaces.
         * @stable ICU 49
         */
        DISPLAY,
        /**
         * Category used to represent the default locale for formatting date, number and/or currency.
         * @stable ICU 49
         */
        FORMAT
    }

    private static final SoftCache<Locale, ULocale, Void> CACHE = new SoftCache<Locale, ULocale, Void>() {
        @Override
        protected ULocale createInstance(Locale key, Void unused) {
            return JDKLocaleHelper.toULocale(key);
        }
    };

    /**
     * Cache the locale.
     */
    private transient volatile Locale locale;

    /**
     * The raw localeID that we were passed in.
     */
    private String localeID;

    /**
     * Cache the locale data container fields.
     * In future, we want to use them as the primary locale identifier storage.
     */
    private transient volatile BaseLocale baseLocale;
    private transient volatile LocaleExtensions extensions;

    /**
     * This table lists pairs of locale ids for canonicalization.
     * The 1st item is the normalized id. The 2nd item is the
     * canonicalized id.
     */
    private static String[][] CANONICALIZE_MAP = {
        { "art__LOJBAN",    "jbo" }, /* registered name */
        { "cel__GAULISH",   "cel__GAULISH" }, /* registered name */
        { "de__1901",       "de__1901" }, /* registered name */
        { "de__1906",       "de__1906" }, /* registered name */
        { "en__BOONT",      "en__BOONT" }, /* registered name */
        { "en__SCOUSE",     "en__SCOUSE" }, /* registered name */
        { "hy__AREVELA",    "hy", null, null }, /* Registered IANA variant */
        { "hy__AREVMDA",    "hyw", null, null }, /* Registered IANA variant */
        { "sl__ROZAJ",      "sl__ROZAJ" }, /* registered name */
        { "zh__GUOYU",      "zh" }, /* registered name */
        { "zh__HAKKA",      "hak" }, /* registered name */
        { "zh__XIANG",      "hsn" }, /* registered name */
        // Three letter subtags won't be treated as variants.
        { "zh_GAN",         "gan" }, /* registered name */
        { "zh_MIN",         "zh__MIN" }, /* registered name */
        { "zh_MIN_NAN",     "nan" }, /* registered name */
        { "zh_WUU",         "wuu" }, /* registered name */
        { "zh_YUE",         "yue" } /* registered name */
    };

    /**
     * Private constructor used by static initializers.
     */
    private ULocale(String localeID, Locale locale) {
        this.localeID = localeID;
        this.locale = locale;
    }

    /**
     * {@icu} Returns a ULocale object for a {@link java.util.Locale}.
     * The ULocale is canonicalized.
     * @param loc a {@link java.util.Locale}
     * @stable ICU 3.2
     */
    public static ULocale forLocale(Locale loc) {
        if (loc == null) {
            return null;
        }
        return CACHE.getInstance(loc, null /* unused */);
    }

    /**
     * {@icu} Constructs a ULocale from a RFC 3066 locale ID. The locale ID consists
     * of optional language, script, country, and variant fields in that order,
     * separated by underscores, followed by an optional keyword list.  The
     * script, if present, is four characters long-- this distinguishes it
     * from a country code, which is two characters long.  Other fields
     * are distinguished by position as indicated by the underscores.  The
     * start of the keyword list is indicated by '@', and consists of two
     * or more keyword/value pairs separated by semicolons(';').
     *
     * <p>This constructor does not canonicalize the localeID.  So, for
     * example, "zh__pinyin" remains unchanged instead of converting
     * to "zh@collation=pinyin".  By default ICU only recognizes the
     * latter as specifying pinyin collation.  Use {@link #createCanonical}
     * or {@link #canonicalize} if you need to canonicalize the localeID.
     *
     * @param localeID string representation of the locale, e.g:
     * "en_US", "sy_Cyrl_YU", "zh__pinyin", "es_ES@currency=EUR;collation=traditional"
     * @stable ICU 2.8
     */
    public ULocale(String localeID) {
        this.localeID = getName(localeID);
    }

    /**
     * Convenience overload of ULocale(String, String, String) for
     * compatibility with java.util.Locale.
     * @see #ULocale(String, String, String)
     * @stable ICU 3.4
     */
    public ULocale(String a, String b) {
        this(a, b, null);
    }

    /**
     * Constructs a ULocale from a localeID constructed from the three 'fields' a, b, and
     * c.  These fields are concatenated using underscores to form a localeID of the form
     * a_b_c, which is then handled like the localeID passed to <code>ULocale(String
     * localeID)</code>.
     *
     * <p>Java locale strings consisting of language, country, and
     * variant will be handled by this form, since the country code
     * (being shorter than four letters long) will not be interpreted
     * as a script code.  If a script code is present, the final
     * argument ('c') will be interpreted as the country code.  It is
     * recommended that this constructor only be used to ease porting,
     * and that clients instead use the single-argument constructor
     * when constructing a ULocale from a localeID.
     * @param a first component of the locale id
     * @param b second component of the locale id
     * @param c third component of the locale id
     * @see #ULocale(String)
     * @stable ICU 3.0
     */
    public ULocale(String a, String b, String c) {
        localeID = getName(lscvToID(a, b, c, EMPTY_STRING));
    }

    /**
     * {@icu} Creates a ULocale from the id by first canonicalizing the id according to CLDR.
     * @param nonCanonicalID the locale id to canonicalize
     * @return the locale created from the canonical version of the ID.
     * @stable ICU 3.0
     */
    public static ULocale createCanonical(String nonCanonicalID) {
        return new ULocale(canonicalize(nonCanonicalID), (Locale)null);
    }

    /**
     * Creates a ULocale from the locale by first canonicalizing the locale according to CLDR.
     * @param locale the ULocale to canonicalize
     * @return the ULocale created from the canonical version of the ULocale.
     * @stable ICU 67
     */
    public static ULocale createCanonical(ULocale locale) {
        return createCanonical(locale.getName());
    }

    private static String lscvToID(String lang, String script, String country, String variant) {
        StringBuilder buf = new StringBuilder();

        if (lang != null && lang.length() > 0) {
            buf.append(lang);
        }
        if (script != null && script.length() > 0) {
            buf.append(UNDERSCORE);
            buf.append(script);
        }
        if (country != null && country.length() > 0) {
            buf.append(UNDERSCORE);
            buf.append(country);
        }
        if (variant != null && variant.length() > 0) {
            if (country == null || country.length() == 0) {
                buf.append(UNDERSCORE);
            }
            buf.append(UNDERSCORE);
            buf.append(variant);
        }
        return buf.toString();
    }

    /**
     * {@icu} Converts this ULocale object to a {@link java.util.Locale}.
     * @return a {@link java.util.Locale} that either exactly represents this object
     * or is the closest approximation.
     * @stable ICU 2.8
     */
    public Locale toLocale() {
        if (locale == null) {
            locale = JDKLocaleHelper.toLocale(this);
        }
        return locale;
    }

    /**
     * Keep our own default ULocale.
     */
    private static Locale defaultLocale = Locale.getDefault();
    private static ULocale defaultULocale;

    private static Locale[] defaultCategoryLocales = new Locale[Category.values().length];
    private static ULocale[] defaultCategoryULocales = new ULocale[Category.values().length];

    static {
        defaultULocale = forLocale(defaultLocale);

        if (JDKLocaleHelper.hasLocaleCategories()) {
            for (Category cat: Category.values()) {
                int idx = cat.ordinal();
                defaultCategoryLocales[idx] = JDKLocaleHelper.getDefault(cat);
                defaultCategoryULocales[idx] = forLocale(defaultCategoryLocales[idx]);
            }
        } else {
            // Android API level 21..23 does not have separate category locales,
            // use the non-category default for all.
            for (Category cat: Category.values()) {
                int idx = cat.ordinal();
                defaultCategoryLocales[idx] = defaultLocale;
                defaultCategoryULocales[idx] = defaultULocale;
            }
        }
    }

    /**
     * Returns the current default ULocale.
     * <p>
     * The default ULocale is synchronized to the default Java Locale. This method checks
     * the current default Java Locale and returns an equivalent ULocale.
     *
     * @return the default ULocale.
     * @stable ICU 2.8
     */
    public static ULocale getDefault() {
        synchronized (ULocale.class) {
            if (defaultULocale == null) {
                // When Java's default locale has extensions (such as ja-JP-u-ca-japanese),
                // Locale -> ULocale mapping requires BCP47 keyword mapping data that is currently
                // stored in a resource bundle. However, UResourceBundle currently requires
                // non-null default ULocale. For now, this implementation returns ULocale.ROOT
                // to avoid the problem.

                // TODO: Consider moving BCP47 mapping data out of resource bundle later.

                return ULocale.ROOT;
            }
            Locale currentDefault = Locale.getDefault();
            if (!defaultLocale.equals(currentDefault)) {
                defaultLocale = currentDefault;
                defaultULocale = forLocale(currentDefault);

                if (!JDKLocaleHelper.hasLocaleCategories()) {
                    // Detected Java default Locale change.
                    // We need to update category defaults to match
                    // Java 7's behavior on Android API level 21..23.
                    for (Category cat : Category.values()) {
                        int idx = cat.ordinal();
                        defaultCategoryLocales[idx] = currentDefault;
                        defaultCategoryULocales[idx] = forLocale(currentDefault);
                    }
                }            }
            return defaultULocale;
        }
    }

    /**
     * Sets the default ULocale.  This also sets the default Locale.
     * If the caller does not have write permission to the
     * user.language property, a security exception will be thrown,
     * and the default ULocale will remain unchanged.
     * <p>
     * By setting the default ULocale with this method, all of the default category locales
     * are also set to the specified default ULocale.
     * @param newLocale the new default locale
     * @throws SecurityException if a security manager exists and its
     *        <code>checkPermission</code> method doesn't allow the operation.
     * @throws NullPointerException if <code>newLocale</code> is null
     * @see SecurityManager#checkPermission(java.security.Permission)
     * @see java.util.PropertyPermission
     * @see ULocale#setDefault(Category, ULocale)
     * @stable ICU 3.0
     */
    public static synchronized void setDefault(ULocale newLocale){
        defaultLocale = newLocale.toLocale();
        Locale.setDefault(defaultLocale);
        defaultULocale = newLocale;
        // This method also updates all category default locales
        for (Category cat : Category.values()) {
            setDefault(cat, newLocale);
        }
    }

    /**
     * Returns the current default ULocale for the specified category.
     *
     * @param category the category
     * @return the default ULocale for the specified category.
     * @stable ICU 49
     */
    public static ULocale getDefault(Category category) {
        synchronized (ULocale.class) {
            int idx = category.ordinal();
            if (defaultCategoryULocales[idx] == null) {
                // Just in case this method is called during ULocale class
                // initialization. Unlike getDefault(), we do not have
                // cyclic dependency for category default.
                return ULocale.ROOT;
            }
            if (JDKLocaleHelper.hasLocaleCategories()) {
                Locale currentCategoryDefault = JDKLocaleHelper.getDefault(category);
                if (!defaultCategoryLocales[idx].equals(currentCategoryDefault)) {
                    defaultCategoryLocales[idx] = currentCategoryDefault;
                    defaultCategoryULocales[idx] = forLocale(currentCategoryDefault);
                }
            } else {
                // java.util.Locale.setDefault(Locale) in Java 7 updates
                // category locale defaults. On Android API level 21..23
                // ICU4J checks if the default locale has changed and update
                // category ULocales here if necessary.

                // Note: When java.util.Locale.setDefault(Locale) is called
                // with a Locale same with the previous one, Java 7 still
                // updates category locale defaults. On Android API level 21..23
                // there is no good way to detect the event, ICU4J simply
                // checks if the default Java Locale has changed since last
                // time.

                Locale currentDefault = Locale.getDefault();
                if (!defaultLocale.equals(currentDefault)) {
                    defaultLocale = currentDefault;
                    defaultULocale = forLocale(currentDefault);

                    for (Category cat : Category.values()) {
                        int tmpIdx = cat.ordinal();
                        defaultCategoryLocales[tmpIdx] = currentDefault;
                        defaultCategoryULocales[tmpIdx] = forLocale(currentDefault);
                    }
                }

                // No synchronization with JDK Locale, because category default
                // is not supported in Android API level 21..23.
            }
            return defaultCategoryULocales[idx];
        }
    }

    /**
     * Sets the default <code>ULocale</code> for the specified <code>Category</code>.
     * This also sets the default <code>Locale</code> for the specified <code>Category</code>
     * of the JVM. If the caller does not have write permission to the
     * user.language property, a security exception will be thrown,
     * and the default ULocale for the specified Category will remain unchanged.
     *
     * @param category the specified category to set the default locale
     * @param newLocale the new default locale
     * @see SecurityManager#checkPermission(java.security.Permission)
     * @see java.util.PropertyPermission
     * @stable ICU 49
     */
    public static synchronized void setDefault(Category category, ULocale newLocale) {
        Locale newJavaDefault = newLocale.toLocale();
        int idx = category.ordinal();
        defaultCategoryULocales[idx] = newLocale;
        defaultCategoryLocales[idx] = newJavaDefault;
        JDKLocaleHelper.setDefault(category, newJavaDefault);
    }

    /**
     * This is for compatibility with Locale-- in actuality, since ULocale is
     * immutable, there is no reason to clone it, so this API returns 'this'.
     * @stable ICU 2.8
     */
    @Override
    public Object clone() {
        return this;
    }

    /**
     * Returns the hashCode.
     * @return a hash code value for this object.
     * @stable ICU 2.8
     */
    @Override
    public int hashCode() {
        return localeID.hashCode();
    }

    /**
     * Returns true if the other object is another ULocale with the
     * same full name.
     * Note that since names are not canonicalized, two ULocales that
     * function identically might not compare equal.
     *
     * @return true if this Locale is equal to the specified object.
     * @stable ICU 2.8
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof ULocale) {
            return localeID.equals(((ULocale)obj).localeID);
        }
        return false;
    }

    /**
     * Compares two ULocale for ordering.
     * <p><b>Note:</b> The order might change in future.
     *
     * @param other the ULocale to be compared.
     * @return a negative integer, zero, or a positive integer as this ULocale is less than, equal to, or greater
     * than the specified ULocale.
     * @throws NullPointerException if <code>other</code> is null.
     *
     * @stable ICU 53
     */
    @Override
    public int compareTo(ULocale other) {
        if (this == other) {
            return 0;
        }

        int cmp = 0;

        // Language
        cmp = getLanguage().compareTo(other.getLanguage());
        if (cmp == 0) {
            // Script
            cmp = getScript().compareTo(other.getScript());
            if (cmp == 0) {
                // Region
                cmp = getCountry().compareTo(other.getCountry());
                if (cmp == 0) {
                    // Variant
                    cmp = getVariant().compareTo(other.getVariant());
                    if (cmp == 0) {
                        // Keywords
                        Iterator<String> thisKwdItr = getKeywords();
                        Iterator<String> otherKwdItr = other.getKeywords();

                        if (thisKwdItr == null) {
                            cmp = otherKwdItr == null ? 0 : -1;
                        } else if (otherKwdItr == null) {
                            cmp = 1;
                        } else {
                            // Both have keywords
                            while (cmp == 0 && thisKwdItr.hasNext()) {
                                if (!otherKwdItr.hasNext()) {
                                    cmp = 1;
                                    break;
                                }
                                // Compare keyword keys
                                String thisKey = thisKwdItr.next();
                                String otherKey = otherKwdItr.next();
                                cmp = thisKey.compareTo(otherKey);
                                if (cmp == 0) {
                                    // Compare keyword values
                                    String thisVal = getKeywordValue(thisKey);
                                    String otherVal = other.getKeywordValue(otherKey);
                                    if (thisVal == null) {
                                        cmp = otherVal == null ? 0 : -1;
                                    } else if (otherVal == null) {
                                        cmp = 1;
                                    } else {
                                        cmp = thisVal.compareTo(otherVal);
                                    }
                                }
                            }
                            if (cmp == 0 && otherKwdItr.hasNext()) {
                                cmp = -1;
                            }
                        }
                    }
                }
            }
        }

        // Normalize the result value:
        // Note: String.compareTo() may return value other than -1, 0, 1.
        // A value other than those are OK by the definition, but we don't want
        // associate any semantics other than negative/zero/positive.
        return (cmp < 0) ? -1 : ((cmp > 0) ? 1 : 0);
    }

    /**
     * {@icunote} Unlike the Locale API, this returns an array of <code>ULocale</code>,
     * not <code>Locale</code>.
     *
     * <p>Returns a list of all installed locales. This is equivalent to calling
     * {@link #getAvailableLocalesByType} with AvailableType.DEFAULT.
     *
     * @stable ICU 3.0
     */
    public static ULocale[] getAvailableLocales() {
        return ICUResourceBundle.getAvailableULocales().clone();
    }

    /**
     * Returns a list of all installed locales according to the specified type.
     *
     * @stable ICU 65
     */
    public static Collection<ULocale> getAvailableLocalesByType(AvailableType type) {
        if (type == null) {
            throw new IllegalArgumentException();
        }
        List<ULocale> result;
        if (type == ULocale.AvailableType.WITH_LEGACY_ALIASES) {
            result = new ArrayList<>();
            Collections.addAll(result,
                    ICUResourceBundle.getAvailableULocales(ULocale.AvailableType.DEFAULT));
            Collections.addAll(result,
                    ICUResourceBundle.getAvailableULocales(ULocale.AvailableType.ONLY_LEGACY_ALIASES));
        } else {
            result = Arrays.asList(ICUResourceBundle.getAvailableULocales(type));
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Returns a list of all 2-letter country codes defined in ISO 3166.
     * Can be used to create Locales.
     * @stable ICU 3.0
     */
    public static String[] getISOCountries() {
        return LocaleIDs.getISOCountries();
    }

    /**
     * Returns a list of all unique language codes defined in ISO 639.
     * They can be 2 or 3 letter codes, as defined by
     * <a href="https://www.ietf.org/rfc/bcp/bcp47.html#section-2.2.1">
     * BCP 47, section 2.2.1</a>. Can be used to create Locales.
     * [NOTE:  ISO 639 is not a stable standard-- some languages' codes have changed.
     * The list this function returns includes both the new and the old codes for the
     * languages whose codes have changed.]
     * @stable ICU 3.0
     */
    public static String[] getISOLanguages() {
        return LocaleIDs.getISOLanguages();
    }

    /**
     * Returns the language code for this locale, which will either be the empty string
     * or a lowercase ISO 639 code.
     * @see #getDisplayLanguage()
     * @see #getDisplayLanguage(ULocale)
     * @stable ICU 3.0
     */
    public String getLanguage() {
        return base().getLanguage();
    }

    /**
     * Returns the language code for the locale ID,
     * which will either be the empty string
     * or a lowercase ISO 639 code.
     * @see #getDisplayLanguage()
     * @see #getDisplayLanguage(ULocale)
     * @stable ICU 3.0
     */
    public static String getLanguage(String localeID) {
        return new LocaleIDParser(localeID).getLanguage();
    }

    /**
     * Returns the script code for this locale, which might be the empty string.
     * @see #getDisplayScript()
     * @see #getDisplayScript(ULocale)
     * @stable ICU 3.0
     */
    public String getScript() {
        return base().getScript();
    }

    /**
     * {@icu} Returns the script code for the specified locale, which might be the empty
     * string.
     * @see #getDisplayScript()
     * @see #getDisplayScript(ULocale)
     * @stable ICU 3.0
     */
    public static String getScript(String localeID) {
        return new LocaleIDParser(localeID).getScript();
    }

    /**
     * Returns the country/region code for this locale, which will either be the empty string
     * or an uppercase ISO 3166 2-letter code.
     * @see #getDisplayCountry()
     * @see #getDisplayCountry(ULocale)
     * @stable ICU 3.0
     */
    public String getCountry() {
        return base().getRegion();
    }

    /**
     * {@icu} Returns the country/region code for this locale, which will either be the empty string
     * or an uppercase ISO 3166 2-letter code.
     * @param localeID The locale identification string.
     * @see #getDisplayCountry()
     * @see #getDisplayCountry(ULocale)
     * @stable ICU 3.0
     */
    public static String getCountry(String localeID) {
        return new LocaleIDParser(localeID).getCountry();
    }

    /**
     * {@icu} Get the region to use for supplemental data lookup.
     * Uses
     * (1) any region specified by locale tag "rg"; if none then
     * (2) any unicode_region_tag in the locale ID; if none then
     * (3) if inferRegion is true, the region suggested by
     *     getLikelySubtags on the localeID.
     * If no region is found, returns empty string ""
     *
     * @param locale
     *     The locale (includes any keywords) from which
     *     to get the region to use for supplemental data.
     * @param inferRegion
     *     If true, will try to infer region from other
     *     locale elements if not found any other way.
     * @return
     *     String with region to use ("" if none found).
     * @internal ICU 57
     * @deprecated This API is ICU internal only.
     */
    @Deprecated
    public static String getRegionForSupplementalData(
                            ULocale locale, boolean inferRegion) {
        String region = locale.getKeywordValue("rg");
        if (region != null && region.length() == 6) {
            String regionUpper = AsciiUtil.toUpperString(region);
            if (regionUpper.endsWith("ZZZZ")) {
                return regionUpper.substring(0,2);
            }
        }
        region = locale.getCountry();
        if (region.length() == 0 && inferRegion) {
            ULocale maximized = addLikelySubtags(locale);
            region = maximized.getCountry();
        }
        return region;
    }

    /**
     * Returns the variant code for this locale, which might be the empty string.
     * @see #getDisplayVariant()
     * @see #getDisplayVariant(ULocale)
     * @stable ICU 3.0
     */
    public String getVariant() {
        return base().getVariant();
    }

    /**
     * {@icu} Returns the variant code for the specified locale, which might be the empty string.
     * @see #getDisplayVariant()
     * @see #getDisplayVariant(ULocale)
     * @stable ICU 3.0
     */
    public static String getVariant(String localeID) {
        return new LocaleIDParser(localeID).getVariant();
    }

    /**
     * {@icu} Returns the fallback locale for the specified locale, which might be the
     * empty string.
     * @stable ICU 3.2
     */
    public static String getFallback(String localeID) {
        return getFallbackString(getName(localeID));
    }

    /**
     * {@icu} Returns the fallback locale for this locale.  If this locale is root,
     * returns null.
     * @stable ICU 3.2
     */
    public ULocale getFallback() {
        if (localeID.length() == 0 || localeID.charAt(0) == '@') {
            return null;
        }
        return new ULocale(getFallbackString(localeID), (Locale)null);
    }

    /**
     * Returns the given (canonical) locale id minus the last part before the tags.
     */
    private static String getFallbackString(String fallback) {
        int extStart = fallback.indexOf('@');
        if (extStart == -1) {
            extStart = fallback.length();
        }
        int last = fallback.lastIndexOf('_', extStart);
        if (last == -1) {
            last = 0;
        } else {
            // truncate empty segment
            while (last > 0) {
                if (fallback.charAt(last - 1) != '_') {
                    break;
                }
                last--;
            }
        }
        return fallback.substring(0, last) + fallback.substring(extStart);
    }

    /**
     * {@icu} Returns the (normalized) base name for this locale,
     * like {@link #getName()}, but without keywords.
     *
     * @return the base name as a String.
     * @stable ICU 3.0
     */
    public String getBaseName() {
        return getBaseName(localeID);
    }

    /**
     * {@icu} Returns the (normalized) base name for the specified locale,
     * like {@link #getName(String)}, but without keywords.
     *
     * @param localeID the locale ID as a string
     * @return the base name as a String.
     * @stable ICU 3.0
     */
    public static String getBaseName(String localeID){
        if (localeID.indexOf('@') == -1) {
            return localeID;
        }
        return new LocaleIDParser(localeID).getBaseName();
    }

    /**
     * {@icu} Returns the (normalized) full name for this locale.
     *
     * @return String the full name of the localeID
     * @stable ICU 3.0
     */
    public String getName() {
        return localeID; // always normalized
    }

    /**
     * Gets the shortest length subtag's size.
     *
     * @param localeID
     * @return The size of the shortest length subtag
     **/
    private static int getShortestSubtagLength(String localeID) {
        int localeIDLength = localeID.length();
        int length = localeIDLength;
        boolean reset = true;
        int tmpLength = 0;

        for (int i = 0; i < localeIDLength; i++) {
            if (localeID.charAt(i) != '_' && localeID.charAt(i) != '-') {
                if (reset) {
                    reset = false;
                    tmpLength = 0;
                }
                tmpLength++;
            } else {
                if (tmpLength != 0 && tmpLength < length) {
                    length = tmpLength;
                }
                reset = true;
            }
        }

        return length;
    }

    /**
     * {@icu} Returns the (normalized) full name for the specified locale.
     *
     * @param localeID the localeID as a string
     * @return String the full name of the localeID
     * @stable ICU 3.0
     */
    public static String getName(String localeID){
        String tmpLocaleID = localeID;
        // Convert BCP47 id if necessary
        if (localeID != null && !localeID.contains("@") && getShortestSubtagLength(localeID) == 1) {
            if (localeID.indexOf('_') >= 0 && localeID.charAt(1) != '_' && localeID.charAt(1) != '-') {
                tmpLocaleID = localeID.replace('_', '-');
            }
            tmpLocaleID = forLanguageTag(tmpLocaleID).getName();
            if (tmpLocaleID.length() == 0) {
                tmpLocaleID = localeID;
            }
        } else if ("root".equalsIgnoreCase(localeID)) {
            tmpLocaleID = EMPTY_STRING;
        } else {
            tmpLocaleID = stripLeadingUnd(localeID);
        }
        return nameCache.getInstance(tmpLocaleID, null /* unused */);
    }

    /**
     * Strips out the leading "und" language code case-insensitively.
     *
     * @implNote Avoids creating new local non-primitive objects to reduce GC pressure.
     */
    private static String stripLeadingUnd(String localeID) {
        int length = localeID.length();
        if (length < 3) {
            return localeID;
        }

        // If not starts with "und", return.
        if (!localeID.regionMatches(/*ignoreCase=*/true, 0, "und", 0, /*len=*/3)) {
            return localeID;
        }

        // The string is equals to "und" case-insensitively.
        if (length == 3) {
            return EMPTY_STRING;
        }

        // localeID must have a length >= 4
        char separator = localeID.charAt(3);
        if (separator == '-' || separator == '_') { // "und-*" or "und_*"
            return localeID.substring(3);
        }

        return localeID;
    }

    /**
     * Returns a string representation of this object.
     * @return a string representation of the object.
     * @stable ICU 2.8
     */
    @Override
    public String toString() {
        return localeID;
    }

    /**
     * {@icu} Returns an iterator over keywords for this locale.  If there
     * are no keywords, returns null.
     * @return iterator over keywords, or null if there are no keywords.
     * @stable ICU 3.0
     */
    public Iterator<String> getKeywords() {
        return getKeywords(localeID);
    }

    /**
     * {@icu} Returns an iterator over keywords for the specified locale.  If there
     * are no keywords, returns null.
     * @return an iterator over the keywords in the specified locale, or null
     * if there are no keywords.
     * @stable ICU 3.0
     */
    public static Iterator<String> getKeywords(String localeID){
        return new LocaleIDParser(localeID).getKeywords();
    }

    /**
     * {@icu} Returns the value for a keyword in this locale. If the keyword is not
     * defined, returns null.
     * @param keywordName name of the keyword whose value is desired. Case insensitive.
     * @return the value of the keyword, or null.
     * @stable ICU 3.0
     */
    public String getKeywordValue(String keywordName){
        return getKeywordValue(localeID, keywordName);
    }

    /**
     * {@icu} Returns the value for a keyword in the specified locale. If the keyword is
     * not defined, returns null.  The locale name does not need to be normalized.
     * @param keywordName name of the keyword whose value is desired. Case insensitive.
     * @return String the value of the keyword as a string
     * @stable ICU 3.0
     */
    public static String getKeywordValue(String localeID, String keywordName) {
        return new LocaleIDParser(localeID).getKeywordValue(keywordName);
    }

    static private class AliasReplacer {
        /**
         * @param language language subtag to be replaced. Cannot be null but could be empty.
         * @param script script subtag to be replaced. Cannot be null but could be empty.
         * @param region region subtag to be replaced. Cannot be null but could be empty.
         * @param variants variant subtags to be replaced. Cannot be null but could be empty.
         * @param extensions extensions in string to be replaced. Cannot be null but could be empty.
         */
        public AliasReplacer(String language, String script, String region,
                String variants, String extensions) {

            assert language != null;
            assert script != null;
            assert region != null;
            assert variants != null;
            assert extensions != null;
            this.language = language;
            this.script = script;
            this.region = region;
            if (!variants.isEmpty()) {
                this.variants =
                    new ArrayList<>(Arrays.asList(variants.split("_")));
            }
            this.extensions = extensions;
        }

        private String language;
        private String script;
        private String region;
        private List<String> variants;
        private String extensions;

        public String replace() {
            boolean changed = false;
            loadAliasData();
            int count = 0;
            while (true) {
                if (count++ > 10) {
                    // Throw exception when we loop through too many time
                    // stop to avoid infinity loop cauesd by incorrect data
                    // in resource.
                    throw new IllegalArgumentException(
                        "Have problem to resolve locale alias of " +
                        lscvToID(language, script, region,
                            ((variants == null) ? "" : Utility.joinStrings("_", variants))) +
                        extensions);
                }
                // Anytime we replace something, we need to start over again.
                //                      lang  REGION  variant
                if (    replaceLanguage(true,  true,  true) ||
                        replaceLanguage(true,  true,  false) ||
                        replaceLanguage(true,  false, true) ||
                        replaceLanguage(true,  false, false) ||
                        replaceLanguage(false, false, true) ||
                        replaceRegion() ||
                        replaceScript() ||
                        replaceVariant()) {
                    // Some values in data is changed, try to match from the
                    // beginning again.
                    changed = true;
                    continue;
                }
                // Nothing changed in this iteration, break out the loop
                break;
            }  // while(1)
            if (extensions == null && !changed) {
                return null;
            }
            String result =  lscvToID(language, script, region,
                    ((variants == null) ? "" : Utility.joinStrings("_", variants)));
            if (extensions != null) {
                boolean keywordChanged = false;
                ULocale temp = new ULocale(result + extensions);
                Iterator<String> keywords = temp.getKeywords();
                while (keywords != null && keywords.hasNext()) {
                    String key = keywords.next();
                    if (key.equals("rg") || key.equals("sd") || key.equals("t")) {
                        String value = temp.getKeywordValue(key);
                        String replacement = key.equals("t") ?
                            replaceTransformedExtensions(value) :
                            replaceSubdivision(value);
                        if (replacement != null) {
                            temp = temp.setKeywordValue(key, replacement);
                            keywordChanged = true;
                        }
                    }
                }
                if (keywordChanged) {
                    extensions = temp.getName().substring(temp.getBaseName().length());
                    changed = true;
                }
                result += extensions;
            }
            if (changed) {
                return result;
            }
            // Nothing changed in any iteration of the loop.
            return null;
        };

        private static boolean aliasDataIsLoaded = false;
        private static Map<String, String> languageAliasMap = null;
        private static Map<String, String> scriptAliasMap = null;
        private static Map<String, List<String>> territoryAliasMap = null;
        private static Map<String, String> variantAliasMap = null;
        private static Map<String, String> subdivisionAliasMap = null;

        /*
         * Initializes the alias data from the ICU resource bundles. The alias
         * data contains alias of language, country, script and variants.
         *
         * If the alias data has already loaded, then this method simply
         * returns without doing anything meaningful.
         *
         */
        private static synchronized void loadAliasData() {
            if (aliasDataIsLoaded) {
                return;
            }
            languageAliasMap = new HashMap<>();
            scriptAliasMap = new HashMap<>();
            territoryAliasMap = new HashMap<>();
            variantAliasMap = new HashMap<>();
            subdivisionAliasMap = new HashMap<>();

            UResourceBundle metadata = UResourceBundle.getBundleInstance(
                ICUData.ICU_BASE_NAME, "metadata",
                ICUResourceBundle.ICU_DATA_CLASS_LOADER);
            UResourceBundle metadataAlias = metadata.get("alias");
            UResourceBundle languageAlias = metadataAlias.get("language");
            UResourceBundle scriptAlias = metadataAlias.get("script");
            UResourceBundle territoryAlias = metadataAlias.get("territory");
            UResourceBundle variantAlias = metadataAlias.get("variant");
            UResourceBundle subdivisionAlias = metadataAlias.get("subdivision");

            for (int i = 0 ; i < languageAlias.getSize(); i++) {
                UResourceBundle res = languageAlias.get(i);
                String aliasFrom = res.getKey();
                String aliasTo = res.get("replacement").getString();
                Locale testLocale = new Locale(aliasFrom);
                // if there are script in the aliasFrom
                // or we have both a und as language and a region code.
                if (    ! testLocale.getScript().isEmpty() ||
                        (aliasFrom.startsWith("und") && ! testLocale.getCountry().isEmpty())) {
                    throw new IllegalArgumentException(
                        "key [" + aliasFrom +
                        "] in alias:language contains unsupported fields combination.");
                }
                languageAliasMap.put(aliasFrom, aliasTo);
            }
            for (int i = 0 ; i < scriptAlias.getSize(); i++) {
                UResourceBundle res = scriptAlias.get(i);
                String aliasFrom = res.getKey();
                String aliasTo = res.get("replacement").getString();
                if (aliasFrom.length() != 4) {
                    throw new IllegalArgumentException(
                        "Incorrect key [" + aliasFrom + "] in alias:script.");
                }
                scriptAliasMap.put(aliasFrom, aliasTo);
            }
            for (int i = 0 ; i < territoryAlias.getSize(); i++) {
                UResourceBundle res = territoryAlias.get(i);
                String aliasFrom = res.getKey();
                String aliasTo = res.get("replacement").getString();
                if (aliasFrom.length() < 2 || aliasFrom.length() > 3) {
                    throw new IllegalArgumentException(
                        "Incorrect key [" + aliasFrom + "] in alias:territory.");
                }
                territoryAliasMap.put(aliasFrom,
                    new ArrayList<>(Arrays.asList(aliasTo.split(" "))));
            }
            for (int i = 0 ; i < variantAlias.getSize(); i++) {
                UResourceBundle res = variantAlias.get(i);
                String aliasFrom = res.getKey();
                String aliasTo = res.get("replacement").getString();
                if (    aliasFrom.length() < 4 ||
                        aliasFrom.length() > 8 ||
                        (aliasFrom.length() == 4 &&
                         (aliasFrom.charAt(0) < '0' || aliasFrom.charAt(0) > '9'))) {
                    throw new IllegalArgumentException(
                        "Incorrect key [" + aliasFrom + "] in alias:variant.");
                }
                if (    aliasTo.length() < 4 ||
                        aliasTo.length() > 8 ||
                        (aliasTo.length() == 4 &&
                         (aliasTo.charAt(0) < '0' || aliasTo.charAt(0) > '9'))) {
                    throw new IllegalArgumentException(
                        "Incorrect variant [" + aliasTo + "] for the key [" + aliasFrom +
                        "] in alias:variant.");
                }
                variantAliasMap.put(aliasFrom, aliasTo);
            }
            for (int i = 0 ; i < subdivisionAlias.getSize(); i++) {
                UResourceBundle res = subdivisionAlias.get(i);
                String aliasFrom = res.getKey();
                String aliasTo = res.get("replacement").getString().split(" ")[0];
                if (aliasFrom.length() < 3 || aliasFrom.length() > 8) {
                    throw new IllegalArgumentException(
                        "Incorrect key [" + aliasFrom + "] in alias:territory.");
                }
                if (aliasTo.length() == 2) {
                    // Add 'zzzz' based on changes to UTS #35 for CLDR-14312.
                    aliasTo += "zzzz";
                } else if (aliasTo.length() < 2 || aliasTo.length() > 8) {
                    throw new IllegalArgumentException(
                        "Incorrect value [" + aliasTo + "] in alias:territory.");
                }
                subdivisionAliasMap.put(aliasFrom, aliasTo);
            }

            aliasDataIsLoaded = true;
        }

        private static String generateKey(
                String language, String region, String variant) {
            assert variant == null || variant.length() >= 4;
            StringBuilder buf = new StringBuilder();
            buf.append(language);
            if (region != null && !region.isEmpty()) {
                buf.append(UNDERSCORE);
                buf.append(region);
            }
            if (variant != null && !variant.isEmpty()) {
                buf.append(UNDERSCORE);
                buf.append(variant);
            }
            return buf.toString();
        }

        /**
         * If replacement is neither null nor empty and input is either null or empty,
         * return replacement.
         * If replacement is neither null nor empty but input is not empty, return input.
         * If replacement is either null or empty and type is either null or empty,
         * return input.
         * Otherwise return null.
         *   replacement  input  type   return
         *       AAA       ""     *      AAA
         *       AAA       BBB    *      BBB
         *       ""        CCC    ""     CCC
         *       ""        *  i   DDD    ""
         */
        private static String deleteOrReplace(
                String input, String type, String replacement) {
            return (replacement != null && !replacement.isEmpty()) ?
                    ((input == null || input.isEmpty()) ? replacement : input) :
                    ((type == null || type.isEmpty()) ? input : null);
        }

        private boolean replaceLanguage(boolean checkLanguage,
                boolean checkRegion, boolean checkVariants) {
            if (    (checkRegion && (region == null || region.isEmpty())) ||
                    (checkVariants && (variants == null))) {
                // Nothing to search
                return false;
            }
            int variantSize = checkVariants ? variants.size() : 1;
            // Since we may have more than one variant, we need to loop through
            // them.
            String searchLanguage = checkLanguage ? language : UNDEFINED_LANGUAGE;
            String searchRegion = checkRegion ? region : null;
            String searchVariant = null;
            for (int variantIndex = 0; variantIndex < variantSize; ++variantIndex) {
                if (checkVariants) {
                    searchVariant = variants.get(variantIndex);
                }
                if (searchVariant != null && searchVariant.length() < 4) {
                    // Do not consider  ill-formed variant subtag.
                    searchVariant = null;
                }
                String typeKey = generateKey(
                    searchLanguage, searchRegion, searchVariant);
                String replacement = languageAliasMap.get(typeKey);
                if (replacement == null) {
                    // Found no replacement data.
                    continue;
                }
                String replacedScript = null;
                String replacedRegion = null;
                String replacedVariant = null;
                String replacedExtensions = null;
                String replacedLanguage = null;

                if (replacement.indexOf('_') < 0) {
                    replacedLanguage = replacement.equals(UNDEFINED_LANGUAGE) ?
                        language : replacement;
                } else {
                    String[] replacementFields = replacement.split("_");
                    replacedLanguage = replacementFields[0];
                    int index = 1;

                    if (replacedLanguage.equals(UNDEFINED_LANGUAGE)) {
                        replacedLanguage = language;
                    }
                    int consumed = replacementFields[0].length() + 1;
                    while (replacementFields.length > index) {
                        String field = replacementFields[index];
                        int len = field.length();
                        if (1 == len) {
                            replacedExtensions = replacement.substring(consumed);
                            break;
                        } else if (len >= 2 && len <= 3) {
                            assert replacedRegion == null;
                            replacedRegion = field;
                        } else if (len >= 5 && len <= 8) {
                            assert replacedVariant == null;
                            replacedVariant = field;
                        } else if (len == 4) {
                            if (field.charAt(0) >= '0' && field.charAt(0) <= '9') {
                                assert replacedVariant == null;
                                replacedVariant = field;
                            } else {
                                assert replacedScript == null;
                                replacedScript = field;
                            }
                        }
                        index++;
                        consumed += len + 1;
                    }
                }

                replacedScript = deleteOrReplace(script, null, replacedScript);
                replacedRegion = deleteOrReplace(region, searchRegion, replacedRegion);
                replacedVariant = deleteOrReplace(searchVariant, searchVariant, replacedVariant);

                if (    this.language.equals(replacedLanguage) &&
                        this.script.equals(replacedScript) &&
                        this.region.equals(replacedRegion) &&
                        Objects.equals(searchVariant, replacedVariant) &&
                        replacedExtensions == null) {
                    // Replacement produce no changes on search.
                    // For example, apply pa_IN=> pa_Guru_IN on pa_Guru_IN.
                    continue;
                }
                this.language = replacedLanguage;
                this.script = replacedScript;
                this.region = replacedRegion;
                if (searchVariant != null && !searchVariant.isEmpty()) {
                    if (replacedVariant != null && !replacedVariant.isEmpty()) {
                        this.variants.set(variantIndex, replacedVariant);
                    } else {
                        this.variants.remove(variantIndex);
                        if (this.variants.isEmpty()) {
                            this.variants = null;
                        }
                    }
                }
                if (replacedExtensions != null && !replacedExtensions.isEmpty()) {
                    // DO NOTHING
                    // UTS35 does not specifiy what should we do if we have extensions in the
                    // replacement. Currently we know only the following 4 "BCP47 LegacyRules" have
                    // extensions in them languageAlias:
                    //  i_default => en_x_i_default
                    //  i_enochian => und_x_i_enochian
                    //  i_mingo => see_x_i_mingo
                    //  zh_min => nan_x_zh_min
                    // But all of them are already changed by code inside LanguageTag before
                    // hitting this code.
                }
                // Something in search changed by language alias data.
                return true;
            }
            // Nothing changed in search by language alias data.
            return false;
        }

        private boolean replaceRegion() {
            if (region == null || region.isEmpty()) return false;
            List<String> replacement = territoryAliasMap.get(region);
            if (replacement == null) {
                // Found no replacement data for this region.
                return false;
            }
            String replacedRegion;
            if (replacement.size() > 1) {
                String regionOfLanguageAndScript =
                    ULocale.addLikelySubtags(
                        new ULocale(this.language, this.script, null))
                    .getCountry();
                replacedRegion = replacement.contains(regionOfLanguageAndScript) ?
                    regionOfLanguageAndScript : replacement.get(0);
            } else {
                replacedRegion = replacement.get(0);
            }
            assert !this.region.equals(replacedRegion);
            this.region = replacedRegion;
            // The region is changed by data in territory alias.
            return true;
        }

        private boolean replaceScript() {
            if (script == null || script.isEmpty()) return false;
            String replacement = scriptAliasMap.get(script);
            if (replacement == null) {
                // Found no replacement data for this script.
                return false;
            }
            assert !this.script.equals(replacement);
            this.script = replacement;
            // The script is changed by data in script alias.
            return true;
        }

        private boolean replaceVariant() {
            if (variants == null) return false;
            for (int i = 0; i < variants.size(); i++) {
                 String variant = variants.get(i);
                 String replacement = variantAliasMap.get(variant);
                 if (replacement == null) {
                     // Found no replacement data for this variant.
                     continue;
                 }
                 assert replacement.length() >= 4;
                 assert replacement.length() <= 8;
                 assert replacement.length() != 4 ||
                     ( replacement.charAt(0) >= '0' && replacement.charAt(0) <= '9');
                 if (!variant.equals(replacement)) {
                     variants.set(i, replacement);
                     // Special hack to handle hepburn-heploc => alalc97
                     if (variant.equals("heploc")) {
                         variants.remove("hepburn");
                         if (variants.isEmpty()) {
                             variants = null;
                         }
                     }
                     return true;
                 }
            }
            return false;
        }

        private String replaceSubdivision(String subdivision) {
            return subdivisionAliasMap.get(subdivision);
        }

        private String replaceTransformedExtensions(String extensions) {
            StringBuilder builder = new StringBuilder();
            List<String> subtags = new ArrayList<>(Arrays.asList(extensions.split(LanguageTag.SEP)));
            List<String> tfields = new ArrayList<>();
            int processedLength = 0;
            int tlangLength = 0;
            String tkey = "";
            for (String subtag : subtags) {
                if (LanguageTag.isTKey(subtag)) {
                    if (tlangLength == 0) {
                        // Found the first tkey. Record the total length of the preceding
                        // tlang subtags. -1 if there is no tlang before the first tkey.
                        tlangLength = processedLength-1;
                    }
                    if (builder.length() > 0) {
                        // Finish & store the previous tkey with its tvalue subtags.
                        tfields.add(builder.toString());
                        builder.setLength(0);
                    }
                    // Start collecting subtags for this new tkey.
                    tkey = subtag;
                    builder.append(subtag);
                } else {
                    if (tlangLength != 0) {
                        builder.append(LanguageTag.SEP).append(toUnicodeLocaleType(tkey, subtag));
                    }
                }
                processedLength += subtag.length() + 1;
            }
            if (builder.length() > 0) {
                // Finish & store the previous=last tkey with its tvalue subtags.
                tfields.add(builder.toString());
                builder.setLength(0);
            }
            String tlang = (tlangLength > 0) ? extensions.substring(0, tlangLength) :
                ((tfields.size() == 0) ? extensions :  "");
            if (tlang.length() > 0) {
                String canonicalized = ULocale.createCanonical(
                    ULocale.forLanguageTag(extensions)).toLanguageTag();
                builder.append(AsciiUtil.toLowerString(canonicalized));
            }

            if (tfields.size() > 0) {
                if (builder.length() > 0) {
                    builder.append(LanguageTag.SEP);
                }
                // tfields are sorted by alphabetical order of their keys
                Collections.sort(tfields);
                builder.append(Utility.joinStrings(LanguageTag.SEP, tfields));
            }
            return builder.toString();
        }
    };

    /**
     * {@icu} Returns the canonical name according to CLDR for the specified locale ID.
     * This is used to convert POSIX and other legacy IDs to standard ICU form.
     * @param localeID the locale id
     * @return the canonicalized id
     * @stable ICU 3.0
     */
    public static String canonicalize(String localeID){
        LocaleIDParser parser = new LocaleIDParser(localeID, true);
        String baseName = parser.getBaseName();
        boolean foundVariant = false;

        if (localeID.equals("")) {
            return "";
        }

        // we have an ID in the form xx_Yyyy_ZZ_KKKKK

        /* See if this is an already known locale */
        for (int i = 0; i < CANONICALIZE_MAP.length; i++) {
            String[] vals = CANONICALIZE_MAP[i];
            if (vals[0].equals(baseName)) {
                foundVariant = true;

                parser.setBaseName(vals[1]);
                break;
            }
        }

        /* total mondo hack for Norwegian, fortunately the main NY case is handled earlier */
        if (!foundVariant) {
            if (parser.getLanguage().equals("nb") && parser.getVariant().equals("NY")) {
                parser.setBaseName(lscvToID("nn", parser.getScript(), parser.getCountry(), null));
            }
        }

        String name = parser.getName();
        if (!isKnownCanonicalizedLocale(name)) {
            AliasReplacer replacer = new AliasReplacer(
                parser.getLanguage(), parser.getScript(), parser.getCountry(),
                AsciiUtil.toLowerString(parser.getVariant()),
                parser.getName().substring(parser.getBaseName().length()));
            String replaced = replacer.replace();
            if (replaced != null) {
                parser =  new LocaleIDParser(replaced);
            }
        }

        return parser.getName();
    }

    private static synchronized boolean isKnownCanonicalizedLocale(String name) {
        if (name.equals("c") || name.equals("en") || name.equals("en_US")) {
            return true;
        }
        if (gKnownCanonicalizedCases == null) {
            List<String> items = Arrays.asList(
                "af", "af_ZA", "am", "am_ET", "ar", "ar_001", "as", "as_IN", "az", "az_AZ",
                "be", "be_BY", "bg", "bg_BG", "bn", "bn_IN", "bs", "bs_BA", "ca", "ca_ES",
                "cs", "cs_CZ", "cy", "cy_GB", "da", "da_DK", "de", "de_DE", "el", "el_GR",
                "en", "en_GB", "en_US", "es", "es_419", "es_ES", "et", "et_EE", "eu",
                "eu_ES", "fa", "fa_IR", "fi", "fi_FI", "fil", "fil_PH", "fr", "fr_FR",
                "ga", "ga_IE", "gl", "gl_ES", "gu", "gu_IN", "he", "he_IL", "hi", "hi_IN",
                "hr", "hr_HR", "hu", "hu_HU", "hy", "hy_AM", "id", "id_ID", "is", "is_IS",
                "it", "it_IT", "ja", "ja_JP", "jv", "jv_ID", "ka", "ka_GE", "kk", "kk_KZ",
                "km", "km_KH", "kn", "kn_IN", "ko", "ko_KR", "ky", "ky_KG", "lo", "lo_LA",
                "lt", "lt_LT", "lv", "lv_LV", "mk", "mk_MK", "ml", "ml_IN", "mn", "mn_MN",
                "mr", "mr_IN", "ms", "ms_MY", "my", "my_MM", "nb", "nb_NO", "ne", "ne_NP",
                "nl", "nl_NL", "no", "or", "or_IN", "pa", "pa_IN", "pl", "pl_PL", "ps", "ps_AF",
                "pt", "pt_BR", "pt_PT", "ro", "ro_RO", "ru", "ru_RU", "sd", "sd_IN", "si",
                "si_LK", "sk", "sk_SK", "sl", "sl_SI", "so", "so_SO", "sq", "sq_AL", "sr",
                "sr_Cyrl_RS", "sr_Latn", "sr_RS", "sv", "sv_SE", "sw", "sw_TZ", "ta",
                "ta_IN", "te", "te_IN", "th", "th_TH", "tk", "tk_TM", "tr", "tr_TR", "uk",
                "uk_UA", "ur", "ur_PK", "uz", "uz_UZ", "vi", "vi_VN", "yue", "yue_Hant",
                "yue_Hant_HK", "yue_HK", "zh", "zh_CN", "zh_Hans", "zh_Hans_CN", "zh_Hant",
                "zh_Hant_TW", "zh_TW", "zu", "zu_ZA");
            gKnownCanonicalizedCases = new HashSet<>(items);

        }
        return gKnownCanonicalizedCases.contains(name);
    }

    private static Set<String> gKnownCanonicalizedCases = null;

    /**
     * {@icu} Given a keyword and a value, return a new locale with an updated
     * keyword and value.  If the keyword is null, this removes all keywords from the locale id.
     * Otherwise, if the value is null, this removes the value for this keyword from the
     * locale id.  Otherwise, this adds/replaces the value for this keyword in the locale id.
     * The keyword and value must not be empty.
     *
     * <p>Related: {@link #getBaseName()} returns the locale ID string with all keywords removed.
     *
     * @param keyword the keyword to add/remove, or null to remove all keywords.
     * @param value the value to add/set, or null to remove this particular keyword.
     * @return the updated locale
     * @stable ICU 3.2
     */
    public ULocale setKeywordValue(String keyword, String value) {
        return new ULocale(setKeywordValue(localeID, keyword, value), (Locale)null);
    }

    /**
     * Given a locale id, a keyword, and a value, return a new locale id with an updated
     * keyword and value.  If the keyword is null, this removes all keywords from the locale id.
     * Otherwise, if the value is null, this removes the value for this keyword from the
     * locale id.  Otherwise, this adds/replaces the value for this keyword in the locale id.
     * The keyword and value must not be empty.
     *
     * <p>Related: {@link #getBaseName(String)} returns the locale ID string with all keywords removed.
     *
     * @param localeID the locale id to modify
     * @param keyword the keyword to add/remove, or null to remove all keywords.
     * @param value the value to add/set, or null to remove this particular keyword.
     * @return the updated locale id
     * @stable ICU 3.2
     */
    public static String setKeywordValue(String localeID, String keyword, String value) {
        LocaleIDParser parser = new LocaleIDParser(localeID);
        parser.setKeywordValue(keyword, value);
        return parser.getName();
    }

    /*
     * Given a locale id, a keyword, and a value, return a new locale id with an updated
     * keyword and value, if the keyword does not already have a value.  The keyword and
     * value must not be null or empty.
     * @param localeID the locale id to modify
     * @param keyword the keyword to add, if not already present
     * @param value the value to add, if not already present
     * @return the updated locale id
     */
    /*    private static String defaultKeywordValue(String localeID, String keyword, String value) {
        LocaleIDParser parser = new LocaleIDParser(localeID);
        parser.defaultKeywordValue(keyword, value);
        return parser.getName();
    }*/

    /**
     * Returns a three-letter abbreviation for this locale's language.  If the locale
     * doesn't specify a language, returns the empty string.  Otherwise, returns
     * a lowercase ISO 639-2/T language code.
     * The ISO 639-2 language codes can be found on-line at
     *   <a href="ftp://dkuug.dk/i18n/iso-639-2.txt"><code>ftp://dkuug.dk/i18n/iso-639-2.txt</code></a>
     * @exception MissingResourceException Throws MissingResourceException if the
     * three-letter language abbreviation is not available for this locale.
     * @stable ICU 3.0
     */
    public String getISO3Language(){
        return getISO3Language(localeID);
    }

    /**
     * {@icu} Returns a three-letter abbreviation for this locale's language.  If the locale
     * doesn't specify a language, returns the empty string.  Otherwise, returns
     * a lowercase ISO 639-2/T language code.
     * The ISO 639-2 language codes can be found on-line at
     *   <a href="ftp://dkuug.dk/i18n/iso-639-2.txt"><code>ftp://dkuug.dk/i18n/iso-639-2.txt</code></a>
     * @exception MissingResourceException Throws MissingResourceException if the
     * three-letter language abbreviation is not available for this locale.
     * @stable ICU 3.0
     */
    public static String getISO3Language(String localeID) {
        return LocaleIDs.getISO3Language(getLanguage(localeID));
    }

    /**
     * Returns a three-letter abbreviation for this locale's country/region.  If the locale
     * doesn't specify a country, returns the empty string.  Otherwise, returns
     * an uppercase ISO 3166 3-letter country code.
     * @exception MissingResourceException Throws MissingResourceException if the
     * three-letter country abbreviation is not available for this locale.
     * @stable ICU 3.0
     */
    public String getISO3Country() {
        return getISO3Country(localeID);
    }

    /**
     * {@icu} Returns a three-letter abbreviation for this locale's country/region.  If the locale
     * doesn't specify a country, returns the empty string.  Otherwise, returns
     * an uppercase ISO 3166 3-letter country code.
     * @exception MissingResourceException Throws MissingResourceException if the
     * three-letter country abbreviation is not available for this locale.
     * @stable ICU 3.0
     */
    public static String getISO3Country(String localeID) {
        return LocaleIDs.getISO3Country(getCountry(localeID));
    }

    /**
     * Pairs of (language subtag, + or -) for finding out fast if common languages
     * are LTR (minus) or RTL (plus).
     */
    private static final String LANG_DIR_STRING =
            "root-en-es-pt-zh-ja-ko-de-fr-it-ar+he+fa+ru-nl-pl-th-tr-";

    /**
     * {@icu} Returns whether this locale's script is written right-to-left.
     * If there is no script subtag, then the likely script is used,
     * see {@link #addLikelySubtags(ULocale)}.
     * If no likely script is known, then false is returned.
     *
     * <p>A script is right-to-left according to the CLDR script metadata
     * which corresponds to whether the script's letters have Bidi_Class=R or AL.
     *
     * <p>Returns true for "ar" and "en-Hebr", false for "zh" and "fa-Cyrl".
     *
     * @return true if the locale's script is written right-to-left
     * @stable ICU 54
     */
    public boolean isRightToLeft() {
        String script = getScript();
        if (script.length() == 0) {
            // Fastpath: We know the likely scripts and their writing direction
            // for some common languages.
            String lang = getLanguage();
            if (!lang.isEmpty()) {
                int langIndex = LANG_DIR_STRING.indexOf(lang);
                if (langIndex >= 0) {
                    switch (LANG_DIR_STRING.charAt(langIndex + lang.length())) {
                    case '-': return false;
                    case '+': return true;
                    default: break;  // partial match of a longer code
                    }
                }
            }
            // Otherwise, find the likely script.
            ULocale likely = addLikelySubtags(this);
            script = likely.getScript();
            if (script.length() == 0) {
                return false;
            }
        }
        int scriptCode = UScript.getCodeFromName(script);
        return UScript.isRightToLeft(scriptCode);
    }

    // display names

    /**
     * Returns this locale's language localized for display in the default <code>DISPLAY</code> locale.
     * @return the localized language name.
     * @see Category#DISPLAY
     * @stable ICU 3.0
     */
    public String getDisplayLanguage() {
        return getDisplayLanguageInternal(this, getDefault(Category.DISPLAY), false);
    }

    /**
     * Returns this locale's language localized for display in the provided locale.
     * @param displayLocale the locale in which to display the name.
     * @return the localized language name.
     * @stable ICU 3.0
     */
    public String getDisplayLanguage(ULocale displayLocale) {
        return getDisplayLanguageInternal(this, displayLocale, false);
    }

    /**
     * {@icu} Returns a locale's language localized for display in the provided locale.
     * This is a cover for the ICU4C API.
     * @param localeID the id of the locale whose language will be displayed
     * @param displayLocaleID the id of the locale in which to display the name.
     * @return the localized language name.
     * @stable ICU 3.0
     */
    public static String getDisplayLanguage(String localeID, String displayLocaleID) {
        return getDisplayLanguageInternal(new ULocale(localeID), new ULocale(displayLocaleID),
                false);
    }

    /**
     * {@icu} Returns a locale's language localized for display in the provided locale.
     * This is a cover for the ICU4C API.
     * @param localeID the id of the locale whose language will be displayed.
     * @param displayLocale the locale in which to display the name.
     * @return the localized language name.
     * @stable ICU 3.0
     */
    public static String getDisplayLanguage(String localeID, ULocale displayLocale) {
        return getDisplayLanguageInternal(new ULocale(localeID), displayLocale, false);
    }
    /**
     * {@icu} Returns this locale's language localized for display in the default <code>DISPLAY</code> locale.
     * If a dialect name is present in the data, then it is returned.
     * @return the localized language name.
     * @see Category#DISPLAY
     * @stable ICU 4.4
     */
    public String getDisplayLanguageWithDialect() {
        return getDisplayLanguageInternal(this, getDefault(Category.DISPLAY), true);
    }

    /**
     * {@icu} Returns this locale's language localized for display in the provided locale.
     * If a dialect name is present in the data, then it is returned.
     * @param displayLocale the locale in which to display the name.
     * @return the localized language name.
     * @stable ICU 4.4
     */
    public String getDisplayLanguageWithDialect(ULocale displayLocale) {
        return getDisplayLanguageInternal(this, displayLocale, true);
    }

    /**
     * {@icu} Returns a locale's language localized for display in the provided locale.
     * If a dialect name is present in the data, then it is returned.
     * This is a cover for the ICU4C API.
     * @param localeID the id of the locale whose language will be displayed
     * @param displayLocaleID the id of the locale in which to display the name.
     * @return the localized language name.
     * @stable ICU 4.4
     */
    public static String getDisplayLanguageWithDialect(String localeID, String displayLocaleID) {
        return getDisplayLanguageInternal(new ULocale(localeID), new ULocale(displayLocaleID),
                true);
    }

    /**
     * {@icu} Returns a locale's language localized for display in the provided locale.
     * If a dialect name is present in the data, then it is returned.
     * This is a cover for the ICU4C API.
     * @param localeID the id of the locale whose language will be displayed.
     * @param displayLocale the locale in which to display the name.
     * @return the localized language name.
     * @stable ICU 4.4
     */
    public static String getDisplayLanguageWithDialect(String localeID, ULocale displayLocale) {
        return getDisplayLanguageInternal(new ULocale(localeID), displayLocale, true);
    }

    private static String getDisplayLanguageInternal(ULocale locale, ULocale displayLocale,
            boolean useDialect) {
        String lang = useDialect ? locale.getBaseName() : locale.getLanguage();
        return LocaleDisplayNames.getInstance(displayLocale).languageDisplayName(lang);
    }

    /**
     * Returns this locale's script localized for display in the default <code>DISPLAY</code> locale.
     * @return the localized script name.
     * @see Category#DISPLAY
     * @stable ICU 3.0
     */
    public String getDisplayScript() {
        return getDisplayScriptInternal(this, getDefault(Category.DISPLAY));
    }

    /**
     * {@icu} Returns this locale's script localized for display in the default <code>DISPLAY</code> locale.
     * @return the localized script name.
     * @see Category#DISPLAY
     * @internal
     * @deprecated This API is ICU internal only.
     */
    @Deprecated
    public String getDisplayScriptInContext() {
        return getDisplayScriptInContextInternal(this, getDefault(Category.DISPLAY));
    }

    /**
     * Returns this locale's script localized for display in the provided locale.
     * @param displayLocale the locale in which to display the name.
     * @return the localized script name.
     * @stable ICU 3.0
     */
    public String getDisplayScript(ULocale displayLocale) {
        return getDisplayScriptInternal(this, displayLocale);
    }

    /**
     * {@icu} Returns this locale's script localized for display in the provided locale.
     * @param displayLocale the locale in which to display the name.
     * @return the localized script name.
     * @internal
     * @deprecated This API is ICU internal only.
     */
    @Deprecated
    public String getDisplayScriptInContext(ULocale displayLocale) {
        return getDisplayScriptInContextInternal(this, displayLocale);
    }

    /**
     * {@icu} Returns a locale's script localized for display in the provided locale.
     * This is a cover for the ICU4C API.
     * @param localeID the id of the locale whose script will be displayed
     * @param displayLocaleID the id of the locale in which to display the name.
     * @return the localized script name.
     * @stable ICU 3.0
     */
    public static String getDisplayScript(String localeID, String displayLocaleID) {
        return getDisplayScriptInternal(new ULocale(localeID), new ULocale(displayLocaleID));
    }
    /**
     * {@icu} Returns a locale's script localized for display in the provided locale.
     * This is a cover for the ICU4C API.
     * @param localeID the id of the locale whose script will be displayed
     * @param displayLocaleID the id of the locale in which to display the name.
     * @return the localized script name.
     * @internal
     * @deprecated This API is ICU internal only.
     */
    @Deprecated
    public static String getDisplayScriptInContext(String localeID, String displayLocaleID) {
        return getDisplayScriptInContextInternal(new ULocale(localeID), new ULocale(displayLocaleID));
    }

    /**
     * {@icu} Returns a locale's script localized for display in the provided locale.
     * @param localeID the id of the locale whose script will be displayed.
     * @param displayLocale the locale in which to display the name.
     * @return the localized script name.
     * @stable ICU 3.0
     */
    public static String getDisplayScript(String localeID, ULocale displayLocale) {
        return getDisplayScriptInternal(new ULocale(localeID), displayLocale);
    }
    /**
     * {@icu} Returns a locale's script localized for display in the provided locale.
     * @param localeID the id of the locale whose script will be displayed.
     * @param displayLocale the locale in which to display the name.
     * @return the localized script name.
     * @internal
     * @deprecated This API is ICU internal only.
     */
    @Deprecated
    public static String getDisplayScriptInContext(String localeID, ULocale displayLocale) {
        return getDisplayScriptInContextInternal(new ULocale(localeID), displayLocale);
    }

    // displayLocaleID is canonical, localeID need not be since parsing will fix this.
    private static String getDisplayScriptInternal(ULocale locale, ULocale displayLocale) {
        return LocaleDisplayNames.getInstance(displayLocale)
                .scriptDisplayName(locale.getScript());
    }

    private static String getDisplayScriptInContextInternal(ULocale locale, ULocale displayLocale) {
        return LocaleDisplayNames.getInstance(displayLocale)
                .scriptDisplayNameInContext(locale.getScript());
    }

    /**
     * Returns this locale's country localized for display in the default <code>DISPLAY</code> locale.
     * <b>Warning: </b>this is for the region part of a valid locale ID; it cannot just be the region code (like "FR").
     * To get the display name for a region alone, or for other options, use {@link LocaleDisplayNames} instead.
     * @return the localized country name.
     * @see Category#DISPLAY
     * @stable ICU 3.0
     */
    public String getDisplayCountry() {
        return getDisplayCountryInternal(this, getDefault(Category.DISPLAY));
    }

    /**
     * Returns this locale's country localized for display in the provided locale.
     * <b>Warning: </b>this is for the region part of a valid locale ID; it cannot just be the region code (like "FR").
     * To get the display name for a region alone, or for other options, use {@link LocaleDisplayNames} instead.
     * @param displayLocale the locale in which to display the name.
     * @return the localized country name.
     * @stable ICU 3.0
     */
    public String getDisplayCountry(ULocale displayLocale){
        return getDisplayCountryInternal(this, displayLocale);
    }

    /**
     * {@icu} Returns a locale's country localized for display in the provided locale.
     * <b>Warning: </b>this is for the region part of a valid locale ID; it cannot just be the region code (like "FR").
     * To get the display name for a region alone, or for other options, use {@link LocaleDisplayNames} instead.
     * This is a cover for the ICU4C API.
     * @param localeID the id of the locale whose country will be displayed
     * @param displayLocaleID the id of the locale in which to display the name.
     * @return the localized country name.
     * @stable ICU 3.0
     */
    public static String getDisplayCountry(String localeID, String displayLocaleID) {
        return getDisplayCountryInternal(new ULocale(localeID), new ULocale(displayLocaleID));
    }

    /**
     * {@icu} Returns a locale's country localized for display in the provided locale.
     * <b>Warning: </b>this is for the region part of a valid locale ID; it cannot just be the region code (like "FR").
     * To get the display name for a region alone, or for other options, use {@link LocaleDisplayNames} instead.
     * This is a cover for the ICU4C API.
     * @param localeID the id of the locale whose country will be displayed.
     * @param displayLocale the locale in which to display the name.
     * @return the localized country name.
     * @stable ICU 3.0
     */
    public static String getDisplayCountry(String localeID, ULocale displayLocale) {
        return getDisplayCountryInternal(new ULocale(localeID), displayLocale);
    }

    // displayLocaleID is canonical, localeID need not be since parsing will fix this.
    private static String getDisplayCountryInternal(ULocale locale, ULocale displayLocale) {
        return LocaleDisplayNames.getInstance(displayLocale)
                .regionDisplayName(locale.getCountry());
    }

    /**
     * Returns this locale's variant localized for display in the default <code>DISPLAY</code> locale.
     * @return the localized variant name.
     * @see Category#DISPLAY
     * @stable ICU 3.0
     */
    public String getDisplayVariant() {
        return getDisplayVariantInternal(this, getDefault(Category.DISPLAY));
    }

    /**
     * Returns this locale's variant localized for display in the provided locale.
     * @param displayLocale the locale in which to display the name.
     * @return the localized variant name.
     * @stable ICU 3.0
     */
    public String getDisplayVariant(ULocale displayLocale) {
        return getDisplayVariantInternal(this, displayLocale);
    }

    /**
     * {@icu} Returns a locale's variant localized for display in the provided locale.
     * This is a cover for the ICU4C API.
     * @param localeID the id of the locale whose variant will be displayed
     * @param displayLocaleID the id of the locale in which to display the name.
     * @return the localized variant name.
     * @stable ICU 3.0
     */
    public static String getDisplayVariant(String localeID, String displayLocaleID){
        return getDisplayVariantInternal(new ULocale(localeID), new ULocale(displayLocaleID));
    }

    /**
     * {@icu} Returns a locale's variant localized for display in the provided locale.
     * This is a cover for the ICU4C API.
     * @param localeID the id of the locale whose variant will be displayed.
     * @param displayLocale the locale in which to display the name.
     * @return the localized variant name.
     * @stable ICU 3.0
     */
    public static String getDisplayVariant(String localeID, ULocale displayLocale) {
        return getDisplayVariantInternal(new ULocale(localeID), displayLocale);
    }

    private static String getDisplayVariantInternal(ULocale locale, ULocale displayLocale) {
        return LocaleDisplayNames.getInstance(displayLocale)
                .variantDisplayName(locale.getVariant());
    }

    /**
     * {@icu} Returns a keyword localized for display in the default <code>DISPLAY</code> locale.
     * @param keyword the keyword to be displayed.
     * @return the localized keyword name.
     * @see #getKeywords()
     * @see Category#DISPLAY
     * @stable ICU 3.0
     */
    public static String getDisplayKeyword(String keyword) {
        return getDisplayKeywordInternal(keyword, getDefault(Category.DISPLAY));
    }

    /**
     * {@icu} Returns a keyword localized for display in the specified locale.
     * @param keyword the keyword to be displayed.
     * @param displayLocaleID the id of the locale in which to display the keyword.
     * @return the localized keyword name.
     * @see #getKeywords(String)
     * @stable ICU 3.0
     */
    public static String getDisplayKeyword(String keyword, String displayLocaleID) {
        return getDisplayKeywordInternal(keyword, new ULocale(displayLocaleID));
    }

    /**
     * {@icu} Returns a keyword localized for display in the specified locale.
     * @param keyword the keyword to be displayed.
     * @param displayLocale the locale in which to display the keyword.
     * @return the localized keyword name.
     * @see #getKeywords(String)
     * @stable ICU 3.0
     */
    public static String getDisplayKeyword(String keyword, ULocale displayLocale) {
        return getDisplayKeywordInternal(keyword, displayLocale);
    }

    private static String getDisplayKeywordInternal(String keyword, ULocale displayLocale) {
        return LocaleDisplayNames.getInstance(displayLocale).keyDisplayName(keyword);
    }

    /**
     * {@icu} Returns a keyword value localized for display in the default <code>DISPLAY</code> locale.
     * @param keyword the keyword whose value is to be displayed.
     * @return the localized value name.
     * @see Category#DISPLAY
     * @stable ICU 3.0
     */
    public String getDisplayKeywordValue(String keyword) {
        return getDisplayKeywordValueInternal(this, keyword, getDefault(Category.DISPLAY));
    }

    /**
     * {@icu} Returns a keyword value localized for display in the specified locale.
     * @param keyword the keyword whose value is to be displayed.
     * @param displayLocale the locale in which to display the value.
     * @return the localized value name.
     * @stable ICU 3.0
     */
    public String getDisplayKeywordValue(String keyword, ULocale displayLocale) {
        return getDisplayKeywordValueInternal(this, keyword, displayLocale);
    }

    /**
     * {@icu} Returns a keyword value localized for display in the specified locale.
     * This is a cover for the ICU4C API.
     * @param localeID the id of the locale whose keyword value is to be displayed.
     * @param keyword the keyword whose value is to be displayed.
     * @param displayLocaleID the id of the locale in which to display the value.
     * @return the localized value name.
     * @stable ICU 3.0
     */
    public static String getDisplayKeywordValue(String localeID, String keyword,
            String displayLocaleID) {
        return getDisplayKeywordValueInternal(new ULocale(localeID), keyword,
                new ULocale(displayLocaleID));
    }

    /**
     * {@icu} Returns a keyword value localized for display in the specified locale.
     * This is a cover for the ICU4C API.
     * @param localeID the id of the locale whose keyword value is to be displayed.
     * @param keyword the keyword whose value is to be displayed.
     * @param displayLocale the id of the locale in which to display the value.
     * @return the localized value name.
     * @stable ICU 3.0
     */
    public static String getDisplayKeywordValue(String localeID, String keyword,
            ULocale displayLocale) {
        return getDisplayKeywordValueInternal(new ULocale(localeID), keyword, displayLocale);
    }

    // displayLocaleID is canonical, localeID need not be since parsing will fix this.
    private static String getDisplayKeywordValueInternal(ULocale locale, String keyword,
            ULocale displayLocale) {
        keyword = AsciiUtil.toLowerString(keyword.trim());
        String value = locale.getKeywordValue(keyword);
        return LocaleDisplayNames.getInstance(displayLocale).keyValueDisplayName(keyword, value);
    }

    /**
     * Returns this locale name localized for display in the default <code>DISPLAY</code> locale.
     * @return the localized locale name.
     * @see Category#DISPLAY
     * @stable ICU 3.0
     */
    public String getDisplayName() {
        return getDisplayNameInternal(this, getDefault(Category.DISPLAY));
    }

    /**
     * Returns this locale name localized for display in the provided locale.
     * @param displayLocale the locale in which to display the locale name.
     * @return the localized locale name.
     * @stable ICU 3.0
     */
    public String getDisplayName(ULocale displayLocale) {
        return getDisplayNameInternal(this, displayLocale);
    }

    /**
     * {@icu} Returns the locale ID localized for display in the provided locale.
     * This is a cover for the ICU4C API.
     * @param localeID the locale whose name is to be displayed.
     * @param displayLocaleID the id of the locale in which to display the locale name.
     * @return the localized locale name.
     * @stable ICU 3.0
     */
    public static String getDisplayName(String localeID, String displayLocaleID) {
        return getDisplayNameInternal(new ULocale(localeID), new ULocale(displayLocaleID));
    }

    /**
     * {@icu} Returns the locale ID localized for display in the provided locale.
     * This is a cover for the ICU4C API.
     * @param localeID the locale whose name is to be displayed.
     * @param displayLocale the locale in which to display the locale name.
     * @return the localized locale name.
     * @stable ICU 3.0
     */
    public static String getDisplayName(String localeID, ULocale displayLocale) {
        return getDisplayNameInternal(new ULocale(localeID), displayLocale);
    }

    private static String getDisplayNameInternal(ULocale locale, ULocale displayLocale) {
        return LocaleDisplayNames.getInstance(displayLocale).localeDisplayName(locale);
    }

    /**
     * {@icu} Returns this locale name localized for display in the default <code>DISPLAY</code> locale.
     * If a dialect name is present in the locale data, then it is returned.
     * @return the localized locale name.
     * @see Category#DISPLAY
     * @stable ICU 4.4
     */
    public String getDisplayNameWithDialect() {
        return getDisplayNameWithDialectInternal(this, getDefault(Category.DISPLAY));
    }

    /**
     * {@icu} Returns this locale name localized for display in the provided locale.
     * If a dialect name is present in the locale data, then it is returned.
     * @param displayLocale the locale in which to display the locale name.
     * @return the localized locale name.
     * @stable ICU 4.4
     */
    public String getDisplayNameWithDialect(ULocale displayLocale) {
        return getDisplayNameWithDialectInternal(this, displayLocale);
    }

    /**
     * {@icu} Returns the locale ID localized for display in the provided locale.
     * If a dialect name is present in the locale data, then it is returned.
     * This is a cover for the ICU4C API.
     * @param localeID the locale whose name is to be displayed.
     * @param displayLocaleID the id of the locale in which to display the locale name.
     * @return the localized locale name.
     * @stable ICU 4.4
     */
    public static String getDisplayNameWithDialect(String localeID, String displayLocaleID) {
        return getDisplayNameWithDialectInternal(new ULocale(localeID),
                new ULocale(displayLocaleID));
    }

    /**
     * {@icu} Returns the locale ID localized for display in the provided locale.
     * If a dialect name is present in the locale data, then it is returned.
     * This is a cover for the ICU4C API.
     * @param localeID the locale whose name is to be displayed.
     * @param displayLocale the locale in which to display the locale name.
     * @return the localized locale name.
     * @stable ICU 4.4
     */
    public static String getDisplayNameWithDialect(String localeID, ULocale displayLocale) {
        return getDisplayNameWithDialectInternal(new ULocale(localeID), displayLocale);
    }

    private static String getDisplayNameWithDialectInternal(ULocale locale, ULocale displayLocale) {
        return LocaleDisplayNames.getInstance(displayLocale, DialectHandling.DIALECT_NAMES)
                .localeDisplayName(locale);
    }

    /**
     * {@icu} Returns this locale's layout orientation for characters.  The possible
     * values are "left-to-right", "right-to-left", "top-to-bottom" or
     * "bottom-to-top".
     * @return The locale's layout orientation for characters.
     * @stable ICU 4.0
     */
    public String getCharacterOrientation() {
        return ICUResourceTableAccess.getTableString(ICUData.ICU_BASE_NAME, this,
                "layout", "characters", "characters");
    }

    /**
     * {@icu} Returns this locale's layout orientation for lines.  The possible
     * values are "left-to-right", "right-to-left", "top-to-bottom" or
     * "bottom-to-top".
     * @return The locale's layout orientation for lines.
     * @stable ICU 4.0
     */
    public String getLineOrientation() {
        return ICUResourceTableAccess.getTableString(ICUData.ICU_BASE_NAME, this,
                "layout", "lines", "lines");
    }

    /**
     * {@icu} Selector for <tt>getLocale()</tt> indicating the locale of the
     * resource containing the data.  This is always at or above the
     * valid locale.  If the valid locale does not contain the
     * specific data being requested, then the actual locale will be
     * above the valid locale.  If the object was not constructed from
     * locale data, then the valid locale is <i>null</i>.
     *
     * @draft ICU 2.8 (retain)
     */
    public static Type ACTUAL_LOCALE = new Type();

    /**
     * {@icu} Selector for <tt>getLocale()</tt> indicating the most specific
     * locale for which any data exists.  This is always at or above
     * the requested locale, and at or below the actual locale.  If
     * the requested locale does not correspond to any resource data,
     * then the valid locale will be above the requested locale.  If
     * the object was not constructed from locale data, then the
     * actual locale is <i>null</i>.
     *
     * <p>Note: The valid locale will be returned correctly in ICU
     * 3.0 or later.  In ICU 2.8, it is not returned correctly.
     * @draft ICU 2.8 (retain)
     */
    public static Type VALID_LOCALE = new Type();

    /**
     * Opaque selector enum for <tt>getLocale()</tt>.
     * @see jdk.internal.icu.util.ULocale
     * @see jdk.internal.icu.util.ULocale#ACTUAL_LOCALE
     * @see jdk.internal.icu.util.ULocale#VALID_LOCALE
     * @draft ICU 2.8 (retainAll)
     */
    public static final class Type {
        private Type() {}
    }

    /**
     * {@icu} Based on a HTTP formatted list of acceptable locales, determine an available
     * locale for the user.  NullPointerException is thrown if acceptLanguageList or
     * availableLocales is null.  If fallback is non-null, it will contain true if a
     * fallback locale (one not in the acceptLanguageList) was returned.  The value on
     * entry is ignored.  ULocale will be one of the locales in availableLocales, or the
     * ROOT ULocale if if a ROOT locale was used as a fallback (because nothing else in
     * availableLocales matched).  No ULocale array element should be null; behavior is
     * undefined if this is the case.
     *
     * <p>This is a thin wrapper over {@link LocalePriorityList} + {@link LocaleMatcher}.
     *
     * @param acceptLanguageList list in HTTP "Accept-Language:" format of acceptable locales
     * @param availableLocales list of available locales. One of these will be returned.
     * @param fallback if non-null, a 1-element array containing a boolean to be set with
     * the fallback status
     * @return one of the locales from the availableLocales list, or null if none match
     * @stable ICU 3.4
     * @see LocaleMatcher
     * @see LocalePriorityList
     */
    public static ULocale acceptLanguage(String acceptLanguageList, ULocale[] availableLocales,
            boolean[] fallback) {
        if (fallback != null) {
            fallback[0] = true;
        }
        LocalePriorityList desired;
        try {
            desired = LocalePriorityList.add(acceptLanguageList).build();
        } catch (IllegalArgumentException e) {
            return null;
        }
        LocaleMatcher.Builder builder = LocaleMatcher.builder();
        for (ULocale locale : availableLocales) {
            builder.addSupportedULocale(locale);
        }
        LocaleMatcher matcher = builder.build();
        LocaleMatcher.Result result = matcher.getBestMatchResult(desired);
        if (result.getDesiredIndex() >= 0) {
            if (fallback != null && result.getDesiredULocale().equals(result.getSupportedULocale())) {
                fallback[0] = false;
            }
            return result.getSupportedULocale();
        }
        return null;
    }

    /**
     * {@icu} Based on a list of acceptable locales, determine an available locale for the
     * user.  NullPointerException is thrown if acceptLanguageList or availableLocales is
     * null.  If fallback is non-null, it will contain true if a fallback locale (one not
     * in the acceptLanguageList) was returned.  The value on entry is ignored.  ULocale
     * will be one of the locales in availableLocales, or the ROOT ULocale if if a ROOT
     * locale was used as a fallback (because nothing else in availableLocales matched).
     * No ULocale array element should be null; behavior is undefined if this is the case.
     *
     * <p>This is a thin wrapper over {@link LocaleMatcher}.
     *
     * @param acceptLanguageList list of acceptable locales
     * @param availableLocales list of available locales. One of these will be returned.
     * @param fallback if non-null, a 1-element array containing a boolean to be set with
     * the fallback status
     * @return one of the locales from the availableLocales list, or null if none match
     * @stable ICU 3.4
     * @see LocaleMatcher
     */

    public static ULocale acceptLanguage(ULocale[] acceptLanguageList, ULocale[] availableLocales,
            boolean[] fallback) {
        if (fallback != null) {
            fallback[0] = true;
        }
        LocaleMatcher.Builder builder = LocaleMatcher.builder();
        for (ULocale locale : availableLocales) {
            builder.addSupportedULocale(locale);
        }
        LocaleMatcher matcher = builder.build();
        LocaleMatcher.Result result;
        if (acceptLanguageList.length == 1) {
            result = matcher.getBestMatchResult(acceptLanguageList[0]);
        } else {
            result = matcher.getBestMatchResult(Arrays.asList(acceptLanguageList));
        }
        if (result.getDesiredIndex() >= 0) {
            if (fallback != null && result.getDesiredULocale().equals(result.getSupportedULocale())) {
                fallback[0] = false;
            }
            return result.getSupportedULocale();
        }
        return null;
    }

    /**
     * {@icu} Based on a HTTP formatted list of acceptable locales, determine an available
     * locale for the user.  NullPointerException is thrown if acceptLanguageList or
     * availableLocales is null.  If fallback is non-null, it will contain true if a
     * fallback locale (one not in the acceptLanguageList) was returned.  The value on
     * entry is ignored.  ULocale will be one of the locales in availableLocales, or the
     * ROOT ULocale if if a ROOT locale was used as a fallback (because nothing else in
     * availableLocales matched).  No ULocale array element should be null; behavior is
     * undefined if this is the case.  This function will choose a locale from the
     * ULocale.getAvailableLocales() list as available.
     *
     * <p>This is a thin wrapper over {@link LocalePriorityList} + {@link LocaleMatcher}.
     *
     * @param acceptLanguageList list in HTTP "Accept-Language:" format of acceptable locales
     * @param fallback if non-null, a 1-element array containing a boolean to be set with
     * the fallback status
     * @return one of the locales from the ULocale.getAvailableLocales() list, or null if
     * none match
     * @stable ICU 3.4
     * @see LocaleMatcher
     * @see LocalePriorityList
     */
    public static ULocale acceptLanguage(String acceptLanguageList, boolean[] fallback) {
        return acceptLanguage(acceptLanguageList, ULocale.getAvailableLocales(),
                fallback);
    }

    /**
     * {@icu} Based on an ordered array of acceptable locales, determine an available
     * locale for the user.  NullPointerException is thrown if acceptLanguageList or
     * availableLocales is null.  If fallback is non-null, it will contain true if a
     * fallback locale (one not in the acceptLanguageList) was returned.  The value on
     * entry is ignored.  ULocale will be one of the locales in availableLocales, or the
     * ROOT ULocale if if a ROOT locale was used as a fallback (because nothing else in
     * availableLocales matched).  No ULocale array element should be null; behavior is
     * undefined if this is the case.  This function will choose a locale from the
     * ULocale.getAvailableLocales() list as available.
     *
     * <p>This is a thin wrapper over {@link LocaleMatcher}.
     *
     * @param acceptLanguageList ordered array of acceptable locales (preferred are listed first)
     * @param fallback if non-null, a 1-element array containing a boolean to be set with
     * the fallback status
     * @return one of the locales from the ULocale.getAvailableLocales() list, or null if none match
     * @stable ICU 3.4
     * @see LocaleMatcher
     */
    public static ULocale acceptLanguage(ULocale[] acceptLanguageList, boolean[] fallback) {
        return acceptLanguage(acceptLanguageList, ULocale.getAvailableLocales(),
                fallback);
    }

    private static final String UNDEFINED_LANGUAGE = "und";
    private static final String UNDEFINED_SCRIPT = "Zzzz";
    private static final String UNDEFINED_REGION = "ZZ";

    /**
     * {@icu} Adds the likely subtags for a provided locale ID, per the algorithm
     * described in the following CLDR technical report:
     *
     *   http://www.unicode.org/reports/tr35/#Likely_Subtags
     *
     * If the provided ULocale instance is already in the maximal form, or there is no
     * data available available for maximization, it will be returned.  For example,
     * "und-Zzzz" cannot be maximized, since there is no reasonable maximization.
     * Otherwise, a new ULocale instance with the maximal form is returned.
     *
     * Examples:
     *
     * "en" maximizes to "en_Latn_US"
     *
     * "de" maximizes to "de_Latn_US"
     *
     * "sr" maximizes to "sr_Cyrl_RS"
     *
     * "sh" maximizes to "sr_Latn_RS" (Note this will not reverse.)
     *
     * "zh_Hani" maximizes to "zh_Hans_CN" (Note this will not reverse.)
     *
     * @param loc The ULocale to maximize
     * @return The maximized ULocale instance.
     * @stable ICU 4.0
     */
    public static ULocale addLikelySubtags(ULocale loc) {
        String[] tags = new String[3];
        String trailing = null;

        int trailingIndex = parseTagString(
                loc.localeID,
                tags);

        if (trailingIndex < loc.localeID.length()) {
            trailing = loc.localeID.substring(trailingIndex);
        }

        String newLocaleID =
                createLikelySubtagsString(
                        tags[0],
                        tags[1],
                        tags[2],
                        trailing);

        return newLocaleID == null ? loc : new ULocale(newLocaleID);
    }

    /**
     * {@icu} Minimizes the subtags for a provided locale ID, per the algorithm described
     * in the following CLDR technical report:<blockquote>
     *
     *   <a href="http://www.unicode.org/reports/tr35/#Likely_Subtags"
     *>http://www.unicode.org/reports/tr35/#Likely_Subtags</a></blockquote>
     *
     * If the provided ULocale instance is already in the minimal form, or there
     * is no data available for minimization, it will be returned.  Since the
     * minimization algorithm relies on proper maximization, see the comments
     * for addLikelySubtags for reasons why there might not be any data.
     *
     * Examples:<pre>
     *
     * "en_Latn_US" minimizes to "en"
     *
     * "de_Latn_US" minimizes to "de"
     *
     * "sr_Cyrl_RS" minimizes to "sr"
     *
     * "zh_Hant_TW" minimizes to "zh_TW" (The region is preferred to the
     * script, and minimizing to "zh" would imply "zh_Hans_CN".) </pre>
     *
     * @param loc The ULocale to minimize
     * @return The minimized ULocale instance.
     * @stable ICU 4.0
     */
    public static ULocale minimizeSubtags(ULocale loc) {
        return minimizeSubtags(loc, Minimize.FAVOR_REGION);
    }

    /**
     * Options for minimizeSubtags.
     * @internal
     * @deprecated This API is ICU internal only.
     */
    @Deprecated
    public enum Minimize {
        /**
         * Favor including the script, when either the region <b>or</b> the script could be suppressed, but not both.
         * @internal
         * @deprecated This API is ICU internal only.
         */
        @Deprecated
        FAVOR_SCRIPT,
        /**
         * Favor including the region, when either the region <b>or</b> the script could be suppressed, but not both.
         * @internal
         * @deprecated This API is ICU internal only.
         */
        @Deprecated
        FAVOR_REGION
    }

    /**
     * {@icu} Minimizes the subtags for a provided locale ID, per the algorithm described
     * in the following CLDR technical report:<blockquote>
     *
     *   <a href="http://www.unicode.org/reports/tr35/#Likely_Subtags"
     *>http://www.unicode.org/reports/tr35/#Likely_Subtags</a></blockquote>
     *
     * If the provided ULocale instance is already in the minimal form, or there
     * is no data available for minimization, it will be returned.  Since the
     * minimization algorithm relies on proper maximization, see the comments
     * for addLikelySubtags for reasons why there might not be any data.
     *
     * Examples:<pre>
     *
     * "en_Latn_US" minimizes to "en"
     *
     * "de_Latn_US" minimizes to "de"
     *
     * "sr_Cyrl_RS" minimizes to "sr"
     *
     * "zh_Hant_TW" minimizes to "zh_TW" if fieldToFavor == {@link Minimize#FAVOR_REGION}
     * "zh_Hant_TW" minimizes to "zh_Hant" if fieldToFavor == {@link Minimize#FAVOR_SCRIPT}
     * </pre>
     * The fieldToFavor only has an effect if either the region or the script could be suppressed, but not both.
     * @param loc The ULocale to minimize
     * @param fieldToFavor Indicate which should be preferred, when either the region <b>or</b> the script could be suppressed, but not both.
     * @return The minimized ULocale instance.
     * @internal
     * @deprecated This API is ICU internal only.
     */
    @Deprecated
    public static ULocale minimizeSubtags(ULocale loc, Minimize fieldToFavor) {
        String[] tags = new String[3];

        int trailingIndex = parseTagString(
                loc.localeID,
                tags);

        String originalLang = tags[0];
        String originalScript = tags[1];
        String originalRegion = tags[2];
        String originalTrailing = null;

        if (trailingIndex < loc.localeID.length()) {
            /*
             * Create a String that contains everything
             * after the language, script, and region.
             */
            originalTrailing = loc.localeID.substring(trailingIndex);
        }

        /**
         * First, we need to first get the maximization
         * by adding any likely subtags.
         **/
        String maximizedLocaleID =
                createLikelySubtagsString(
                        originalLang,
                        originalScript,
                        originalRegion,
                        null);

        /**
         * If maximization fails, there's nothing
         * we can do.
         **/
        if (isEmptyString(maximizedLocaleID)) {
            return loc;
        }
        else {
            /**
             * Start first with just the language.
             **/
            String tag =
                    createLikelySubtagsString(
                            originalLang,
                            null,
                            null,
                            null);

            if (tag.equals(maximizedLocaleID)) {
                String newLocaleID =
                        createTagString(
                                originalLang,
                                null,
                                null,
                                originalTrailing);

                return new ULocale(newLocaleID);
            }
        }

        /**
         * Next, try the language and region.
         **/
        if (fieldToFavor == Minimize.FAVOR_REGION) {
            if (originalRegion.length() != 0) {
                String tag =
                        createLikelySubtagsString(
                                originalLang,
                                null,
                                originalRegion,
                                null);

                if (tag.equals(maximizedLocaleID)) {
                    String newLocaleID =
                            createTagString(
                                    originalLang,
                                    null,
                                    originalRegion,
                                    originalTrailing);

                    return new ULocale(newLocaleID);
                }
            }
            if (originalScript.length() != 0){
                String tag =
                        createLikelySubtagsString(
                                originalLang,
                                originalScript,
                                null,
                                null);

                if (tag.equals(maximizedLocaleID)) {
                    String newLocaleID =
                            createTagString(
                                    originalLang,
                                    originalScript,
                                    null,
                                    originalTrailing);

                    return new ULocale(newLocaleID);
                }
            }
        } else { // FAVOR_SCRIPT, so
            if (originalScript.length() != 0){
                String tag =
                        createLikelySubtagsString(
                                originalLang,
                                originalScript,
                                null,
                                null);

                if (tag.equals(maximizedLocaleID)) {
                    String newLocaleID =
                            createTagString(
                                    originalLang,
                                    originalScript,
                                    null,
                                    originalTrailing);

                    return new ULocale(newLocaleID);
                }
            }
            if (originalRegion.length() != 0) {
                String tag =
                        createLikelySubtagsString(
                                originalLang,
                                null,
                                originalRegion,
                                null);

                if (tag.equals(maximizedLocaleID)) {
                    String newLocaleID =
                            createTagString(
                                    originalLang,
                                    null,
                                    originalRegion,
                                    originalTrailing);

                    return new ULocale(newLocaleID);
                }
            }
        }
        return loc;
    }

    /**
     * A trivial utility function that checks for a null
     * reference or checks the length of the supplied String.
     *
     *   @param string The string to check
     *
     *   @return true if the String is empty, or if the reference is null.
     */
    private static boolean isEmptyString(String string) {
        return string == null || string.length() == 0;
    }

    /**
     * Append a tag to a StringBuilder, adding the separator if necessary.The tag must
     * not be a zero-length string.
     *
     * @param tag The tag to add.
     * @param buffer The output buffer.
     **/
    private static void appendTag(String tag, StringBuilder buffer) {
        if (buffer.length() != 0) {
            buffer.append(UNDERSCORE);
        }

        buffer.append(tag);
    }

    /**
     * Create a tag string from the supplied parameters.  The lang, script and region
     * parameters may be null references.
     *
     * If any of the language, script or region parameters are empty, and the alternateTags
     * parameter is not null, it will be parsed for potential language, script and region tags
     * to be used when constructing the new tag.  If the alternateTags parameter is null, or
     * it contains no language tag, the default tag for the unknown language is used.
     *
     * @param lang The language tag to use.
     * @param script The script tag to use.
     * @param region The region tag to use.
     * @param trailing Any trailing data to append to the new tag.
     * @param alternateTags A string containing any alternate tags.
     * @return The new tag string.
     **/
    private static String createTagString(String lang, String script, String region,
            String trailing, String alternateTags) {

        LocaleIDParser parser = null;
        boolean regionAppended = false;

        StringBuilder tag = new StringBuilder();

        if (!isEmptyString(lang)) {
            appendTag(
                    lang,
                    tag);
        }
        else if (isEmptyString(alternateTags)) {
            /*
             * Append the value for an unknown language, if
             * we found no language.
             */
            appendTag(
                    UNDEFINED_LANGUAGE,
                    tag);
        }
        else {
            parser = new LocaleIDParser(alternateTags);

            String alternateLang = parser.getLanguage();

            /*
             * Append the value for an unknown language, if
             * we found no language.
             */
            appendTag(
                    !isEmptyString(alternateLang) ? alternateLang : UNDEFINED_LANGUAGE,
                            tag);
        }

        if (!isEmptyString(script)) {
            appendTag(
                    script,
                    tag);
        }
        else if (!isEmptyString(alternateTags)) {
            /*
             * Parse the alternateTags string for the script.
             */
            if (parser == null) {
                parser = new LocaleIDParser(alternateTags);
            }

            String alternateScript = parser.getScript();

            if (!isEmptyString(alternateScript)) {
                appendTag(
                        alternateScript,
                        tag);
            }
        }

        if (!isEmptyString(region)) {
            appendTag(
                    region,
                    tag);

            regionAppended = true;
        }
        else if (!isEmptyString(alternateTags)) {
            /*
             * Parse the alternateTags string for the region.
             */
            if (parser == null) {
                parser = new LocaleIDParser(alternateTags);
            }

            String alternateRegion = parser.getCountry();

            if (!isEmptyString(alternateRegion)) {
                appendTag(
                        alternateRegion,
                        tag);

                regionAppended = true;
            }
        }

        if (trailing != null && trailing.length() > 1) {
            /*
             * The current ICU format expects two underscores
             * will separate the variant from the preceding
             * parts of the tag, if there is no region.
             */
            int separators = 0;

            if (trailing.charAt(0) == UNDERSCORE) {
                if (trailing.charAt(1) == UNDERSCORE) {
                    separators = 2;
                }
            }
            else {
                separators = 1;
            }

            if (regionAppended) {
                /*
                 * If we appended a region, we may need to strip
                 * the extra separator from the variant portion.
                 */
                if (separators == 2) {
                    tag.append(trailing.substring(1));
                }
                else {
                    tag.append(trailing);
                }
            }
            else {
                /*
                 * If we did not append a region, we may need to add
                 * an extra separator to the variant portion.
                 */
                if (separators == 1) {
                    tag.append(UNDERSCORE);
                }
                tag.append(trailing);
            }
        }

        return tag.toString();
    }

    /**
     * Create a tag string from the supplied parameters.  The lang, script and region
     * parameters may be null references.If the lang parameter is an empty string, the
     * default value for an unknown language is written to the output buffer.
     *
     * @param lang The language tag to use.
     * @param script The script tag to use.
     * @param region The region tag to use.
     * @param trailing Any trailing data to append to the new tag.
     * @return The new String.
     **/
    static String createTagString(String lang, String script, String region, String trailing) {
        return createTagString(lang, script, region, trailing, null);
    }

    /**
     * Parse the language, script, and region subtags from a tag string, and return the results.
     *
     * This function does not return the canonical strings for the unknown script and region.
     *
     * @param localeID The locale ID to parse.
     * @param tags An array of three String references to return the subtag strings.
     * @return The number of chars of the localeID parameter consumed.
     **/
    private static int parseTagString(String localeID, String tags[]) {
        LocaleIDParser parser = new LocaleIDParser(localeID);

        String lang = parser.getLanguage();
        String script = parser.getScript();
        String region = parser.getCountry();

        if (isEmptyString(lang)) {
            tags[0] = UNDEFINED_LANGUAGE;
        }
        else {
            tags[0] = lang;
        }

        if (script.equals(UNDEFINED_SCRIPT)) {
            tags[1] = "";
        }
        else {
            tags[1] = script;
        }

        if (region.equals(UNDEFINED_REGION)) {
            tags[2] = "";
        }
        else {
            tags[2] = region;
        }

        /*
         * Search for the variant.  If there is one, then return the index of
         * the preceding separator.
         * If there's no variant, search for the keyword delimiter,
         * and return its index.  Otherwise, return the length of the
         * string.
         *
         * $TOTO(dbertoni) we need to take into account that we might
         * find a part of the language as the variant, since it can
         * can have a variant portion that is long enough to contain
         * the same characters as the variant.
         */
        String variant = parser.getVariant();

        if (!isEmptyString(variant)){
            int index = localeID.indexOf(variant);


            return  index > 0 ? index - 1 : index;
        }
        else
        {
            int index = localeID.indexOf('@');

            return index == -1 ? localeID.length() : index;
        }
    }

    private static String lookupLikelySubtags(String localeId) {
        UResourceBundle bundle =
                UResourceBundle.getBundleInstance(
                        ICUData.ICU_BASE_NAME, "likelySubtags");
        try {
            return bundle.getString(localeId);
        }
        catch(MissingResourceException e) {
            return null;
        }
    }

    private static String createLikelySubtagsString(String lang, String script, String region,
            String variants) {

        /**
         * Try the language with the script and region first.
         */
        if (!isEmptyString(script) && !isEmptyString(region)) {

            String searchTag =
                    createTagString(
                            lang,
                            script,
                            region,
                            null);

            String likelySubtags = lookupLikelySubtags(searchTag);

            /*
            if (likelySubtags == null) {
                if (likelySubtags2 != null) {
                    System.err.println("Tag mismatch: \"(null)\" \"" + likelySubtags2 + "\"");
                }
            }
            else if (likelySubtags2 == null) {
                System.err.println("Tag mismatch: \"" + likelySubtags + "\" \"(null)\"");
            }
            else if (!likelySubtags.equals(likelySubtags2)) {
                System.err.println("Tag mismatch: \"" + likelySubtags + "\" \"" + likelySubtags2
                    + "\"");
            }
             */
            if (likelySubtags != null) {
                // Always use the language tag from the
                // maximal string, since it may be more
                // specific than the one provided.
                return createTagString(
                        null,
                        null,
                        null,
                        variants,
                        likelySubtags);
            }
        }

        /**
         * Try the language with just the script.
         **/
        if (!isEmptyString(script)) {

            String searchTag =
                    createTagString(
                            lang,
                            script,
                            null,
                            null);

            String likelySubtags = lookupLikelySubtags(searchTag);
            if (likelySubtags != null) {
                // Always use the language tag from the
                // maximal string, since it may be more
                // specific than the one provided.
                return createTagString(
                        null,
                        null,
                        region,
                        variants,
                        likelySubtags);
            }
        }

        /**
         * Try the language with just the region.
         **/
        if (!isEmptyString(region)) {

            String searchTag =
                    createTagString(
                            lang,
                            null,
                            region,
                            null);

            String likelySubtags = lookupLikelySubtags(searchTag);

            if (likelySubtags != null) {
                // Always use the language tag from the
                // maximal string, since it may be more
                // specific than the one provided.
                return createTagString(
                        null,
                        script,
                        null,
                        variants,
                        likelySubtags);
            }
        }

        /**
         * Finally, try just the language.
         **/
        {
            String searchTag =
                    createTagString(
                            lang,
                            null,
                            null,
                            null);

            String likelySubtags = lookupLikelySubtags(searchTag);

            if (likelySubtags != null) {
                // Always use the language tag from the
                // maximal string, since it may be more
                // specific than the one provided.
                return createTagString(
                        null,
                        script,
                        region,
                        variants,
                        likelySubtags);
            }
        }

        return null;
    }

    // --------------------------------
    //      BCP47/OpenJDK APIs
    // --------------------------------

    /**
     * The key for the private use locale extension ('x').
     *
     * @see #getExtension(char)
     * @see Builder#setExtension(char, String)
     *
     * @stable ICU 4.2
     */
    public static final char PRIVATE_USE_EXTENSION = 'x';

    /**
     * The key for Unicode locale extension ('u').
     *
     * @see #getExtension(char)
     * @see Builder#setExtension(char, String)
     *
     * @stable ICU 4.2
     */
    public static final char UNICODE_LOCALE_EXTENSION = 'u';

    /**
     * Returns the extension (or private use) value associated with
     * the specified key, or null if there is no extension
     * associated with the key. To be well-formed, the key must be one
     * of <code>[0-9A-Za-z]</code>. Keys are case-insensitive, so
     * for example 'z' and 'Z' represent the same extension.
     *
     * @param key the extension key
     * @return The extension, or null if this locale defines no
     * extension for the specified key.
     * @throws IllegalArgumentException if key is not well-formed
     * @see #PRIVATE_USE_EXTENSION
     * @see #UNICODE_LOCALE_EXTENSION
     *
     * @stable ICU 4.2
     */
    public String getExtension(char key) {
        if (!LocaleExtensions.isValidKey(key)) {
            throw new IllegalArgumentException("Invalid extension key: " + key);
        }
        return extensions().getExtensionValue(key);
    }

    /**
     * Returns the set of extension keys associated with this locale, or the
     * empty set if it has no extensions. The returned set is unmodifiable.
     * The keys will all be lower-case.
     *
     * @return the set of extension keys, or the empty set if this locale has
     * no extensions
     * @stable ICU 4.2
     */
    public Set<Character> getExtensionKeys() {
        return extensions().getKeys();
    }

    /**
     * Returns the set of unicode locale attributes associated with
     * this locale, or the empty set if it has no attributes. The
     * returned set is unmodifiable.
     *
     * @return The set of attributes.
     * @stable ICU 4.6
     */
    public Set<String> getUnicodeLocaleAttributes() {
        return extensions().getUnicodeLocaleAttributes();
    }

    /**
     * Returns the Unicode locale type associated with the specified Unicode locale key
     * for this locale. Returns the empty string for keys that are defined with no type.
     * Returns null if the key is not defined. Keys are case-insensitive. The key must
     * be two alphanumeric characters ([0-9a-zA-Z]), or an IllegalArgumentException is
     * thrown.
     *
     * @param key the Unicode locale key
     * @return The Unicode locale type associated with the key, or null if the
     * locale does not define the key.
     * @throws IllegalArgumentException if the key is not well-formed
     * @throws NullPointerException if <code>key</code> is null
     *
     * @stable ICU 4.4
     */
    public String getUnicodeLocaleType(String key) {
        if (!LocaleExtensions.isValidUnicodeLocaleKey(key)) {
            throw new IllegalArgumentException("Invalid Unicode locale key: " + key);
        }
        return extensions().getUnicodeLocaleType(key);
    }

    /**
     * Returns the set of Unicode locale keys defined by this locale, or the empty set if
     * this locale has none.  The returned set is immutable.  Keys are all lower case.
     *
     * @return The set of Unicode locale keys, or the empty set if this locale has
     * no Unicode locale keywords.
     *
     * @stable ICU 4.4
     */
    public Set<String> getUnicodeLocaleKeys() {
        return extensions().getUnicodeLocaleKeys();
    }

    /**
     * Returns a well-formed IETF BCP 47 language tag representing
     * this locale.
     *
     * <p>If this <code>ULocale</code> has a language, script, country, or
     * variant that does not satisfy the IETF BCP 47 language tag
     * syntax requirements, this method handles these fields as
     * described below:
     *
     * <p><b>Language:</b> If language is empty, or not well-formed
     * (for example "a" or "e2"), it will be emitted as "und" (Undetermined).
     *
     * <p><b>Script:</b> If script is not well-formed (for example "12"
     * or "Latin"), it will be omitted.
     *
     * <p><b>Country:</b> If country is not well-formed (for example "12"
     * or "USA"), it will be omitted.
     *
     * <p><b>Variant:</b> If variant <b>is</b> well-formed, each sub-segment
     * (delimited by '-' or '_') is emitted as a subtag.  Otherwise:
     * <ul>
     *
     * <li>if all sub-segments match <code>[0-9a-zA-Z]{1,8}</code>
     * (for example "WIN" or "Oracle_JDK_Standard_Edition"), the first
     * ill-formed sub-segment and all following will be appended to
     * the private use subtag.  The first appended subtag will be
     * "lvariant", followed by the sub-segments in order, separated by
     * hyphen. For example, "x-lvariant-WIN",
     * "Oracle-x-lvariant-JDK-Standard-Edition".
     *
     * <li>if any sub-segment does not match
     * <code>[0-9a-zA-Z]{1,8}</code>, the variant will be truncated
     * and the problematic sub-segment and all following sub-segments
     * will be omitted.  If the remainder is non-empty, it will be
     * emitted as a private use subtag as above (even if the remainder
     * turns out to be well-formed).  For example,
     * "Solaris_isjustthecoolestthing" is emitted as
     * "x-lvariant-Solaris", not as "solaris".</li></ul>
     *
     * <p><b>Note:</b> Although the language tag created by this
     * method is well-formed (satisfies the syntax requirements
     * defined by the IETF BCP 47 specification), it is not
     * necessarily a valid BCP 47 language tag.  For example,
     * <pre>
     *   new Locale("xx", "YY").toLanguageTag();</pre>
     *
     * will return "xx-YY", but the language subtag "xx" and the
     * region subtag "YY" are invalid because they are not registered
     * in the IANA Language Subtag Registry.
     *
     * @return a BCP47 language tag representing the locale
     * @see #forLanguageTag(String)
     *
     * @stable ICU 4.2
     */
    public String toLanguageTag() {
        BaseLocale base = base();
        LocaleExtensions exts = extensions();

        if (base.getVariant().equalsIgnoreCase("POSIX")) {
            // special handling for variant POSIX
            base = BaseLocale.getInstance(base.getLanguage(), base.getScript(), base.getRegion(), "");
            if (exts.getUnicodeLocaleType("va") == null) {
                // add va-posix
                InternalLocaleBuilder ilocbld = new InternalLocaleBuilder();
                try {
                    ilocbld.setLocale(BaseLocale.ROOT, exts);
                    ilocbld.setUnicodeLocaleKeyword("va", "posix");
                    exts = ilocbld.getLocaleExtensions();
                } catch (LocaleSyntaxException e) {
                    // this should not happen
                    throw new RuntimeException(e);
                }
            }
        }

        LanguageTag tag = LanguageTag.parseLocale(base, exts);

        StringBuilder buf = new StringBuilder();
        String subtag = tag.getLanguage();
        if (subtag.length() > 0) {
            buf.append(LanguageTag.canonicalizeLanguage(subtag));
        }

        subtag = tag.getScript();
        if (subtag.length() > 0) {
            buf.append(LanguageTag.SEP);
            buf.append(LanguageTag.canonicalizeScript(subtag));
        }

        subtag = tag.getRegion();
        if (subtag.length() > 0) {
            buf.append(LanguageTag.SEP);
            buf.append(LanguageTag.canonicalizeRegion(subtag));
        }

        List<String>subtags = tag.getVariants();
        // ICU-20478: Sort variants per UTS35.
        ArrayList<String> variants = new ArrayList<>(subtags);
        Collections.sort(variants);
        for (String s : variants) {
            buf.append(LanguageTag.SEP);
            buf.append(LanguageTag.canonicalizeVariant(s));
        }

        subtags = tag.getExtensions();
        for (String s : subtags) {
            buf.append(LanguageTag.SEP);
            buf.append(LanguageTag.canonicalizeExtension(s));
        }

        subtag = tag.getPrivateuse();
        if (subtag.length() > 0) {
            if (buf.length() == 0) {
               buf.append(UNDEFINED_LANGUAGE);
            }
            buf.append(LanguageTag.SEP);
            buf.append(LanguageTag.PRIVATEUSE).append(LanguageTag.SEP);
            buf.append(LanguageTag.canonicalizePrivateuse(subtag));
        }

        return buf.toString();
    }

    /**
     * Returns a locale for the specified IETF BCP 47 language tag string.
     *
     * <p>If the specified language tag contains any ill-formed subtags,
     * the first such subtag and all following subtags are ignored.  Compare
     * to {@link ULocale.Builder#setLanguageTag} which throws an exception
     * in this case.
     *
     * <p>The following <b>conversions</b> are performed:
     * <ul>
     *
     * <li>The language code "und" is mapped to language "".
     *
     * <li>The portion of a private use subtag prefixed by "lvariant",
     * if any, is removed and appended to the variant field in the
     * result locale (without case normalization).  If it is then
     * empty, the private use subtag is discarded:
     *
     * <pre>
     *     ULocale loc;
     *     loc = ULocale.forLanguageTag("en-US-x-lvariant-icu4j);
     *     loc.getVariant(); // returns "ICU4J"
     *     loc.getExtension('x'); // returns null
     *
     *     loc = Locale.forLanguageTag("de-icu4j-x-URP-lvariant-Abc-Def");
     *     loc.getVariant(); // returns "ICU4J_ABC_DEF"
     *     loc.getExtension('x'); // returns "urp"
     * </pre>
     *
     * <li>When the languageTag argument contains an extlang subtag,
     * the first such subtag is used as the language, and the primary
     * language subtag and other extlang subtags are ignored:
     *
     * <pre>
     *     ULocale.forLanguageTag("ar-aao").getLanguage(); // returns "aao"
     *     ULocale.forLanguageTag("en-abc-def-us").toString(); // returns "abc_US"
     * </pre>
     *
     * <li>Case is normalized. Language is normalized to lower case,
     * script to title case, country to upper case, variant to upper case,
     * and extensions to lower case.
     *
     * </ul>
     *
     * <p>This implements the 'Language-Tag' production of BCP 47, and so
     * supports legacy language tags (marked as "Type: grandfathered" in BCP 47)
     * (regular and irregular) as well as private use language tags.
     *
     * <p>Stand-alone private use tags are represented as empty language and extension 'x-whatever',
     * and legacy tags are converted to their canonical replacements where they exist.
     *
     * <p>Note that a few legacy tags have no modern replacement;
     * these will be converted using the fallback described in
     * the first paragraph, so some information might be lost.
     *
     * <p><b>Note</b>: there is no guarantee that <code>toLanguageTag</code>
     * and <code>forLanguageTag</code> will round-trip.
     *
     * @param languageTag the language tag
     * @return The locale that best represents the language tag.
     * @throws NullPointerException if <code>languageTag</code> is <code>null</code>
     * @see #toLanguageTag()
     * @see ULocale.Builder#setLanguageTag(String)
     *
     * @stable ICU 4.2
     */
    public static ULocale forLanguageTag(String languageTag) {
        LanguageTag tag = LanguageTag.parse(languageTag, null);
        InternalLocaleBuilder bldr = new InternalLocaleBuilder();
        bldr.setLanguageTag(tag);
        return getInstance(bldr.getBaseLocale(), bldr.getLocaleExtensions());
    }

    /**
     * {@icu} Converts the specified keyword (legacy key, or BCP 47 Unicode locale
     * extension key) to the equivalent BCP 47 Unicode locale extension key.
     * For example, BCP 47 Unicode locale extension key "co" is returned for
     * the input keyword "collation".
     * <p>
     * When the specified keyword is unknown, but satisfies the BCP syntax,
     * then the lower-case version of the input keyword will be returned.
     * For example,
     * <code>toUnicodeLocaleKey("ZZ")</code> returns "zz".
     *
     * @param keyword       the input locale keyword (either legacy key
     *                      such as "collation" or BCP 47 Unicode locale extension
     *                      key such as "co").
     * @return              the well-formed BCP 47 Unicode locale extension key,
     *                      or null if the specified locale keyword cannot be mapped
     *                      to a well-formed BCP 47 Unicode locale extension key.
     * @see #toLegacyKey(String)
     * @stable ICU 54
     */
    public static String toUnicodeLocaleKey(String keyword) {
        String bcpKey = KeyTypeData.toBcpKey(keyword);
        if (bcpKey == null && UnicodeLocaleExtension.isKey(keyword)) {
            // unknown keyword, but syntax is fine..
            bcpKey = AsciiUtil.toLowerString(keyword);
        }
        return bcpKey;
    }

    /**
     * {@icu} Converts the specified keyword value (legacy type, or BCP 47
     * Unicode locale extension type) to the well-formed BCP 47 Unicode locale
     * extension type for the specified keyword (category). For example, BCP 47
     * Unicode locale extension type "phonebk" is returned for the input
     * keyword value "phonebook", with the keyword "collation" (or "co").
     * <p>
     * When the specified keyword is not recognized, but the specified value
     * satisfies the syntax of the BCP 47 Unicode locale extension type,
     * or when the specified keyword allows 'variable' type and the specified
     * value satisfies the syntax, the lower-case version of the input value
     * will be returned. For example,
     * <code>toUnicodeLocaleType("Foo", "Bar")</code> returns "bar",
     * <code>toUnicodeLocaleType("variableTop", "00A4")</code> returns "00a4".
     *
     * @param keyword       the locale keyword (either legacy key such as
     *                      "collation" or BCP 47 Unicode locale extension
     *                      key such as "co").
     * @param value         the locale keyword value (either legacy type
     *                      such as "phonebook" or BCP 47 Unicode locale extension
     *                      type such as "phonebk").
     * @return              the well-formed BCP47 Unicode locale extension type,
     *                      or null if the locale keyword value cannot be mapped to
     *                      a well-formed BCP 47 Unicode locale extension type.
     * @see #toLegacyType(String, String)
     * @stable ICU 54
     */
    public static String toUnicodeLocaleType(String keyword, String value) {
        String bcpType = KeyTypeData.toBcpType(keyword, value, null, null);
        if (bcpType == null && UnicodeLocaleExtension.isType(value)) {
            // unknown keyword, but syntax is fine..
            bcpType = AsciiUtil.toLowerString(value);
        }
        return bcpType;
    }

    /**
     * {@icu} Converts the specified keyword (BCP 47 Unicode locale extension key, or
     * legacy key) to the legacy key. For example, legacy key "collation" is
     * returned for the input BCP 47 Unicode locale extension key "co".
     *
     * @param keyword       the input locale keyword (either BCP 47 Unicode locale
     *                      extension key or legacy key).
     * @return              the well-formed legacy key, or null if the specified
     *                      keyword cannot be mapped to a well-formed legacy key.
     * @see #toUnicodeLocaleKey(String)
     * @stable ICU 54
     */
    public static String toLegacyKey(String keyword) {
        String legacyKey = KeyTypeData.toLegacyKey(keyword);
        if (legacyKey == null) {
            // Checks if the specified locale key is well-formed with the legacy locale syntax.
            //
            // Note:
            //  Neither ICU nor LDML/CLDR provides the definition of keyword syntax.
            //  However, a key should not contain '=' obviously. For now, all existing
            //  keys are using ASCII alphabetic letters only. We won't add any new key
            //  that is not compatible with the BCP 47 syntax. Therefore, we assume
            //  a valid key consist from [0-9a-zA-Z], no symbols.
            if (keyword.matches("[0-9a-zA-Z]+")) {
                legacyKey = AsciiUtil.toLowerString(keyword);
            }
        }
        return legacyKey;
    }

    /**
     * {@icu} Converts the specified keyword value (BCP 47 Unicode locale extension type,
     * or legacy type or type alias) to the canonical legacy type. For example,
     * the legacy type "phonebook" is returned for the input BCP 47 Unicode
     * locale extension type "phonebk" with the keyword "collation" (or "co").
     * <p>
     * When the specified keyword is not recognized, but the specified value
     * satisfies the syntax of legacy key, or when the specified keyword
     * allows 'variable' type and the specified value satisfies the syntax,
     * the lower-case version of the input value will be returned.
     * For example,
     * <code>toLegacyType("Foo", "Bar")</code> returns "bar",
     * <code>toLegacyType("vt", "00A4")</code> returns "00a4".
     *
     * @param keyword       the locale keyword (either legacy keyword such as
     *                      "collation" or BCP 47 Unicode locale extension
     *                      key such as "co").
     * @param value         the locale keyword value (either BCP 47 Unicode locale
     *                      extension type such as "phonebk" or legacy keyword value
     *                      such as "phonebook").
     * @return              the well-formed legacy type, or null if the specified
     *                      keyword value cannot be mapped to a well-formed legacy
     *                      type.
     * @see #toUnicodeLocaleType(String, String)
     * @stable ICU 54
     */
    public static String toLegacyType(String keyword, String value) {
        String legacyType = KeyTypeData.toLegacyType(keyword, value, null, null);
        if (legacyType == null) {
            // Checks if the specified locale type is well-formed with the legacy locale syntax.
            //
            // Note:
            //  Neither ICU nor LDML/CLDR provides the definition of keyword syntax.
            //  However, a type should not contain '=' obviously. For now, all existing
            //  types are using ASCII alphabetic letters with a few symbol letters. We won't
            //  add any new type that is not compatible with the BCP 47 syntax except timezone
            //  IDs. For now, we assume a valid type start with [0-9a-zA-Z], but may contain
            //  '-' '_' '/' in the middle.
            if (value.matches("[0-9a-zA-Z]+([_/\\-][0-9a-zA-Z]+)*")) {
                legacyType = AsciiUtil.toLowerString(value);
            }
        }
        return legacyType;
    }

    /**
     * <code>Builder</code> is used to build instances of <code>ULocale</code>
     * from values configured by the setters.  Unlike the <code>ULocale</code>
     * constructors, the <code>Builder</code> checks if a value configured by a
     * setter satisfies the syntax requirements defined by the <code>ULocale</code>
     * class.  A <code>ULocale</code> object created by a <code>Builder</code> is
     * well-formed and can be transformed to a well-formed IETF BCP 47 language tag
     * without losing information.
     *
     * <p><b>Note:</b> The <code>ULocale</code> class does not provide any
     * syntactic restrictions on variant, while BCP 47 requires each variant
     * subtag to be 5 to 8 alphanumerics or a single numeric followed by 3
     * alphanumerics.  The method <code>setVariant</code> throws
     * <code>IllformedLocaleException</code> for a variant that does not satisfy
     * this restriction. If it is necessary to support such a variant, use a
     * ULocale constructor.  However, keep in mind that a <code>ULocale</code>
     * object created this way might lose the variant information when
     * transformed to a BCP 47 language tag.
     *
     * <p>The following example shows how to create a <code>Locale</code> object
     * with the <code>Builder</code>.
     * <blockquote>
     * <pre>
     *     ULocale aLocale = new Builder().setLanguage("sr").setScript("Latn").setRegion("RS").build();
     * </pre>
     * </blockquote>
     *
     * <p>Builders can be reused; <code>clear()</code> resets all
     * fields to their default values.
     *
     * @see ULocale#toLanguageTag()
     *
     * @stable ICU 4.2
     */
    public static final class Builder {

        private final InternalLocaleBuilder _locbld;

        /**
         * Constructs an empty Builder. The default value of all
         * fields, extensions, and private use information is the
         * empty string.
         *
         * @stable ICU 4.2
         */
        public Builder() {
            _locbld = new InternalLocaleBuilder();
        }

        /**
         * Resets the <code>Builder</code> to match the provided
         * <code>locale</code>.  Existing state is discarded.
         *
         * <p>All fields of the locale must be well-formed, see {@link Locale}.
         *
         * <p>Locales with any ill-formed fields cause
         * <code>IllformedLocaleException</code> to be thrown.
         *
         * @param locale the locale
         * @return This builder.
         * @throws IllformedLocaleException if <code>locale</code> has
         * any ill-formed fields.
         * @throws NullPointerException if <code>locale</code> is null.
         *
         * @stable ICU 4.2
         */
        public Builder setLocale(ULocale locale) {
            try {
                _locbld.setLocale(locale.base(), locale.extensions());
            } catch (LocaleSyntaxException e) {
                throw new IllformedLocaleException(e.getMessage(), e.getErrorIndex());
            }
            return this;
        }

        /**
         * Resets the Builder to match the provided IETF BCP 47
         * language tag.  Discards the existing state.  Null and the
         * empty string cause the builder to be reset, like {@link
         * #clear}.  Legacy tags (see {@link
         * ULocale#forLanguageTag}) are converted to their canonical
         * form before being processed.  Otherwise, the language tag
         * must be well-formed (see {@link ULocale}) or an exception is
         * thrown (unlike <code>ULocale.forLanguageTag</code>, which
         * just discards ill-formed and following portions of the
         * tag).
         *
         * @param languageTag the language tag
         * @return This builder.
         * @throws IllformedLocaleException if <code>languageTag</code> is ill-formed
         * @see ULocale#forLanguageTag(String)
         *
         * @stable ICU 4.2
         */
        public Builder setLanguageTag(String languageTag) {
            ParseStatus sts = new ParseStatus();
            LanguageTag tag = LanguageTag.parse(languageTag, sts);
            if (sts.isError()) {
                throw new IllformedLocaleException(sts.getErrorMessage(), sts.getErrorIndex());
            }
            _locbld.setLanguageTag(tag);

            return this;
        }

        /**
         * Sets the language.  If <code>language</code> is the empty string or
         * null, the language in this <code>Builder</code> is removed.  Otherwise,
         * the language must be <a href="./Locale.html#def_language">well-formed</a>
         * or an exception is thrown.
         *
         * <p>The typical language value is a two or three-letter language
         * code as defined in ISO639.
         *
         * @param language the language
         * @return This builder.
         * @throws IllformedLocaleException if <code>language</code> is ill-formed
         *
         * @stable ICU 4.2
         */
        public Builder setLanguage(String language) {
            try {
                _locbld.setLanguage(language);
            } catch (LocaleSyntaxException e) {
                throw new IllformedLocaleException(e.getMessage(), e.getErrorIndex());
            }
            return this;
        }

        /**
         * Sets the script. If <code>script</code> is null or the empty string,
         * the script in this <code>Builder</code> is removed.
         * Otherwise, the script must be well-formed or an exception is thrown.
         *
         * <p>The typical script value is a four-letter script code as defined by ISO 15924.
         *
         * @param script the script
         * @return This builder.
         * @throws IllformedLocaleException if <code>script</code> is ill-formed
         *
         * @stable ICU 4.2
         */
        public Builder setScript(String script) {
            try {
                _locbld.setScript(script);
            } catch (LocaleSyntaxException e) {
                throw new IllformedLocaleException(e.getMessage(), e.getErrorIndex());
            }
            return this;
        }

        /**
         * Sets the region.  If region is null or the empty string, the region
         * in this <code>Builder</code> is removed.  Otherwise,
         * the region must be well-formed or an exception is thrown.
         *
         * <p>The typical region value is a two-letter ISO 3166 code or a
         * three-digit UN M.49 area code.
         *
         * <p>The country value in the <code>Locale</code> created by the
         * <code>Builder</code> is always normalized to upper case.
         *
         * @param region the region
         * @return This builder.
         * @throws IllformedLocaleException if <code>region</code> is ill-formed
         *
         * @stable ICU 4.2
         */
        public Builder setRegion(String region) {
            try {
                _locbld.setRegion(region);
            } catch (LocaleSyntaxException e) {
                throw new IllformedLocaleException(e.getMessage(), e.getErrorIndex());
            }
            return this;
        }

        /**
         * Sets the variant.  If variant is null or the empty string, the
         * variant in this <code>Builder</code> is removed.  Otherwise, it
         * must consist of one or more well-formed subtags, or an exception is thrown.
         *
         * <p><b>Note:</b> This method checks if <code>variant</code>
         * satisfies the IETF BCP 47 variant subtag's syntax requirements,
         * and normalizes the value to lowercase letters.  However,
         * the <code>ULocale</code> class does not impose any syntactic
         * restriction on variant.  To set such a variant,
         * use a ULocale constructor.
         *
         * @param variant the variant
         * @return This builder.
         * @throws IllformedLocaleException if <code>variant</code> is ill-formed
         *
         * @stable ICU 4.2
         */
        public Builder setVariant(String variant) {
            try {
                _locbld.setVariant(variant);
            } catch (LocaleSyntaxException e) {
                throw new IllformedLocaleException(e.getMessage(), e.getErrorIndex());
            }
            return this;
        }

        /**
         * Sets the extension for the given key. If the value is null or the
         * empty string, the extension is removed.  Otherwise, the extension
         * must be well-formed or an exception is thrown.
         *
         * <p><b>Note:</b> The key {@link ULocale#UNICODE_LOCALE_EXTENSION
         * UNICODE_LOCALE_EXTENSION} ('u') is used for the Unicode locale extension.
         * Setting a value for this key replaces any existing Unicode locale key/type
         * pairs with those defined in the extension.
         *
         * <p><b>Note:</b> The key {@link ULocale#PRIVATE_USE_EXTENSION
         * PRIVATE_USE_EXTENSION} ('x') is used for the private use code. To be
         * well-formed, the value for this key needs only to have subtags of one to
         * eight alphanumeric characters, not two to eight as in the general case.
         *
         * @param key the extension key
         * @param value the extension value
         * @return This builder.
         * @throws IllformedLocaleException if <code>key</code> is illegal
         * or <code>value</code> is ill-formed
         * @see #setUnicodeLocaleKeyword(String, String)
         *
         * @stable ICU 4.2
         */
        public Builder setExtension(char key, String value) {
            try {
                _locbld.setExtension(key, value);
            } catch (LocaleSyntaxException e) {
                throw new IllformedLocaleException(e.getMessage(), e.getErrorIndex());
            }
            return this;
        }

        /**
         * Sets the Unicode locale keyword type for the given key.  If the type
         * is null, the Unicode keyword is removed.  Otherwise, the key must be
         * non-null and both key and type must be well-formed or an exception
         * is thrown.
         *
         * <p>Keys and types are converted to lower case.
         *
         * <p><b>Note</b>:Setting the 'u' extension via {@link #setExtension}
         * replaces all Unicode locale keywords with those defined in the
         * extension.
         *
         * @param key the Unicode locale key
         * @param type the Unicode locale type
         * @return This builder.
         * @throws IllformedLocaleException if <code>key</code> or <code>type</code>
         * is ill-formed
         * @throws NullPointerException if <code>key</code> is null
         * @see #setExtension(char, String)
         *
         * @stable ICU 4.4
         */
        public Builder setUnicodeLocaleKeyword(String key, String type) {
            try {
                _locbld.setUnicodeLocaleKeyword(key, type);
            } catch (LocaleSyntaxException e) {
                throw new IllformedLocaleException(e.getMessage(), e.getErrorIndex());
            }
            return this;
        }

        /**
         * Adds a unicode locale attribute, if not already present, otherwise
         * has no effect.  The attribute must not be null and must be well-formed
         * or an exception is thrown.
         *
         * @param attribute the attribute
         * @return This builder.
         * @throws NullPointerException if <code>attribute</code> is null
         * @throws IllformedLocaleException if <code>attribute</code> is ill-formed
         * @see #setExtension(char, String)
         *
         * @stable ICU 4.6
         */
        public Builder addUnicodeLocaleAttribute(String attribute) {
            try {
                _locbld.addUnicodeLocaleAttribute(attribute);
            } catch (LocaleSyntaxException e) {
                throw new IllformedLocaleException(e.getMessage(), e.getErrorIndex());
            }
            return this;
        }

        /**
         * Removes a unicode locale attribute, if present, otherwise has no
         * effect.  The attribute must not be null and must be well-formed
         * or an exception is thrown.
         *
         * <p>Attribute comparison for removal is case-insensitive.
         *
         * @param attribute the attribute
         * @return This builder.
         * @throws NullPointerException if <code>attribute</code> is null
         * @throws IllformedLocaleException if <code>attribute</code> is ill-formed
         * @see #setExtension(char, String)
         *
         * @stable ICU 4.6
         */
        public Builder removeUnicodeLocaleAttribute(String attribute) {
            try {
                _locbld.removeUnicodeLocaleAttribute(attribute);
            } catch (LocaleSyntaxException e) {
                throw new IllformedLocaleException(e.getMessage(), e.getErrorIndex());
            }
            return this;
        }

        /**
         * Resets the builder to its initial, empty state.
         *
         * @return this builder
         *
         * @stable ICU 4.2
         */
        public Builder clear() {
            _locbld.clear();
            return this;
        }

        /**
         * Resets the extensions to their initial, empty state.
         * Language, script, region and variant are unchanged.
         *
         * @return this builder
         * @see #setExtension(char, String)
         *
         * @stable ICU 4.2
         */
        public Builder clearExtensions() {
            _locbld.clearExtensions();
            return this;
        }

        /**
         * Returns an instance of <code>ULocale</code> created from the fields set
         * on this builder.
         *
         * @return a new Locale
         *
         * @stable ICU 4.4
         */
        public ULocale build() {
            return getInstance(_locbld.getBaseLocale(), _locbld.getLocaleExtensions());
        }
    }

    private static ULocale getInstance(BaseLocale base, LocaleExtensions exts) {
        String id = lscvToID(base.getLanguage(), base.getScript(), base.getRegion(),
                base.getVariant());

        Set<Character> extKeys = exts.getKeys();
        if (!extKeys.isEmpty()) {
            // legacy locale ID assume Unicode locale keywords and
            // other extensions are at the same level.
            // e.g. @a=ext-for-aa;calendar=japanese;m=ext-for-mm;x=priv-use

            TreeMap<String, String> kwds = new TreeMap<>();
            for (Character key : extKeys) {
                Extension ext = exts.getExtension(key);
                if (ext instanceof UnicodeLocaleExtension) {
                    UnicodeLocaleExtension uext = (UnicodeLocaleExtension)ext;
                    Set<String> ukeys = uext.getUnicodeLocaleKeys();
                    for (String bcpKey : ukeys) {
                        String bcpType = uext.getUnicodeLocaleType(bcpKey);
                        // convert to legacy key/type
                        String lkey = toLegacyKey(bcpKey);
                        String ltype = toLegacyType(bcpKey, ((bcpType.length() == 0) ? "yes" : bcpType)); // use "yes" as the value of typeless keywords
                        // special handling for u-va-posix, since this is a variant, not a keyword
                        if (lkey.equals("va") && ltype.equals("posix") && base.getVariant().length() == 0) {
                            id = id + "_POSIX";
                        } else {
                            kwds.put(lkey, ltype);
                        }
                    }
                    // Mapping Unicode locale attribute to the special keyword, attribute=xxx-yyy
                    Set<String> uattributes = uext.getUnicodeLocaleAttributes();
                    if (uattributes.size() > 0) {
                        StringBuilder attrbuf = new StringBuilder();
                        for (String attr : uattributes) {
                            if (attrbuf.length() > 0) {
                                attrbuf.append('-');
                            }
                            attrbuf.append(attr);
                        }
                        kwds.put(LOCALE_ATTRIBUTE_KEY, attrbuf.toString());
                    }
                } else {
                    kwds.put(String.valueOf(key), ext.getValue());
                }
            }

            if (!kwds.isEmpty()) {
                StringBuilder buf = new StringBuilder(id);
                buf.append("@");
                Set<Map.Entry<String, String>> kset = kwds.entrySet();
                boolean insertSep = false;
                for (Map.Entry<String, String> kwd : kset) {
                    if (insertSep) {
                        buf.append(";");
                    } else {
                        insertSep = true;
                    }
                    buf.append(kwd.getKey());
                    buf.append("=");
                    buf.append(kwd.getValue());
                }

                id = buf.toString();
            }
        }
        return new ULocale(id);
    }

    private BaseLocale base() {
        if (baseLocale == null) {
            String language, script, region, variant;
            language = script = region = variant = "";
            if (!equals(ULocale.ROOT)) {
                LocaleIDParser lp = new LocaleIDParser(localeID);
                language = lp.getLanguage();
                script = lp.getScript();
                region = lp.getCountry();
                variant = lp.getVariant();
            }
            baseLocale = BaseLocale.getInstance(language, script, region, variant);
        }
        return baseLocale;
    }

    private LocaleExtensions extensions() {
        if (extensions == null) {
            Iterator<String> kwitr = getKeywords();
            if (kwitr == null) {
                extensions = LocaleExtensions.EMPTY_EXTENSIONS;
            } else {
                InternalLocaleBuilder intbld = new InternalLocaleBuilder();
                while (kwitr.hasNext()) {
                    String key = kwitr.next();
                    if (key.equals(LOCALE_ATTRIBUTE_KEY)) {
                        // special keyword used for representing Unicode locale attributes
                        String[] uattributes = getKeywordValue(key).split("[-_]");
                        for (String uattr : uattributes) {
                            try {
                                intbld.addUnicodeLocaleAttribute(uattr);
                            } catch (LocaleSyntaxException e) {
                                // ignore and fall through
                            }
                        }
                    } else if (key.length() >= 2) {
                        String bcpKey = toUnicodeLocaleKey(key);
                        String bcpType = toUnicodeLocaleType(key, getKeywordValue(key));
                        if (bcpKey != null && bcpType != null) {
                            try {
                                intbld.setUnicodeLocaleKeyword(bcpKey, bcpType);
                            } catch (LocaleSyntaxException e) {
                                // ignore and fall through
                            }
                        }
                    } else if (key.length() == 1 && (key.charAt(0) != UNICODE_LOCALE_EXTENSION)) {
                        try  {
                            intbld.setExtension(key.charAt(0), getKeywordValue(key).replace("_",
                                    LanguageTag.SEP));
                        } catch (LocaleSyntaxException e) {
                            // ignore and fall through
                        }
                    }
                }
                extensions = intbld.getLocaleExtensions();
            }
        }
        return extensions;
    }

    /*
     * JDK Locale Helper
     */
    private static final class JDKLocaleHelper {
        // Java 7 has java.util.Locale.Category.
        // Android API level 21..23 do not yet have it; only API level 24 (Nougat) adds it.
        // https://developer.android.com/reference/java/util/Locale.Category
        private static boolean hasLocaleCategories = false;

        private static Method mGetDefault;
        private static Method mSetDefault;
        private static Object eDISPLAY;
        private static Object eFORMAT;

        static {
            do {
                try {
                    Class<?> cCategory = null;
                    Class<?>[] classes = Locale.class.getDeclaredClasses();
                    for (Class<?> c : classes) {
                        if (c.getName().equals("java.util.Locale$Category")) {
                            cCategory = c;
                            break;
                        }
                    }
                    if (cCategory == null) {
                        break;
                    }
                    mGetDefault = Locale.class.getDeclaredMethod("getDefault", cCategory);
                    mSetDefault = Locale.class.getDeclaredMethod("setDefault", cCategory, Locale.class);

                    Method mName = cCategory.getMethod("name", (Class[]) null);
                    Object[] enumConstants = cCategory.getEnumConstants();
                    for (Object e : enumConstants) {
                        String catVal = (String)mName.invoke(e, (Object[])null);
                        if (catVal.equals("DISPLAY")) {
                            eDISPLAY = e;
                        } else if (catVal.equals("FORMAT")) {
                            eFORMAT = e;
                        }
                    }
                    if (eDISPLAY == null || eFORMAT == null) {
                        break;
                    }

                    hasLocaleCategories = true;
                } catch (NoSuchMethodException e) {
                } catch (IllegalArgumentException e) {
                } catch (IllegalAccessException e) {
                } catch (InvocationTargetException e) {
                } catch (SecurityException e) {
                    // TODO : report?
                }
            } while (false);
        }

        private JDKLocaleHelper() {
        }

        public static boolean hasLocaleCategories() {
            return hasLocaleCategories;
        }

        public static ULocale toULocale(Locale loc) {
            String language = loc.getLanguage();
            String script = "";
            String country = loc.getCountry();
            String variant = loc.getVariant();

            Set<String> attributes = null;
            Map<String, String> keywords = null;

            script = loc.getScript();
            Set<Character> extKeys = loc.getExtensionKeys();
            if (!extKeys.isEmpty()) {
                for (Character extKey : extKeys) {
                    if (extKey.charValue() == 'u') {
                        // Found Unicode locale extension

                        // attributes
                        @SuppressWarnings("unchecked")
                        Set<String> uAttributes = loc.getUnicodeLocaleAttributes();
                        if (!uAttributes.isEmpty()) {
                            attributes = new TreeSet<>();
                            for (String attr : uAttributes) {
                                attributes.add(attr);
                            }
                        }

                        // keywords
                        Set<String> uKeys = loc.getUnicodeLocaleKeys();
                        for (String kwKey : uKeys) {
                            String kwVal = loc.getUnicodeLocaleType(kwKey);
                            if (kwVal != null) {
                                if (kwKey.equals("va")) {
                                    // va-* is interpreted as a variant
                                    variant = (variant.length() == 0) ? kwVal : kwVal + "_" + variant;
                                } else {
                                    if (keywords == null) {
                                        keywords = new TreeMap<>();
                                    }
                                    keywords.put(kwKey, kwVal);
                                }
                            }
                        }
                    } else {
                        String extVal = loc.getExtension(extKey);
                        if (extVal != null) {
                            if (keywords == null) {
                                keywords = new TreeMap<>();
                            }
                            keywords.put(String.valueOf(extKey), extVal);
                        }
                    }
                }
            }

            // JDK locale no_NO_NY is not interpreted as Nynorsk by ICU,
            // and it should be transformed to nn_NO.

            // Note: JDK7+ unerstand both no_NO_NY and nn_NO. When convert
            // ICU locale to JDK, we do not need to map nn_NO back to no_NO_NY.

            if (language.equals("no") && country.equals("NO") && variant.equals("NY")) {
                language = "nn";
                variant = "";
            }

            // Constructing ID
            StringBuilder buf = new StringBuilder(language);

            if (script.length() > 0) {
                buf.append('_');
                buf.append(script);
            }

            if (country.length() > 0) {
                buf.append('_');
                buf.append(country);
            }

            if (variant.length() > 0) {
                if (country.length() == 0) {
                    buf.append('_');
                }
                buf.append('_');
                buf.append(variant);
            }

            if (attributes != null) {
                // transform Unicode attributes into a keyword
                StringBuilder attrBuf = new StringBuilder();
                for (String attr : attributes) {
                    if (attrBuf.length() != 0) {
                        attrBuf.append('-');
                    }
                    attrBuf.append(attr);
                }
                if (keywords == null) {
                    keywords = new TreeMap<>();
                }
                keywords.put(LOCALE_ATTRIBUTE_KEY, attrBuf.toString());
            }

            if (keywords != null) {
                buf.append('@');
                boolean addSep = false;
                for (Entry<String, String> kwEntry : keywords.entrySet()) {
                    String kwKey = kwEntry.getKey();
                    String kwVal = kwEntry.getValue();

                    if (kwKey.length() != 1) {
                        // Unicode locale key
                        kwKey = toLegacyKey(kwKey);
                        // use "yes" as the value of typeless keywords
                        kwVal = toLegacyType(kwKey, ((kwVal.length() == 0) ? "yes" : kwVal));
                    }

                    if (addSep) {
                        buf.append(';');
                    } else {
                        addSep = true;
                    }
                    buf.append(kwKey);
                    buf.append('=');
                    buf.append(kwVal);
                }
            }

            return new ULocale(getName(buf.toString()), loc);
        }

        public static Locale toLocale(ULocale uloc) {
            Locale loc = null;
            String ulocStr = uloc.getName();
            if (uloc.getScript().length() > 0 || ulocStr.contains("@")) {
                // With script or keywords available, the best way
                // to get a mapped Locale is to go through a language tag.
                // A Locale with script or keywords can only have variants
                // that is 1 to 8 alphanum. If this ULocale has a variant
                // subtag not satisfying the criteria, the variant subtag
                // will be lost.
                String tag = uloc.toLanguageTag();

                // Workaround for variant casing problem:
                //
                // The variant field in ICU is case insensitive and normalized
                // to upper case letters by getVariant(), while
                // the variant field in JDK Locale is case sensitive.
                // ULocale#toLanguageTag use lower case characters for
                // BCP 47 variant and private use x-lvariant.
                //
                // Locale#forLanguageTag in JDK preserves character casing
                // for variant. Because ICU always normalizes variant to
                // upper case, we convert language tag to upper case here.
                tag = AsciiUtil.toUpperString(tag);
                loc = Locale.forLanguageTag(tag);
            }
            if (loc == null) {
                // Without script or keywords, use a Locale constructor,
                // so we can preserve any ill-formed variants.
                loc = new Locale(uloc.getLanguage(), uloc.getCountry(), uloc.getVariant());
            }
            return loc;
        }

        public static Locale getDefault(Category category) {
            if (hasLocaleCategories) {
                Object cat = null;
                switch (category) {
                case DISPLAY:
                    cat = eDISPLAY;
                    break;
                case FORMAT:
                    cat = eFORMAT;
                    break;
                }
                if (cat != null) {
                    try {
                        return (Locale)mGetDefault.invoke(null, cat);
                    } catch (InvocationTargetException e) {
                        // fall through - use the base default
                    } catch (IllegalArgumentException e) {
                        // fall through - use the base default
                    } catch (IllegalAccessException e) {
                        // fall through - use the base default
                    }
                }
            }
            return Locale.getDefault();
        }

        public static void setDefault(Category category, Locale newLocale) {
            if (hasLocaleCategories) {
                Object cat = null;
                switch (category) {
                case DISPLAY:
                    cat = eDISPLAY;
                    break;
                case FORMAT:
                    cat = eFORMAT;
                    break;
                }
                if (cat != null) {
                    try {
                        mSetDefault.invoke(null, cat, newLocale);
                    } catch (InvocationTargetException e) {
                        // fall through - no effects
                    } catch (IllegalArgumentException e) {
                        // fall through - no effects
                    } catch (IllegalAccessException e) {
                        // fall through - no effects
                    }
                }
            }
        }
    }
}
