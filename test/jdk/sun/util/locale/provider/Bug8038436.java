/*
 * Copyright (c) 2014, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8038436 8158504 8065555 8167143 8167273 8189272 8287340 8174269
 * @summary Test for changes in 8038436
 * @modules java.base/sun.util.locale.provider
 *          java.base/sun.util.spi
 *          jdk.localedata
 * @compile -XDignore.symbol.file Bug8038436.java
 * @run main Bug8038436 availlocs
 */

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import sun.util.locale.provider.LocaleProviderAdapter;

public class Bug8038436 {
    public static void main(String[] args) {

        switch (args[0]) {

        case "availlocs":
            availableLocalesTests();
            break;
        default:
            throw new RuntimeException("no test was specified.");
        }

    }


    static final String[] bipLocs = (", en, en_US, nb, nb_NO, nn_NO, th, ").split(",\\s*");

    static final String[] cpLocs = (", ar, be, bg, ca, cs, da, el, en, en_US, es, et, fi, " +
        "fr, he, hi, hr, hu, is, ja, ko, lt, lv, mk, nb, nb_NO, nn_NO, no, pl, ro, ru, sk, sl, " +
        "sq, sr, sr__#Latn, sv, th, tr, uk, vi, zh, zh_HK, zh_HK_#Hant, " +
        "zh_TW, zh_TW_#Hant, ").split(",\\s*");

    /*
     * Validate whether FALLBACK's *Providers return supported locales list based on
     * their actual resource bundle existence. The above golden data
     * are manually extracted, so they need to be updated if new locale
     * data resource bundle were added.
     */
    private static void availableLocalesTests() {
        LocaleProviderAdapter fallback = LocaleProviderAdapter.forType(LocaleProviderAdapter.Type.FALLBACK);

        checkAvailableLocales("BreakIteratorProvider",
            fallback.getBreakIteratorProvider().getAvailableLocales(), bipLocs);
        checkAvailableLocales("CollatorProvider",
            fallback.getCollatorProvider().getAvailableLocales(), cpLocs);
    }

    private static void checkAvailableLocales(String testName, Locale[] got, String[] expected) {
        System.out.println("Testing available locales for " + testName);
        List<String> gotList = Arrays.stream(got)
            .map(Locale::toString)
            .sorted()
            .toList();
        List<String> expectedList = Arrays.stream(expected)
            .toList();

        if (!gotList.equals(expectedList)) {
            throw new RuntimeException("\n" + gotList + "\n is not equal to \n" + expectedList);
        }
    }
}
