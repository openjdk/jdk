/*
 * Copyright (c) 2012, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6336885 7196799 7197573 7198834 8000245 8000615 8001440 8008577
 *      8010666 8013086 8013233 8013903 8015960 8028771 8054482 8062006
 *      8150432 8215913 8220227 8228465 8232871 8232860 8236495 8245241
 *      8246721 8248695 8257964 8261919 8268379
 * @summary tests for "java.locale.providers" system property
 * @library /test/lib
 * @build LocaleProviders
 *        providersrc.spi.src.tznp
 *        providersrc.spi.src.tznp8013086
 * @modules java.base/sun.util.locale
 *          java.base/sun.util.locale.provider
 * @run junit/othervm -Djdk.lang.Process.allowAmbiguousCommands=false LocaleProvidersRun
 */

import java.util.Arrays;
import java.util.Locale;

import jdk.test.lib.JDKToolLauncher;
import jdk.test.lib.Utils;
import jdk.test.lib.process.ProcessTools;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/*
 * This class serves as a test runner that launches LocaleProvider related
 * tests with a new JVM, so that different providers can be set at the command line.
 * To add a new test, add the method implementation to LocaleProviders,
 * then add a test to this class which simply invokes the actual test method.
 * As this test has shown to cause intermittent issues, each test method is set with
 * a timeout of 10 seconds, so that the test class itself can recover if a test
 * method times out.
 */
@Timeout(10)
public class LocaleProvidersRun {

    // Locale DISPLAY values
    private static String defLang;
    private static String defCtry;
    // Locale FORMAT values
    private static String defFmtLang;
    private static String defFmtCtry;

    // Store the language and country of the Locale.Category.DISPLAY and FORMAT,
    // which are used in various data providers.
    @BeforeAll
    static void getLocaleValues() {
        var platDefLoc = Locale.getDefault(Locale.Category.DISPLAY);
        defLang = platDefLoc.getLanguage();
        defCtry = platDefLoc.getCountry();
        var platDefFormat = Locale.getDefault(Locale.Category.FORMAT);
        defFmtLang = platDefFormat.getLanguage();
        defFmtCtry = platDefFormat.getCountry();
    }


    // Ensure that the correct adapter can be loaded when expected
    @ParameterizedTest
    @MethodSource
    public void adapterTest(String provider, String testName,
                            String param1, String param2, String param3) {
            launchTest(provider, testName, param1, param2, param3);
    }

    // Variety of different ordered locale providers/fallbacks to ensure
    // the adapters are well tested and are correctly returned
    static Arguments[] adapterTest() {
        // Run Test
        // testing HOST is selected for the default locale,
        // if specified on Windows or MacOSX
        String osName = System.getProperty("os.name");
        String param1 = "JRE";
        if (osName.startsWith("Windows") || osName.startsWith("Mac")) {
            param1 = "HOST";
        }

        // Testing HOST is NOT selected for the non-default locale, if specified
        // Try to find the locale JRE supports which is not the platform default
        // (HOST supports that one)
        String param2;
        String param3;
        if (!defLang.equals("en") && !defFmtLang.equals("en")){
            param2 = "en";
            param3 = "US";
        } else if(!defLang.equals("ja") && !defFmtLang.equals("ja")){
            param2 = "ja";
            param3 = "JP";
        } else {
            param2 = "zh";
            param3 = "CN";
        }
        return new Arguments[] {
                arguments("HOST,JRE", "adapterTest", param1, defLang, defCtry),
                arguments("HOST,JRE", "adapterTest", "JRE", param2, param3),
                // Testing SPI is NOT selected, as there is none.
                arguments("SPI,JRE", "adapterTest", "JRE", "en", "US"),
                arguments("SPI,COMPAT", "adapterTest", "JRE", "en", "US"),
                // Testing the order, variant #1. This assumes en_GB DateFormat data are
                // available both in JRE & CLDR
                arguments("CLDR,JRE", "adapterTest", "CLDR", "en", "GB"),
                arguments("CLDR,COMPAT", "adapterTest", "CLDR", "en", "GB"),
                // Testing the order, variant #2. This assumes en_GB DateFormat data are
                // available both in JRE & CLDR
                arguments("JRE,CLDR", "adapterTest", "JRE", "en", "GB"),
                arguments("COMPAT,CLDR", "adapterTest", "JRE", "en", "GB"),
                // Testing the order, variant #3 for non-existent locale in JRE
                // assuming "haw" is not in JRE.
                arguments("JRE,CLDR", "adapterTest", "CLDR", "haw", ""),
                arguments("COMPAT,CLDR", "adapterTest", "CLDR", "haw", ""),
                // Testing the order, variant #4 for the bug 7196799. CLDR's "zh" data
                // should be used in "zh_CN"
                arguments("CLDR", "adapterTest", "CLDR", "zh", "CN"),
                // Testing FALLBACK provider. SPI and invalid one cases.
                arguments("SPI", "adapterTest", "FALLBACK", "en", "US"),
                arguments("FOO", "adapterTest", "CLDR", "en", "US"),
                arguments("BAR,SPI", "adapterTest", "FALLBACK", "en", "US")

        };
    }

