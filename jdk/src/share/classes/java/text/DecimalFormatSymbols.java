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
 *   The original version of this source code and documentation is copyrighted
 * and owned by Taligent, Inc., a wholly-owned subsidiary of IBM. These
 * materials are provided under terms of a License Agreement between Taligent
 * and Sun. This technology is protected by multiple US and International
 * patents. This notice and attribution to Taligent may not be removed.
 *   Taligent is a registered trademark of Taligent, Inc.
 *
 */

package java.text;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.text.spi.DecimalFormatSymbolsProvider;
import java.util.Currency;
import java.util.Hashtable;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.spi.LocaleServiceProvider;
import sun.util.LocaleServiceProviderPool;
import sun.util.resources.LocaleData;

/**
 * This class represents the set of symbols (such as the decimal separator,
 * the grouping separator, and so on) needed by <code>DecimalFormat</code>
 * to format numbers. <code>DecimalFormat</code> creates for itself an instance of
 * <code>DecimalFormatSymbols</code> from its locale data.  If you need to change any
 * of these symbols, you can get the <code>DecimalFormatSymbols</code> object from
 * your <code>DecimalFormat</code> and modify it.
 *
 * @see          java.util.Locale
 * @see          DecimalFormat
 * @author       Mark Davis
 * @author       Alan Liu
 */

public class DecimalFormatSymbols implements Cloneable, Serializable {

    /**
     * Create a DecimalFormatSymbols object for the default locale.
     * This constructor can only construct instances for the locales
     * supported by the Java runtime environment, not for those
     * supported by installed
     * {@link java.text.spi.DecimalFormatSymbolsProvider DecimalFormatSymbolsProvider}
     * implementations. For full locale coverage, use the
     * {@link #getInstance(Locale) getInstance} method.
     */
    public DecimalFormatSymbols() {
        initialize( Locale.getDefault() );
    }

    /**
     * Create a DecimalFormatSymbols object for the given locale.
     * This constructor can only construct instances for the locales
     * supported by the Java runtime environment, not for those
     * supported by installed
     * {@link java.text.spi.DecimalFormatSymbolsProvider DecimalFormatSymbolsProvider}
     * implementations. For full locale coverage, use the
     * {@link #getInstance(Locale) getInstance} method.
     *
     * @exception NullPointerException if <code>locale</code> is null
     */
    public DecimalFormatSymbols( Locale locale ) {
        initialize( locale );
    }

    /**
     * Returns an array of all locales for which the
     * <code>getInstance</code> methods of this class can return
     * localized instances.
     * The returned array represents the union of locales supported by the Java
     * runtime and by installed
     * {@link java.text.spi.DecimalFormatSymbolsProvider DecimalFormatSymbolsProvider}
     * implementations.  It must contain at least a <code>Locale</code>
     * instance equal to {@link java.util.Locale#US Locale.US}.
     *
     * @return An array of locales for which localized
     *         <code>DecimalFormatSymbols</code> instances are available.
     * @since 1.6
     */
    public static Locale[] getAvailableLocales() {
        LocaleServiceProviderPool pool =
            LocaleServiceProviderPool.getPool(DecimalFormatSymbolsProvider.class);
        return pool.getAvailableLocales();
    }

    /**
     * Gets the <code>DecimalFormatSymbols</code> instance for the default
     * locale.  This method provides access to <code>DecimalFormatSymbols</code>
     * instances for locales supported by the Java runtime itself as well
     * as for those supported by installed
     * {@link java.text.spi.DecimalFormatSymbolsProvider
     * DecimalFormatSymbolsProvider} implementations.
     * @return a <code>DecimalFormatSymbols</code> instance.
     * @since 1.6
     */
    public static final DecimalFormatSymbols getInstance() {
        return getInstance(Locale.getDefault());
    }

