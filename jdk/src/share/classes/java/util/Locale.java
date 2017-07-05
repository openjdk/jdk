/*
 * Copyright (c) 1996, 2006, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * (C) Copyright Taligent, Inc. 1996, 1997 - All Rights Reserved
 * (C) Copyright IBM Corp. 1996 - 1998 - All Rights Reserved
 *
 * The original version of this source code and documentation
 * is copyrighted and owned by Taligent, Inc., a wholly-owned
 * subsidiary of IBM. These materials are provided under terms
 * of a License Agreement between Taligent and Sun. This technology
 * is protected by multiple US and International patents.
 *
 * This notice and attribution to Taligent may not be removed.
 * Taligent is a registered trademark of Taligent, Inc.
 *
 */

package java.util;

import java.io.*;
import java.security.AccessController;
import java.text.MessageFormat;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.spi.LocaleNameProvider;
import java.util.spi.LocaleServiceProvider;
import sun.security.action.GetPropertyAction;
import sun.util.LocaleServiceProviderPool;
import sun.util.resources.LocaleData;
import sun.util.resources.OpenListResourceBundle;

/**
 *
 * A <code>Locale</code> object represents a specific geographical, political,
 * or cultural region. An operation that requires a <code>Locale</code> to perform
 * its task is called <em>locale-sensitive</em> and uses the <code>Locale</code>
 * to tailor information for the user. For example, displaying a number
 * is a locale-sensitive operation--the number should be formatted
 * according to the customs/conventions of the user's native country,
 * region, or culture.
 *
 * <P>
 * Create a <code>Locale</code> object using the constructors in this class:
 * <blockquote>
 * <pre>
 * Locale(String language)
 * Locale(String language, String country)
 * Locale(String language, String country, String variant)
 * </pre>
 * </blockquote>
 * The language argument is a valid <STRONG>ISO Language Code.</STRONG>
 * These codes are the lower-case, two-letter codes as defined by ISO-639.
 * You can find a full list of these codes at a number of sites, such as:
 * <BR><a href ="http://www.loc.gov/standards/iso639-2/php/English_list.php">
 * <code>http://www.loc.gov/standards/iso639-2/php/English_list.php</code></a>
 *
 * <P>
 * The country argument is a valid <STRONG>ISO Country Code.</STRONG> These
 * codes are the upper-case, two-letter codes as defined by ISO-3166.
 * You can find a full list of these codes at a number of sites, such as:
 * <BR><a href="http://www.iso.ch/iso/en/prods-services/iso3166ma/02iso-3166-code-lists/list-en1.html">
 * <code>http://www.iso.ch/iso/en/prods-services/iso3166ma/02iso-3166-code-lists/list-en1.html</code></a>
 *
 * <P>
 * The variant argument is a vendor or browser-specific code.
 * For example, use WIN for Windows, MAC for Macintosh, and POSIX for POSIX.
 * Where there are two variants, separate them with an underscore, and
 * put the most important one first. For example, a Traditional Spanish collation
 * might construct a locale with parameters for language, country and variant as:
 * "es", "ES", "Traditional_WIN".
 *
 * <P>
 * Because a <code>Locale</code> object is just an identifier for a region,
 * no validity check is performed when you construct a <code>Locale</code>.
 * If you want to see whether particular resources are available for the
 * <code>Locale</code> you construct, you must query those resources. For
 * example, ask the <code>NumberFormat</code> for the locales it supports
 * using its <code>getAvailableLocales</code> method.
 * <BR><STRONG>Note:</STRONG> When you ask for a resource for a particular
 * locale, you get back the best available match, not necessarily
 * precisely what you asked for. For more information, look at
 * {@link ResourceBundle}.
 *
 * <P>
 * The <code>Locale</code> class provides a number of convenient constants
 * that you can use to create <code>Locale</code> objects for commonly used
 * locales. For example, the following creates a <code>Locale</code> object
 * for the United States:
 * <blockquote>
 * <pre>
 * Locale.US
 * </pre>
 * </blockquote>
 *
 * <P>
 * Once you've created a <code>Locale</code> you can query it for information about
 * itself. Use <code>getCountry</code> to get the ISO Country Code and
 * <code>getLanguage</code> to get the ISO Language Code. You can
 * use <code>getDisplayCountry</code> to get the
 * name of the country suitable for displaying to the user. Similarly,
 * you can use <code>getDisplayLanguage</code> to get the name of
 * the language suitable for displaying to the user. Interestingly,
 * the <code>getDisplayXXX</code> methods are themselves locale-sensitive
 * and have two versions: one that uses the default locale and one
 * that uses the locale specified as an argument.
 *
 * <P>
 * The Java Platform provides a number of classes that perform locale-sensitive
 * operations. For example, the <code>NumberFormat</code> class formats
 * numbers, currency, or percentages in a locale-sensitive manner. Classes
 * such as <code>NumberFormat</code> have a number of convenience methods
 * for creating a default object of that type. For example, the
 * <code>NumberFormat</code> class provides these three convenience methods
 * for creating a default <code>NumberFormat</code> object:
 * <blockquote>
 * <pre>
 * NumberFormat.getInstance()
 * NumberFormat.getCurrencyInstance()
 * NumberFormat.getPercentInstance()
 * </pre>
 * </blockquote>
 * These methods have two variants; one with an explicit locale
 * and one without; the latter using the default locale.
 * <blockquote>
 * <pre>
 * NumberFormat.getInstance(myLocale)
 * NumberFormat.getCurrencyInstance(myLocale)
 * NumberFormat.getPercentInstance(myLocale)
 * </pre>
 * </blockquote>
 * A <code>Locale</code> is the mechanism for identifying the kind of object
 * (<code>NumberFormat</code>) that you would like to get. The locale is
 * <STRONG>just</STRONG> a mechanism for identifying objects,
 * <STRONG>not</STRONG> a container for the objects themselves.
 *
 * @see         ResourceBundle
 * @see         java.text.Format
 * @see         java.text.NumberFormat
 * @see         java.text.Collator
 * @author      Mark Davis
 * @since       1.1
 */

