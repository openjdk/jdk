/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7069824 8042360
 * @summary Verify implementation for Locale matching.
 * @run main Bug7069824
 */

import java.util.*;
import java.util.Locale.*;
import static java.util.Locale.FilteringMode.*;
import static java.util.Locale.LanguageRange.*;

public class Bug7069824 {

    static boolean err = false;

    public static void main(String[] args) {
        testLanguageRange();
        testLocale();

        if (err) {
            throw new RuntimeException("Failed.");
        }
    }

    private static void testLanguageRange() {
        System.out.println("Test LanguageRange class...");
        testConstants();
        testConstructors();
        testMethods();
    }

    private static void testLocale() {
        System.out.println("Test Locale class...");
        test_filter();
        test_filterTags();
        test_lookup();
        test_lookupTag();
    }

    private static void testConstants() {
        boolean error = false;

        if (MIN_WEIGHT != 0.0) {
            error = true;
            System.err.println("    MIN_WEIGHT should be 0.0 but got "
                + MIN_WEIGHT);
        }

        if (MAX_WEIGHT != 1.0) {
            error = true;
            System.err.println("    MAX_WEIGHT should be 1.0 but got "
                + MAX_WEIGHT);
        }

        if (error) {
            err = true;
            System.err.println("  testConstants() failed.");
        } else {
            System.out.println("  testConstants() passed.");
        }
    }

    private static void testConstructors() {
        boolean error = false;

        LanguageRange lr;
        String range;
        double weight;

        // Testcase for 8042360
        range = "en-Latn-1234567890";
        try {
            lr = new LanguageRange(range);
            error = true;
            System.err.println("    IAE should be thrown for LanguageRange("
                + range + ").");
        }
        catch (IllegalArgumentException ex) {
        }

        range = null;
        try {
            lr = new LanguageRange(range);
            error = true;
            System.err.println("    NPE should be thrown for LanguageRange("
                + range + ").");
        }
        catch (NullPointerException ex) {
        }

        range = null;
        weight = 0.8;
        try {
            lr = new LanguageRange(range, weight);
            error = true;
            System.err.println("    NPE should be thrown for LanguageRange("
                + range + ", " + weight + ").");
        }
        catch (NullPointerException ex) {
        }

        range = "elvish";
        try {
            lr = new LanguageRange(range);
        }
        catch (Exception ex) {
            error = true;
            System.err.println("    " + ex
                + " should not be thrown for LanguageRange(" + range + ").");
        }

        range = "de-DE";
        try {
            lr = new LanguageRange(range);
        }
        catch (Exception ex) {
            error = true;
            System.err.println("    " + ex
                + " should not be thrown for LanguageRange(" + range + ").");
        }

        range = "ar";
        weight = 0.8;
        try {
            lr = new LanguageRange(range, weight);
        }
        catch (Exception ex) {
            error = true;
            System.err.println("    " + ex
                + " should not be thrown for LanguageRange(" + range + ", "
                + weight + ").");
        }

        range = "ja";
        weight = -0.8;
        try {
            lr = new LanguageRange(range, weight);
            error = true;
            System.err.println("    IAE should be thrown for LanguageRange("
                + range + ", " + weight + ").");
        }
        catch (IllegalArgumentException ex) {
        }

        range = "Elvish";
        weight = 3.0;
        try {
            lr = new LanguageRange(range, weight);
            error = true;
            System.err.println("    IAE should be thrown for LanguageRange("
                + range + ", " + weight + ").");
        }
        catch (IllegalArgumentException ex) {
        }

        String[] illformedRanges = {"-ja", "ja--JP", "en-US-", "a4r", "ar*",
            "ar-*EG", "", "abcdefghijklmn", "ja-J=", "ja-opqrstuvwxyz"};
        for (String r : illformedRanges) {
            try {
                lr = new LanguageRange(r);
                error = true;
                System.err.println("    IAE should be thrown for LanguageRange("
                    + r + ").");
            }
            catch (IllegalArgumentException ex) {
            }
        }


        if (error) {
            err = true;
            System.err.println("  testConstructors() failed.");
        } else {
            System.out.println("  testConstructors() passed.");
        }
    }