    /**
     * Gets the <code>DecimalFormatSymbols</code> instance for the specified
     * locale.  This method provides access to <code>DecimalFormatSymbols</code>
     * instances for locales supported by the Java runtime itself as well
     * as for those supported by installed
     * {@link java.text.spi.DecimalFormatSymbolsProvider
     * DecimalFormatSymbolsProvider} implementations.
     * @param locale the desired locale.
     * @return a <code>DecimalFormatSymbols</code> instance.
     * @exception NullPointerException if <code>locale</code> is null
     * @since 1.6
     */
    public static final DecimalFormatSymbols getInstance(Locale locale) {

        // Check whether a provider can provide an implementation that's closer
        // to the requested locale than what the Java runtime itself can provide.
        LocaleServiceProviderPool pool =
            LocaleServiceProviderPool.getPool(DecimalFormatSymbolsProvider.class);
        if (pool.hasProviders()) {
            DecimalFormatSymbols providersInstance = pool.getLocalizedObject(
                                DecimalFormatSymbolsGetter.INSTANCE, locale);
            if (providersInstance != null) {
                return providersInstance;
            }
        }

        return new DecimalFormatSymbols(locale);
    }

    /**
     * Gets the character used for zero. Different for Arabic, etc.
     */
    public char getZeroDigit() {
        return zeroDigit;
    }

    /**
     * Sets the character used for zero. Different for Arabic, etc.
     */
    public void setZeroDigit(char zeroDigit) {
        this.zeroDigit = zeroDigit;
    }

    /**
     * Gets the character used for thousands separator. Different for French, etc.
     */
    public char getGroupingSeparator() {
        return groupingSeparator;
    }

    /**
     * Sets the character used for thousands separator. Different for French, etc.
     */
    public void setGroupingSeparator(char groupingSeparator) {
        this.groupingSeparator = groupingSeparator;
    }

    /**
     * Gets the character used for decimal sign. Different for French, etc.
     */
    public char getDecimalSeparator() {
        return decimalSeparator;
    }

    /**
     * Sets the character used for decimal sign. Different for French, etc.
     */
    public void setDecimalSeparator(char decimalSeparator) {
        this.decimalSeparator = decimalSeparator;
    }

    /**
     * Gets the character used for per mille sign. Different for Arabic, etc.
     */
    public char getPerMill() {
        return perMill;
    }

    /**
     * Sets the character used for per mille sign. Different for Arabic, etc.
     */
    public void setPerMill(char perMill) {
        this.perMill = perMill;
    }

    /**
     * Gets the character used for percent sign. Different for Arabic, etc.
     */
    public char getPercent() {
        return percent;
    }

    /**
     * Sets the character used for percent sign. Different for Arabic, etc.
     */
    public void setPercent(char percent) {
        this.percent = percent;
    }

    /**
     * Gets the character used for a digit in a pattern.
     */
    public char getDigit() {
        return digit;
    }

    /**
     * Sets the character used for a digit in a pattern.
     */
    public void setDigit(char digit) {
        this.digit = digit;
    }

    /**
     * Gets the character used to separate positive and negative subpatterns
     * in a pattern.
     */
    public char getPatternSeparator() {
        return patternSeparator;
    }

    /**
     * Sets the character used to separate positive and negative subpatterns
     * in a pattern.
     */
    public void setPatternSeparator(char patternSeparator) {
        this.patternSeparator = patternSeparator;
    }

    /**
     * Gets the string used to represent infinity. Almost always left
     * unchanged.
     */
    public String getInfinity() {
        return infinity;
    }

    /**
     * Sets the string used to represent infinity. Almost always left
     * unchanged.
     */
    public void setInfinity(String infinity) {
        this.infinity = infinity;
    }

    /**
     * Gets the string used to represent "not a number". Almost always left
     * unchanged.
     */
    public String getNaN() {
        return NaN;
    }

    /**
     * Sets the string used to represent "not a number". Almost always left
     * unchanged.
     */
    public void setNaN(String NaN) {
        this.NaN = NaN;
    }