public final class Locale implements Cloneable, Serializable {

    // cache to store singleton Locales
    private final static ConcurrentHashMap<String, Locale> cache =
        new ConcurrentHashMap<String, Locale>(32);

    /** Useful constant for language.
     */
    static public final Locale ENGLISH = createSingleton("en__", "en", "");

    /** Useful constant for language.
     */
    static public final Locale FRENCH = createSingleton("fr__", "fr", "");

    /** Useful constant for language.
     */
    static public final Locale GERMAN = createSingleton("de__", "de", "");

    /** Useful constant for language.
     */
    static public final Locale ITALIAN = createSingleton("it__", "it", "");

    /** Useful constant for language.
     */
    static public final Locale JAPANESE = createSingleton("ja__", "ja", "");

    /** Useful constant for language.
     */
    static public final Locale KOREAN = createSingleton("ko__", "ko", "");

    /** Useful constant for language.
     */
    static public final Locale CHINESE = createSingleton("zh__", "zh", "");

    /** Useful constant for language.
     */
    static public final Locale SIMPLIFIED_CHINESE = createSingleton("zh_CN_", "zh", "CN");

    /** Useful constant for language.
     */
    static public final Locale TRADITIONAL_CHINESE = createSingleton("zh_TW_", "zh", "TW");

    /** Useful constant for country.
     */
    static public final Locale FRANCE = createSingleton("fr_FR_", "fr", "FR");

    /** Useful constant for country.
     */
    static public final Locale GERMANY = createSingleton("de_DE_", "de", "DE");

    /** Useful constant for country.
     */
    static public final Locale ITALY = createSingleton("it_IT_", "it", "IT");

    /** Useful constant for country.
     */
    static public final Locale JAPAN = createSingleton("ja_JP_", "ja", "JP");

    /** Useful constant for country.
     */
    static public final Locale KOREA = createSingleton("ko_KR_", "ko", "KR");

    /** Useful constant for country.
     */
    static public final Locale CHINA = SIMPLIFIED_CHINESE;

    /** Useful constant for country.
     */
    static public final Locale PRC = SIMPLIFIED_CHINESE;

    /** Useful constant for country.
     */
    static public final Locale TAIWAN = TRADITIONAL_CHINESE;

    /** Useful constant for country.
     */
    static public final Locale UK = createSingleton("en_GB_", "en", "GB");

    /** Useful constant for country.
     */
    static public final Locale US = createSingleton("en_US_", "en", "US");

    /** Useful constant for country.
     */
    static public final Locale CANADA = createSingleton("en_CA_", "en", "CA");

    /** Useful constant for country.
     */
    static public final Locale CANADA_FRENCH = createSingleton("fr_CA_", "fr", "CA");

    /**
     * Useful constant for the root locale.  The root locale is the locale whose
     * language, country, and variant are empty ("") strings.  This is regarded
     * as the base locale of all locales, and is used as the language/country
     * neutral locale for the locale sensitive operations.
     *
     * @since 1.6
     */
    static public final Locale ROOT = createSingleton("__", "", "");

    /** serialization ID
     */
    static final long serialVersionUID = 9149081749638150636L;

    /**
     * Display types for retrieving localized names from the name providers.
     */
    private static final int DISPLAY_LANGUAGE = 0;
    private static final int DISPLAY_COUNTRY  = 1;
    private static final int DISPLAY_VARIANT  = 2;

