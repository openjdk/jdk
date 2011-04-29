/*
 * Copyright (c) 2007, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
 * @test
 * @bug 4290801 4692419 4693631 5101540 5104960 6296410 6336600 6371531
 *    6488442 7036905
 * @summary Basic tests for Currency class.
 */

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Calendar;
import java.util.Date;
import java.util.Currency;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;


public class CurrencyTest {

    public static void main(String[] args) throws Exception {
        CheckDataVersion.check();
        testCurrencyCodeValidation();
        testLocaleMapping();
        testSymbols();
        testFractionDigits();
        testSerialization();
        testDisplayNames();
    }

    static void testCurrencyCodeValidation() {
        // test creation of some valid currencies
        testValidCurrency("USD");
        testValidCurrency("EUR");
        testValidCurrency("GBP");
        testValidCurrency("JPY");
        testValidCurrency("CNY");
        testValidCurrency("CHF");

        // test creation of some fictitious currencies
        testInvalidCurrency("AQD");
        testInvalidCurrency("US$");
        testInvalidCurrency("\u20AC");
    }

    static void testValidCurrency(String currencyCode) {
        Currency currency1 = Currency.getInstance(currencyCode);
        Currency currency2 = Currency.getInstance(currencyCode);
        if (currency1 != currency2) {
            throw new RuntimeException("Didn't get same instance for same currency code");
        }
        if (!currency1.getCurrencyCode().equals(currencyCode)) {
            throw new RuntimeException("Currency code changed");
        }
    }

    static void testInvalidCurrency(String currencyCode) {
        boolean gotException = false;
        try {
            Currency currency = Currency.getInstance(currencyCode);
        } catch (IllegalArgumentException e) {
            gotException = true;
        }
        if (!gotException) {
            throw new RuntimeException("didn't get specified exception");
        }
    }

    static void testLocaleMapping() {
        // very basic test: most countries have their own currency, and then
        // their currency code is an extension of their country code.
        Locale[] locales = Locale.getAvailableLocales();
        int goodCountries = 0;
        int ownCurrencies = 0;
        for (int i = 0; i < locales.length; i++) {
            Locale locale = locales[i];
            if (locale.getCountry().length() == 0) {
                boolean gotException = false;
                try {
                    Currency.getInstance(locale);
                } catch (IllegalArgumentException e) {
                    gotException = true;
                }
                if (!gotException) {
                    throw new RuntimeException("didn't get specified exception");
                }
            } else {
                goodCountries++;
                Currency currency = Currency.getInstance(locale);
                if (currency.getCurrencyCode().indexOf(locale.getCountry()) == 0) {
                    ownCurrencies++;
                }
            }
        }
        System.out.println("Countries tested: " + goodCountries +
                ", own currencies: " + ownCurrencies);
        if (ownCurrencies < (goodCountries / 2 + 1)) {
            throw new RuntimeException("suspicious: not enough countries have their own currency.");
        }

        // check a few countries that don't change their currencies too often
        String[] country1 = {"US", "CA", "JP", "CN", "SG", "CH"};
        String[] currency1 = {"USD", "CAD", "JPY", "CNY", "SGD", "CHF"};
        for (int i = 0; i < country1.length; i++) {
            checkCountryCurrency(country1[i], currency1[i]);
        }

        // check currency changes
        String[] switchOverCtry = {"DE", "FR", "ES", "IT", "NL", "BE", "TR", "RO", "AZ", "MZ", "GH", "VE"};
        String[] switchOverOld = {"DEM", "FRF", "ESP", "ITL", "NLG", "BEF", "TRL", "ROL", "AZM", "MZM", "GHC", "VEB"};
        String[] switchOverNew = {"EUR", "EUR", "EUR", "EUR", "EUR", "EUR", "TRY", "RON", "AZN", "MZN", "GHS", "VEF"};
        String[] switchOverTZ = {"Europe/Paris", "Europe/Paris", "Europe/Paris", "Europe/Paris",
                                 "Europe/Paris", "Europe/Paris", "Asia/Istanbul", "Europe/Bucharest",
                                 "Asia/Baku", "Africa/Maputo", "Africa/Accra", "America/Caracas"};
        int[] switchOverYear = {2002, 2002, 2002, 2002, 2002, 2002, 2005, 2005, 2006, 2006, 2007, 2008};
        int[] switchOverMonth = {Calendar.JANUARY, Calendar.JANUARY, Calendar.JANUARY, Calendar.JANUARY,
                                 Calendar.JANUARY, Calendar.JANUARY, Calendar.JANUARY, Calendar.JULY,
                                 Calendar.JANUARY, Calendar.JULY, Calendar.JULY, Calendar.JANUARY};
        int[] switchOverDay = {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1};
        for (int i = 0; i < switchOverCtry.length; i++) {
            TimeZone.setDefault(TimeZone.getTimeZone(switchOverTZ[i]));
            Calendar date = new GregorianCalendar(switchOverYear[i], switchOverMonth[i], switchOverDay[i]);
            long switchOver = date.getTime().getTime();
            boolean switchedOver = System.currentTimeMillis() >= switchOver;
            checkCountryCurrency(switchOverCtry[i], switchedOver ? switchOverNew[i] : switchOverOld[i]);
        }

        // check a country code which doesn't have a currency
        checkCountryCurrency("AQ", null);

        // check an invalid country code
        boolean gotException = false;
        try {
            Currency.getInstance(new Locale("", "EU"));
        } catch (IllegalArgumentException e) {
            gotException = true;
        }
        if (!gotException) {
            throw new RuntimeException("didn't get specified exception.");
        }
    }

