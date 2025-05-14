/*
 * Copyright (c) 2007, 2025, Oracle and/or its affiliates. All rights reserved.
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
 *      8208746 8209775 8264792 8274658 8283277 8296239 8321480 8334653
 *      8354343 8354344 8356096
 * @summary Validate ISO 4217 data for Currency class.
 * @modules java.base/java.util:open
 *          jdk.localedata
 * @library /test/lib
 * @run junit/othervm -DMOCKED.TIME=setup ValidateISO4217
 * @run main/othervm --patch-module java.base=${test.class.path}
 *      -DMOCKED.TIME=check -Djava.util.currency.data=${test.src}/currency.properties ValidateISO4217
 * @run junit/othervm --patch-module java.base=${test.class.path}
 *      -DMOCKED.TIME=true ValidateISO4217
 */

/* The run invocation order is important. The first invocation will generate
 * class files for Currency that mock System.currentTimeMillis() as Long.MAX_VALUE,
 * which is required by the subsequent invocations. The second invocation ensures that
 * the module patch and mocked time are functioning correctly; it does not run any tests.
 * The third invocation using the modded class files via a module patch allow us
 * to test any cut-over dates after the transition.
 * Valid MOCKED.TIME values are "setup", "check", and "true".
 */

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.CodeTransform;
import java.lang.classfile.MethodTransform;
import java.lang.classfile.instruction.InvokeInstruction;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Currency;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TimeZone;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
 * based on ISO 4217. The golden-data file, 'ISO4217-list-one.txt', based on the
 * "List one: Currency, fund and precious metal codes" has the following
 * format: <Country code>\t<Currency code>\t<Numeric code>\t<Minor unit>[\t<Cutover Date>\t<new Currency code>\t<new Numeric code>\t<new Minor unit>]
 * The Cutover Date is given in SimpleDateFormat's 'yyyy-MM-dd-HH-mm-ss' format in the GMT time zone.
 */
public class ValidateISO4217 {

    // Input golden-data file
    private static final File dataFile = new File(System.getProperty(
            "test.src", "."), "ISO4217-list-one.txt");
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
    // Special case currencies that should only exist after the cut-over occurs
    private static final Set<String> currenciesNotYetDefined = new HashSet<>();
    // Codes that are obsolete, do not have related country, extra currency
    private static final String otherCodes =
            "ADP-AFA-ATS-AYM-AZM-BEF-BGL-BOV-BYB-BYR-CHE-CHW-CLF-COU-CUC-CYP-"
                    + "DEM-EEK-ESP-FIM-FRF-GHC-GRD-GWP-HRK-IEP-ITL-LTL-LUF-LVL-MGF-MRO-MTL-MXV-MZM-NLG-"
                    + "PTE-ROL-RUR-SDD-SIT-SLL-SKK-SRG-STD-TMM-TPE-TRL-VEF-UYI-USN-USS-VEB-VED-"
                    + "XAD-XAG-XAU-XBA-XBB-XBC-XBD-XDR-XFO-XFU-XPD-XPT-XSU-XTS-XUA-XXX-"
                    + "YUM-ZMK-ZWD-ZWL-ZWN-ZWR";
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
            {"AX", "EUR", "978", "2"},      // ÅLAND ISLANDS
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
    private static final String MODULE_PATCH_LOCATION =
            System.getProperty("test.classes") + "/java/util/";
    private static final String MOCKED_TIME = System.getProperty("MOCKED.TIME");

    // Classes that should mock System.currentTimeMillis()
    private static final String[] CLASSES =
            Stream.concat(
                Stream.of("Currency.class"),
                Arrays.stream(Currency.class.getDeclaredClasses())
                        .map(c -> "Currency$" + c.getSimpleName() + ".class")
            ).toArray(String[]::new);

    // "check" invocation only runs the main method (and not any tests) to determine if the
    // future time checking is correct
    public static void main(String[] args) {
        if (MOCKED_TIME.equals("check")) {
            // Check that the module patch class files exist
            checkModulePatchExists();
            // Check time is mocked
            // Override for PK=PKR in test/currency.properties is PKZ - simple
            // Override for PW=USD in test/currency.properties is MWP - special
            assertEquals("PKZ", Currency.getInstance(Locale.of("", "PK")).getCurrencyCode(),
                    "Mocked time / module patch not working");
            assertEquals("MWP", Currency.getInstance(Locale.of("", "PW")).getCurrencyCode(),
                    "Mocked time / module patch not working");
            // Properly working. Do nothing and move to third invocation
        } else {
            throw new RuntimeException(
                    "Incorrect usage of ValidateISO4217. Main method invoked without proper system property value");
        }
    }

    @BeforeAll
    static void setUp() throws Exception {
        checkUsage();
        setUpPatchedClasses();
        setUpTestingData();
    }

