/*
 * Copyright (c) 2007, 2023, Oracle and/or its affiliates. All rights reserved.
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
 *      6488442 7036905 8008577 8039317 8074350 8074351 8150324 8167143
 *      8264792
 * @summary Basic tests for Currency class.
 * @modules java.base/java.util:open
 *          jdk.localedata
 * @run junit CurrencyTest
 */

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


public class CurrencyTest {

    // 'tablea1.txt' should be up-to-date before testing
    @Test
    public void dataVersionTest() {
        CheckDataVersion.check();
    }

    @Nested
    class CodeValidationTests {
        // Calling getInstance() on equal currency codes should return equal currencies
        @ParameterizedTest
        @MethodSource("validCurrencies")
        public void validCurrencyTest(String currencyCode) {
            compareCurrencies(currencyCode);
        }

        private static Stream<String> validCurrencies() {
            return Stream.of("USD", "EUR", "GBP", "JPY", "CNY", "CHF");
        }

        // Calling getInstance() with an invalid currency code should throw an IAE
        @ParameterizedTest
        @MethodSource("non4217Currencies")
        public void invalidCurrencyTest(String currencyCode) {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    Currency.getInstance(currencyCode), "getInstance() did not throw IAE");
            assertEquals("The input currency code is not a" +
                    " valid ISO 4217 code", ex.getMessage());
        }

        private static Stream<String> non4217Currencies() {
            return Stream.of("AQD", "US$");
        }

