/*
 * Copyright (c) 2000, 2005, Oracle and/or its affiliates. All rights reserved.
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

package java.util;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.Serializable;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.spi.CurrencyNameProvider;
import java.util.spi.LocaleServiceProvider;
import sun.util.LocaleServiceProviderPool;
import sun.util.logging.PlatformLogger;
import sun.util.resources.LocaleData;
import sun.util.resources.OpenListResourceBundle;


/**
 * Represents a currency. Currencies are identified by their ISO 4217 currency
 * codes. Visit the <a href="http://www.iso.org/iso/en/prods-services/popstds/currencycodes.html">
 * ISO web site</a> for more information, including a table of
 * currency codes.
 * <p>
 * The class is designed so that there's never more than one
 * <code>Currency</code> instance for any given currency. Therefore, there's
 * no public constructor. You obtain a <code>Currency</code> instance using
 * the <code>getInstance</code> methods.
 * <p>
 * Users can supersede the Java runtime currency data by creating a properties
 * file named <code>&lt;JAVA_HOME&gt;/lib/currency.properties</code>.  The contents
 * of the properties file are key/value pairs of the ISO 3166 country codes
 * and the ISO 4217 currency data respectively.  The value part consists of
 * three ISO 4217 values of a currency, i.e., an alphabetic code, a numeric
 * code, and a minor unit.  Those three ISO 4217 values are separated by commas.
 * The lines which start with '#'s are considered comment lines.  For example,
 * <p>
 * <code>
 * #Sample currency properties<br>
 * JP=JPZ,999,0
 * </code>
 * <p>
 * will supersede the currency data for Japan.
 *
 * @since 1.4
 */
public final class Currency implements Serializable {

    private static final long serialVersionUID = -158308464356906721L;

    /**
     * ISO 4217 currency code for this currency.
     *
     * @serial
     */
    private final String currencyCode;

    /**
     * Default fraction digits for this currency.
     * Set from currency data tables.
     */
    transient private final int defaultFractionDigits;

    /**
     * ISO 4217 numeric code for this currency.
     * Set from currency data tables.
     */
    transient private final int numericCode;


    // class data: instance map

    private static HashMap<String, Currency> instances = new HashMap<String, Currency>(7);
    private static HashSet<Currency> available;


    // Class data: currency data obtained from currency.data file.
    // Purpose:
    // - determine valid country codes
    // - determine valid currency codes
    // - map country codes to currency codes
    // - obtain default fraction digits for currency codes
    //
    // sc = special case; dfd = default fraction digits
    // Simple countries are those where the country code is a prefix of the
    // currency code, and there are no known plans to change the currency.
    //
    // table formats:
    // - mainTable:
    //   - maps country code to 32-bit int
    //   - 26*26 entries, corresponding to [A-Z]*[A-Z]
    //   - \u007F -> not valid country
    //   - bits 18-31: unused
    //   - bits 8-17: numeric code (0 to 1023)
    //   - bit 7: 1 - special case, bits 0-4 indicate which one
    //            0 - simple country, bits 0-4 indicate final char of currency code
    //   - bits 5-6: fraction digits for simple countries, 0 for special cases
    //   - bits 0-4: final char for currency code for simple country, or ID of special case
    // - special case IDs:
    //   - 0: country has no currency
    //   - other: index into sc* arrays + 1
    // - scCutOverTimes: cut-over time in millis as returned by
    //   System.currentTimeMillis for special case countries that are changing
    //   currencies; Long.MAX_VALUE for countries that are not changing currencies
    // - scOldCurrencies: old currencies for special case countries
    // - scNewCurrencies: new currencies for special case countries that are
    //   changing currencies; null for others
    // - scOldCurrenciesDFD: default fraction digits for old currencies
    // - scNewCurrenciesDFD: default fraction digits for new currencies, 0 for
    //   countries that are not changing currencies
    // - otherCurrencies: concatenation of all currency codes that are not the
    //   main currency of a simple country, separated by "-"
    // - otherCurrenciesDFD: decimal format digits for currencies in otherCurrencies, same order

    static int formatVersion;
    static int dataVersion;
    static int[] mainTable;
    static long[] scCutOverTimes;
    static String[] scOldCurrencies;
    static String[] scNewCurrencies;
    static int[] scOldCurrenciesDFD;
    static int[] scNewCurrenciesDFD;
    static int[] scOldCurrenciesNumericCode;
    static int[] scNewCurrenciesNumericCode;
    static String otherCurrencies;
    static int[] otherCurrenciesDFD;
    static int[] otherCurrenciesNumericCode;