    /**
     * Construct a locale from language, country, variant.
     * NOTE:  ISO 639 is not a stable standard; some of the language codes it defines
     * (specifically iw, ji, and in) have changed.  This constructor accepts both the
     * old codes (iw, ji, and in) and the new codes (he, yi, and id), but all other
     * API on Locale will return only the OLD codes.
     * @param language lowercase two-letter ISO-639 code.
     * @param country uppercase two-letter ISO-3166 code.
     * @param variant vendor and browser specific code. See class description.
     * @exception NullPointerException thrown if any argument is null.
     */
    public Locale(String language, String country, String variant) {
        this.language = convertOldISOCodes(language);
        this.country = toUpperCase(country).intern();
        this.variant = variant.intern();
    }

    /**
     * Construct a locale from language, country.
     * NOTE:  ISO 639 is not a stable standard; some of the language codes it defines
     * (specifically iw, ji, and in) have changed.  This constructor accepts both the
     * old codes (iw, ji, and in) and the new codes (he, yi, and id), but all other
     * API on Locale will return only the OLD codes.
     * @param language lowercase two-letter ISO-639 code.
     * @param country uppercase two-letter ISO-3166 code.
     * @exception NullPointerException thrown if either argument is null.
     */
    public Locale(String language, String country) {
        this(language, country, "");
    }

    /**
     * Construct a locale from a language code.
     * NOTE:  ISO 639 is not a stable standard; some of the language codes it defines
     * (specifically iw, ji, and in) have changed.  This constructor accepts both the
     * old codes (iw, ji, and in) and the new codes (he, yi, and id), but all other
     * API on Locale will return only the OLD codes.
     * @param language lowercase two-letter ISO-639 code.
     * @exception NullPointerException thrown if argument is null.
     * @since 1.4
     */
    public Locale(String language) {
        this(language, "", "");
    }

    /**
     * Constructs a <code>Locale</code> using <code>language</code>
     * and <code>country</code>.  This constructor assumes that
     * <code>language</code> and <code>contry</code> are interned and
     * it is invoked by createSingleton only. (flag is just for
     * avoiding the conflict with the public constructors.
     */
    private Locale(String language, String country, boolean flag) {
        this.language = language;
        this.country = country;
        this.variant = "";
    }

    /**
     * Creates a <code>Locale</code> instance with the given
     * <code>language</code> and <code>counry</code> and puts the
     * instance under the given <code>key</code> in the cache. This
     * method must be called only when initializing the Locale
     * constants.
     */
    private static Locale createSingleton(String key, String language, String country) {
        Locale locale = new Locale(language, country, false);
        cache.put(key, locale);
        return locale;
    }

    /**
     * Returns a <code>Locale</code> constructed from the given
     * <code>language</code>, <code>country</code> and
     * <code>variant</code>. If the same <code>Locale</code> instance
     * is available in the cache, then that instance is
     * returned. Otherwise, a new <code>Locale</code> instance is
     * created and cached.
     *
     * @param language lowercase two-letter ISO-639 code.
     * @param country uppercase two-letter ISO-3166 code.
     * @param variant vendor and browser specific code. See class description.
     * @return the <code>Locale</code> instance requested
     * @exception NullPointerException if any argument is null.
     */
    static Locale getInstance(String language, String country, String variant) {
        if (language== null || country == null || variant == null) {
            throw new NullPointerException();
        }

        StringBuilder sb = new StringBuilder();
        sb.append(language).append('_').append(country).append('_').append(variant);
        String key = sb.toString();
        Locale locale = cache.get(key);
        if (locale == null) {
            locale = new Locale(language, country, variant);
            Locale l = cache.putIfAbsent(key, locale);
            if (l != null) {
                locale = l;
            }
        }
        return locale;
    }

    /**
     * Gets the current value of the default locale for this instance
     * of the Java Virtual Machine.
     * <p>
     * The Java Virtual Machine sets the default locale during startup
     * based on the host environment. It is used by many locale-sensitive
     * methods if no locale is explicitly specified.
     * It can be changed using the
     * {@link #setDefault(java.util.Locale) setDefault} method.
     *
     * @return the default locale for this instance of the Java Virtual Machine
     */
    public static Locale getDefault() {
        // do not synchronize this method - see 4071298
        // it's OK if more than one default locale happens to be created
        if (defaultLocale == null) {
            String language, region, country, variant;
            language = AccessController.doPrivileged(
                new GetPropertyAction("user.language", "en"));
            // for compatibility, check for old user.region property
            region = AccessController.doPrivileged(
                new GetPropertyAction("user.region"));
            if (region != null) {
                // region can be of form country, country_variant, or _variant
                int i = region.indexOf('_');
                if (i >= 0) {
                    country = region.substring(0, i);
                    variant = region.substring(i + 1);
                } else {
                    country = region;
                    variant = "";
                }
            } else {
                country = AccessController.doPrivileged(
                    new GetPropertyAction("user.country", ""));
                variant = AccessController.doPrivileged(
                    new GetPropertyAction("user.variant", ""));
            }
            defaultLocale = getInstance(language, country, variant);
        }
        return defaultLocale;
    }

