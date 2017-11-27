/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8040211 8191404
 * @summary Checks the IANA language subtag registry data updation
 *          (LSR Revision: 2017-08-15) with Locale and Locale.LanguageRange
 *          class methods.
 * @run main Bug8040211
 */

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Locale;
import java.util.List;
import java.util.Locale.LanguageRange;
import java.util.Locale.FilteringMode;
import static java.util.Locale.FilteringMode.EXTENDED_FILTERING;

public class Bug8040211 {

    static boolean err = false;

    public static void main(String[] args) {
        testLanguageRange();
        testLocale();

        if (err) {
            throw new RuntimeException("Failed.");
        }
    }

    private static void testLanguageRange() {
        System.out.println("Test LanguageRange class parse method...");
        test_parse();
    }

    private static void testLocale() {
        System.out.println("Test Locale class methods...");
        test_filter();
        test_filterTags();
        test_lookup();
        test_lookupTag();
    }

    private static void test_parse() {
        boolean error = false;
        String str = "Accept-Language: aam, adp, aue, bcg, cqu, ema,"
                + " en-gb-oed, gti, koj, kwq, kxe, lii, lmm, mtm, ngv,"
                + " oyb, phr, pub, suj, taj;q=0.9, yug;q=0.5, gfx;q=0.4";
        ArrayList<LanguageRange> expected = new ArrayList<>();
        expected.add(new LanguageRange("aam", 1.0));
        expected.add(new LanguageRange("aas", 1.0));
        expected.add(new LanguageRange("adp", 1.0));
        expected.add(new LanguageRange("dz", 1.0));
        expected.add(new LanguageRange("aue", 1.0));
        expected.add(new LanguageRange("ktz", 1.0));
        expected.add(new LanguageRange("bcg", 1.0));
        expected.add(new LanguageRange("bgm", 1.0));
        expected.add(new LanguageRange("cqu", 1.0));
        expected.add(new LanguageRange("quh", 1.0));
        expected.add(new LanguageRange("ema", 1.0));
        expected.add(new LanguageRange("uok", 1.0));
        expected.add(new LanguageRange("en-gb-oed", 1.0));
        expected.add(new LanguageRange("en-gb-oxendict", 1.0));
        expected.add(new LanguageRange("gti", 1.0));
        expected.add(new LanguageRange("nyc", 1.0));
        expected.add(new LanguageRange("koj", 1.0));
        expected.add(new LanguageRange("kwv", 1.0));
        expected.add(new LanguageRange("kwq", 1.0));
        expected.add(new LanguageRange("yam", 1.0));
        expected.add(new LanguageRange("kxe", 1.0));
        expected.add(new LanguageRange("tvd", 1.0));
        expected.add(new LanguageRange("lii", 1.0));
        expected.add(new LanguageRange("raq", 1.0));
        expected.add(new LanguageRange("lmm", 1.0));
        expected.add(new LanguageRange("rmx", 1.0));
        expected.add(new LanguageRange("mtm", 1.0));
        expected.add(new LanguageRange("ymt", 1.0));
        expected.add(new LanguageRange("ngv", 1.0));
        expected.add(new LanguageRange("nnx", 1.0));
        expected.add(new LanguageRange("oyb", 1.0));
        expected.add(new LanguageRange("thx", 1.0));
        expected.add(new LanguageRange("skk", 1.0));
        expected.add(new LanguageRange("jeg", 1.0));
        expected.add(new LanguageRange("phr", 1.0));
        expected.add(new LanguageRange("pmu", 1.0));
        expected.add(new LanguageRange("pub", 1.0));
        expected.add(new LanguageRange("puz", 1.0));
        expected.add(new LanguageRange("suj", 1.0));
        expected.add(new LanguageRange("xsj", 1.0));
        expected.add(new LanguageRange("taj", 0.9));
        expected.add(new LanguageRange("tsf", 0.9));
        expected.add(new LanguageRange("yug", 0.5));
        expected.add(new LanguageRange("yuu", 0.5));
        expected.add(new LanguageRange("gfx", 0.4));
        expected.add(new LanguageRange("oun", 0.4));
        expected.add(new LanguageRange("mwj", 0.4));
        expected.add(new LanguageRange("vaj", 0.4));
        List<LanguageRange> got = LanguageRange.parse(str);
        if (!areEqual(expected, got)) {
            error = true;
            System.err.println("    language parse() test failed.");
        }

        if (error) {
            err = true;
            System.err.println("  test_parse() failed.");
        } else {
            System.out.println("  test_parse() passed.");
        }

    }

    private static boolean areEqual(List<LanguageRange> expected,
            List<LanguageRange> got) {
        boolean error = false;

        int expectedSize = expected.size();
        int actualSize = got.size();

        if (expectedSize != actualSize) {
            error = true;

            System.err.println("  Expected size=" + expectedSize);
            for (LanguageRange lr : expected) {
                System.err.println("    range=" + lr.getRange()
                        + ", weight=" + lr.getWeight());
            }

            System.out.println("  Actual size=" + actualSize);
            for (LanguageRange lr : got) {
                System.err.println("    range=" + lr.getRange()
                        + ", weight=" + lr.getWeight());
            }
        } else {
            for (int i = 0; i < expectedSize; i++) {
                LanguageRange lr1 = expected.get(i);
                LanguageRange lr2 = got.get(i);

                if (!lr1.getRange().equals(lr2.getRange())
                        || lr1.getWeight() != lr2.getWeight()) {
                    error = true;
                    System.err.println("  " + i + ": Expected: range=" + lr1.getRange()
                            + ", weight=" + lr1.getWeight());
                    System.err.println("  " + i + ": Actual:   range=" + lr2.getRange()
                            + ", weight=" + lr2.getWeight());
                }
            }
        }

        return !error;
    }

