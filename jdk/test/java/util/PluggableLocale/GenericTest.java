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
 *
 */

import java.text.*;
import java.util.*;
import sun.util.resources.*;

public class GenericTest {

    // test providers
    com.foo.BreakIteratorProviderImpl breakIP = new com.foo.BreakIteratorProviderImpl();
    com.foo.CollatorProviderImpl collatorP = new com.foo.CollatorProviderImpl();
    com.foo.DateFormatProviderImpl dateFP = new com.foo.DateFormatProviderImpl();
    com.foo.DateFormatSymbolsProviderImpl dateFSP = new com.foo.DateFormatSymbolsProviderImpl();
    com.foo.DecimalFormatSymbolsProviderImpl decimalFSP = new com.foo.DecimalFormatSymbolsProviderImpl();
    com.foo.NumberFormatProviderImpl numberFP = new com.foo.NumberFormatProviderImpl();
    com.bar.CurrencyNameProviderImpl currencyNP = new com.bar.CurrencyNameProviderImpl();
    com.bar.LocaleNameProviderImpl localeNP = new com.bar.LocaleNameProviderImpl();
    com.bar.TimeZoneNameProviderImpl tzNP = new com.bar.TimeZoneNameProviderImpl();

    public static void main(String[] s) {
        new GenericTest();
    }

    GenericTest() {
        availableLocalesTest();
        localeFallbackTest();
    }

    /**
     * Make sure that all the locales are available from the existing providers
     */
    void availableLocalesTest() {
        // Check that Locale.getAvailableLocales() returns the union of the JRE supported
        // locales and providers' locales
        HashSet<Locale> result =
            new HashSet<Locale>(Arrays.asList(Locale.getAvailableLocales()));
        HashSet<Locale> expected =
            new HashSet<Locale>(Arrays.asList(LocaleData.getAvailableLocales()));
        expected.addAll(Arrays.asList(breakIP.getAvailableLocales()));
        expected.addAll(Arrays.asList(collatorP.getAvailableLocales()));
        expected.addAll(Arrays.asList(dateFP.getAvailableLocales()));
        expected.addAll(Arrays.asList(dateFSP.getAvailableLocales()));
        expected.addAll(Arrays.asList(decimalFSP.getAvailableLocales()));
        expected.addAll(Arrays.asList(numberFP.getAvailableLocales()));
        expected.addAll(Arrays.asList(currencyNP.getAvailableLocales()));
        expected.addAll(Arrays.asList(localeNP.getAvailableLocales()));
        expected.addAll(Arrays.asList(tzNP.getAvailableLocales()));
        if (!result.equals(expected)) {
            throw new RuntimeException("Locale.getAvailableLocales() does not return the union of locales");
        }
    }

    /**
     * test with "xx_YY_ZZ", which is an example locale not contained
     * in Locale.getAvailableLocales().  Fallback tests for supported locales
     * are done in each xxxProviderTest test cases.
     */
    void localeFallbackTest() {
        Locale xx = new Locale("xx");
        Locale dispLocale = new Locale ("xx", "YY", "ZZ");

        String xxname = xx.getDisplayLanguage(xx);
        String expected = localeNP.getDisplayLanguage(xx.getLanguage(), dispLocale);
        if (!xxname.equals(expected)) {
            throw new RuntimeException("Locale fallback did not perform correctly. got: "+xxname+" expected: "+expected);
        }
    }
}