    /**
     * Sets the default locale for this instance of the Java Virtual Machine.
     * This does not affect the host locale.
     * <p>
     * If there is a security manager, its <code>checkPermission</code>
     * method is called with a <code>PropertyPermission("user.language", "write")</code>
     * permission before the default locale is changed.
     * <p>
     * The Java Virtual Machine sets the default locale during startup
     * based on the host environment. It is used by many locale-sensitive
     * methods if no locale is explicitly specified.
     * <p>
     * Since changing the default locale may affect many different areas
     * of functionality, this method should only be used if the caller
     * is prepared to reinitialize locale-sensitive code running
     * within the same Java Virtual Machine.
     *
     * @throws SecurityException
     *        if a security manager exists and its
     *        <code>checkPermission</code> method doesn't allow the operation.
     * @throws NullPointerException if <code>newLocale</code> is null
     * @param newLocale the new default locale
     * @see SecurityManager#checkPermission
     * @see java.util.PropertyPermission
     */
    public static synchronized void setDefault(Locale newLocale) {
        if (newLocale == null)
            throw new NullPointerException("Can't set default locale to NULL");

        SecurityManager sm = System.getSecurityManager();
        if (sm != null) sm.checkPermission(new PropertyPermission
                        ("user.language", "write"));
            defaultLocale = newLocale;
    }

    /**
     * Returns an array of all installed locales.
     * The returned array represents the union of locales supported
     * by the Java runtime environment and by installed
     * {@link java.util.spi.LocaleServiceProvider LocaleServiceProvider}
     * implementations.  It must contain at least a <code>Locale</code>
     * instance equal to {@link java.util.Locale#US Locale.US}.
     *
     * @return An array of installed locales.
     */
    public static Locale[] getAvailableLocales() {
        return LocaleServiceProviderPool.getAllAvailableLocales();
    }

    /**
     * Returns a list of all 2-letter country codes defined in ISO 3166.
     * Can be used to create Locales.
     */
    public static String[] getISOCountries() {
        if (isoCountries == null) {
            isoCountries = getISO2Table(LocaleISOData.isoCountryTable);
        }
        String[] result = new String[isoCountries.length];
        System.arraycopy(isoCountries, 0, result, 0, isoCountries.length);
        return result;
    }

    /**
     * Returns a list of all 2-letter language codes defined in ISO 639.
     * Can be used to create Locales.
     * [NOTE:  ISO 639 is not a stable standard-- some languages' codes have changed.
     * The list this function returns includes both the new and the old codes for the
     * languages whose codes have changed.]
     */
    public static String[] getISOLanguages() {
        if (isoLanguages == null) {
            isoLanguages = getISO2Table(LocaleISOData.isoLanguageTable);
        }
        String[] result = new String[isoLanguages.length];
        System.arraycopy(isoLanguages, 0, result, 0, isoLanguages.length);
        return result;
    }

    private static final String[] getISO2Table(String table) {
        int len = table.length() / 5;
        String[] isoTable = new String[len];
        for (int i = 0, j = 0; i < len; i++, j += 5) {
            isoTable[i] = table.substring(j, j + 2);
        }
        return isoTable;
    }

    /**
     * Returns the language code for this locale, which will either be the empty string
     * or a lowercase ISO 639 code.
     * <p>NOTE:  ISO 639 is not a stable standard-- some languages' codes have changed.
     * Locale's constructor recognizes both the new and the old codes for the languages
     * whose codes have changed, but this function always returns the old code.  If you
     * want to check for a specific language whose code has changed, don't do <pre>
     * if (locale.getLanguage().equals("he"))
     *    ...
     * </pre>Instead, do<pre>
     * if (locale.getLanguage().equals(new Locale("he", "", "").getLanguage()))
     *    ...</pre>
     * @see #getDisplayLanguage
     */
    public String getLanguage() {
        return language;
    }

    /**
     * Returns the country/region code for this locale, which will
     * either be the empty string or an uppercase ISO 3166 2-letter code.
     * @see #getDisplayCountry
     */
    public String getCountry() {
        return country;
    }

    /**
     * Returns the variant code for this locale.
     * @see #getDisplayVariant
     */
    public String getVariant() {
        return variant;
    }

    /**
     * Getter for the programmatic name of the entire locale,
     * with the language, country and variant separated by underbars.
     * Language is always lower case, and country is always upper case.
     * If the language is missing, the string will begin with an underbar.
     * If both the language and country fields are missing, this function
     * will return the empty string, even if the variant field is filled in
     * (you can't have a locale with just a variant-- the variant must accompany
     * a valid language or country code).
     * Examples: "en", "de_DE", "_GB", "en_US_WIN", "de__POSIX", "fr__MAC"
     * @see #getDisplayName
     */
    public final String toString() {
        boolean l = language.length() != 0;
        boolean c = country.length() != 0;
        boolean v = variant.length() != 0;
        StringBuilder result = new StringBuilder(language);
        if (c||(l&&v)) {
            result.append('_').append(country); // This may just append '_'
        }
        if (v&&(l||c)) {
            result.append('_').append(variant);
        }
        return result.toString();
    }