    /**
     * Gets the character used to represent minus sign. If no explicit
     * negative format is specified, one is formed by prefixing
     * minusSign to the positive format.
     */
    public char getMinusSign() {
        return minusSign;
    }

    /**
     * Sets the character used to represent minus sign. If no explicit
     * negative format is specified, one is formed by prefixing
     * minusSign to the positive format.
     */
    public void setMinusSign(char minusSign) {
        this.minusSign = minusSign;
    }

    /**
     * Returns the currency symbol for the currency of these
     * DecimalFormatSymbols in their locale.
     * @since 1.2
     */
    public String getCurrencySymbol()
    {
        return currencySymbol;
    }

    /**
     * Sets the currency symbol for the currency of these
     * DecimalFormatSymbols in their locale.
     * @since 1.2
     */
    public void setCurrencySymbol(String currency)
    {
        currencySymbol = currency;
    }

    /**
     * Returns the ISO 4217 currency code of the currency of these
     * DecimalFormatSymbols.
     * @since 1.2
     */
    public String getInternationalCurrencySymbol()
    {
        return intlCurrencySymbol;
    }

    /**
     * Sets the ISO 4217 currency code of the currency of these
     * DecimalFormatSymbols.
     * If the currency code is valid (as defined by
     * {@link java.util.Currency#getInstance(java.lang.String) Currency.getInstance}),
     * this also sets the currency attribute to the corresponding Currency
     * instance and the currency symbol attribute to the currency's symbol
     * in the DecimalFormatSymbols' locale. If the currency code is not valid,
     * then the currency attribute is set to null and the currency symbol
     * attribute is not modified.
     *
     * @see #setCurrency
     * @see #setCurrencySymbol
     * @since 1.2
     */
    public void setInternationalCurrencySymbol(String currencyCode)
    {
        intlCurrencySymbol = currencyCode;
        currency = null;
        if (currencyCode != null) {
            try {
                currency = Currency.getInstance(currencyCode);
                currencySymbol = currency.getSymbol();
            } catch (IllegalArgumentException e) {
            }
        }
    }

    /**
     * Gets the currency of these DecimalFormatSymbols. May be null if the
     * currency symbol attribute was previously set to a value that's not
     * a valid ISO 4217 currency code.
     *
     * @return the currency used, or null
     * @since 1.4
     */
    public Currency getCurrency() {
        return currency;
    }

    /**
     * Sets the currency of these DecimalFormatSymbols.
     * This also sets the currency symbol attribute to the currency's symbol
     * in the DecimalFormatSymbols' locale, and the international currency
     * symbol attribute to the currency's ISO 4217 currency code.
     *
     * @param currency the new currency to be used
     * @exception NullPointerException if <code>currency</code> is null
     * @since 1.4
     * @see #setCurrencySymbol
     * @see #setInternationalCurrencySymbol
     */
    public void setCurrency(Currency currency) {
        if (currency == null) {
            throw new NullPointerException();
        }
        this.currency = currency;
        intlCurrencySymbol = currency.getCurrencyCode();
        currencySymbol = currency.getSymbol(locale);
    }


    /**
     * Returns the monetary decimal separator.
     * @since 1.2
     */
    public char getMonetaryDecimalSeparator()
    {
        return monetarySeparator;
    }

    /**
     * Sets the monetary decimal separator.
     * @since 1.2
     */
    public void setMonetaryDecimalSeparator(char sep)
    {
        monetarySeparator = sep;
    }

    //------------------------------------------------------------
    // BEGIN   Package Private methods ... to be made public later
    //------------------------------------------------------------

    /**
     * Returns the character used to separate the mantissa from the exponent.
     */
    char getExponentialSymbol()
    {
        return exponential;
    }
  /**
   * Returns the string used to separate the mantissa from the exponent.
   * Examples: "x10^" for 1.23x10^4, "E" for 1.23E4.
   *
   * @return the exponent separator string
   * @see #setExponentSeparator(java.lang.String)
   * @since 1.6
   */
    public String getExponentSeparator()
    {
        return exponentialSeparator;
    }