    // handy constants - must match definitions in GenerateCurrencyData
    // magic number
    private static final int MAGIC_NUMBER = 0x43757244;
    // number of characters from A to Z
    private static final int A_TO_Z = ('Z' - 'A') + 1;
    // entry for invalid country codes
    private static final int INVALID_COUNTRY_ENTRY = 0x007F;
    // entry for countries without currency
    private static final int COUNTRY_WITHOUT_CURRENCY_ENTRY = 0x0080;
    // mask for simple case country entries
    private static final int SIMPLE_CASE_COUNTRY_MASK = 0x0000;
    // mask for simple case country entry final character
    private static final int SIMPLE_CASE_COUNTRY_FINAL_CHAR_MASK = 0x001F;
    // mask for simple case country entry default currency digits
    private static final int SIMPLE_CASE_COUNTRY_DEFAULT_DIGITS_MASK = 0x0060;
    // shift count for simple case country entry default currency digits
    private static final int SIMPLE_CASE_COUNTRY_DEFAULT_DIGITS_SHIFT = 5;
    // mask for special case country entries
    private static final int SPECIAL_CASE_COUNTRY_MASK = 0x0080;
    // mask for special case country index
    private static final int SPECIAL_CASE_COUNTRY_INDEX_MASK = 0x001F;
    // delta from entry index component in main table to index into special case tables
    private static final int SPECIAL_CASE_COUNTRY_INDEX_DELTA = 1;
    // mask for distinguishing simple and special case countries
    private static final int COUNTRY_TYPE_MASK = SIMPLE_CASE_COUNTRY_MASK | SPECIAL_CASE_COUNTRY_MASK;
    // mask for the numeric code of the currency
    private static final int NUMERIC_CODE_MASK = 0x0003FF00;
    // shift count for the numeric code of the currency
    private static final int NUMERIC_CODE_SHIFT = 8;

    // Currency data format version
    private static final int VALID_FORMAT_VERSION = 1;

    static {
        AccessController.doPrivileged(new PrivilegedAction() {
            public Object run() {
                String homeDir = System.getProperty("java.home");
                try {
                    String dataFile = homeDir + File.separator +
                            "lib" + File.separator + "currency.data";
                    DataInputStream dis = new DataInputStream(
                        new BufferedInputStream(
                        new FileInputStream(dataFile)));
                    if (dis.readInt() != MAGIC_NUMBER) {
                        throw new InternalError("Currency data is possibly corrupted");
                    }
                    formatVersion = dis.readInt();
                    if (formatVersion != VALID_FORMAT_VERSION) {
                        throw new InternalError("Currency data format is incorrect");
                    }
                    dataVersion = dis.readInt();
                    mainTable = readIntArray(dis, A_TO_Z * A_TO_Z);
                    int scCount = dis.readInt();
                    scCutOverTimes = readLongArray(dis, scCount);
                    scOldCurrencies = readStringArray(dis, scCount);
                    scNewCurrencies = readStringArray(dis, scCount);
                    scOldCurrenciesDFD = readIntArray(dis, scCount);
                    scNewCurrenciesDFD = readIntArray(dis, scCount);
                    scOldCurrenciesNumericCode = readIntArray(dis, scCount);
                    scNewCurrenciesNumericCode = readIntArray(dis, scCount);
                    int ocCount = dis.readInt();
                    otherCurrencies = dis.readUTF();
                    otherCurrenciesDFD = readIntArray(dis, ocCount);
                    otherCurrenciesNumericCode = readIntArray(dis, ocCount);
                    dis.close();
                } catch (IOException e) {
                    InternalError ie = new InternalError();
                    ie.initCause(e);
                    throw ie;
                }

                // look for the properties file for overrides
                try {
                    File propFile = new File(homeDir + File.separator +
                                             "lib" + File.separator +
                                             "currency.properties");
                    if (propFile.exists()) {
                        Properties props = new Properties();
                        props.load(new FileReader(propFile));
                        Set<String> keys = props.stringPropertyNames();
                        Pattern propertiesPattern =
                            Pattern.compile("([A-Z]{3})\\s*,\\s*(\\d{3})\\s*,\\s*([0-3])");
                        for (String key : keys) {
                           replaceCurrencyData(propertiesPattern,
                               key.toUpperCase(Locale.ROOT),
                               props.getProperty(key).toUpperCase(Locale.ROOT));
                        }
                    }
                } catch (IOException e) {
                    info("currency.properties is ignored because of an IOException", e);
                }
                return null;
            }
        });
    }