    /**
     * Returns a three-letter abbreviation for this locale's language.  If the locale
     * doesn't specify a language, this will be the empty string.  Otherwise, this will
     * be a lowercase ISO 639-2/T language code.
     * The ISO 639-2 language codes can be found on-line at
     * <a href="http://www.loc.gov/standards/iso639-2/englangn.html">
     * <code>http://www.loc.gov/standards/iso639-2/englangn.html</code>.</a>
     * @exception MissingResourceException Throws MissingResourceException if the
     * three-letter language abbreviation is not available for this locale.
     */
    public String getISO3Language() throws MissingResourceException {
        String language3 = getISO3Code(language, LocaleISOData.isoLanguageTable);
        if (language3 == null) {
            throw new MissingResourceException("Couldn't find 3-letter language code for "
                    + language, "FormatData_" + toString(), "ShortLanguage");
        }
        return language3;
    }

    /**
     * Returns a three-letter abbreviation for this locale's country.  If the locale
     * doesn't specify a country, this will be the empty string.  Otherwise, this will
     * be an uppercase ISO 3166 3-letter country code.
     * The ISO 3166-2 country codes can be found on-line at
     * <a href="http://www.davros.org/misc/iso3166.txt">
     * <code>http://www.davros.org/misc/iso3166.txt</code>.</a>
     * @exception MissingResourceException Throws MissingResourceException if the
     * three-letter country abbreviation is not available for this locale.
     */
    public String getISO3Country() throws MissingResourceException {
        String country3 = getISO3Code(country, LocaleISOData.isoCountryTable);
        if (country3 == null) {
            throw new MissingResourceException("Couldn't find 3-letter country code for "
                    + country, "FormatData_" + toString(), "ShortCountry");
        }
        return country3;
    }

    private static final String getISO3Code(String iso2Code, String table) {
        int codeLength = iso2Code.length();
        if (codeLength == 0) {
            return "";
        }

        int tableLength = table.length();
        int index = tableLength;
        if (codeLength == 2) {
            char c1 = iso2Code.charAt(0);
            char c2 = iso2Code.charAt(1);
            for (index = 0; index < tableLength; index += 5) {
                if (table.charAt(index) == c1
                    && table.charAt(index + 1) == c2) {
                    break;
                }
            }
        }
        return index < tableLength ? table.substring(index + 2, index + 5) : null;
    }

    /**
     * Returns a name for the locale's language that is appropriate for display to the
     * user.
     * If possible, the name returned will be localized for the default locale.
     * For example, if the locale is fr_FR and the default locale
     * is en_US, getDisplayLanguage() will return "French"; if the locale is en_US and
     * the default locale is fr_FR, getDisplayLanguage() will return "anglais".
     * If the name returned cannot be localized for the default locale,
     * (say, we don't have a Japanese name for Croatian),
     * this function falls back on the English name, and uses the ISO code as a last-resort
     * value.  If the locale doesn't specify a language, this function returns the empty string.
     */
    public final String getDisplayLanguage() {
        return getDisplayLanguage(getDefault());
    }

    /**
     * Returns a name for the locale's language that is appropriate for display to the
     * user.
     * If possible, the name returned will be localized according to inLocale.
     * For example, if the locale is fr_FR and inLocale
     * is en_US, getDisplayLanguage() will return "French"; if the locale is en_US and
     * inLocale is fr_FR, getDisplayLanguage() will return "anglais".
     * If the name returned cannot be localized according to inLocale,
     * (say, we don't have a Japanese name for Croatian),
     * this function falls back on the English name, and finally
     * on the ISO code as a last-resort value.  If the locale doesn't specify a language,
     * this function returns the empty string.
     *
     * @exception NullPointerException if <code>inLocale</code> is <code>null</code>
     */
    public String getDisplayLanguage(Locale inLocale) {
        return getDisplayString(language, inLocale, DISPLAY_LANGUAGE);
    }

    /**
     * Returns a name for the locale's country that is appropriate for display to the
     * user.
     * If possible, the name returned will be localized for the default locale.
     * For example, if the locale is fr_FR and the default locale
     * is en_US, getDisplayCountry() will return "France"; if the locale is en_US and
     * the default locale is fr_FR, getDisplayCountry() will return "Etats-Unis".
     * If the name returned cannot be localized for the default locale,
     * (say, we don't have a Japanese name for Croatia),
     * this function falls back on the English name, and uses the ISO code as a last-resort
     * value.  If the locale doesn't specify a country, this function returns the empty string.
     */
    public final String getDisplayCountry() {
        return getDisplayCountry(getDefault());
    }