    private static void testMethods() {
        test_getRange();
        test_getWeight();
        test_equals();
        test_parse();
        test_mapEquivalents();
    }

    private static void test_getRange() {
        boolean error = false;

        String range = "ja";
        double weight = 0.5;
        LanguageRange lr = new LanguageRange(range, weight);
        if (!lr.getRange().equals(range)) {
             error = true;
             System.err.println("    LanguageRange.getRange() returned unexpected value. Expected: "
                 + range + ", got: " + lr.getRange());
        }

        range = "en-US";
        weight = 0.5;
        lr = new LanguageRange(range, weight);
        if (!lr.getRange().equals(range.toLowerCase())) {
             error = true;
             System.err.println("    LanguageRange.getRange() returned unexpected value. Expected: "
                 + range + ", got: " + lr.getRange());
        }

        if (error) {
            err = true;
            System.err.println("  test_getRange() failed.");
        } else {
            System.out.println("  test_getRange() passed.");
        }
    }

    private static void test_getWeight() {
        boolean error = false;

        String range = "ja";
        double weight = 0.5;
        LanguageRange lr = new LanguageRange(range, weight);
        if (lr.getWeight() != weight) {
             error = true;
             System.err.println("    LanguageRange.getWeight() returned unexpected value. Expected: "
                 + weight + ", got: " + lr.getWeight());
        }

        range = "ja";
        weight = MAX_WEIGHT; // default
        lr = new LanguageRange(range);
        if (!lr.getRange().equals(range) || lr.getWeight() != MAX_WEIGHT) {
             error = true;
             System.err.println("    LanguageRange.getWeight() returned unexpected value. Expected: "
                 + weight + ", got: " + lr.getWeight());
        }

        if (error) {
            err = true;
            System.err.println("  test_getWeight() failed.");
        } else {
            System.out.println("  test_getWeight() passed.");
        }
    }

    private static void test_equals() {
        boolean error = false;

        LanguageRange lr1 = new LanguageRange("ja", 1.0);
        LanguageRange lr2 = new LanguageRange("ja");
        LanguageRange lr3 = new LanguageRange("ja", 0.1);
        LanguageRange lr4 = new LanguageRange("en", 1.0);

        if (!lr1.equals(lr2)) {
            error = true;
            System.err.println("    LanguageRange(LR(ja, 1.0)).equals(LR(ja)) should return true.");
        }

        if (lr1.equals(lr3)) {
            error = true;
            System.err.println("    LanguageRange(LR(ja, 1.0)).equals(LR(ja, 0.1)) should return false.");
        }

        if (lr1.equals(lr4)) {
            error = true;
            System.err.println("    LanguageRange(LR(ja, 1.0)).equals(LR(en, 1.0)) should return false.");
        }

        if (lr1.equals(null)) {
            error = true;
            System.err.println("    LanguageRange(LR(ja, 1.0)).equals(null) should return false.");
        }

        if (lr1.equals("")) {
            error = true;
            System.err.println("    LanguageRange(LR(ja, 1.0)).equals(\"\") should return false.");

        }

        if (error) {
            err = true;
            System.err.println("  test_equals() failed.");
        } else {
            System.out.println("  test_equals() passed.");
        }
    }