    /**
     * Constants for retrieving localized names from the name providers.
     */
    private static final int SYMBOL = 0;
    private static final int DISPLAYNAME = 1;


    /**
     * Constructs a <code>Currency</code> instance. The constructor is private
     * so that we can insure that there's never more than one instance for a
     * given currency.
     */
    private Currency(String currencyCode, int defaultFractionDigits, int numericCode) {
        this.currencyCode = currencyCode;
        this.defaultFractionDigits = defaultFractionDigits;
        this.numericCode = numericCode;
    }

    /**
     * Returns the <code>Currency</code> instance for the given currency code.
     *
     * @param currencyCode the ISO 4217 code of the currency
     * @return the <code>Currency</code> instance for the given currency code
     * @exception NullPointerException if <code>currencyCode</code> is null
     * @exception IllegalArgumentException if <code>currencyCode</code> is not
     * a supported ISO 4217 code.
     */
    public static Currency getInstance(String currencyCode) {
        return getInstance(currencyCode, Integer.MIN_VALUE, 0);
    }

    private static Currency getInstance(String currencyCode, int defaultFractionDigits,
        int numericCode) {
        synchronized (instances) {
            // Try to look up the currency code in the instances table.
            // This does the null pointer check as a side effect.
            // Also, if there already is an entry, the currencyCode must be valid.
            Currency instance = instances.get(currencyCode);
            if (instance != null) {
                return instance;
            }

            if (defaultFractionDigits == Integer.MIN_VALUE) {
                // Currency code not internally generated, need to verify first
                // A currency code must have 3 characters and exist in the main table
                // or in the list of other currencies.
                if (currencyCode.length() != 3) {
                    throw new IllegalArgumentException();
                }
                char char1 = currencyCode.charAt(0);
                char char2 = currencyCode.charAt(1);
                int tableEntry = getMainTableEntry(char1, char2);
                if ((tableEntry & COUNTRY_TYPE_MASK) == SIMPLE_CASE_COUNTRY_MASK
                        && tableEntry != INVALID_COUNTRY_ENTRY
                        && currencyCode.charAt(2) - 'A' == (tableEntry & SIMPLE_CASE_COUNTRY_FINAL_CHAR_MASK)) {
                    defaultFractionDigits = (tableEntry & SIMPLE_CASE_COUNTRY_DEFAULT_DIGITS_MASK) >> SIMPLE_CASE_COUNTRY_DEFAULT_DIGITS_SHIFT;
                    numericCode = (tableEntry & NUMERIC_CODE_MASK) >> NUMERIC_CODE_SHIFT;
                } else {
                    // Check for '-' separately so we don't get false hits in the table.
                    if (currencyCode.charAt(2) == '-') {
                        throw new IllegalArgumentException();
                    }
                    int index = otherCurrencies.indexOf(currencyCode);
                    if (index == -1) {
                        throw new IllegalArgumentException();
                    }
                    defaultFractionDigits = otherCurrenciesDFD[index / 4];
                    numericCode = otherCurrenciesNumericCode[index / 4];
                }
            }

            instance = new Currency(currencyCode, defaultFractionDigits, numericCode);
            instances.put(currencyCode, instance);
            return instance;
        }
    }

