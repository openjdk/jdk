/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @run main/othervm ValidateCurrencyCoverage
 * @summary Validates that all currency codes and country-currency mappings
 * in the input file are consistent with the Java Currency API.
 */

import java.io.*;
import java.text.*;
import java.util.*;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ValidateCurrencyCoverage {

    private static final int ALPHA_NUM = 26;
    private static final byte UNDEFINED = 0; // Indicates that a country-currency mapping has not yet been processed
    private static final byte DEFINED = 1; // Indicates that a country-currency mapping has been processed and defined

    // Input data file
    private static final String DATA_FILE = "VerifyCurrencyList.txt";

    // Mapping array to track defined country codes
    private static final byte[] codes = new byte[ALPHA_NUM * ALPHA_NUM];

    // Global error flag
    private static boolean errorOccurred = false;

    // Set to store all test currencies found in the input
    private static final Set<Currency> testCurrencies = new HashSet<>();

    public static void main(String[] args) throws Exception {
        Path SRC_DIR = Paths.get(System.getProperty("test.src", "src"));

        // Override the default Java currency data property for testing
        System.setProperty("java.util.currency.data", SRC_DIR + File.separator + "currency.properties");
        testCurrencyCoverage();
        if (errorOccurred) {
            throw new RuntimeException("Currency validation failed");
        }
    }

    /**
     * Reads the currency definitions from the input file and validates each entry.
     */
    private static void testCurrencyCoverage() throws Exception {
        try (FileReader fr = new FileReader(new File(System.getProperty("test.src", "."), DATA_FILE));
                BufferedReader in = new BufferedReader(fr)) {
            String line;
            SimpleDateFormat format = null;

            while ((line = in.readLine()) != null) {
                // Skip empty or commented lines
                if (line.length() == 0 || line.charAt(0) == '#') {
                    continue;
                }
                StringTokenizer tokens = new StringTokenizer(line, "\t");
                String country = tokens.nextToken();

                // Skip lines with invalid country codes
                if (country.length() != 2) {
                    continue;
                }
                String currency;
                String numeric;
                String minorUnit;
                int tokensCount = tokens.countTokens();
                if (tokensCount < 3) {
                    // Default values if not all tokens are present
                    currency = "";
                    numeric = "0";
                    minorUnit = "0";
                } else {
                    currency = tokens.nextToken();
                    numeric = tokens.nextToken();
                    minorUnit = tokens.nextToken();
                    testCurrencies.add(Currency.getInstance(currency));
                    // Handle future currency transitions if date is specified
                    if (tokensCount > 3) {
                        if (format == null) {
                            format = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US);
                            format.setTimeZone(TimeZone.getTimeZone("GMT"));
                            format.setLenient(false);
                        }
                        String dates = tokens.nextToken();
                        System.out.println(dates);
                        if (format.parse(dates).getTime() < System.currentTimeMillis()) {
                            currency = tokens.nextToken();
                            numeric = tokens.nextToken();
                            minorUnit = tokens.nextToken();
                            testCurrencies.add(Currency.getInstance(currency));
                        }
                    }
                }
                int index = toIndex(country);
                int numericCode = Integer.parseInt(numeric);
                int digits = Integer.parseInt(minorUnit);
                testCountryCurrencyWithLocale(country, currency, numericCode, digits, index);
                testCountryCurrencyWithCurrencyCode(currency, numericCode, digits);
            }
        }

    }

    /**
     * Converts a two-letter country code to an array index.
     */
    private static int toIndex(String s) {
        return ((s.charAt(0) - 'A') * ALPHA_NUM + s.charAt(1) - 'A');
    }

    /**
     * Validates the locale based currency instance returned.
     */
    private static void testCountryCurrencyWithLocale(String country, String currencyCode, int numericCode, int digits,
            int index) {
        if (currencyCode.length() == 0) {
            return;
        }
        Locale loc = new Locale("", country);
        try {
            Currency currency = Currency.getInstance(loc);
            if (!currency.getCurrencyCode().equals(currencyCode)) {
                System.err.println("testCountryCurrencyWithLocale:");
                System.err.println("Error: [" + country + ":" + loc.getDisplayCountry() + "] expected: " + currencyCode
                        + ", got: " + currency.getCurrencyCode());
                errorOccurred = true;
            }
            if (codes[index] != UNDEFINED) {
                System.out.println("Warning: [" + country + ":" + loc.getDisplayCountry()
                        + "] multiple definitions. currency code=" + currencyCode);
            }
            codes[index] = DEFINED;
        } catch (Exception e) {
            System.err.println("testCountryCurrencyWithLocale:");
            System.err.println("Error: " + e + ": Country=" + country);
            errorOccurred = true;
        }
    }

    /**
     * Validates the currencycode based currency instance.
     */
    private static void testCountryCurrencyWithCurrencyCode(String currencyCode, int numericCode, int digits) {
        if (currencyCode.length() == 0) {
            return;
        }
        try {
            Currency currency = Currency.getInstance(currencyCode);
            if (currency.getNumericCode() != numericCode) {
                System.err.println("testCountryCurrencyWithCurrencyCode:");
                System.err.println("Error: [" + currencyCode + "] expected: " + numericCode + "; got: "
                        + currency.getNumericCode());
                errorOccurred = true;
            }
            if (currency.getDefaultFractionDigits() != digits) {
                System.err.println("testCountryCurrencyWithCurrencyCode:");
                System.err.println("Error: [" + currencyCode + "] expected: " + digits + "; got: "
                        + currency.getDefaultFractionDigits());
                errorOccurred = true;
            }
        } catch (Exception e) {
            System.err.println("testCountryCurrencyWithCurrencyCode:");
            System.err.println("Error: " + e + ": Currency code=" + currencyCode);
            errorOccurred = true;
        }
    }
}