    private static void test_filter() {
        boolean error = false;

        String ranges = "mtm-RU, en-gb-oed, coy";
        String tags = "de-DE, en, mtm-RU, ymt-RU, en-gb-oxendict, ja-JP, pij, nts";
        FilteringMode mode = EXTENDED_FILTERING;

        List<LanguageRange> priorityList = LanguageRange.parse(ranges);
        List<Locale> tagList = generateLocales(tags);
        String actualLocales
                = showLocales(Locale.filter(priorityList, tagList, mode));
        String expectedLocales = "mtm-RU, ymt-RU, en-GB-oxendict, nts, pij";

        if (!expectedLocales.equals(actualLocales)) {
            error = true;
            showErrorMessage("#1 filter(" + mode + ")",
                    ranges, tags, expectedLocales, actualLocales);
        }

        ranges = "phr-*-IN, ja-JP";
        tags = "en, pmu-Guru-IN, ja-Latn-JP, iw";
        mode = EXTENDED_FILTERING;

        priorityList = LanguageRange.parse(ranges);
        tagList = generateLocales(tags);
        actualLocales = showLocales(Locale.filter(priorityList, tagList, mode));
        expectedLocales = "pmu-Guru-IN, ja-Latn-JP";

        if (!expectedLocales.equals(actualLocales)) {
            error = true;
            showErrorMessage("#2 filter(" + mode + ")",
                    ranges, tags, expectedLocales, actualLocales);
        }

        if (error) {
            err = true;
            System.out.println("  test_filter() failed.");
        } else {
            System.out.println("  test_filter() passed.");
        }
    }

    private static void test_filterTags() {
        boolean error = false;

        String ranges = "gti;q=0.2, gfx, kzj";
        String tags = "de-DE, gti, he, nyc, mwj, vaj, ktr, dtp";

        List<LanguageRange> priorityList = LanguageRange.parse(ranges);
        List<String> tagList = generateLanguageTags(tags);
        String actualTags
                = showLanguageTags(Locale.filterTags(priorityList, tagList));
        String expectedTags = "mwj, vaj, ktr, dtp, gti, nyc";

        if (!expectedTags.equals(actualTags)) {
            error = true;
            showErrorMessage("filterTags()",
                    ranges, tags, expectedTags, actualTags);
        }

        if (error) {
            err = true;
            System.out.println("  test_filterTags() failed.");
        } else {
            System.out.println("  test_filterTags() passed.");
        }
    }

    private static void test_lookup() {
        boolean error = false;

        String ranges = "en;q=0.2, yam, rmx;q=0.9";
        String tags = "de-DE, en, kwq, lmm";
        List<LanguageRange> priorityList = LanguageRange.parse(ranges);
        List<Locale> localeList = generateLocales(tags);
        String actualLocale
                = Locale.lookup(priorityList, localeList).toLanguageTag();
        String expectedLocale = "kwq";

        if (!expectedLocale.equals(actualLocale)) {
            error = true;
            showErrorMessage("lookup()", ranges, tags, expectedLocale, actualLocale);
        }

        if (error) {
            err = true;
            System.out.println("  test_lookup() failed.");
        } else {
            System.out.println("  test_lookup() passed.");
        }
    }

    private static void test_lookupTag() {
        boolean error = false;

        String ranges = "en, tsf;q=0.2";
        String tags = "es, ja-JP, taj";
        List<LanguageRange> priorityList = LanguageRange.parse(ranges);
        List<String> tagList = generateLanguageTags(tags);
        String actualTag = Locale.lookupTag(priorityList, tagList);
        String expectedTag = "taj";

        if (!expectedTag.equals(actualTag)) {
            error = true;
            showErrorMessage("lookupTag()", ranges, tags, expectedTag, actualTag);
        }

        if (error) {
            err = true;
            System.out.println("  test_lookupTag() failed.");
        } else {
            System.out.println("  test_lookupTag() passed.");
        }
    }

    private static List<Locale> generateLocales(String tags) {
        if (tags == null) {
            return null;
        }

        List<Locale> localeList = new ArrayList<>();
        if (tags.equals("")) {
            return localeList;
        }
        String[] t = tags.split(", ");
        for (String tag : t) {
            localeList.add(Locale.forLanguageTag(tag));
        }
        return localeList;
    }

    private static List<String> generateLanguageTags(String tags) {
        List<String> tagList = new ArrayList<>();
        String[] t = tags.split(", ");
        for (String tag : t) {
            tagList.add(tag);
        }
        return tagList;
    }

    private static String showLanguageTags(List<String> tags) {
        StringBuilder sb = new StringBuilder();

        Iterator<String> itr = tags.iterator();
        if (itr.hasNext()) {
            sb.append(itr.next());
        }
        while (itr.hasNext()) {
            sb.append(", ");
            sb.append(itr.next());
        }

        return sb.toString().trim();
    }

    private static String showLocales(List<Locale> locales) {
        StringBuilder sb = new StringBuilder();

        java.util.Iterator<Locale> itr = locales.iterator();
        if (itr.hasNext()) {
            sb.append(itr.next().toLanguageTag());
        }
        while (itr.hasNext()) {
            sb.append(", ");
            sb.append(itr.next().toLanguageTag());
        }

        return sb.toString().trim();
    }

    private static void showErrorMessage(String methodName,
            String priorityList,
            String tags,
            String expectedTags,
            String actualTags) {
        System.out.println("\nIncorrect " + methodName + " result.");
        System.out.println("  Priority list  :  " + priorityList);
        System.out.println("  Language tags  :  " + tags);
        System.out.println("  Expected value : " + expectedTags);
        System.out.println("  Actual value   : " + actualTags);
    }

}