    static void checkCountryCurrency(String countryCode, String expected) {
        Locale locale = new Locale("", countryCode);
        Currency currency = Currency.getInstance(locale);
        String code = (currency != null) ? currency.getCurrencyCode() : null;
        if (!(expected == null ? code == null : expected.equals(code))) {
            throw new RuntimeException("Wrong currency for " +
                    locale.getDisplayCountry() +
                    ": expected " + expected + ", got " + code);
        }
    }

    static void testSymbols() {
        testSymbol("USD", Locale.US, "$");
        testSymbol("EUR", Locale.GERMANY, "\u20AC");
        testSymbol("USD", Locale.PRC, "USD");
    }

    static void testSymbol(String currencyCode, Locale locale, String expectedSymbol) {
        String symbol = Currency.getInstance(currencyCode).getSymbol(locale);
        if (!symbol.equals(expectedSymbol)) {
            throw new RuntimeException("Wrong symbol for currency " +
                    currencyCode +": expected " + expectedSymbol +
                    ", got " + symbol);
        }
    }

    static void testFractionDigits() {
        testFractionDigits("USD", 2);
        testFractionDigits("EUR", 2);
        testFractionDigits("JPY", 0);
        testFractionDigits("XDR", -1);

        testFractionDigits("BHD", 3);
        testFractionDigits("IQD", 3);
        testFractionDigits("JOD", 3);
        testFractionDigits("KWD", 3);
        testFractionDigits("LYD", 3);
        testFractionDigits("OMR", 3);
        testFractionDigits("TND", 3);

        // Turkish Lira
        testFractionDigits("TRL", 0);
        testFractionDigits("TRY", 2);
    }

    static void testFractionDigits(String currencyCode, int expectedFractionDigits) {
        int digits = Currency.getInstance(currencyCode).getDefaultFractionDigits();
        if (digits != expectedFractionDigits) {
            throw new RuntimeException("Wrong number of fraction digits for currency " +
                    currencyCode +": expected " + expectedFractionDigits +
                    ", got " + digits);
        }
    }

    static void testSerialization() throws Exception {
        Currency currency1 = Currency.getInstance("DEM");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oStream = new ObjectOutputStream(baos);
        oStream.writeObject(currency1);
        oStream.flush();
        byte[] bytes = baos.toByteArray();

        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        ObjectInputStream iStream = new ObjectInputStream(bais);
        Currency currency2 = (Currency) iStream.readObject();

        if (currency1 != currency2) {
            throw new RuntimeException("serialization breaks class invariant");
        }
    }

    static void testDisplayNames() {
        // null argument test
        try {
            testDisplayName("USD", null, "");
            throw new RuntimeException("getDisplayName(NULL) did not throw an NPE.");
        } catch (NullPointerException npe) {}

        testDisplayName("USD", Locale.ENGLISH, "US Dollar");
        testDisplayName("FRF", Locale.FRENCH, "franc fran\u00e7ais");
        testDisplayName("DEM", Locale.GERMAN, "Deutsche Mark");
        testDisplayName("ESP", new Locale("es"), "peseta espa\u00f1ola");
        testDisplayName("ITL", new Locale("it"), "Lira Italiana");
        testDisplayName("JPY", Locale.JAPANESE, "\u65e5\u672c\u5186");
        testDisplayName("KRW", Locale.KOREAN, "\ub300\ud55c\ubbfc\uad6d \uc6d0");
        testDisplayName("SEK", new Locale("sv"), "svensk krona");
        testDisplayName("CNY", Locale.SIMPLIFIED_CHINESE, "\u4eba\u6c11\u5e01");
        testDisplayName("TWD", Locale.TRADITIONAL_CHINESE, "\u65b0\u81fa\u5e63");
    }

    static void testDisplayName(String currencyCode, Locale locale, String expectedName) {
        String name = Currency.getInstance(currencyCode).getDisplayName(locale);
        if (!name.equals(expectedName)) {
            throw new RuntimeException("Wrong display name for currency " +
                    currencyCode +": expected '" + expectedName +
                    "', got '" + name + "'");
        }
    }

}