    private static void test_parse() {
        boolean error = false;

        List<LanguageRange> list;
        String str = null;
        try {
            list = LanguageRange.parse(str);
            error = true;
            System.err.println("    NPE should be thrown for parse("
                + str + ").");
        }
        catch (NullPointerException ex) {
        }

        str = "";
        try {
            list = LanguageRange.parse("");
            error = true;
            System.err.println("    IAE should be thrown for parse("
                + str + ").");
        }
        catch (IllegalArgumentException ex) {
        }

        str = "ja;q=3";
        try {
            list = LanguageRange.parse(str);
            error = true;
            System.err.println("IAE should be thrown for parse("
                 + str + ").");
        }
        catch (IllegalArgumentException ex) {
        }

        str = "Accept-Language: fr-FX,de-DE;q=0.5, fr-tp-x-FOO;q=0.1,"
                  + "en-X-tp;q=0.6,en-FR;q=.7,de-de;q=0.8, iw;q=0.4, "
                  + "he;q=0.4, de-de;q=0.5,ja, in-tpp, in-tp;q=0.2";
        ArrayList<LanguageRange> expected = new ArrayList<>();
        expected.add(new LanguageRange("fr-fx", 1.0));
        expected.add(new LanguageRange("fr-fr", 1.0));
        expected.add(new LanguageRange("ja", 1.0));
        expected.add(new LanguageRange("in-tpp", 1.0));
        expected.add(new LanguageRange("id-tpp", 1.0));
        expected.add(new LanguageRange("en-fr", 0.7));
        expected.add(new LanguageRange("en-fx", 0.7));
        expected.add(new LanguageRange("en-x-tp", 0.6));
        expected.add(new LanguageRange("de-de", 0.5));
        expected.add(new LanguageRange("de-dd", 0.5));
        expected.add(new LanguageRange("iw", 0.4));
        expected.add(new LanguageRange("he", 0.4));
        expected.add(new LanguageRange("in-tp", 0.2));
        expected.add(new LanguageRange("id-tl", 0.2));
        expected.add(new LanguageRange("id-tp", 0.2));
        expected.add(new LanguageRange("in-tl", 0.2));
        expected.add(new LanguageRange("fr-tp-x-foo", 0.1));
        expected.add(new LanguageRange("fr-tl-x-foo", 0.1));
        List<LanguageRange> got = LanguageRange.parse(str);
        if (!areEqual(expected, got)) {
            error = true;
            System.err.println("    #1 parse() test failed.");
        }

        str = "Accept-Language: hak-CN;q=0.8, no-bok-NO;q=0.9, no-nyn, cmn-CN;q=0.1";
        expected = new ArrayList<>();
        expected.add(new LanguageRange("no-nyn", 1.0));
        expected.add(new LanguageRange("nn", 1.0));
        expected.add(new LanguageRange("no-bok-no", 0.9));
        expected.add(new LanguageRange("nb-no", 0.9));
        expected.add(new LanguageRange("hak-CN", 0.8));
        expected.add(new LanguageRange("zh-hakka-CN", 0.8));
        expected.add(new LanguageRange("i-hak-CN", 0.8));
        expected.add(new LanguageRange("cmn-CN", 0.1));
        expected.add(new LanguageRange("zh-cmn-CN", 0.1));
        expected.add(new LanguageRange("zh-guoyu-CN", 0.1));
        got = LanguageRange.parse(str);
        if (!areEqual(expected, got)) {
            error = true;
            System.err.println("    #2 parse() test failed.");
        }

        str = "Accept-Language: rki;q=0.4, no-bok-NO;q=0.9, ccq;q=0.5";
        expected = new ArrayList<>();
        expected.add(new LanguageRange("no-bok-no", 0.9));
        expected.add(new LanguageRange("nb-no", 0.9));
        expected.add(new LanguageRange("rki", 0.4));
        expected.add(new LanguageRange("ybd", 0.4));
        expected.add(new LanguageRange("ccq", 0.4));
        got = LanguageRange.parse(str);
        if (!areEqual(expected, got)) {
            error = true;
            System.err.println("    #3 parse() test failed.");
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

    private static void test_mapEquivalents() {
        boolean error = false;

        String ranges = "zh, zh-TW;q=0.8, ar;q=0.9, EN, zh-HK, ja-JP;q=0.2, es;q=0.4";
        List<LanguageRange> priorityList = LanguageRange.parse(ranges);
        HashMap<String, List<String>> map = null;

        try {
            List<LanguageRange> list =
                LanguageRange.mapEquivalents(priorityList, null);
        }
        catch (Exception ex) {
             error = true;
             System.err.println(ex
                 + " should not be thrown for mapEquivalents(priorityList, null).");
        }

        map = new HashMap<>();
        try {
            List<LanguageRange> list =
                LanguageRange.mapEquivalents(priorityList, map);
        }
        catch (Exception ex) {
             error = true;
             System.err.println(ex
                 + " should not be thrown for mapEquivalents(priorityList, empty map).");
        }

        ArrayList<String> equivalentList = new ArrayList<>();
        equivalentList.add("ja");
        equivalentList.add("ja-Hira");
        map.put("ja", equivalentList);
        try {
            List<LanguageRange> list = LanguageRange.mapEquivalents(null, map);
             error = true;
             System.err.println("NPE should be thrown for mapEquivalents(null, map).");
        }
        catch (NullPointerException ex) {
        }

        map = new LinkedHashMap<>();
        ArrayList<String> equivalentList1 = new ArrayList<>();
        equivalentList1.add("ja");
        equivalentList1.add("ja-Hira");
        map.put("ja", equivalentList1);
        ArrayList<String> equivalentList2 = new ArrayList<>();
        equivalentList2.add("zh-Hans");
        equivalentList2.add("zh-Hans-CN");
        equivalentList2.add("zh-CN");
        map.put("zh", equivalentList2);
        ArrayList<String> equivalentList3 = new ArrayList<>();
        equivalentList3.add("zh-TW");
        equivalentList3.add("zh-Hant");
        map.put("zh-TW", equivalentList3);
        map.put("es", null);
        ArrayList<String> equivalentList4 = new ArrayList<>();
        map.put("en", equivalentList4);
        ArrayList<String> equivalentList5 = new ArrayList<>();
        equivalentList5.add("de");
        map.put("zh-HK", equivalentList5);

        ArrayList<LanguageRange> expected = new ArrayList<>();
        expected.add(new LanguageRange("zh-hans", 1.0));
        expected.add(new LanguageRange("zh-hans-cn", 1.0));
        expected.add(new LanguageRange("zh-cn", 1.0));
        expected.add(new LanguageRange("de", 1.0));
        expected.add(new LanguageRange("ar", 0.9));
        expected.add(new LanguageRange("zh-tw", 0.8));
        expected.add(new LanguageRange("zh-hant", 0.8));
        expected.add(new LanguageRange("ja-jp", 0.2));
        expected.add(new LanguageRange("ja-hira-jp", 0.2));
        List<LanguageRange> got =
            LanguageRange.mapEquivalents(priorityList, map);

        if (!areEqual(expected, got)) {
            error = true;
        }

        if (error) {
            err = true;
            System.err.println("  test_mapEquivalents() failed.");
        } else {
            System.out.println("  test_mapEquivalents() passed.");
        }
    }

    private static void test_filter() {
        boolean error = false;

        String ranges = "ja-JP, fr-FR";
        String tags = "de-DE, en, ja-JP-hepburn, fr, he, ja-Latn-JP";
        FilteringMode mode = EXTENDED_FILTERING;

        List<LanguageRange> priorityList = LanguageRange.parse(ranges);
        List<Locale> tagList = generateLocales(tags);
        String actualLocales =
            showLocales(Locale.filter(priorityList, tagList, mode));
        String expectedLocales = "ja-JP-hepburn, ja-Latn-JP";

        if (!expectedLocales.equals(actualLocales)) {
            error = true;
            showErrorMessage("#1 filter(" + mode + ")",
                             ranges, tags, expectedLocales, actualLocales);
        }


        ranges = "ja-*-JP, fr-FR";
        tags = "de-DE, en, ja-JP-hepburn, fr, he, ja-Latn-JP";
        mode = EXTENDED_FILTERING;

        priorityList = LanguageRange.parse(ranges);
        tagList = generateLocales(tags);
        actualLocales = showLocales(Locale.filter(priorityList, tagList, mode));
        expectedLocales = "ja-JP-hepburn, ja-Latn-JP";

        if (!expectedLocales.equals(actualLocales)) {
            error = true;
            showErrorMessage("#2 filter(" + mode + ")",
                             ranges, tags, expectedLocales, actualLocales);
        }


        ranges = "ja-*-JP, fr-FR, de-de;q=0.2";
        tags = "de-DE, en, ja-JP-hepburn, de-de, fr, he, ja-Latn-JP";
        mode = AUTOSELECT_FILTERING;

        priorityList = LanguageRange.parse(ranges);
        tagList = generateLocales(tags);
        actualLocales = showLocales(Locale.filter(priorityList, tagList, mode));
        expectedLocales = "ja-JP-hepburn, ja-Latn-JP, de-DE";

        if (!expectedLocales.equals(actualLocales)) {
            error = true;
            showErrorMessage("#3 filter(" + mode + ")",
                             ranges, tags,expectedLocales, actualLocales);
        }

        ranges = "ja-JP, fr-FR, de-de;q=0.2";
        tags = "de-DE, en, ja-JP-hepburn, de-de, fr, he, ja-Latn-JP";
        mode = AUTOSELECT_FILTERING;

        priorityList = LanguageRange.parse(ranges);
        tagList = generateLocales(tags);
        actualLocales = showLocales(Locale.filter(priorityList, tagList, mode));
        expectedLocales = "ja-JP-hepburn, de-DE";

        if (!expectedLocales.equals(actualLocales)) {
            error = true;
            showErrorMessage("#4 filter(" + mode + ")",
                             ranges, tags, expectedLocales, actualLocales);
        }


        ranges = "en;q=0.2, ja-*-JP, fr-JP";
        tags = "de-DE, en, ja-JP-hepburn, fr, he, ja-Latn-JP";
        mode = IGNORE_EXTENDED_RANGES;

        priorityList = LanguageRange.parse(ranges);
        tagList = generateLocales(tags);
        actualLocales = showLocales(Locale.filter(priorityList, tagList, mode));
        expectedLocales = "en";

        if (!expectedLocales.equals(actualLocales)) {
            error = true;
            showErrorMessage("#5 filter(" + mode + ")",
                             ranges, tags, expectedLocales, actualLocales);
        }


        ranges = "en;q=0.2, ja-*-JP, fr-JP";
        tags = "de-DE, en, ja-JP-hepburn, fr, he, ja-Latn-JP";
        mode = MAP_EXTENDED_RANGES;

        priorityList = LanguageRange.parse(ranges);
        tagList = generateLocales(tags);
        actualLocales = showLocales(Locale.filter(priorityList, tagList, mode));
        expectedLocales = "ja-JP-hepburn, en";

        if (!expectedLocales.equals(actualLocales)) {
            error = true;
            showErrorMessage("#6 filter(" + mode + ")",
                             ranges, tags, expectedLocales, actualLocales);
        }


        ranges = "en;q=0.2, ja-JP, fr-JP";
        tags = "de-DE, en, ja-JP-hepburn, fr, he, ja-Latn-JP";
        mode = REJECT_EXTENDED_RANGES;

        priorityList = LanguageRange.parse(ranges);
        tagList = generateLocales(tags);
        actualLocales = showLocales(Locale.filter(priorityList, tagList, mode));
        expectedLocales = "ja-JP-hepburn, en";

        if (!expectedLocales.equals(actualLocales)) {
            error = true;
            showErrorMessage("#7 filter(" + mode + ")",
                             ranges, tags, expectedLocales, actualLocales);
        }


        ranges = "en;q=0.2, ja-*-JP, fr-JP";
        tags = "de-DE, en, ja-JP-hepburn, fr, he, ja-Latn-JP";
        mode = REJECT_EXTENDED_RANGES;

        priorityList = LanguageRange.parse(ranges);
        tagList = generateLocales(tags);
        try {
            actualLocales =
                showLocales(Locale.filter(priorityList, tagList, mode));
            error = true;
            System.out.println("IAE should be thrown for filter("
                + mode + ").");
        }
        catch (IllegalArgumentException ex) {
        }


        ranges = "en;q=0.2, ja-*-JP, fr-JP";
        tags = null;
        mode = REJECT_EXTENDED_RANGES;

        priorityList = LanguageRange.parse(ranges);
        tagList = generateLocales(tags);
        try {
            actualLocales =
                showLocales(Locale.filter(priorityList, tagList, mode));
            error = true;
            System.out.println("NPE should be thrown for filter(tags=null).");
        }
        catch (NullPointerException ex) {
        }


        ranges = null;
        tags = "de-DE, en, ja-JP-hepburn, fr, he, ja-Latn-JP";
        mode = REJECT_EXTENDED_RANGES;

        try {
            priorityList = LanguageRange.parse(ranges);
            tagList = generateLocales(tags);
            actualLocales =
                showLocales(Locale.filter(priorityList, tagList, mode));
            error = true;
            System.out.println("NPE should be thrown for filter(ranges=null).");
        }
        catch (NullPointerException ex) {
        }


        ranges = "en;q=0.2, ja-*-JP, fr-JP";
        tags = "";
        mode = REJECT_EXTENDED_RANGES;

        priorityList = LanguageRange.parse(ranges);
        tagList = generateLocales(tags);
        try {
            actualLocales =
                showLocales(Locale.filter(priorityList, tagList, mode));
        }
        catch (Exception ex) {
            error = true;
            System.out.println(ex
                + " should not be thrown for filter(" + ranges + ", \"\").");
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

        String ranges = "en;q=0.2, *;q=0.6, ja";
        String tags = "de-DE, en, ja-JP-hepburn, fr-JP, he";

        List<LanguageRange> priorityList = LanguageRange.parse(ranges);
        List<String> tagList = generateLanguageTags(tags);
        String actualTags =
            showLanguageTags(Locale.filterTags(priorityList, tagList));
        String expectedTags = tags;

        if (!expectedTags.equals(actualTags)) {
            error = true;
            showErrorMessage("#1 filterTags()",
                             ranges, tags, expectedTags, actualTags);
        }


        ranges = "en;q=0.2, ja-JP, fr-JP";
        tags = "de-DE, en, ja-JP-hepburn, fr, he";
        priorityList = LanguageRange.parse(ranges);
        tagList = generateLanguageTags(tags);
        actualTags = showLanguageTags(Locale.filterTags(priorityList, tagList));
        expectedTags = "ja-jp-hepburn, en";

        if (!expectedTags.equals(actualTags)) {
            error = true;
            showErrorMessage("#2 filterTags()",
                             ranges, tags, expectedTags, actualTags);
        }


        ranges = "de-DE";
        tags = "de-DE, de-de, de-Latn-DE, de-Latf-DE, de-DE-x-goethe, "
               + "de-Latn-DE-1996, de-Deva-DE, de, de-x-DE, de-Deva";
        FilteringMode mode = MAP_EXTENDED_RANGES;
        priorityList = LanguageRange.parse(ranges);
        tagList = generateLanguageTags(tags);
        actualTags = showLanguageTags(Locale.filterTags(priorityList, tagList, mode));
        expectedTags = "de-de, de-de-x-goethe";

        if (!expectedTags.equals(actualTags)) {
            error = true;
            showErrorMessage("#3 filterTags(" + mode + ")",
                             ranges, tags, expectedTags, actualTags);
        }


        ranges = "de-DE";
        tags = "de-DE, de-de, de-Latn-DE, de-Latf-DE, de-DE-x-goethe, "
               + "de-Latn-DE-1996, de-Deva-DE, de, de-x-DE, de-Deva";
        mode = EXTENDED_FILTERING;
        priorityList = LanguageRange.parse(ranges);
        tagList = generateLanguageTags(tags);
        actualTags = showLanguageTags(Locale.filterTags(priorityList, tagList, mode));
        expectedTags = "de-de, de-latn-de, de-latf-de, de-de-x-goethe, "
                       + "de-latn-de-1996, de-deva-de";

        if (!expectedTags.equals(actualTags)) {
            error = true;
            showErrorMessage("#4 filterTags(" + mode + ")",
                             ranges, tags, expectedTags, actualTags);
        }


        ranges = "de-*-DE";
        tags = "de-DE, de-de, de-Latn-DE, de-Latf-DE, de-DE-x-goethe, "
               + "de-Latn-DE-1996, de-Deva-DE, de, de-x-DE, de-Deva";
        mode = EXTENDED_FILTERING;
        priorityList = LanguageRange.parse(ranges);
        tagList = generateLanguageTags(tags);
        actualTags = showLanguageTags(Locale.filterTags(priorityList, tagList, mode));
        expectedTags = "de-de, de-latn-de, de-latf-de, de-de-x-goethe, "
                       + "de-latn-de-1996, de-deva-de";

        if (!expectedTags.equals(actualTags)) {
            error = true;
            showErrorMessage("#5 filterTags(" + mode + ")",
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

        String ranges = "en;q=0.2, *-JP;q=0.6, iw";
        String tags = "de-DE, en, ja-JP-hepburn, fr-JP, he";
        List<LanguageRange> priorityList = LanguageRange.parse(ranges);
        List<Locale> localeList = generateLocales(tags);
        String actualLocale =
            Locale.lookup(priorityList, localeList).toLanguageTag();
        String expectedLocale ="he";

        if (!expectedLocale.equals(actualLocale)) {
            error = true;
            showErrorMessage("#1 lookup()", ranges, tags, expectedLocale, actualLocale);
        }


        ranges = "en;q=0.2, *-JP;q=0.6, iw";
        tags = "de-DE, he-IL, en, iw";
        priorityList = LanguageRange.parse(ranges);
        localeList = generateLocales(tags);
        actualLocale = Locale.lookup(priorityList, localeList).toLanguageTag();
        expectedLocale = "he";

        if (!expectedLocale.equals(actualLocale)) {
            error = true;
            showErrorMessage("#2 lookup()", ranges, tags, expectedLocale, actualLocale);
        }


        ranges = "en;q=0.2, ja-*-JP-x-foo;q=0.6, iw";
        tags = "de-DE, fr, en, ja-Latn-JP";
        priorityList = LanguageRange.parse(ranges);
        localeList = generateLocales(tags);
        actualLocale = Locale.lookup(priorityList, localeList).toLanguageTag();
        expectedLocale = "ja-Latn-JP";

        if (!expectedLocale.equals(actualLocale)) {
            error = true;
            showErrorMessage("#3 lookup()", ranges, tags, expectedLocale, actualLocale);
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

        String ranges = "en, *";
        String tags = "es, de, ja-JP";
        List<LanguageRange> priorityList = LanguageRange.parse(ranges);
        List<String> tagList = generateLanguageTags(tags);
        String actualTag = Locale.lookupTag(priorityList, tagList);
        String expectedTag = null;

        if (actualTag != null) {
            error = true;
            showErrorMessage("#1 lookupTag()", ranges, tags, expectedTag, actualTag);
        }


        ranges= "en;q=0.2, *-JP";
        tags = "de-DE, en, ja-JP-hepburn, fr-JP, en-JP";
        priorityList = LanguageRange.parse(ranges);
        tagList = generateLanguageTags(tags);
        actualTag = Locale.lookupTag(priorityList, tagList);
        expectedTag = "fr-jp";

        if (!expectedTag.equals(actualTag)) {
            error = true;
            showErrorMessage("#2 lookupTag()", ranges, tags, expectedTag, actualTag);
        }


        ranges = "en;q=0.2, ar-MO, iw";
        tags = "de-DE, he, fr-JP";
        priorityList = LanguageRange.parse(ranges);
        tagList = generateLanguageTags(tags);
        actualTag = Locale.lookupTag(priorityList, tagList);
        expectedTag = "he";

        if (!expectedTag.equals(actualTag)) {
            error = true;
            showErrorMessage("#3 lookupTag()", ranges, tags, expectedTag, actualTag);
        }


        ranges = "en;q=0.2, ar-MO, he";
        tags = "de-DE, iw, fr-JP";
        priorityList = LanguageRange.parse(ranges);
        tagList = generateLanguageTags(tags);
        actualTag = Locale.lookupTag(priorityList, tagList);
        expectedTag = "iw";

        if (!expectedTag.equals(actualTag)) {
            error = true;
            showErrorMessage("#4 lookupTag()", ranges, tags, expectedTag, actualTag);
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

    private static String showPriorityList(List<LanguageRange> priorityList) {
        StringBuilder sb = new StringBuilder();

        Iterator<LanguageRange> itr = priorityList.iterator();
        LanguageRange lr;
        if (itr.hasNext()) {
            lr = itr.next();
            sb.append(lr.getRange());
            sb.append(";q=");
            sb.append(lr.getWeight());
        }
        while (itr.hasNext()) {
            sb.append(", ");
            lr = itr.next();
            sb.append(lr.getRange());
            sb.append(";q=");
            sb.append(lr.getWeight());
        }

        return sb.toString();
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

        Iterator<Locale> itr = locales.iterator();
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