    // Enforce correct usage of ValidateISO4217
    static void checkUsage() {
        if (MOCKED_TIME == null
                || (!MOCKED_TIME.equals("setup") && !MOCKED_TIME.equals("true"))) {
            throw new RuntimeException(
                    "Incorrect usage of ValidateISO4217. Missing \"MOCKED.TIME\" system property");
        }
        if (MOCKED_TIME.equals("true")) {
            checkModulePatchExists();
        }
    }

    static void checkModulePatchExists() {
        // Check that the module patch class files exist
        for (String className : CLASSES) {
            var file = new File(MODULE_PATCH_LOCATION + className);
            assertTrue(file.isFile(), "Module patch class files missing");
        }
    }

    // Patch the relevant classes required for module patch
    static void setUpPatchedClasses() throws IOException {
        if (MOCKED_TIME.equals("setup")) {
            new File(MODULE_PATCH_LOCATION).mkdirs();
            for (String s : CLASSES) {
                patchClass(s);
            }
        }
    }

    // Mock calls of System.currentTimeMillis() within Currency to Long.MAX_VALUE.
    // This effectively ensures that we are always past any cut-over dates.
    private static void patchClass(String name) throws IOException {
        CodeTransform codeTransform = (codeBuilder, e) -> {
            switch (e) {
                case InvokeInstruction i when
                        i.owner().asInternalName().equals("java/lang/System")
                                && i.name().equalsString("currentTimeMillis") ->
                    codeBuilder.loadConstant(Long.MAX_VALUE); // mock
                default -> codeBuilder.accept(e);
            }
        };
        MethodTransform methodTransform = MethodTransform.transformingCode(codeTransform);
        ClassTransform classTransform = ClassTransform.transformingMethods(methodTransform);
        ClassFile cf = ClassFile.of();
        byte[] newBytes = cf.transformClass(cf.parse(
                Files.readAllBytes(Paths.get(URI.create("jrt:/java.base/java/util/" + name)))), classTransform);

        String patchedClass = MODULE_PATCH_LOCATION + name;
        var file = new File(patchedClass);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(newBytes);
        }
    }

    // Sets up the following test data:
    // ISO4217Codes, additionalCodes, testCurrencies, codes, currenciesNotYetDefined
    static void setUpTestingData() throws Exception {
        // These functions laterally setup 'testCurrencies' and 'codes'
        // at the same time
        setUpISO4217Codes();
        setUpAdditionalCodes();
        setUpOtherCurrencies();
        setUpNotYetDefined();
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
                // If the cut-over already passed, use the new currency for ISO4217Codes
                if (format.parse(tokens.nextToken()).getTime() < currentTimeMillis()) {
                    currency = tokens.nextToken();
                    numeric = tokens.nextToken();
                    minorUnit = tokens.nextToken();
                    testCurrencies.add(Currency.getInstance(currency));
                } else {
                    // Add all future currencies to the set.
                    // We process it later once 'testCurrencies' is complete
                    // to only include ones that should not be defined yet.
                    currenciesNotYetDefined.add(tokens.nextToken());
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

    // Future currencies that are already defined as ISO 4217 codes should be
    // removed. For example, in CW=ANG;2025-04-01-04-00-00;EUR "EUR" would be
    // removed as it is an already valid ISO 4217 code
    private static void setUpNotYetDefined() {
        var allFutureCurrencies = testCurrencies
                .stream()
                .map(Currency::getCurrencyCode)
                .collect(Collectors.toSet());
        currenciesNotYetDefined.removeIf(allFutureCurrencies::contains);
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

    // Any future currencies that do not already exist before the cut-over
    // should not be instantiable. This scenario is when a country transfers
    // to a new code, that is not already a valid ISO 4217 code. For example,
    // what occurred in the 176 update situation.
    @Test
    public void nonDefinedFutureCurrenciesTest() {
        for (String curr : currenciesNotYetDefined) {
            assertThrows(IllegalArgumentException.class, () -> Currency.getInstance(curr),
                    "The future cut-over currency: %s should not exist".formatted(curr));
        }
    }

    // This method ensures that getAvailableCurrencies() returns
    // the expected amount of currencies.
    @Test
    public void getAvailableCurrenciesTest() {
        Set<Currency> jreCurrencies = Currency.getAvailableCurrencies();
        // Ensure that testCurrencies has all the JRE currency codes
        assertTrue(testCurrencies.containsAll(jreCurrencies),
                getSetDiffs(jreCurrencies, testCurrencies));
        // Implicitly checks that jreCurrencies does not contain any currencies
        // defined in currenciesNotYetDefined
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

    // Either the current system time, or a mocked value equal to Long.MAX_VALUE
    static long currentTimeMillis() {
        var mocked = MOCKED_TIME.equals("true");
        return mocked ? Long.MAX_VALUE : System.currentTimeMillis();
    }
}
