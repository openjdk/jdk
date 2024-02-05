/*
 * Copyright (c) 2012, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6336885 7196799 7197573 8008577 8010666 8013233 8015960 8028771
 *      8054482 8062006 8150432 8215913 8220227 8236495
 * @summary General Locale provider test (ex: adapter loading). See the
 *          other LocaleProviders* test classes for more specific tests (ex:
 *          java.text.Format related bugs).
 * @library /test/lib
 * @build LocaleProviders
 * @modules java.base/sun.util.locale.provider
 * @run junit/othervm LocaleProvidersRun
 */

import java.util.Locale;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.condition.OS.WINDOWS;

/*
 * Note: If this test launches too many JVMs, consider increasing timeout.
 * As the LocaleProvider is set during java startup time, this test and the subclasses
 * will always have to launch a separate JVM for testing of different providers.
 */
public class LocaleProvidersRun {

    private static String defLang;
    private static String defCtry;
    private static String defFmtLang;
    private static String defFmtCtry;

    // Get the system default locale values. Used to decide param values for tests.
    @BeforeAll
    static void setUp() {
        Locale platDefLoc = Locale.getDefault(Locale.Category.DISPLAY);
        Locale platDefFormat = Locale.getDefault(Locale.Category.FORMAT);
        defLang = platDefLoc.getLanguage();
        defCtry = platDefLoc.getCountry();
        defFmtLang = platDefFormat.getLanguage();
        defFmtCtry = platDefFormat.getCountry();

        // Print out system defaults for diagnostic purposes
        System.out.printf("DEFLANG = %s, DEFCTRY = %s, DEFFMTLANG = %s, DEFFMTCTRY = %s",
                defLang, defCtry, defFmtLang, defFmtCtry);
    }

    /*
     * Test the adapter loading logic in LocaleProviderAdapter.
     * Ensures that correct fallbacks are implemented.
     */
    @ParameterizedTest
    @MethodSource
    public void adapterTest(String prefList, String param1,
                            String param2, String param3) throws Throwable {
        LocaleProviders.test(prefList, "adapterTest", param1, param2, param3);
    }

    /*
     * Data provider which only launches against the LocaleProvider::adapterTest
     * method. The arguments are dictated based off the operating system/platform
     * Locale. Tests against variety of provider orders.
     */
    private static Stream<Arguments> adapterTest() {
        // Testing HOST is selected for the default locale if specified on Windows or MacOSX
        String osName = System.getProperty("os.name");
        String param1 = "JRE";
        if (osName.startsWith("Windows") || osName.startsWith("Mac")) {
            param1 = "HOST";
        }

        // Testing HOST is NOT selected for the non-default locale, if specified
        // try to find the locale JRE supports which is not the platform default
        // (HOST supports that one)
        String param2;
        String param3;
        if (!defLang.equals("en") && !defFmtLang.equals("en")) {
            param2 = "en";
            param3 = "US";
        } else if (!defLang.equals("ja") && !defFmtLang.equals("ja")) {
            param2 = "ja";
            param3 = "JP";
        } else {
            param2 = "zh";
            param3 = "CN";
        }

        return Stream.of(
                Arguments.of("HOST,JRE", param1, defLang, defCtry),
                Arguments.of("HOST,JRE", "JRE", param2, param3),

                // Testing SPI is NOT selected, as there is none.
                Arguments.of("SPI,JRE", "JRE", "en", "US"),
                Arguments.of("SPI,COMPAT", "JRE", "en", "US"),

                // Testing the order, variant #1. This assumes en_GB DateFormat data are
                // available both in JRE & CLDR
                Arguments.of("CLDR,JRE", "CLDR", "en", "GB"),
                Arguments.of("CLDR,COMPAT", "CLDR", "en", "GB"),

                // Testing the order, variant #2. This assumes en_GB DateFormat data are
                // available both in JRE & CLDR
                Arguments.of("JRE,CLDR", "JRE", "en", "GB"),
                Arguments.of("COMPAT,CLDR", "JRE", "en", "GB"),

                // Testing the order, variant #3 for non-existent locale in JRE
                // assuming "haw" is not in JRE.
                Arguments.of("JRE,CLDR", "CLDR", "haw", ""),
                Arguments.of("COMPAT,CLDR", "CLDR", "haw", ""),

                // Testing the order, variant #4 for the bug 7196799. CLDR's "zh" data
                // should be used in "zh_CN"
                Arguments.of("CLDR", "CLDR", "zh", "CN"),

                // Testing FALLBACK provider. SPI and invalid one cases.
                Arguments.of("SPI", "FALLBACK", "en", "US"),
                Arguments.of("FOO", "CLDR", "en", "US"),
                Arguments.of("BAR,SPI", "FALLBACK", "en", "US")
            );
    }

    /*
     * 8010666: Test to ensure correct implementation of Currency/LocaleNameProvider
     * in HOST Windows provider (English locale)
     */
    @Test
    @EnabledOnOs(WINDOWS)
    @EnabledIfSystemProperty(named = "user.language", matches = "en")
    public void currencyNameProviderWindowsHost() throws Throwable {
        LocaleProviders.test("HOST", "bug8010666Test");
    }

    /*
     * 8220227: Ensure Locale::getDisplayCountry does not display error message
     * under HOST Windows (non-english locale)
     */
    @Test
    @EnabledOnOs(WINDOWS)
    @DisabledIfSystemProperty(named = "user.language", matches = "en")
    public void nonEnglishDisplayCountryHost() throws Throwable {
        LocaleProviders.test("HOST", "bug8220227Test");
    }
}