    /**
     * Returns the <code>Currency</code> instance for the country of the
     * given locale. The language and variant components of the locale
     * are ignored. The result may vary over time, as countries change their
     * currencies. For example, for the original member countries of the
     * European Monetary Union, the method returns the old national currencies
     * until December 31, 2001, and the Euro from January 1, 2002, local time
     * of the respective countries.
     * <p>
     * The method returns <code>null</code> for territories that don't
     * have a currency, such as Antarctica.
     *
     * @param locale the locale for whose country a <code>Currency</code>
     * instance is needed
     * @return the <code>Currency</code> instance for the country of the given
     * locale, or null
     * @exception NullPointerException if <code>locale</code> or its country
     * code is null
     * @exception IllegalArgumentException if the country of the given locale
     * is not a supported ISO 3166 country code.
     */
    public static Currency getInstance(Locale locale) {
        String country = locale.getCountry();
        if (country == null) {
            throw new NullPointerException();
        }

        if (country.length() != 2) {
            throw new IllegalArgumentException();
        }

        char char1 = country.charAt(0);
        char char2 = country.charAt(1);
        int tableEntry = getMainTableEntry(char1, char2);
        if ((tableEntry & COUNTRY_TYPE_MASK) == SIMPLE_CASE_COUNTRY_MASK
                    && tableEntry != INVALID_COUNTRY_ENTRY) {
            char finalChar = (char) ((tableEntry & SIMPLE_CASE_COUNTRY_FINAL_CHAR_MASK) + 'A');
            int defaultFractionDigits = (tableEntry & SIMPLE_CASE_COUNTRY_DEFAULT_DIGITS_MASK) >> SIMPLE_CASE_COUNTRY_DEFAULT_DIGITS_SHIFT;
            int numericCode = (tableEntry & NUMERIC_CODE_MASK) >> NUMERIC_CODE_SHIFT;
            StringBuffer sb = new StringBuffer(country);
            sb.append(finalChar);
            return getInstance(sb.toString(), defaultFractionDigits, numericCode);
        } else {
            // special cases
            if (tableEntry == INVALID_COUNTRY_ENTRY) {
                throw new IllegalArgumentException();
            }
            if (tableEntry == COUNTRY_WITHOUT_CURRENCY_ENTRY) {
                return null;
            } else {
                int index = (tableEntry & SPECIAL_CASE_COUNTRY_INDEX_MASK) - SPECIAL_CASE_COUNTRY_INDEX_DELTA;
                if (scCutOverTimes[index] == Long.MAX_VALUE || System.currentTimeMillis() < scCutOverTimes[index]) {
                    return getInstance(scOldCurrencies[index], scOldCurrenciesDFD[index],
                        scOldCurrenciesNumericCode[index]);
                } else {
                    return getInstance(scNewCurrencies[index], scNewCurrenciesDFD[index],
                        scNewCurrenciesNumericCode[index]);
                }
            }
        }
    }

    /**
     * Gets the set of available currencies.  The returned set of currencies
     * contains all of the available currencies, which may include currencies
     * that represent obsolete ISO 4217 codes.  The set can be modified
     * without affecting the available currencies in the runtime.
     *
     * @return the set of available currencies.  If there is no currency
     *    available in the runtime, the returned set is empty.
     * @since 1.7
     */
    public static Set<Currency> getAvailableCurrencies() {
        synchronized(Currency.class) {
            if (available == null) {
                available = new HashSet<Currency>(256);

                // Add simple currencies first
                for (char c1 = 'A'; c1 <= 'Z'; c1 ++) {
                    for (char c2 = 'A'; c2 <= 'Z'; c2 ++) {
                        int tableEntry = getMainTableEntry(c1, c2);
                        if ((tableEntry & COUNTRY_TYPE_MASK) == SIMPLE_CASE_COUNTRY_MASK
                             && tableEntry != INVALID_COUNTRY_ENTRY) {
                            char finalChar = (char) ((tableEntry & SIMPLE_CASE_COUNTRY_FINAL_CHAR_MASK) + 'A');
                            int defaultFractionDigits = (tableEntry & SIMPLE_CASE_COUNTRY_DEFAULT_DIGITS_MASK) >> SIMPLE_CASE_COUNTRY_DEFAULT_DIGITS_SHIFT;
                            int numericCode = (tableEntry & NUMERIC_CODE_MASK) >> NUMERIC_CODE_SHIFT;
                            StringBuilder sb = new StringBuilder();
                            sb.append(c1);
                            sb.append(c2);
                            sb.append(finalChar);
                            available.add(getInstance(sb.toString(), defaultFractionDigits, numericCode));
                        }
                    }
                }

                // Now add other currencies
                StringTokenizer st = new StringTokenizer(otherCurrencies, "-");
                while (st.hasMoreElements()) {
                    available.add(getInstance((String)st.nextElement()));
                }
            }
        }

        return (Set<Currency>) available.clone();
    }

    /**
     * Gets the ISO 4217 currency code of this currency.
     *
     * @return the ISO 4217 currency code of this currency.
     */
    public String getCurrencyCode() {
        return currencyCode;
    }

    /**
     * Gets the symbol of this currency for the default locale.
     * For example, for the US Dollar, the symbol is "$" if the default
     * locale is the US, while for other locales it may be "US$". If no
     * symbol can be determined, the ISO 4217 currency code is returned.
     *
     * @return the symbol of this currency for the default locale
     */
    public String getSymbol() {
        return getSymbol(Locale.getDefault(Locale.Category.DISPLAY));
    }