    /**
     * Sets the character used to separate the mantissa from the exponent.
     */
    void setExponentialSymbol(char exp)
    {
        exponential = exp;
    }

  /**
   * Sets the string used to separate the mantissa from the exponent.
   * Examples: "x10^" for 1.23x10^4, "E" for 1.23E4.
   *
   * @param exp the exponent separator string
   * @exception NullPointerException if <code>exp</code> is null
   * @see #getExponentSeparator()
   * @since 1.6
   */
    public void setExponentSeparator(String exp)
    {
        if (exp == null) {
            throw new NullPointerException();
        }
        exponentialSeparator = exp;
     }


    //------------------------------------------------------------
    // END     Package Private methods ... to be made public later
    //------------------------------------------------------------

    /**
     * Standard override.
     */
    public Object clone() {
        try {
            return (DecimalFormatSymbols)super.clone();
            // other fields are bit-copied
        } catch (CloneNotSupportedException e) {
            throw new InternalError();
        }
    }

    /**
     * Override equals.
     */
    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (this == obj) return true;
        if (getClass() != obj.getClass()) return false;
        DecimalFormatSymbols other = (DecimalFormatSymbols) obj;
        return (zeroDigit == other.zeroDigit &&
        groupingSeparator == other.groupingSeparator &&
        decimalSeparator == other.decimalSeparator &&
        percent == other.percent &&
        perMill == other.perMill &&
        digit == other.digit &&
        minusSign == other.minusSign &&
        patternSeparator == other.patternSeparator &&
        infinity.equals(other.infinity) &&
        NaN.equals(other.NaN) &&
        currencySymbol.equals(other.currencySymbol) &&
        intlCurrencySymbol.equals(other.intlCurrencySymbol) &&
        currency == other.currency &&
        monetarySeparator == other.monetarySeparator &&
        exponentialSeparator.equals(other.exponentialSeparator) &&
        locale.equals(other.locale));
    }

    /**
     * Override hashCode.
     */
    public int hashCode() {
            int result = zeroDigit;
            result = result * 37 + groupingSeparator;
            result = result * 37 + decimalSeparator;
            return result;
    }

    /**
     * Initializes the symbols from the FormatData resource bundle.
     */
    private void initialize( Locale locale ) {
        this.locale = locale;

        // get resource bundle data - try the cache first
        boolean needCacheUpdate = false;
        Object[] data = (Object[]) cachedLocaleData.get(locale);
        if (data == null) {  /* cache miss */
            data = new Object[3];
            ResourceBundle rb = LocaleData.getNumberFormatData(locale);
            data[0] = rb.getStringArray("NumberElements");
            needCacheUpdate = true;
        }

        String[] numberElements = (String[]) data[0];

        decimalSeparator = numberElements[0].charAt(0);
        groupingSeparator = numberElements[1].charAt(0);
        patternSeparator = numberElements[2].charAt(0);
        percent = numberElements[3].charAt(0);
        zeroDigit = numberElements[4].charAt(0); //different for Arabic,etc.
        digit = numberElements[5].charAt(0);
        minusSign = numberElements[6].charAt(0);
        exponential = numberElements[7].charAt(0);
        exponentialSeparator = numberElements[7]; //string representation new since 1.6
        perMill = numberElements[8].charAt(0);
        infinity  = numberElements[9];
        NaN = numberElements[10];

        // Try to obtain the currency used in the locale's country.
        // Check for empty country string separately because it's a valid
        // country ID for Locale (and used for the C locale), but not a valid
        // ISO 3166 country code, and exceptions are expensive.
        if (!"".equals(locale.getCountry())) {
            try {
                currency = Currency.getInstance(locale);
            } catch (IllegalArgumentException e) {
                // use default values below for compatibility
            }
        }
        if (currency != null) {
            intlCurrencySymbol = currency.getCurrencyCode();
            if (data[1] != null && data[1] == intlCurrencySymbol) {
                currencySymbol = (String) data[2];
            } else {
                currencySymbol = currency.getSymbol(locale);
                data[1] = intlCurrencySymbol;
                data[2] = currencySymbol;
                needCacheUpdate = true;
            }
        } else {
            // default values
            intlCurrencySymbol = "XXX";
            try {
                currency = Currency.getInstance(intlCurrencySymbol);
            } catch (IllegalArgumentException e) {
            }
            currencySymbol = "\u00A4";
        }
        // Currently the monetary decimal separator is the same as the
        // standard decimal separator for all locales that we support.
        // If that changes, add a new entry to NumberElements.
        monetarySeparator = decimalSeparator;

        if (needCacheUpdate) {
            cachedLocaleData.put(locale, data);
        }
    }

    /**
     * Reads the default serializable fields, provides default values for objects
     * in older serial versions, and initializes non-serializable fields.
     * If <code>serialVersionOnStream</code>
     * is less than 1, initializes <code>monetarySeparator</code> to be
     * the same as <code>decimalSeparator</code> and <code>exponential</code>
     * to be 'E'.
     * If <code>serialVersionOnStream</code> is less than 2,
     * initializes <code>locale</code>to the root locale, and initializes
     * If <code>serialVersionOnStream</code> is less than 3, it initializes
     * <code>exponentialSeparator</code> using <code>exponential</code>.
     * Sets <code>serialVersionOnStream</code> back to the maximum allowed value so that
     * default serialization will work properly if this object is streamed out again.
     * Initializes the currency from the intlCurrencySymbol field.
     *
     * @since JDK 1.1.6
     */
    private void readObject(ObjectInputStream stream)
            throws IOException, ClassNotFoundException {
        stream.defaultReadObject();
        if (serialVersionOnStream < 1) {
            // Didn't have monetarySeparator or exponential field;
            // use defaults.
            monetarySeparator = decimalSeparator;
            exponential       = 'E';
        }
        if (serialVersionOnStream < 2) {
            // didn't have locale; use root locale
            locale = Locale.ROOT;
        }
        if (serialVersionOnStream < 3) {
            // didn't have exponentialSeparator. Create one using exponential
            exponentialSeparator = Character.toString(exponential);
        }
        serialVersionOnStream = currentSerialVersion;

        if (intlCurrencySymbol != null) {
            try {
                 currency = Currency.getInstance(intlCurrencySymbol);
            } catch (IllegalArgumentException e) {
            }
        }
    }

    /**
     * Character used for zero.
     *
     * @serial
     * @see #getZeroDigit
     */
    private  char    zeroDigit;

    /**
     * Character used for thousands separator.
     *
     * @serial
     * @see #getGroupingSeparator
     */
    private  char    groupingSeparator;

    /**
     * Character used for decimal sign.
     *
     * @serial
     * @see #getDecimalSeparator
     */
    private  char    decimalSeparator;

    /**
     * Character used for per mille sign.
     *
     * @serial
     * @see #getPerMill
     */
    private  char    perMill;

    /**
     * Character used for percent sign.
     * @serial
     * @see #getPercent
     */
    private  char    percent;

    /**
     * Character used for a digit in a pattern.
     *
     * @serial
     * @see #getDigit
     */
    private  char    digit;

    /**
     * Character used to separate positive and negative subpatterns
     * in a pattern.
     *
     * @serial
     * @see #getPatternSeparator
     */
    private  char    patternSeparator;

    /**
     * String used to represent infinity.
     * @serial
     * @see #getInfinity
     */
    private  String  infinity;

    /**
     * String used to represent "not a number".
     * @serial
     * @see #getNaN
     */
    private  String  NaN;

    /**
     * Character used to represent minus sign.
     * @serial
     * @see #getMinusSign
     */
    private  char    minusSign;

    /**
     * String denoting the local currency, e.g. "$".
     * @serial
     * @see #getCurrencySymbol
     */
    private  String  currencySymbol;

    /**
     * ISO 4217 currency code denoting the local currency, e.g. "USD".
     * @serial
     * @see #getInternationalCurrencySymbol
     */
    private  String  intlCurrencySymbol;

    /**
     * The decimal separator used when formatting currency values.
     * @serial
     * @since JDK 1.1.6
     * @see #getMonetaryDecimalSeparator
     */
    private  char    monetarySeparator; // Field new in JDK 1.1.6

    /**
     * The character used to distinguish the exponent in a number formatted
     * in exponential notation, e.g. 'E' for a number such as "1.23E45".
     * <p>
     * Note that the public API provides no way to set this field,
     * even though it is supported by the implementation and the stream format.
     * The intent is that this will be added to the API in the future.
     *
     * @serial
     * @since JDK 1.1.6
     */
    private  char    exponential;       // Field new in JDK 1.1.6

  /**
   * The string used to separate the mantissa from the exponent.
   * Examples: "x10^" for 1.23x10^4, "E" for 1.23E4.
   * <p>
   * If both <code>exponential</code> and <code>exponentialSeparator</code>
   * exist, this <code>exponentialSeparator</code> has the precedence.
   *
   * @serial
   * @since 1.6
   */
    private  String    exponentialSeparator;       // Field new in JDK 1.6

    /**
     * The locale of these currency format symbols.
     *
     * @serial
     * @since 1.4
     */
    private Locale locale;

    // currency; only the ISO code is serialized.
    private transient Currency currency;

    // Proclaim JDK 1.1 FCS compatibility
    static final long serialVersionUID = 5772796243397350300L;

    // The internal serial version which says which version was written
    // - 0 (default) for version up to JDK 1.1.5
    // - 1 for version from JDK 1.1.6, which includes two new fields:
    //     monetarySeparator and exponential.
    // - 2 for version from J2SE 1.4, which includes locale field.
    // - 3 for version from J2SE 1.6, which includes exponentialSeparator field.
    private static final int currentSerialVersion = 3;

    /**
     * Describes the version of <code>DecimalFormatSymbols</code> present on the stream.
     * Possible values are:
     * <ul>
     * <li><b>0</b> (or uninitialized): versions prior to JDK 1.1.6.
     *
     * <li><b>1</b>: Versions written by JDK 1.1.6 or later, which include
     *      two new fields: <code>monetarySeparator</code> and <code>exponential</code>.
     * <li><b>2</b>: Versions written by J2SE 1.4 or later, which include a
     *      new <code>locale</code> field.
     * <li><b>3</b>: Versions written by J2SE 1.6 or later, which include a
     *      new <code>exponentialSeparator</code> field.
     * </ul>
     * When streaming out a <code>DecimalFormatSymbols</code>, the most recent format
     * (corresponding to the highest allowable <code>serialVersionOnStream</code>)
     * is always written.
     *
     * @serial
     * @since JDK 1.1.6
     */
    private int serialVersionOnStream = currentSerialVersion;

    /**
     * cache to hold the NumberElements and the Currency
     * of a Locale.
     */
    private static final Hashtable cachedLocaleData = new Hashtable(3);

    /**
     * Obtains a DecimalFormatSymbols instance from a DecimalFormatSymbolsProvider
     * implementation.
     */
    private static class DecimalFormatSymbolsGetter
        implements LocaleServiceProviderPool.LocalizedObjectGetter<DecimalFormatSymbolsProvider,
                                                                   DecimalFormatSymbols> {
        private static final DecimalFormatSymbolsGetter INSTANCE =
            new DecimalFormatSymbolsGetter();

        public DecimalFormatSymbols getObject(
                                DecimalFormatSymbolsProvider decimalFormatSymbolsProvider,
                                Locale locale,
                                String key,
                                Object... params) {
            assert params.length == 0;
            return decimalFormatSymbolsProvider.getInstance(locale);
        }
    }
}