    /**
     * Returns a name for the locale's country that is appropriate for display to the
     * user.
     * If possible, the name returned will be localized according to inLocale.
     * For example, if the locale is fr_FR and inLocale
     * is en_US, getDisplayCountry() will return "France"; if the locale is en_US and
     * inLocale is fr_FR, getDisplayCountry() will return "Etats-Unis".
     * If the name returned cannot be localized according to inLocale.
     * (say, we don't have a Japanese name for Croatia),
     * this function falls back on the English name, and finally
     * on the ISO code as a last-resort value.  If the locale doesn't specify a country,
     * this function returns the empty string.
     *
     * @exception NullPointerException if <code>inLocale</code> is <code>null</code>
     */
    public String getDisplayCountry(Locale inLocale) {
        return getDisplayString(country, inLocale, DISPLAY_COUNTRY);
    }

    private String getDisplayString(String code, Locale inLocale, int type) {
        if (code.length() == 0) {
            return "";
        }

        if (inLocale == null) {
            throw new NullPointerException();
        }

        try {
            OpenListResourceBundle bundle = LocaleData.getLocaleNames(inLocale);
            String key = (type == DISPLAY_VARIANT ? "%%"+code : code);
            String result = null;

            // Check whether a provider can provide an implementation that's closer
            // to the requested locale than what the Java runtime itself can provide.
            LocaleServiceProviderPool pool =
                LocaleServiceProviderPool.getPool(LocaleNameProvider.class);
            if (pool.hasProviders()) {
                result = pool.getLocalizedObject(
                                    LocaleNameGetter.INSTANCE,
                                    inLocale, bundle, key,
                                    type, code);
            }

            if (result == null) {
                result = bundle.getString(key);
            }

            if (result != null) {
                return result;
            }
        }
        catch (Exception e) {
            // just fall through
        }
        return code;
    }

    /**
     * Returns a name for the locale's variant code that is appropriate for display to the
     * user.  If possible, the name will be localized for the default locale.  If the locale
     * doesn't specify a variant code, this function returns the empty string.
     */
    public final String getDisplayVariant() {
        return getDisplayVariant(getDefault());
    }

    /**
     * Returns a name for the locale's variant code that is appropriate for display to the
     * user.  If possible, the name will be localized for inLocale.  If the locale
     * doesn't specify a variant code, this function returns the empty string.
     *
     * @exception NullPointerException if <code>inLocale</code> is <code>null</code>
     */
    public String getDisplayVariant(Locale inLocale) {
        if (variant.length() == 0)
            return "";

        OpenListResourceBundle bundle = LocaleData.getLocaleNames(inLocale);

        String names[] = getDisplayVariantArray(bundle, inLocale);

        // Get the localized patterns for formatting a list, and use
        // them to format the list.
        String listPattern = null;
        String listCompositionPattern = null;
        try {
            listPattern = bundle.getString("ListPattern");
            listCompositionPattern = bundle.getString("ListCompositionPattern");
        } catch (MissingResourceException e) {
        }
        return formatList(names, listPattern, listCompositionPattern);
    }

    /**
     * Returns a name for the locale that is appropriate for display to the
     * user.  This will be the values returned by getDisplayLanguage(), getDisplayCountry(),
     * and getDisplayVariant() assembled into a single string.  The display name will have
     * one of the following forms:<p><blockquote>
     * language (country, variant)<p>
     * language (country)<p>
     * language (variant)<p>
     * country (variant)<p>
     * language<p>
     * country<p>
     * variant<p></blockquote>
     * depending on which fields are specified in the locale.  If the language, country,
     * and variant fields are all empty, this function returns the empty string.
     */
    public final String getDisplayName() {
        return getDisplayName(getDefault());
    }

