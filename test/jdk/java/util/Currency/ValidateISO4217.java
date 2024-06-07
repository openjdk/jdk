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
 * @bug 4691089 4819436 4942982 5104960 6544471 6627549 7066203 7195759
 *      8039317 8074350 8074351 8145952 8187946 8193552 8202026 8204269
 *      8208746 8209775 8264792 8274658 8283277 8296239 8321480
 * @summary Validate ISO 4217 data for Currency class.
 * @modules java.base/java.util:open
 *          jdk.localedata
 * @run junit ValidateISO4217
 */

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TimeZone;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This class tests the latest ISO 4217 data and Java's currency data which is
 * based on ISO 4217. The golden-data file (ISO 4217 data) 'tablea1.txt' has the following
 * format: <Country code>\t<Currency code>\t<Numeric code>\t<Minor unit>[\t<Cutover Date>\t<new Currency code>\t<new Numeric code>\t<new Minor unit>]
 * The Cutover Date is given in SimpleDateFormat's 'yyyy-MM-dd-HH-mm-ss' format in the GMT time zone.
 */
public class ValidateISO4217 {

    // Input golden-data file
    private static final File dataFile = new File(System.getProperty(
            "test.src", "."), "tablea1.txt");
    // Code statuses
    private static final byte UNDEFINED = 0;
    private static final byte DEFINED = 1;
    private static final byte SKIPPED = 2;
    private static final byte TESTED = 4;
    private static final int ALPHA_NUM = 26;
    // An alpha2 code table which maps the status of a country
    private static final byte[] codes = new byte[ALPHA_NUM * ALPHA_NUM];
    // Codes derived from ISO4217 golden-data file
    private static final List<Arguments> ISO4217Codes = new ArrayList<Arguments>();
    // Additional codes not from the ISO4217 golden-data file
    private static final List<Arguments> additionalCodes = new ArrayList<Arguments>();
    // Currencies to test (derived from ISO4217Codes and additionalCodes)
    private static final Set<Currency> testCurrencies = new HashSet<>();
    // Codes that are obsolete, do not have related country, extra currency
    private static final String otherCodes =
            "ADP-AFA-ATS-AYM-AZM-BEF-BGL-BOV-BYB-BYR-CHE-CHW-CLF-COU-CUC-CYP-"
                    + "DEM-EEK-ESP-FIM-FRF-GHC-GRD-GWP-HRK-IEP-ITL-LTL-LUF-LVL-MGF-MRO-MTL-MXV-MZM-NLG-"
                    + "PTE-ROL-RUR-SDD-SIT-SLL-SKK-SRG-STD-TMM-TPE-TRL-VEF-UYI-USN-USS-VEB-VED-"
                    + "XAG-XAU-XBA-XBB-XBC-XBD-XDR-XFO-XFU-XPD-XPT-XSU-XTS-XUA-XXX-"
                    + "YUM-ZMK-ZWD-ZWN-ZWR";
    private static final String[][] extraCodes = {
            /* Defined in ISO 4217 list, but don't have code and minor unit info. */
            {"AQ", "", "", "0"},    // Antarctica
            /*
             * Defined in ISO 4217 list, but don't have code and minor unit info in
             * it. On the other hand, both code and minor unit are defined in
             * .properties file. I don't know why, though.
             */
            {"GS", "GBP", "826", "2"},      // South Georgia And The South Sandwich Islands
            /* Not defined in ISO 4217 list, but defined in .properties file. */
            {"AX", "EUR", "978", "2"},      // \u00c5LAND ISLANDS
            {"PS", "ILS", "376", "2"},      // Palestinian Territory, Occupied
            /* Not defined in ISO 4217 list, but added in ISO 3166 country code list */
            {"JE", "GBP", "826", "2"},      // Jersey
            {"GG", "GBP", "826", "2"},      // Guernsey
            {"IM", "GBP", "826", "2"},      // Isle of Man
            {"BL", "EUR", "978", "2"},      // Saint Barthelemy
            {"MF", "EUR", "978", "2"},      // Saint Martin
            /* Defined neither in ISO 4217 nor ISO 3166 list */
            {"XK", "EUR", "978", "2"},      // Kosovo
    };
    private static SimpleDateFormat format = null;