    /**
     * Gets the symbol of this currency for the specified locale.
     * For example, for the US Dollar, the symbol is "$" if the specified
     * locale is the US, while for other locales it may be "US$". If no
     * symbol can be determined, the ISO 4217 currency code is returned.
     *
     * @param locale the locale for which a display name for this currency is
     * needed
     * @return the symbol of this currency for the specified locale
     * @exception NullPointerException if <code>locale</code> is null
     */
    public String getSymbol(Locale locale) {
        try {
            // Check whether a provider can provide an implementation that's closer
            // to the requested locale than what the Java runtime itself can provide.
            LocaleServiceProviderPool pool =
                LocaleServiceProviderPool.getPool(CurrencyNameProvider.class);

            if (pool.hasProviders()) {
                // Assuming that all the country locales include necessary currency
                // symbols in the Java runtime's resources,  so there is no need to
                // examine whether Java runtime's currency resource bundle is missing
                // names.  Therefore, no resource bundle is provided for calling this
                // method.
                String symbol = pool.getLocalizedObject(
                                    CurrencyNameGetter.INSTANCE,
                                    locale, (OpenListResourceBundle)null,
                                    currencyCode, SYMBOL);
                if (symbol != null) {
                    return symbol;
                }
            }

            ResourceBundle bundle = LocaleData.getCurrencyNames(locale);
            return bundle.getString(currencyCode);
        } catch (MissingResourceException e) {
            // use currency code as symbol of last resort
            return currencyCode;
        }
    }

    /**
     * Gets the default number of fraction digits used with this currency.
     * For example, the default number of fraction digits for the Euro is 2,
     * while for the Japanese Yen it's 0.
     * In the case of pseudo-currencies, such as IMF Special Drawing Rights,
     * -1 is returned.
     *
     * @return the default number of fraction digits used with this currency
     */
    public int getDefaultFractionDigits() {
        return defaultFractionDigits;
    }

    /**
     * Returns the ISO 4217 numeric code of this currency.
     *
     * @return the ISO 4217 numeric code of this currency
     * @since 1.7
     */
    public int getNumericCode() {
        return numericCode;
    }

    /**
     * Gets the name that is suitable for displaying this currency for
     * the default locale.  If there is no suitable display name found
     * for the default locale, the ISO 4217 currency code is returned.
     *
     * @return the display name of this currency for the default locale
     * @since 1.7
     */
    public String getDisplayName() {
        return getDisplayName(Locale.getDefault(Locale.Category.DISPLAY));
    }

    /**
     * Gets the name that is suitable for displaying this currency for
     * the specified locale.  If there is no suitable display name found
     * for the specified locale, the ISO 4217 currency code is returned.
     *
     * @param locale the locale for which a display name for this currency is
     * needed
     * @return the display name of this currency for the specified locale
     * @exception NullPointerException if <code>locale</code> is null
     * @since 1.7
     */
    public String getDisplayName(Locale locale) {
        try {
            OpenListResourceBundle bundle = LocaleData.getCurrencyNames(locale);
            String result = null;
            String bundleKey = currencyCode.toLowerCase(Locale.ROOT);

            // Check whether a provider can provide an implementation that's closer
            // to the requested locale than what the Java runtime itself can provide.
            LocaleServiceProviderPool pool =
                LocaleServiceProviderPool.getPool(CurrencyNameProvider.class);
            if (pool.hasProviders()) {
                result = pool.getLocalizedObject(
                                    CurrencyNameGetter.INSTANCE,
                                    locale, bundleKey, bundle, currencyCode, DISPLAYNAME);
            }

            if (result == null) {
                result = bundle.getString(bundleKey);
            }

            if (result != null) {
                return result;
            }
        } catch (MissingResourceException e) {
            // fall through
        }

        // use currency code as symbol of last resort
        return currencyCode;
    }

    /**
     * Returns the ISO 4217 currency code of this currency.
     *
     * @return the ISO 4217 currency code of this currency
     */
    public String toString() {
        return currencyCode;
    }

    /**
     * Resolves instances being deserialized to a single instance per currency.
     */
    private Object readResolve() {
        return getInstance(currencyCode);
    }

