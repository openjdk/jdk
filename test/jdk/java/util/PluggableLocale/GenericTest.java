/*
 * Copyright (c) 2007, 2012, Oracle and/or its affiliates. All rights reserved.
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
import sun.util.locale.provider.*;

public class GenericTest {

    // test providers
    com.foo.BreakIteratorProviderImpl breakIP = new com.foo.BreakIteratorProviderImpl();
    com.foo.CollatorProviderImpl collatorP = new com.foo.CollatorProviderImpl();
    com.foo.DateFormatProviderImpl dateFP = new com.foo.DateFormatProviderImpl();
    com.foo.DateFormatSymbolsProviderImpl dateFSP = new com.foo.DateFormatSymbolsProviderImpl();
    com.foo.DecimalFormatSymbolsProviderImpl decimalFSP = new com.foo.DecimalFormatSymbolsProviderImpl();
    com.foo.NumberFormatProviderImpl numberFP = new com.foo.NumberFormatProviderImpl();
    com.bar.CurrencyNameProviderImpl currencyNP = new com.bar.CurrencyNameProviderImpl();
    com.bar.CurrencyNameProviderImpl2 currencyNP2 = new com.bar.CurrencyNameProviderImpl2();
    com.bar.LocaleNameProviderImpl localeNP = new com.bar.LocaleNameProviderImpl();
    com.bar.TimeZoneNameProviderImpl tzNP = new com.bar.TimeZoneNameProviderImpl();
    com.bar.GenericTimeZoneNameProviderImpl tzGenNP = new com.bar.GenericTimeZoneNameProviderImpl();
    com.bar.CalendarDataProviderImpl calDataP = new com.bar.CalendarDataProviderImpl();
    com.bar.CalendarNameProviderImpl calNameP = new com.bar.CalendarNameProviderImpl();

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
            new HashSet<>(Arrays.asList(Locale.getAvailableLocales()));
        HashSet<Locale> expected =
            new HashSet<>(Arrays.asList(LocaleProviderAdapter.forJRE().getAvailableLocales()));
        expected.addAll(Arrays.asList(breakIP.getAvailableLocales()));
        expected.addAll(Arrays.asList(collatorP.getAvailableLocales()));
        expected.addAll(Arrays.asList(dateFP.getAvailableLocales()));
        expected.addAll(Arrays.asList(dateFSP.getAvailableLocales()));
        expected.addAll(Arrays.asList(decimalFSP.getAvailableLocales()));
        expected.addAll(Arrays.asList(numberFP.getAvailableLocales()));
        expected.addAll(Arrays.asList(currencyNP.getAvailableLocales()));
        expected.addAll(Arrays.asList(currencyNP2.getAvailableLocales()));
        expected.addAll(Arrays.asList(localeNP.getAvailableLocales()));
        expected.addAll(Arrays.asList(tzNP.getAvailableLocales()));
        expected.addAll(Arrays.asList(tzGenNP.getAvailableLocales()));
        expected.addAll(Arrays.asList(calDataP.getAvailableLocales()));
        expected.addAll(Arrays.asList(calNameP.getAvailableLocales()));
        if (!result.equals(expected)) {
            throw new RuntimeException("Locale.getAvailableLocales() does not return the union of locales: diff="
                                       + getDiff(result, expected));
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

    private static String getDiff(Set set1, Set set2) {
        Set s1 = (Set)((HashSet)set1).clone();
        s1.removeAll(set2);

        Set s2 = (Set)((HashSet)set2).clone();
        s2.removeAll(set1);
        s2.addAll(s1);
        return s2.toString();
    }
}