    // Sets up the following test data:
    // ISO4217Codes, additionalCodes, testCurrencies, codes
    @BeforeAll
    static void setUpTestingData() throws Exception {
        // These functions laterally setup 'testCurrencies' and 'codes'
        // at the same time
        setUpISO4217Codes();
        setUpAdditionalCodes();
        setUpOtherCurrencies();
    }

    // Parse the ISO4217 file and populate ISO4217Codes and testCurrencies.
    private static void setUpISO4217Codes() throws Exception{
        try (FileReader fr = new FileReader(dataFile);
             BufferedReader in = new BufferedReader(fr))
        {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.length() == 0 || line.charAt(0) == '#') {
                    // Skip comments and empty lines
                    continue;
                }
                StringTokenizer tokens = new StringTokenizer(line, "\t");
                String country = tokens.nextToken();
                if (country.length() != 2) {
                    // Skip invalid countries
                    continue;
                }
                // If the country is valid, process the additional columns
                processColumns(tokens, country);
            }
        }
    }

    private static void processColumns(StringTokenizer tokens, String country) throws ParseException {
        String currency;
        String numeric;
        String minorUnit;
        int tokensCount = tokens.countTokens();
        if (tokensCount < 3) {
            // Ill-defined columns
            currency = "";
            numeric = "0";
            minorUnit = "0";
        } else {
            // Fully defined columns
            currency = tokens.nextToken();
            numeric = tokens.nextToken();
            minorUnit = tokens.nextToken();
            testCurrencies.add(Currency.getInstance(currency));
            // Check for the cut-over if a currency is changing
            if (tokensCount > 3) {
                if (format == null) {
                    createDateFormat();
                }
                // If the cut-over already passed, use the new curency for ISO4217Codes
                if (format.parse(tokens.nextToken()).getTime() < System.currentTimeMillis()) {
                    currency = tokens.nextToken();
                    numeric = tokens.nextToken();
                    minorUnit = tokens.nextToken();
                    testCurrencies.add(Currency.getInstance(currency));
                }
            }
        }
        int index = toIndex(country);
        ISO4217Codes.add(Arguments.of(country, currency, Integer.parseInt(numeric),
                Integer.parseInt(minorUnit), index));
        codes[index] = DEFINED;
    }

    // Generates a unique index for an alpha-2 country
    private static int toIndex(String country) {
        return ((country.charAt(0) - 'A') * ALPHA_NUM + country.charAt(1) - 'A');
    }

    private static void createDateFormat() {
        format = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("GMT"));
        format.setLenient(false);
    }

    // Process 'extraCodes', turning them into JUnit arguments and populate
    // both additionalCodes and testCurrencies.
    private static void setUpAdditionalCodes() {
        for (String[] extraCode : extraCodes) {
            int index = toIndex(extraCode[0]);
            if (extraCode[1].length() != 0) {
                additionalCodes.add(Arguments.of(extraCode[0], extraCode[1],
                        Integer.parseInt(extraCode[2]), Integer.parseInt(extraCode[3]), index));
                testCurrencies.add(Currency.getInstance(extraCode[1]));
            } else {
                codes[index] = SKIPPED; // For example, Antarctica
            }
        }
    }

    // The previous set-up method populated most of testCurrencies. This
    // method finishes populating the list with 'otherCodes'.
    private static void setUpOtherCurrencies() {
        // Add otherCodes
        StringTokenizer st = new StringTokenizer(otherCodes, "-");
        while (st.hasMoreTokens()) {
            testCurrencies.add(Currency.getInstance(st.nextToken()));
        }
    }

    // Check that the data file is up-to-date
    @Test
    public void dataVersionTest() {
        CheckDataVersion.check();
    }

    /**
     * Tests the JDK's ISO4217 data and ensures the values for getNumericCode(),
     * getDefaultFractionDigits(), and getCurrencyCode() are as expected.
     */
    @ParameterizedTest
    @MethodSource({"ISO4217CodesProvider", "additionalCodesProvider"})
    public void countryCurrencyTest(String country, String currencyCode,
                                    int numericCode, int digits, int index) {
        currencyTest(currencyCode, numericCode, digits);
        countryTest(country, currencyCode);
        assertNotEquals(codes[index], TESTED,
                "Error: Re-testing a previously defined code, possible duplication");
        codes[index] = TESTED;
    }

    // Test a Currency built from currencyCode
    private static void currencyTest(String currencyCode, int numericCode, int digits) {
        Currency currency = Currency.getInstance(currencyCode);
        assertEquals(currency.getNumericCode(), numericCode);
        assertEquals(currency.getDefaultFractionDigits(), digits);
    }

    // Test a Currency built from country
    private static void countryTest(String country, String currencyCode) {
        Locale loc = Locale.of("", country);
        Currency currency = Currency.getInstance(loc);
        assertEquals(currency.getCurrencyCode(), currencyCode);
    }

    private static List<Arguments> ISO4217CodesProvider() {
        return ISO4217Codes;
    }

    private static List<Arguments> additionalCodesProvider() {
        return additionalCodes;
    }

    /**
     * Tests trying to create a Currency from an invalid alpha-2 country either
     * throws an IllegalArgumentException or returns null. The test data
     * supplied is every possible combination of AA -> ZZ.
     */
    @Test
    public void twoLetterCodesTest() {
        for (String country : codeCombos()) {
            if (codes[toIndex(country)] == UNDEFINED) {
                // if a code is undefined / 0, creating a Currency from it
                // should throw an IllegalArgumentException
                assertThrows(IllegalArgumentException.class,
                        () -> Currency.getInstance(Locale.of("", country)),
                        "Error: This should be an undefined code and throw IllegalArgumentException: " + country);
            } else if (codes[toIndex(country)] == SKIPPED) {
                // if a code is marked as skipped / 2, creating a Currency from it
                // should return null
                assertNull(Currency.getInstance(Locale.of("", country)),
                        "Error: Currency.getInstance() for this locale should return null: " + country);
            }
        }
    }

    // This method generates code combos from AA to ZZ
    private static List<String> codeCombos() {
        List<String> codeCombos = new ArrayList<>();
        for (int i = 0; i < ALPHA_NUM; i++) {
            for (int j = 0; j < ALPHA_NUM; j++) {
                char[] code = new char[2];
                code[0] = (char) ('A' + i);
                code[1] = (char) ('A' + j);
                codeCombos.add(new String(code));
            }
        }
        return codeCombos;
    }

    // This method ensures that getAvailableCurrencies() returns
    // the expected amount of currencies.
    @Test
    public void getAvailableCurrenciesTest() {
        Set<Currency> jreCurrencies = Currency.getAvailableCurrencies();
        // Ensure that testCurrencies has all the JRE currency codes
        assertTrue(testCurrencies.containsAll(jreCurrencies),
                getSetDiffs(jreCurrencies, testCurrencies));
    }

    private static String getSetDiffs(Set<Currency> jreCurrencies, Set<Currency> testCurrencies) {
        StringBuilder bldr = new StringBuilder();
        bldr.append("Error: getAvailableCurrencies() returned unexpected currencies: ");
        jreCurrencies.removeAll(testCurrencies);
        for (Currency curr : jreCurrencies) {
            bldr.append(" " + curr);
        }
        bldr.append("\n");
        return bldr.toString();
    }
}