    /**
     * Returns a name for the locale that is appropriate for display to the
     * user.  This will be the values returned by getDisplayLanguage(), getDisplayCountry(),
     * and getDisplayVariant() assembled into a single string.  The display name will have
     * one of the following forms:<p><blockquote>
     * language (country, variant)<p>
     * language (country)<p>
     * language (variant)<p>
     * country (variant)<p>
     * language<p>
     * country<p>
     * variant<p></blockquote>
     * depending on which fields are specified in the locale.  If the language, country,
     * and variant fields are all empty, this function returns the empty string.
     *
     * @exception NullPointerException if <code>inLocale</code> is <code>null</code>
     */
    public String getDisplayName(Locale inLocale) {
        OpenListResourceBundle bundle = LocaleData.getLocaleNames(inLocale);

        String languageName = getDisplayLanguage(inLocale);
        String countryName = getDisplayCountry(inLocale);
        String[] variantNames = getDisplayVariantArray(bundle, inLocale);

        // Get the localized patterns for formatting a display name.
        String displayNamePattern = null;
        String listPattern = null;
        String listCompositionPattern = null;
        try {
            displayNamePattern = bundle.getString("DisplayNamePattern");
            listPattern = bundle.getString("ListPattern");
            listCompositionPattern = bundle.getString("ListCompositionPattern");
        } catch (MissingResourceException e) {
        }

        // The display name consists of a main name, followed by qualifiers.
        // Typically, the format is "MainName (Qualifier, Qualifier)" but this
        // depends on what pattern is stored in the display locale.
        String   mainName       = null;
        String[] qualifierNames = null;

        // The main name is the language, or if there is no language, the country.
        // If there is neither language nor country (an anomalous situation) then
        // the display name is simply the variant's display name.
        if (languageName.length() != 0) {
            mainName = languageName;
            if (countryName.length() != 0) {
                qualifierNames = new String[variantNames.length + 1];
                System.arraycopy(variantNames, 0, qualifierNames, 1, variantNames.length);
                qualifierNames[0] = countryName;
            }
            else qualifierNames = variantNames;
        }
        else if (countryName.length() != 0) {
            mainName = countryName;
            qualifierNames = variantNames;
        }
        else {
            return formatList(variantNames, listPattern, listCompositionPattern);
        }

        // Create an array whose first element is the number of remaining
        // elements.  This serves as a selector into a ChoiceFormat pattern from
        // the resource.  The second and third elements are the main name and
        // the qualifier; if there are no qualifiers, the third element is
        // unused by the format pattern.
        Object[] displayNames = {
            new Integer(qualifierNames.length != 0 ? 2 : 1),
            mainName,
            // We could also just call formatList() and have it handle the empty
            // list case, but this is more efficient, and we want it to be
            // efficient since all the language-only locales will not have any
            // qualifiers.
            qualifierNames.length != 0 ? formatList(qualifierNames, listPattern, listCompositionPattern) : null
        };

        if (displayNamePattern != null) {
            return new MessageFormat(displayNamePattern).format(displayNames);
        }
        else {
            // If we cannot get the message format pattern, then we use a simple
            // hard-coded pattern.  This should not occur in practice unless the
            // installation is missing some core files (FormatData etc.).
            StringBuilder result = new StringBuilder();
            result.append((String)displayNames[1]);
            if (displayNames.length > 2) {
                result.append(" (");
                result.append((String)displayNames[2]);
                result.append(')');
            }
            return result.toString();
        }
    }

    /**
     * Overrides Cloneable
     */
    public Object clone()
    {
        try {
            Locale that = (Locale)super.clone();
            return that;
        } catch (CloneNotSupportedException e) {
            throw new InternalError();
        }
    }

    /**
     * Override hashCode.
     * Since Locales are often used in hashtables, caches the value
     * for speed.
     */
    public int hashCode() {
        int hc = hashCodeValue;
        if (hc == 0) {
            hc = (language.hashCode() << 8) ^ country.hashCode() ^ (variant.hashCode() << 4);
            hashCodeValue = hc;
        }
        return hc;
    }

    // Overrides

    /**
     * Returns true if this Locale is equal to another object.  A Locale is
     * deemed equal to another Locale with identical language, country,
     * and variant, and unequal to all other objects.
     *
     * @return true if this Locale is equal to the specified object.
     */

    public boolean equals(Object obj) {
        if (this == obj)                      // quick check
            return true;
        if (!(obj instanceof Locale))
            return false;
        Locale other = (Locale) obj;
        return language == other.language
            && country == other.country
            && variant == other.variant;
    }

    // ================= privates =====================================

    // XXX instance and class variables. For now keep these separate, since it is
    // faster to match. Later, make into single string.

    /**
     * @serial
     * @see #getLanguage
     */
    private final String language;

    /**
     * @serial
     * @see #getCountry
     */
    private final String country;

    /**
     * @serial
     * @see #getVariant
     */
    private final String variant;

    /**
     * Placeholder for the object's hash code.  Always -1.
     * @serial
     */
    private volatile int hashcode = -1;        // lazy evaluate

    /**
     * Calculated hashcode to fix 4518797.
     */
    private transient volatile int hashCodeValue = 0;

    private static Locale defaultLocale = null;

    /**
     * Return an array of the display names of the variant.
     * @param bundle the ResourceBundle to use to get the display names
     * @return an array of display names, possible of zero length.
     */
    private String[] getDisplayVariantArray(OpenListResourceBundle bundle, Locale inLocale) {
        // Split the variant name into tokens separated by '_'.
        StringTokenizer tokenizer = new StringTokenizer(variant, "_");
        String[] names = new String[tokenizer.countTokens()];

        // For each variant token, lookup the display name.  If
        // not found, use the variant name itself.
        for (int i=0; i<names.length; ++i) {
            names[i] = getDisplayString(tokenizer.nextToken(),
                                inLocale, DISPLAY_VARIANT);
        }

        return names;
    }