    // Ensure the Windows HOST adapter is not appending extra spaces for date patterns
    @Test
    public void bug7198834Test() {
        // Testing 7198834 fix.
        launchTest("HOST", "bug7198834Test");
    }

    // Ensure TimeZoneNameProvider returns expected output
    @ParameterizedTest
    @MethodSource
    public void tzNameTest(String provider, String testName, String param) {
        launchTest(provider, testName, param);
    }

    // Various locale providers to test for correct timezone name
    static Arguments[] tzNameTest() {
        return new Arguments[]{
                // Testing 8000245 fix.
                arguments("JRE", "tzNameTest", "Europe/Moscow"),
                arguments("COMPAT", "tzNameTest", "Europe/Moscow"),
                // Testing 8000615 fix.
                arguments("JRE", "tzNameTest", "America/Los_Angeles"),
                arguments("COMPAT", "tzNameTest", "America/Los_Angeles")
        };
    }

    // Ensure invalid number extension does not cause exception in NumberFormat.format()
    @Test
    public void bug8001440Test() {
        // Testing 8001440 fix.
        launchTest("CLDR", "bug8001440Test");
    }

    // Implement Currency/LocaleNameProvider in Windows Host LocaleProviderAdapter
    @Test
    public void bug8010666Test() {
        // Testing 8010666 fix.
        if (defLang.equals("en")) {
            launchTest("HOST", "bug8010666Test");
        }
    }

    // Ensure NPE not thrown by SimpleDateFormat.parse() with custom impl
    // of TimeZoneNameProvider
    @ParameterizedTest
    @MethodSource
    public void bug8013086Test(String provider, String param1, String param2) {
        launchTest(provider, "bug8013086Test", param1, param2);
    }

    // Providers with SPI fallback, create Japanese locale
    static Arguments[] bug8013086Test() {
        return new Arguments[]{
                // Testing 8013086 fix.
                arguments("JRE,SPI", "ja", "JP"),
                arguments("COMPAT,SPI", "ja", "JP")
        };
    }


    // Ensure HOST Windows produces correct Japanese era, date, and month for SDfmt
    @ParameterizedTest
    @MethodSource
    public void bug8013903Test(String provider) {
        launchTest(provider, "bug8013903Test");
    }

    // Various providers/fallbacks
    static Arguments[] bug8013903Test() {
        return new Arguments[]{
                // Testing 8013903 fix. (Windows only)
                arguments("HOST"),
                arguments("HOST, JRE"),
                arguments("HOST, COMPAT")
            };
    }

    // Ensure expected currency symbol for Window's zh_CN. Testing 8027289 fix.
    @ParameterizedTest
    @MethodSource
    public void bug8027289Test(String provider, String param) {
        if (defFmtLang.equals("zh") && defFmtCtry.equals("CN")) {
            launchTest(provider, "bug8027289Test", param);
        }
    }