    /**
     * Gets the main table entry for the country whose country code consists
     * of char1 and char2.
     */
    private static int getMainTableEntry(char char1, char char2) {
        if (char1 < 'A' || char1 > 'Z' || char2 < 'A' || char2 > 'Z') {
            throw new IllegalArgumentException();
        }
        return mainTable[(char1 - 'A') * A_TO_Z + (char2 - 'A')];
    }

    /**
     * Sets the main table entry for the country whose country code consists
     * of char1 and char2.
     */
    private static void setMainTableEntry(char char1, char char2, int entry) {
        if (char1 < 'A' || char1 > 'Z' || char2 < 'A' || char2 > 'Z') {
            throw new IllegalArgumentException();
        }
        mainTable[(char1 - 'A') * A_TO_Z + (char2 - 'A')] = entry;
    }

    /**
     * Obtains a localized currency names from a CurrencyNameProvider
     * implementation.
     */
    private static class CurrencyNameGetter
        implements LocaleServiceProviderPool.LocalizedObjectGetter<CurrencyNameProvider,
                                                                   String> {
        private static final CurrencyNameGetter INSTANCE = new CurrencyNameGetter();

        public String getObject(CurrencyNameProvider currencyNameProvider,
                                Locale locale,
                                String key,
                                Object... params) {
            assert params.length == 1;
            int type = (Integer)params[0];

            switch(type) {
            case SYMBOL:
                return currencyNameProvider.getSymbol(key, locale);
            case DISPLAYNAME:
                return currencyNameProvider.getDisplayName(key, locale);
            default:
                assert false; // shouldn't happen
            }

            return null;
        }
    }

    private static int[] readIntArray(DataInputStream dis, int count) throws IOException {
        int[] ret = new int[count];
        for (int i = 0; i < count; i++) {
            ret[i] = dis.readInt();
        }

        return ret;
    }

    private static long[] readLongArray(DataInputStream dis, int count) throws IOException {
        long[] ret = new long[count];
        for (int i = 0; i < count; i++) {
            ret[i] = dis.readLong();
        }

        return ret;
    }

    private static String[] readStringArray(DataInputStream dis, int count) throws IOException {
        String[] ret = new String[count];
        for (int i = 0; i < count; i++) {
            ret[i] = dis.readUTF();
        }

        return ret;
    }

    /**
     * Replaces currency data found in the currencydata.properties file
     *
     * @param pattern regex pattern for the properties
     * @param ctry country code
     * @param data currency data.  This is a comma separated string that
     *    consists of "three-letter alphabet code", "three-digit numeric code",
     *    and "one-digit (0,1,2, or 3) default fraction digit".
     *    For example, "JPZ,392,0".
     * @throws
     */
    private static void replaceCurrencyData(Pattern pattern, String ctry, String curdata) {

        if (ctry.length() != 2) {
            // ignore invalid country code
            String message = new StringBuilder()
                .append("The entry in currency.properties for ")
                .append(ctry).append(" is ignored because of the invalid country code.")
                .toString();
            info(message, null);
            return;
        }

        Matcher m = pattern.matcher(curdata);
        if (!m.find()) {
            // format is not recognized.  ignore the data
            String message = new StringBuilder()
                .append("The entry in currency.properties for ")
                .append(ctry)
                .append(" is ignored because the value format is not recognized.")
                .toString();
            info(message, null);
            return;
        }

        String code = m.group(1);
        int numeric = Integer.parseInt(m.group(2));
        int fraction = Integer.parseInt(m.group(3));
        int entry = numeric << NUMERIC_CODE_SHIFT;

        int index;
        for (index = 0; index < scOldCurrencies.length; index++) {
            if (scOldCurrencies[index].equals(code)) {
                break;
            }
        }

        if (index == scOldCurrencies.length) {
            // simple case
            entry |= (fraction << SIMPLE_CASE_COUNTRY_DEFAULT_DIGITS_SHIFT) |
                     (code.charAt(2) - 'A');
        } else {
            // special case
            entry |= SPECIAL_CASE_COUNTRY_MASK |
                     (index + SPECIAL_CASE_COUNTRY_INDEX_DELTA);
        }
        setMainTableEntry(ctry.charAt(0), ctry.charAt(1), entry);
    }

    private static void info(String message, Throwable t) {
        PlatformLogger logger = PlatformLogger.getLogger("java.util.Currency");
        if (logger.isLoggable(PlatformLogger.INFO)) {
            if (t != null) {
                logger.info(message, t);
            } else {
                logger.info(message);
            }
        }
    }
}