        // Calling getInstance() with a currency code not 3 characters long should throw
        // an IAE
        @ParameterizedTest
        @MethodSource("invalidLengthCurrencies")
        public void invalidCurrencyLengthTest(String currencyCode) {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    Currency.getInstance(currencyCode), "getInstance() did not throw IAE");
            assertEquals("The input currency code must have a length of 3" +
                    " characters", ex.getMessage());
        }

        private static Stream<String> invalidLengthCurrencies() {
            return Stream.of("\u20AC", "", "12345");
        }
    }

    @Nested
    class FundsCodesTests {
        // Calling getInstance() on equal currency codes should return equal currencies
        @ParameterizedTest
        @MethodSource("fundsCodes")
        public void validCurrencyTest(String currencyCode) {
            compareCurrencies(currencyCode);
        }

        // Verify a currency has the expected fractional digits
        @ParameterizedTest
        @MethodSource("fundsCodes")
        public void fractionDigitTest(String currencyCode, int expectedFractionDigits) {
            compareFractionDigits(currencyCode, expectedFractionDigits);
        }

        // Verify a currency has the expected numeric code
        @ParameterizedTest
        @MethodSource("fundsCodes")
        public void numericCodeTest(String currencyCode, int ignored, int expectedNumeric) {
            int numeric = Currency.getInstance(currencyCode).getNumericCode();
            assertEquals(numeric, expectedNumeric, String.format(
                    "Wrong numeric code for currency %s, expected %s, got %s",
                    currencyCode, expectedNumeric, numeric));
        }

        private static Stream<Arguments> fundsCodes() {
            return Stream.of(
                    Arguments.of("BOV", 2, 984), Arguments.of("CHE", 2, 947),
                    Arguments.of("CHW", 2, 948), Arguments.of("CLF", 4, 990),
                    Arguments.of("COU", 2, 970), Arguments.of("MXV", 2, 979),
                    Arguments.of("USN", 2, 997), Arguments.of("UYI", 0, 940)
            );
        }
    }

    @Nested
    class LocaleMappingTests {

        // very basic test: most countries have their own currency, and then
        // their currency code is an extension of their country code.
        @Test
        public void localeMappingTest() {
            Locale[] locales = Locale.getAvailableLocales();
            int goodCountries = 0;
            int ownCurrencies = 0;
            for (Locale locale : locales) {
                String ctryCode = locale.getCountry();
                int ctryLength = ctryCode.length();
                if (ctryLength == 0 ||
                        ctryLength == 3 || // UN M.49 code
                        ctryCode.matches("AA|Q[M-Z]|X[A-JL-Z]|ZZ" + // user defined codes, excluding "XK" (Kosovo)
                                "AC|CP|DG|EA|EU|FX|IC|SU|TA|UK")) { // exceptional reservation codes
                    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                            () -> Currency.getInstance(locale), "Did not throw IAE");
                    assertEquals("The country of the input locale is not a" +
                            " valid ISO 3166 country code", ex.getMessage());
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
        }

        // Check an invalid country code
        @Test
        public void invalidCountryTest() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    ()-> Currency.getInstance(Locale.of("", "EU")), "Did not throw IAE");
            assertEquals("The country of the input locale is not a valid" +
                    " ISO 3166 country code", ex.getMessage());
        }

        // Ensure a selection of countries have the expected currency
        @ParameterizedTest
        @MethodSource({"countryProvider", "switchedOverCountries"})
        public void countryCurrencyTest(String countryCode, String expected) {
            Locale locale = Locale.of("", countryCode);
            Currency currency = Currency.getInstance(locale);
            String code = (currency != null) ? currency.getCurrencyCode() : null;
            assertEquals(expected, code, generateErrMsg(
                    "currency for", locale.getDisplayCountry(), expected, code));
        }

        private static Stream<Arguments> countryProvider() {
            return Stream.of(
                    // Check country that does not have a currency
                    Arguments.of("AQ", null),
                    // Check some countries that don't change their currencies often
                    Arguments.of("US", "USD"),
                    Arguments.of("CA", "CAD"),
                    Arguments.of("JP", "JPY"),
                    Arguments.of("CN", "CNY"),
                    Arguments.of("SG", "SGD"),
                    Arguments.of("CH", "CHF")
            );
        }

        /*
         * Check Currency Changes
         * In the current implementation, there is no data of old currency and transition
         * date at jdk/src/java.base/share/data/currency/CurrencyData.properties.
         * So, all the switch data arrays are empty. In the future, if data of old
         * currency and transition date are necessary for any country, the
         * arrays here can be updated so that the program can check the currency switch.
         */
        private static List<Arguments> switchedOverCountries() {
            List<Arguments> switched = new ArrayList<Arguments>();
            String[] switchOverCtry = {};
            String[] switchOverOld = {};
            String[] switchOverNew = {};
            String[] switchOverTZ = {};
            int[] switchOverYear = {};
            int[] switchOverMonth = {}; // java.time APIs accept month starting from 1 i.e. 01 for January
            int[] switchOverDay = {};

            for (int i = 0; i < switchOverCtry.length; i++) {
                ZoneId zoneId = ZoneId.of(switchOverTZ[i]);
                ZonedDateTime zonedDateAndTime  = ZonedDateTime.of(LocalDate.of(
                        switchOverYear[i], switchOverMonth[i], switchOverDay[i]), LocalTime.MIDNIGHT, zoneId);
                ZonedDateTime currentZonedDateAndTime =  ZonedDateTime.now(zoneId);
                switched.add(Arguments.of(switchOverCtry[i], (currentZonedDateAndTime.isAfter(zonedDateAndTime)
                        || currentZonedDateAndTime.isEqual(zonedDateAndTime)) ? switchOverNew[i] : switchOverOld[i]));
            }
            return switched;
        }
    }

    // NON-NESTED TESTS

    // Ensure selection of currencies have the correct fractional digits
    @ParameterizedTest
    @MethodSource("expectedFractionsProvider")
    public void fractionDigitsTest(String currencyCode, int expectedFractionDigits) {
        compareFractionDigits(currencyCode, expectedFractionDigits);
    }

    private static Stream<Arguments> expectedFractionsProvider() {
        return Stream.of(
                Arguments.of("USD", 2), Arguments.of("EUR", 2),
                Arguments.of("JPY", 0), Arguments.of("XDR", -1),
                Arguments.of("BHD", 3), Arguments.of("IQD", 3),
                Arguments.of("JOD", 3), Arguments.of("KWD", 3),
                Arguments.of("LYD", 3), Arguments.of("OMR", 3),
                Arguments.of("TND", 3),

                // Old and New Turkish Lira
                Arguments.of("TRL", 0), Arguments.of("TRY", 2)
        );
    }

    // Ensure selection of currencies have the expected symbol
    @ParameterizedTest
    @MethodSource("symbolProvider")
    public void symbolTest(String currencyCode, Locale locale, String expectedSymbol) {
        String symbol = Currency.getInstance(currencyCode).getSymbol(locale);
        assertEquals(symbol, expectedSymbol, generateErrMsg(
                "symbol for", currencyCode, expectedSymbol, symbol));
    }

    private static Stream<Arguments> symbolProvider() {
        return Stream.of(
                Arguments.of("USD", Locale.US, "$"),
                Arguments.of("EUR", Locale.GERMANY, "\u20AC"),
                Arguments.of("USD", Locale.PRC, "US$")
        );
    }

    // Ensure serialization does not break class invariant.
    // Currency should be able to round-trip and remain the same value.
    @Test
    public void serializationTest() throws Exception {
        Currency currency1 = Currency.getInstance("DEM");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oStream = new ObjectOutputStream(baos);
        oStream.writeObject(currency1);
        oStream.flush();
        byte[] bytes = baos.toByteArray();

        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        ObjectInputStream iStream = new ObjectInputStream(bais);
        Currency currency2 = (Currency) iStream.readObject();
        assertEquals(currency1, currency2, "serialization breaks class invariant");
    }

    // Ensure getInstance() throws null when passed a null locale
    @Test
    public void nullDisplayNameTest() {
        assertThrows(NullPointerException.class, ()->
                Currency.getInstance("USD").getDisplayName(null));
    }

    // Ensure a selection of currencies/locale combos have the correct display name
    @ParameterizedTest
    @MethodSource("displayNameProvider")
    public void displayNameTest(String currencyCode, Locale locale, String expectedName) {
        String name = Currency.getInstance(currencyCode).getDisplayName(locale);
        assertEquals(name, expectedName, generateErrMsg(
                "display name for", currencyCode, expectedName, name));
    }

    private static Stream<Arguments> displayNameProvider() {
        return Stream.of(
                Arguments.of("USD", Locale.ENGLISH, "US Dollar"),
                Arguments.of("FRF", Locale.FRENCH, "franc fran\u00e7ais"),
                Arguments.of("DEM", Locale.GERMAN, "Deutsche Mark"),
                Arguments.of("ESP", Locale.of("es"), "peseta espa\u00f1ola"),
                Arguments.of("ITL", Locale.ITALIAN, "lira italiana"),
                Arguments.of("JPY", Locale.JAPANESE, "\u65e5\u672c\u5186"),
                Arguments.of("KRW", Locale.KOREAN, "\ub300\ud55c\ubbfc\uad6d \uc6d0"),
                Arguments.of("SEK", Locale.of("sv"), "svensk krona"),
                Arguments.of("CNY", Locale.SIMPLIFIED_CHINESE, "\u4eba\u6c11\u5e01"),
                Arguments.of("TWD", Locale.TRADITIONAL_CHINESE, "\u65b0\u53f0\u5e63")
        );
    }

    // HELPER FUNCTIONS

    // A Currency instance returned from getInstance() should always be
    // equal if supplied the same currencyCode. getCurrencyCode() should
    // always be equal to the currencyCode used to create the Currency.
    private static void compareCurrencies(String currencyCode) {
        Currency currency1 = Currency.getInstance(currencyCode);
        Currency currency2 = Currency.getInstance(currencyCode);
        assertEquals(currency1, currency2, "Didn't get same instance for same currency code");
        assertEquals(currency1.getCurrencyCode(), currencyCode, "getCurrencyCode()" +
                " did not return the expected value");
    }

    // Ensures the getDefaultFractionDigits() method returns the expected amount
    private static void compareFractionDigits(String currencyCode,
                                              int expectedFractionDigits) {
        int digits = Currency.getInstance(currencyCode).getDefaultFractionDigits();
        assertEquals(digits, expectedFractionDigits, generateErrMsg(
                "number of fraction digits for currency",
                currencyCode, Integer.toString(expectedFractionDigits), Integer.toString(digits)));
    }

    // Used for logging on failing tests
    private static String generateErrMsg(String subject, String currency,
                                         String expected, String got) {
        return String.format("Wrong %s %s: expected '%s', got '%s'",
                subject, currency, expected, got);
    }
}