    // Combos of locale providers and fallbacks with expected symbol
    static Arguments[] bug8027289Test() {
        // If the platform format default is zh_CN, this assumes Windows' currency
        // symbol for zh_CN is \u00A5, the yen (yuan) sign.
        return new Arguments[]{
                arguments("JRE,HOST", "FFE5"),
                arguments("COMPAT,HOST", "FFE5"),
                arguments("HOST", "00A5")
            };
    }

    // Ensure Locale.getDisplayCountry() does not throw error message
    // for non-English Windows 10
    @Test
    public void bug8220227Test() {
        // Testing 8220227 fix. (Windows only)
        if (!defLang.equals("en")) {
            launchTest("HOST", "bug8220227Test");
        }
    }

    // Ensure correct hera name for GregorianCalendar with US locale
    @Test
    public void bug8228465Test() {
        // Testing 8228465 fix. (Windows only)
        launchTest("HOST", "bug8228465Test");
    }

    // Ensure Japanese returned for Host Locale Provider on Mac
    @Test
    public void bug8232871Test() {
        // Testing 8232871 fix. (macOS only)
        launchTest("HOST", "bug8232871Test");
    }

    // Ensure correctly formatted values for MessageFormat.format() under HOST
    @Test
    public void bug8232860Test() {
        // Testing 8232860 fix. (macOS/Windows only)
        launchTest("HOST", "bug8232860Test");
    }

    // Ensure that an incorrect locale provider is logged to user
    @Test
    public void bug8245241Test() {
        // Testing 8245241 fix.
        // jdk.lang.Process.allowAmbiguousCommands=false is needed for properly
        // escaping double quotes in the string argument.
        launchTest("FOO", "bug8245241Test",
                "Invalid locale provider adapter \"FOO\" ignored.");
    }

    // Ensure HostLocaleProviderAdapterImpl provides correct date-only
    @Test
    public void bug8248695Test() {
        // Testing 8248695 fix.
        launchTest("HOST", "bug8248695Test");
    }

    // Tests that Calendar.getMinimalDaysInFirstWeek works for HOST
    @Test
    public void bug8257964Test() {
        // Testing 8257964 fix. (macOS/Windows only)
        launchTest("HOST", "bug8257964Test");
    }

    /*
     * Launches a test in a new JVM which can be any method found in LocaleProviders.
     * These tests have access to sun.util.locale.provider and are passed the
     * desired locale provider(s) (e.g. "HOST").
     */
    private static void launchTest(String providers, String methodName, String... params) {
        System.out.printf("$$$ Launching test LocaleProviders::%s, with Djava.locale.providers=%s," +
                " and params:%s $$$%n", methodName, providers, Arrays.toString(params));

        JDKToolLauncher launcher = JDKToolLauncher.createUsingTestJDK("java");
        launcher.addToolArg("-ea") // Enable assertions
                .addToolArg("-esa") // Enable system assertions
                .addToolArg("-cp")
                .addToolArg(Utils.TEST_CLASS_PATH)
                // Used for bug 8245241
                .addToolArg("-Djava.util.logging.config.class=LocaleProviders$LogConfig")
                .addToolArg("-Djava.locale.providers=" + providers)
                .addToolArg("--add-exports=java.base/sun.util.locale.provider=ALL-UNNAMED")
                .addToolArg("LocaleProviders")
                .addToolArg(methodName);

        // Add parameters if required by the desired test method
        for (String param : params) {
            launcher.addToolArg(param);
        }

        // Launch the test
        try {
            int exitCode = ProcessTools.executeCommand(launcher.getCommand())
                    .getExitValue();
            assertEquals(0, exitCode, "Unexpected exit code: " + exitCode);
        } catch (Throwable t) {
            throw new RuntimeException("Launched test failed with: "+ t);
        }
    }
}