    /**
     * Format a list using given pattern strings.
     * If either of the patterns is null, then a the list is
     * formatted by concatenation with the delimiter ','.
     * @param stringList the list of strings to be formatted.
     * @param listPattern should create a MessageFormat taking 0-3 arguments
     * and formatting them into a list.
     * @param listCompositionPattern should take 2 arguments
     * and is used by composeList.
     * @return a string representing the list.
     */
    private static String formatList(String[] stringList, String listPattern, String listCompositionPattern) {
        // If we have no list patterns, compose the list in a simple,
        // non-localized way.
        if (listPattern == null || listCompositionPattern == null) {
            StringBuffer result = new StringBuffer();
            for (int i=0; i<stringList.length; ++i) {
                if (i>0) result.append(',');
                result.append(stringList[i]);
            }
            return result.toString();
        }

        // Compose the list down to three elements if necessary
        if (stringList.length > 3) {
            MessageFormat format = new MessageFormat(listCompositionPattern);
            stringList = composeList(format, stringList);
        }

        // Rebuild the argument list with the list length as the first element
        Object[] args = new Object[stringList.length + 1];
        System.arraycopy(stringList, 0, args, 1, stringList.length);
        args[0] = new Integer(stringList.length);

        // Format it using the pattern in the resource
        MessageFormat format = new MessageFormat(listPattern);
        return format.format(args);
    }

    /**
     * Given a list of strings, return a list shortened to three elements.
     * Shorten it by applying the given format to the first two elements
     * recursively.
     * @param format a format which takes two arguments
     * @param list a list of strings
     * @return if the list is three elements or shorter, the same list;
     * otherwise, a new list of three elements.
     */
    private static String[] composeList(MessageFormat format, String[] list) {
        if (list.length <= 3) return list;

        // Use the given format to compose the first two elements into one
        String[] listItems = { list[0], list[1] };
        String newItem = format.format(listItems);

        // Form a new list one element shorter
        String[] newList = new String[list.length-1];
        System.arraycopy(list, 2, newList, 1, newList.length-1);
        newList[0] = newItem;

        // Recurse
        return composeList(format, newList);
    }

    /**
     * Replace the deserialized Locale object with a newly
     * created object. Newer language codes are replaced with older ISO
     * codes. The country and variant codes are replaced with internalized
     * String copies.
     */
    private Object readResolve() throws java.io.ObjectStreamException {
        return getInstance(language, country, variant);
    }

    private static volatile String[] isoLanguages = null;

    private static volatile String[] isoCountries = null;

    /*
     * Locale needs its own, locale insensitive version of toLowerCase to
     * avoid circularity problems between Locale and String.
     * The most straightforward algorithm is used. Look at optimizations later.
     */
    private String toLowerCase(String str) {
        char[] buf = new char[str.length()];
        for (int i = 0; i < buf.length; i++) {
            buf[i] = Character.toLowerCase(str.charAt(i));
        }
        return new String( buf );
    }

    /*
     * Locale needs its own, locale insensitive version of toUpperCase to
     * avoid circularity problems between Locale and String.
     * The most straightforward algorithm is used. Look at optimizations later.
     */
    private String toUpperCase(String str) {
        char[] buf = new char[str.length()];
        for (int i = 0; i < buf.length; i++) {
            buf[i] = Character.toUpperCase(str.charAt(i));
        }
        return new String( buf );
    }

    private String convertOldISOCodes(String language) {
        // we accept both the old and the new ISO codes for the languages whose ISO
        // codes have changed, but we always store the OLD code, for backward compatibility
        language = toLowerCase(language).intern();
        if (language == "he") {
            return "iw";
        } else if (language == "yi") {
            return "ji";
        } else if (language == "id") {
            return "in";
        } else {
            return language;
        }
    }

    /**
     * Obtains a localized locale names from a LocaleNameProvider
     * implementation.
     */
    private static class LocaleNameGetter
        implements LocaleServiceProviderPool.LocalizedObjectGetter<LocaleNameProvider, String> {
        private static final LocaleNameGetter INSTANCE = new LocaleNameGetter();

        public String getObject(LocaleNameProvider localeNameProvider,
                                Locale locale,
                                String key,
                                Object... params) {
            assert params.length == 2;
            int type = (Integer)params[0];
            String code = (String)params[1];

            switch(type) {
            case DISPLAY_LANGUAGE:
                return localeNameProvider.getDisplayLanguage(code, locale);
            case DISPLAY_COUNTRY:
                return localeNameProvider.getDisplayCountry(code, locale);
            case DISPLAY_VARIANT:
                return localeNameProvider.getDisplayVariant(code, locale);
            default:
                assert false; // shouldn't happen
            }

            return null;
        }
    }
}
