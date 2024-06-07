/*
 * Copyright (c) 2007, 2024, Oracle and/or its affiliates. All rights reserved.
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
/**
 * @test
 * @bug 4052404 4052440 4084688 4092475 4101316 4105828 4107014 4107953 4110613
 * 4118587 4118595 4122371 4126371 4126880 4135316 4135752 4139504 4139940 4143951
 * 4147315 4147317 4147552 4335196 4778440 4940539 5010672 6475525 6544471 6627549
 * 6786276 7066203 7085757 8008577 8030696 8170840 8174269 8255086 8263202 8287868
 * @summary test Locales
 * @modules jdk.localedata
 * @run junit LocaleTest
 * @run junit/othervm -Djava.locale.useOldISOCodes=true LocaleTest
 */
/*
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, the following notice accompanied the original version of this
 * file and, per its terms, should not be removed:
 *
 * (C) Copyright Taligent, Inc. 1996, 1997 - All Rights Reserved
 * (C) Copyright IBM Corp. 1996 - 1998 - All Rights Reserved
 *
 * Portions copyright (c) 2007 Sun Microsystems, Inc.
 * All Rights Reserved.
 *
 * The original version of this source code and documentation
 * is copyrighted and owned by Taligent, Inc., a wholly-owned
 * subsidiary of IBM. These materials are provided under terms
 * of a License Agreement between Taligent and Sun. This technology
 * is protected by multiple US and International patents.
 *
 * This notice and attribution to Taligent may not be removed.
 * Taligent is a registered trademark of Taligent, Inc.
 *
 * Permission to use, copy, modify, and distribute this software
 * and its documentation for NON-COMMERCIAL purposes and without
 * fee is hereby granted provided that this copyright notice
 * appears in all copies. Please refer to the file "copyright.html"
 * for further important copyright and licensing information.
 *
 * SUN MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF
 * THE SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
 * TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE, OR NON-INFRINGEMENT. SUN SHALL NOT BE LIABLE FOR
 * ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING OR
 * DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES.
 *
 */

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OptionalDataException;
import java.io.StreamCorruptedException;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

public class LocaleTest {
    public LocaleTest() {
    }

    private int ENGLISH = 0;
    private int FRENCH = 1;
    private int CROATIAN = 2;
    private int GREEK = 3;
    private int NORWEGIAN = 4;
    private int ITALIAN = 5;
    private int DUMMY = 6;
    private int MAX_LOCALES = 6;

    private int LANG = 0;
    private int CTRY = 1;
    private int VAR = 2;
    private int NAME = 3;
    private int LANG3 = 4;
    private int CTRY3 = 5;
    private int LCID = 6;
    private int DLANG_EN = 7;
    private int DCTRY_EN = 8;
    private int DVAR_EN = 9;
    private int DNAME_EN = 10;
    private int DLANG_FR = 11;
    private int DCTRY_FR = 12;
    private int DVAR_FR = 13;
    private int DNAME_FR = 14;
    private int DLANG_HR = 15;
    private int DCTRY_HR = 16;
    private int DVAR_HR = 17;
    private int DNAME_HR = 18;
    private int DLANG_EL = 19;
    private int DCTRY_EL = 20;
    private int DVAR_EL = 21;
    private int DNAME_EL = 22;
    private int DLANG_ROOT = 23;
    private int DCTRY_ROOT = 24;
    private int DVAR_ROOT = 25;
    private int DNAME_ROOT = 26;

    private String[][] dataTable = {
        // language code
        {   "en",   "fr",   "hr",   "el",   "no",   "it",   "xx"    },
        // country code
        {   "US",   "FR",   "HR",   "GR",   "NO",   "",   "YY"    },
        // variant code
        {   "",     "",     "",     "",     "NY",   "",   ""    },
        // full name
        {   "en_US",    "fr_FR",    "hr_HR",    "el_GR",    "no_NO_NY", "it",   "xx_YY"  },
        // ISO-3 language
        {   "eng",  "fra",  "hrv",  "ell",  "nor",  "ita",  ""   },
        // ISO-3 country
        {   "USA",  "FRA",  "HRV",  "GRC",  "NOR",  "",     ""   },
        // LCID (not currently public)
        {   "0409", "040c", "041a", "0408", "0814", "",     ""  },

        // display language (English)
        {   "English",  "French",   "Croatian", "Greek",    "Norwegian",    "Italian",  "xx" },
        // display country (English)
        {   "United States",    "France",   "Croatia",  "Greece",   "Norway",   "",     "YY" },
        // display variant (English)
        {   "",     "",     "",     "",     "Nynorsk",   "",     ""},
        // display name (English)
        // Updated no_NO_NY English display name for new pattern-based algorithm
        // (part of Euro support).
        {   "English (United States)", "French (France)", "Croatian (Croatia)", "Greek (Greece)", "Norwegian (Norway, Nynorsk)", "Italian", "xx (YY)" },

        // display language (French)
        {   "anglais",  "fran\u00e7ais",   "croate", "grec",    "norv\u00e9gien",    "italien", "xx" },
        // display country (French)
        {   "\u00c9tats-Unis",    "France",   "Croatie",  "Gr\u00e8ce",   "Norv\u00e8ge", "",     "YY" },
        // display variant (French)
        {   "",     "",     "",     "",     "",     "",    "" },
        // display name (French)
        {   "anglais (\u00c9tats-Unis)", "fran\u00e7ais (France)", "croate (Croatie)", "grec (Gr\u00e8ce)", "norv\u00e9gien (Norv\u00e8ge, Nynorsk)", "italien", "xx (YY)" },

        // display language (Croatian)
        {   "engleski",  "francuski", "hrvatski", "gr\u010dki",    "norve\u0161ki", "talijanski", "xx" },
        // display country (Croatian)
        {   "Sjedinjene Ameri\u010dke Dr\u017eave",    "Francuska",   "Hrvatska",  "Gr\u010dka",   "Norve\u0161ka", "", "YY" },
        // display variant (Croatian)
        {   "",     "",     "",     "",     "", "", ""},
        // display name (Croatian)
        {   "engleski (Sjedinjene Ameri\u010dke Dr\u017eave)", "francuski (Francuska)", "hrvatski (Hrvatska)", "gr\u010dki (Gr\u010dka)", "norve\u0161ki (Norve\u0161ka, Nynorsk)", "talijanski", "xx (YY)" },

        // display language (Greek)
        {   "\u0391\u03b3\u03b3\u03bb\u03b9\u03ba\u03ac",  "\u0393\u03b1\u03bb\u03bb\u03b9\u03ba\u03ac", "\u039a\u03c1\u03bf\u03b1\u03c4\u03b9\u03ba\u03ac", "\u0395\u03bb\u03bb\u03b7\u03bd\u03b9\u03ba\u03ac",    "\u039d\u03bf\u03c1\u03b2\u03b7\u03b3\u03b9\u03ba\u03ac", "\u0399\u03c4\u03b1\u03bb\u03b9\u03ba\u03ac", "xx" },
        // display country (Greek)
        {   "\u0397\u03bd\u03c9\u03bc\u03ad\u03bd\u03b5\u03c2 \u03a0\u03bf\u03bb\u03b9\u03c4\u03b5\u03af\u03b5\u03c2",    "\u0393\u03b1\u03bb\u03bb\u03af\u03b1",   "\u039a\u03c1\u03bf\u03b1\u03c4\u03af\u03b1",  "\u0395\u03bb\u03bb\u03ac\u03b4\u03b1",   "\u039d\u03bf\u03c1\u03b2\u03b7\u03b3\u03af\u03b1", "", "YY" },
        // display variant (Greek)
        {   "",     "",     "",     "",     "", "", "" },
        // display name (Greek)
        {   "\u0391\u03b3\u03b3\u03bb\u03b9\u03ba\u03ac (\u0397\u03bd\u03c9\u03bc\u03ad\u03bd\u03b5\u03c2 \u03a0\u03bf\u03bb\u03b9\u03c4\u03b5\u03af\u03b5\u03c2)", "\u0393\u03b1\u03bb\u03bb\u03b9\u03ba\u03ac (\u0393\u03b1\u03bb\u03bb\u03af\u03b1)", "\u039a\u03c1\u03bf\u03b1\u03c4\u03b9\u03ba\u03ac (\u039a\u03c1\u03bf\u03b1\u03c4\u03af\u03b1)", "\u0395\u03bb\u03bb\u03b7\u03bd\u03b9\u03ba\u03ac (\u0395\u03bb\u03bb\u03ac\u03b4\u03b1)", "\u039d\u03bf\u03c1\u03b2\u03b7\u03b3\u03b9\u03ba\u03ac (\u039d\u03bf\u03c1\u03b2\u03b7\u03b3\u03af\u03b1, Nynorsk)", "\u0399\u03c4\u03b1\u03bb\u03b9\u03ba\u03ac", "xx (YY)" },

        // display language (<root>)
        {   "English",  "French",   "Croatian", "Greek",    "Norwegian",  "Italian",  "xx" },
        // display country (<root>)
        {   "United States",    "France",   "Croatia",  "Greece",   "Norway",  "",     "YY" },
        // display variant (<root>)
        {   "",     "",     "",     "",     "Nynorsk",   "",     ""},
        // display name (<root>)
        {   "English (United States)", "French (France)", "Croatian (Croatia)", "Greek (Greece)", "Norwegian (Norway, Nynorsk)", "Italian", "xx (YY)" },
    };

    @Test
    public void TestBasicGetters() {
        for (int i = 0; i <= MAX_LOCALES; i++) {
            Locale testLocale = Locale.of(dataTable[LANG][i], dataTable[CTRY][i], dataTable[VAR][i]);
            System.out.println("Testing " + testLocale + "...");

            if (!testLocale.getLanguage().equals(dataTable[LANG][i])) {
                fail("  Language code mismatch: " + testLocale.getLanguage() + " versus "
                        + dataTable[LANG][i]);
            }
            if (!testLocale.getCountry().equals(dataTable[CTRY][i])) {
                fail("  Country code mismatch: " + testLocale.getCountry() + " versus "
                        + dataTable[CTRY][i]);
            }
            if (!testLocale.getVariant().equals(dataTable[VAR][i])) {
                fail("  Variant code mismatch: " + testLocale.getVariant() + " versus "
                        + dataTable[VAR][i]);
            }
            if (!testLocale.toString().equals(dataTable[NAME][i])) {
                fail("  Locale name mismatch: " + testLocale.toString() + " versus "
                        + dataTable[NAME][i]);
            }
        }

        System.out.println("Same thing without variant codes...");
        for (int i = 0; i <= MAX_LOCALES; i++) {
            Locale testLocale = Locale.of(dataTable[LANG][i], dataTable[CTRY][i]);
            System.out.println("Testing " + testLocale + "...");

            if (!testLocale.getLanguage().equals(dataTable[LANG][i])) {
                fail("  Language code mismatch: " + testLocale.getLanguage() + " versus "
                        + dataTable[LANG][i]);
            }
            if (!testLocale.getCountry().equals(dataTable[CTRY][i])) {
                fail("  Country code mismatch: " + testLocale.getCountry() + " versus "
                        + dataTable[CTRY][i]);
            }
            if (!testLocale.getVariant().equals("")) {
                fail("  Variant code mismatch: " + testLocale.getVariant() + " versus \"\"");
            }
        }
    }

    @Test
    public void TestSimpleResourceInfo() {
        for (int i = 0; i <= MAX_LOCALES; i++) {
            if (dataTable[LANG][i].equals("xx")) {
                continue;
            }

            Locale testLocale = Locale.of(dataTable[LANG][i], dataTable[CTRY][i], dataTable[VAR][i]);
            System.out.println("Testing " + testLocale + "...");

            if (!testLocale.getISO3Language().equals(dataTable[LANG3][i])) {
                fail("  ISO-3 language code mismatch: " + testLocale.getISO3Language()
                        + " versus " + dataTable[LANG3][i]);
            }
            if (!testLocale.getISO3Country().equals(dataTable[CTRY3][i])) {
                fail("  ISO-3 country code mismatch: " + testLocale.getISO3Country()
                        + " versus " + dataTable[CTRY3][i]);
            }
/*
            // getLCID() is currently private
            if (!String.valueOf(testLocale.getLCID()).equals(dataTable[LCID][i]))
                fail("  LCID mismatch: " + testLocale.getLCID() + " versus "
                            + dataTable[LCID][i]);
*/
        }
    }

    /*
     * @bug 4101316
     * @bug 4084688 (This bug appears to be a duplicate of something, because it was fixed
     *              between 1.1.5 and 1.1.6, but I included a new test for it anyway)
     * @bug 4052440 Stop falling back to the default locale.
     */
    @Test
    public void TestDisplayNames() {
        Locale saveDefault = Locale.getDefault();
        Locale english = Locale.US;
        Locale french = Locale.FRANCE;
        Locale croatian = Locale.of("hr", "HR");
        Locale greek = Locale.of("el", "GR");

        Locale.setDefault(english);
        System.out.println("With default = en_US...");
        System.out.println("  In default locale...");
        doTestDisplayNames(null, DLANG_EN, false);
        System.out.println("  In locale = en_US...");
        doTestDisplayNames(english, DLANG_EN, false);
        System.out.println("  In locale = fr_FR...");
        doTestDisplayNames(french, DLANG_FR, false);
        System.out.println("  In locale = hr_HR...");
        doTestDisplayNames(croatian, DLANG_HR, false);
        System.out.println("  In locale = el_GR...");
        doTestDisplayNames(greek, DLANG_EL, false);

        Locale.setDefault(french);
        System.out.println("With default = fr_FR...");
        System.out.println("  In default locale...");
        doTestDisplayNames(null, DLANG_FR, true);
        System.out.println("  In locale = en_US...");
        doTestDisplayNames(english, DLANG_EN, true);
        System.out.println("  In locale = fr_FR...");
        doTestDisplayNames(french, DLANG_FR, true);
        System.out.println("  In locale = hr_HR...");
        doTestDisplayNames(croatian, DLANG_HR, true);
        System.out.println("  In locale = el_GR...");
        doTestDisplayNames(greek, DLANG_EL, true);

        Locale.setDefault(saveDefault);
    }

    private void doTestDisplayNames(Locale inLocale, int compareIndex, boolean defaultIsFrench) {
        String language = Locale.getDefault().getLanguage();

        if (defaultIsFrench && !language.equals("fr")) {
            fail("Default locale should be French, but it's really " + language);
        } else if (!defaultIsFrench && !language.equals("en")) {
            fail("Default locale should be English, but it's really " + language);
        }

        for (int i = 0; i <= MAX_LOCALES; i++) {
            Locale testLocale = Locale.of(dataTable[LANG][i], dataTable[CTRY][i], dataTable[VAR][i]);
            System.out.println("  Testing " + testLocale + "...");

            String testLang;
            String testCtry;
            String testVar;
            String testName;

            if (inLocale == null) {
                testLang = testLocale.getDisplayLanguage();
                testCtry = testLocale.getDisplayCountry();
                testVar = testLocale.getDisplayVariant();
                testName = testLocale.getDisplayName();
            } else {
                testLang = testLocale.getDisplayLanguage(inLocale);
                testCtry = testLocale.getDisplayCountry(inLocale);
                testVar = testLocale.getDisplayVariant(inLocale);
                testName = testLocale.getDisplayName(inLocale);
            }

            String expectedLang;
            String expectedCtry;
            String expectedVar;
            String expectedName;

            expectedLang = dataTable[compareIndex][i];
            if (expectedLang.equals("") && defaultIsFrench) {
                expectedLang = dataTable[DLANG_EN][i];
            }
            if (expectedLang.equals("")) {
                expectedLang = dataTable[DLANG_ROOT][i];
            }

            expectedCtry = dataTable[compareIndex + 1][i];
            if (expectedCtry.equals("") && defaultIsFrench) {
                expectedCtry = dataTable[DCTRY_EN][i];
            }
            if (expectedCtry.equals("")) {
                expectedCtry = dataTable[DCTRY_ROOT][i];
            }

            expectedVar = dataTable[compareIndex + 2][i];
            if (expectedVar.equals("") && defaultIsFrench) {
                expectedVar = dataTable[DVAR_EN][i];
            }
            if (expectedVar.equals("")) {
                expectedVar = dataTable[DVAR_ROOT][i];
            }

            expectedName = dataTable[compareIndex + 3][i];
            if (expectedName.equals("") && defaultIsFrench) {
                expectedName = dataTable[DNAME_EN][i];
            }
            if (expectedName.equals("")) {
                expectedName = dataTable[DNAME_ROOT][i];
            }

            if (!testLang.equals(expectedLang)) {
                fail("Display language mismatch: " + testLang + " versus " + expectedLang);
            }
            if (!testCtry.equals(expectedCtry)) {
                fail("Display country mismatch: " + testCtry + " versus " + expectedCtry);
            }
            if (!testVar.equals(expectedVar)) {
                fail("Display variant mismatch: " + testVar + " versus " + expectedVar);
            }
            if (!testName.equals(expectedName)) {
                fail("Display name mismatch: " + testName + " versus " + expectedName);
            }
        }
    }

    @SuppressWarnings("deprecation")
    @Test
    public void TestSimpleObjectStuff() {
        Locale test1 = new Locale("aa", "AA");
        Locale test2 = new Locale("aa", "AA");
        Locale test3 = (Locale) test1.clone();
        Locale test4 = Locale.of("zz", "ZZ");

        if (test1 == test2 || test1 == test3 || test1 == test4 || test2 == test3) {
            fail("Some of the test variables point to the same locale!");
        }

        if (test3 == null) {
            fail("clone() failed to produce a valid object!");
        }

        if (!test1.equals(test2) || !test1.equals(test3) || !test2.equals(test3)) {
            fail("clone() or equals() failed: objects that should compare equal don't");
        }

        if (test1.equals(test4) || test2.equals(test4) || test3.equals(test4)) {
            fail("equals() failed: objects that shouldn't compare equal do");
        }

        int hash1 = test1.hashCode();
        int hash2 = test2.hashCode();
        int hash3 = test3.hashCode();

        if (hash1 != hash2 || hash1 != hash3 || hash2 != hash3) {
            fail("hashCode() failed: objects that should have the same hash code don't");
        }
    }

    /**
     * @bug 4011756 4011380
     */
    @Test
    public void TestISO3Fallback() {
        Locale test = Locale.of("xx", "YY");
        boolean gotException = false;
        String result = "";

        try {
            result = test.getISO3Language();
        } catch (MissingResourceException e) {
            gotException = true;
        }
        if (!gotException) {
            fail("getISO3Language() on xx_YY returned " + result + " instead of throwing an exception");
        }

        gotException = false;
        try {
            result = test.getISO3Country();
        } catch (MissingResourceException e) {
            gotException = true;
        }
        if (!gotException) {
            fail("getISO3Country() on xx_YY returned " + result + " instead of throwing an exception");
        }
    }

    /**
     * @bug 4106155 4118587 7066203 7085757
     */
    @Test
    public void TestGetLangsAndCountries() {
        // It didn't seem right to just do an exhaustive test of everything here, so I check
        // for the following things:
        // 1) Does each list have the right total number of entries?
        // 2) Does each list contain certain language and country codes we think are important
        //     (the G7 countries, plus a couple others)?
        // 3) Does each list have every entry formatted correctly? (i.e., two characters,
        //     all lower case for the language codes, all upper case for the country codes)
        // 4) Is each list in sorted order?
        String[] test = Locale.getISOLanguages();
        String[] spotCheck1 = {"en", "es", "fr", "de", "it", "ja", "ko", "zh", "th",
            "he", "id", "iu", "ug", "yi", "za"};

        if (test.length != 188) {
            fail("Expected getISOLanguages() to return 188 languages; it returned " + test.length);
        } else {
            for (int i = 0; i < spotCheck1.length; i++) {
                int j;
                for (j = 0; j < test.length; j++) {
                    if (test[j].equals(spotCheck1[i])) {
                        break;
                    }
                }
                if (j == test.length || !test[j].equals(spotCheck1[i])) {
                    fail("Couldn't find " + spotCheck1[i] + " in language list.");
                }
            }
        }
        for (int i = 0; i < test.length; i++) {
            if (!test[i].equals(test[i].toLowerCase())) {
                fail(test[i] + " is not all lower case.");
            }
            if (test[i].length() != 2) {
                fail(test[i] + " is not two characters long.");
            }
            if (i > 0 && test[i].compareTo(test[i - 1]) <= 0) {
                fail(test[i] + " appears in an out-of-order position in the list.");
            }
        }

        test = Locale.getISOCountries();
        String[] spotCheck2 = {"US", "CA", "GB", "FR", "DE", "IT", "JP", "KR", "CN", "TW", "TH"};


        if (test.length != 249) {
            fail("Expected getISOCountries to return 249 countries; it returned " + test.length);
        } else {
            for (int i = 0; i < spotCheck2.length; i++) {
                int j;
                for (j = 0; j < test.length; j++) {
                    if (test[j].equals(spotCheck2[i])) {
                        break;
                    }
                }
                if (j == test.length || !test[j].equals(spotCheck2[i])) {
                    fail("Couldn't find " + spotCheck2[i] + " in country list.");
                }
            }
        }
        for (int i = 0; i < test.length; i++) {
            if (!test[i].equals(test[i].toUpperCase())) {
                fail(test[i] + " is not all upper case.");
            }
            if (test[i].length() != 2) {
                fail(test[i] + " is not two characters long.");
            }
            if (i > 0 && test[i].compareTo(test[i - 1]) <= 0) {
                fail(test[i] + " appears in an out-of-order position in the list.");
            }
        }
    }

    /**
     * @bug 4126880
     */
    void Test4126880() {
        String[] test;

        test = Locale.getISOCountries();
        test[0] = "SUCKER!!!";
        test = Locale.getISOCountries();
        if (test[0].equals("SUCKER!!!")) {
            fail("Changed internal country code list!");
        }

        test = Locale.getISOLanguages();
        test[0] = "HAHAHAHA!!!";
        test = Locale.getISOLanguages();
        if (test[0].equals("HAHAHAHA!!!")) { // Fixed typo
            fail("Changes internal language code list!");
        }
    }

    /**
     * @bug 4107014
     */
    @Test
    public void TestGetAvailableLocales() {
        Locale[] locales = Locale.getAvailableLocales();
        if (locales == null || locales.length == 0) {
            fail("Locale.getAvailableLocales() returned no installed locales!");
        } else {
            System.out.println("Locale.getAvailableLocales() returned a list of " + locales.length + " locales.");
            for (int i = 0; i < locales.length; i++) {
                System.out.println(locales[i].toString());
            }
        }
    }

    /**
     * @bug 4135316
     */
    @Test
    public void TestBug4135316() {
        Locale[] locales1 = Locale.getAvailableLocales();
        Locale[] locales2 = Locale.getAvailableLocales();
        if (locales1 == locales2) {
            fail("Locale.getAvailableLocales() doesn't clone its internal storage!");
        }
    }

    /**
     * @bug 4107953
     */
/*
test commented out pending API-change approval
    @Test
    public void TestGetLanguagesForCountry() {
        String[] languages = Locale.getLanguagesForCountry("US");

        if (!searchStringArrayFor("en", languages))
            fail("Didn't get en as a language for US");

        languages = Locale.getLanguagesForCountry("FR");
        if (!searchStringArrayFor("fr", languages))
            fail("Didn't get fr as a language for FR");

        languages = Locale.getLanguagesForCountry("CH");
        if (!searchStringArrayFor("fr", languages))
            fail("Didn't get fr as a language for CH");
        if (!searchStringArrayFor("it", languages))
            fail("Didn't get it as a language for CH");
        if (!searchStringArrayFor("de", languages))
            fail("Didn't get de as a language for CH");

        languages = Locale.getLanguagesForCountry("JP");
        if (!searchStringArrayFor("ja", languages))
            fail("Didn't get ja as a language for JP");
    }
*/

    private boolean searchStringArrayFor(String s, String[] array) {
        for (int i = 0; i < array.length; i++)
            if (s.equals(array[i]))
                return true;
        return false;
    }
    /**
     * @bug 4110613
     */
    @Test
    public void TestSerialization() throws ClassNotFoundException, OptionalDataException,
            IOException, StreamCorruptedException {
        ObjectOutputStream ostream;
        ByteArrayOutputStream obstream;
        byte[] bytes = null;

        obstream = new ByteArrayOutputStream();
        ostream = new ObjectOutputStream(obstream);

        Locale test1 = Locale.of("zh", "TW");
        int dummy = test1.hashCode();   // fill in the cached hash-code value
        ostream.writeObject(test1);

        bytes = obstream.toByteArray();

        ObjectInputStream istream = new ObjectInputStream(new ByteArrayInputStream(bytes));

        Locale test2 = (Locale) (istream.readObject());

        if (!test1.equals(test2) || test1.hashCode() != test2.hashCode()) {
            fail("Locale failed to deserialize correctly.");
        }
    }

    /**
     * @bug 4118587
     */
    @Test
    public void TestSimpleDisplayNames() {
        // This test is different from TestDisplayNames because TestDisplayNames checks
        // fallback behavior, combination of language and country names to form locale
        // names, and other stuff like that.  This test just checks specific language
        // and country codes to make sure we have the correct names for them.
        String[] languageCodes = {"he", "id", "iu", "ug", "yi", "za"};
        String[] languageNames = {"Hebrew", "Indonesian", "Inuktitut", "Uyghur", "Yiddish",
            "Zhuang"};

        for (int i = 0; i < languageCodes.length; i++) {
            String test = (Locale.of(languageCodes[i])).getDisplayLanguage(Locale.US);
            if (!test.equals(languageNames[i])) {
                fail("Got wrong display name for " + languageCodes[i] + ": Expected \""
                        + languageNames[i] + "\", got \"" + test + "\".");
            }
        }
    }

    /**
     * @bug 4118595
     */
    @Test
    public void TestUninstalledISO3Names() {
        // This test checks to make sure getISO3Language and getISO3Country work right
        // even for locales that are not installed.
        String[] iso2Languages = {"am", "ba", "fy", "mr", "rn", "ss", "tw", "zu"};
        String[] iso3Languages = {"amh", "bak", "fry", "mar", "run", "ssw", "twi", "zul"};

        for (int i = 0; i < iso2Languages.length; i++) {
            String test = (Locale.of(iso2Languages[i])).getISO3Language();
            if (!test.equals(iso3Languages[i])) {
                fail("Got wrong ISO3 code for " + iso2Languages[i] + ": Expected \""
                        + iso3Languages[i] + "\", got \"" + test + "\".");
            }
        }

        String[] iso2Countries = {"AF", "BW", "KZ", "MO", "MN", "SB", "TC", "ZW"};
        String[] iso3Countries = {"AFG", "BWA", "KAZ", "MAC", "MNG", "SLB", "TCA", "ZWE"};

        for (int i = 0; i < iso2Countries.length; i++) {
            String test = (Locale.of("", iso2Countries[i])).getISO3Country();
            if (!test.equals(iso3Countries[i])) {
                fail("Got wrong ISO3 code for " + iso2Countries[i] + ": Expected \""
                        + iso3Countries[i] + "\", got \"" + test + "\".");
            }
        }
    }

    /**
     * @bug 4052404 4778440 8263202
     */
    @Test
    public void TestChangedISO639Codes() {
        Locale hebrewOld = Locale.of("iw", "IL");
        Locale hebrewNew = Locale.of("he", "IL");
        Locale yiddishOld = Locale.of("ji", "IL");
        Locale yiddishNew = Locale.of("yi", "IL");
        Locale indonesianOld = Locale.of("in");
        Locale indonesianNew = Locale.of("id");

        if ("true".equalsIgnoreCase(System.getProperty("java.locale.useOldISOCodes"))) {
            if (!hebrewNew.getLanguage().equals("iw")) {
                fail("Got back wrong language code for new Hebrew: expected \"iw\", got \""
                        + hebrewNew.getLanguage() + "\"");
            }
            if (!yiddishNew.getLanguage().equals("ji")) {
                fail("Got back wrong language code for new Yiddish: expected \"ji\", got \""
                        + yiddishNew.getLanguage() + "\"");
            }
            if (!indonesianNew.getLanguage().equals("in")) {
                fail("Got back wrong language code for new Indonesian: expected \"in\", got \""
                        + indonesianNew.getLanguage() + "\"");
            }
        } else {
            if (!hebrewOld.getLanguage().equals("he")) {
                fail("Got back wrong language code for old Hebrew: expected \"he\", got \""
                        + hebrewNew.getLanguage() + "\"");
            }
            if (!yiddishOld.getLanguage().equals("yi")) {
                fail("Got back wrong language code for old Yiddish: expected \"yi\", got \""
                        + yiddishNew.getLanguage() + "\"");
            }
            if (!indonesianOld.getLanguage().equals("id")) {
                fail("Got back wrong language code for old Indonesian: expected \"id\", got \""
                        + indonesianNew.getLanguage() + "\"");
            }
        }

    }

    /**
     * @bug 4092475
     * I could not reproduce this bug.  I'm pretty convinced it was fixed with the
     * big locale-data reorg of 10/28/97.  The lookup logic for language and country
     * display names was also changed at that time in that check-in.    --rtg 3/20/98

     * This test is not designed to work in any other locale but en_US.
     * Most of the LocaleElements do not contain display names for other languages,
     * so this test fails (bug 4289223) when run under different locales. For example,
     * LocaleElements_es as of kestrel does not have a localized name for Japanese, so
     * the getDisplayName method asks the default locale for a display name. The Japanese
     * localized name for "Japanese" does not equal "Japanese" so this test fails for es
     * display names if run under a ja locale. Eventually, he LocaleElements should probably
     * be updated to contain more localized language and region display names.
     * 1999-11-19 joconner
     *
     */
    @Test
    public void TestAtypicalLocales() {
        Locale[] localesToTest = { Locale.of("de", "CA"),
                                   Locale.of("ja", "ZA"),
                                   Locale.of("ru", "MX"),
                                   Locale.of("en", "FR"),
                                   Locale.of("es", "DE"),
                                   Locale.of("", "HR"),
                                   Locale.of("", "SE"),
                                   Locale.of("", "DO"),
                                   Locale.of("", "BE") };
        String[] englishDisplayNames = { "German (Canada)",
                                         "Japanese (South Africa)",
                                         "Russian (Mexico)",
                                         "English (France)",
                                         "Spanish (Germany)",
                                         "Croatia",
                                         "Sweden",
                                         "Dominican Republic",
                                         "Belgium" };
        String[] frenchDisplayNames = { "allemand (Canada)",
                                        "japonais (Afrique du Sud)",
                                        "russe (Mexique)",
                                         "anglais (France)",
                                         "espagnol (Allemagne)",
                                        "Croatie",
                                        "Su\u00e8de",
                                        "R\u00e9publique dominicaine",
                                        "Belgique" };
        String[] spanishDisplayNames = { "alem\u00E1n (Canad\u00E1)",
                                         "japon\u00E9s (Sud\u00E1frica)",
                                         "ruso (M\u00e9xico)",
                                         "ingl\u00E9s (Francia)",
                                         "espa\u00f1ol (Alemania)",
                                         "Croacia",
                                         "Suecia",
                                         "Rep\u00fablica Dominicana",
                                         "B\u00E9lgica" };


        // save the default locale and set to the new default to en_US
        Locale defaultLocale = Locale.getDefault();
        Locale.setDefault(Locale.US);

        for (int i = 0; i < localesToTest.length; i++) {
            String name = localesToTest[i].getDisplayName(Locale.US);
            System.out.println(name);
            if (!name.equals(englishDisplayNames[i])) {
                fail("Lookup in English failed: expected \"" + englishDisplayNames[i]
                        + "\", got \"" + name + "\"");
            }
        }

        for (int i = 0; i < localesToTest.length; i++) {
            String name = localesToTest[i].getDisplayName(Locale.of("es", "ES"));
            System.out.println(name);
            if (!name.equals(spanishDisplayNames[i])) {
                fail("Lookup in Spanish failed: expected \"" + spanishDisplayNames[i]
                        + "\", got \"" + name + "\"");
            }
        }

        for (int i = 0; i < localesToTest.length; i++) {
            String name = localesToTest[i].getDisplayName(Locale.FRANCE);
            System.out.println(name);
            if (!name.equals(frenchDisplayNames[i])) {
                fail("Lookup in French failed: expected \"" + frenchDisplayNames[i]
                        + "\", got \"" + name + "\"");
            }
        }

        // restore the default locale for other tests
        Locale.setDefault(defaultLocale);
    }

    /**
     * @bug 4126371
     */
    @Test
    public void TestNullDefault() {
        // why on earth anyone would ever try to do this is beyond me, but we should
        // definitely make sure we don't let them
        boolean gotException = false;
        try {
            Locale.setDefault(null);
        } catch (NullPointerException e) {
            // all other exception types propagate through here back to the test harness
            gotException = true;
        }
        if (Locale.getDefault() == null) {
            fail("Locale.getDefault() allowed us to set default to NULL!");
        }
        if (!gotException) {
            fail("Trying to set default locale to NULL didn't throw exception!");
        }
    }

    /**
     * @bug 4135752
     * This would be better tested by the LocaleDataTest.  Will move it when I
     * get the LocaleDataTest working again.
     */
    @Test
    public void TestThaiCurrencyFormat() {
        DecimalFormat thaiCurrency = (DecimalFormat) NumberFormat.getCurrencyInstance(
                Locale.of("th", "TH"));
        if (!thaiCurrency.getPositivePrefix().equals("\u0e3f")) {
            fail("Thai currency prefix wrong: expected \"\u0e3f\", got \""
                    + thaiCurrency.getPositivePrefix() + "\"");
        }
        if (!thaiCurrency.getPositiveSuffix().equals("")) {
            fail("Thai currency suffix wrong: expected \"\", got \""
                    + thaiCurrency.getPositiveSuffix() + "\"");
        }
    }

    /**
     * @bug 4122371
     * Confirm that Euro support works.  This test is pretty rudimentary; all it does
     * is check that any locales with the EURO variant format a number using the
     * Euro currency symbol.
     *
     * ASSUME: All locales encode the Euro character "\u20AC".
     * If this is changed to use the single-character Euro symbol, this
     * test must be updated.
     *
     * DON'T ASSUME: Any specific countries support the Euro.  Instead,
     * iterate through all locales.
     */
    @Test
    public void TestEuroSupport() {
        final String EURO_VARIANT = "EURO";
        final String EURO_CURRENCY = "\u20AC"; // Look for this string in formatted Euro currency

        Locale[] locales = NumberFormat.getAvailableLocales();
        for (int i = 0; i < locales.length; ++i) {
            Locale loc = locales[i];
            if (loc.getVariant().indexOf(EURO_VARIANT) >= 0) {
                NumberFormat nf = NumberFormat.getCurrencyInstance(loc);
                String pos = nf.format(271828.182845);
                String neg = nf.format(-271828.182845);
                if (pos.indexOf(EURO_CURRENCY) >= 0
                        && neg.indexOf(EURO_CURRENCY) >= 0) {
                    System.out.println("Ok: " + loc.toString()
                            + ": " + pos + " / " + neg);
                } else {
                    fail("Fail: " + loc.toString()
                            + " formats without " + EURO_CURRENCY
                            + ": " + pos + " / " + neg
                            + "\n*** THIS FAILURE MAY ONLY MEAN THAT LOCALE DATA HAS CHANGED ***");
                }
            }
        }
    }

    /**
     * @bug 4139504
     * toString() doesn't work with language_VARIANT.
     */
    @Test
    public void TestToString() {
        Object[] DATA = {
            Locale.of("xx", "", ""), "xx",
            Locale.of("", "YY", ""), "_YY",
            Locale.of("", "", "ZZ"), "",
            Locale.of("xx", "YY", ""), "xx_YY",
            Locale.of("xx", "", "ZZ"), "xx__ZZ",
            Locale.of("", "YY", "ZZ"), "_YY_ZZ",
            Locale.of("xx", "YY", "ZZ"), "xx_YY_ZZ",
        };
        for (int i = 0; i < DATA.length; i += 2) {
            Locale loc = (Locale) DATA[i];
            String fmt = (String) DATA[i + 1];
            if (!loc.toString().equals(fmt)) {
                fail("Fail: Locale.toString(" + fmt + ")=>" + loc);
            }
        }
    }

    /**
     * @bug 4105828
     * Currency symbol in zh is wrong.  We will test this at the NumberFormat
     * end to test the whole pipe.
     */
    @Test
    public void Test4105828() {
        Locale[] LOC = {Locale.CHINESE, Locale.of("zh", "CN"),
            Locale.of("zh", "TW"), Locale.of("zh", "HK")};
        for (int i = 0; i < LOC.length; ++i) {
            NumberFormat fmt = NumberFormat.getPercentInstance(LOC[i]);
            String result = fmt.format(1);
            if (!result.equals("100%")) {
                fail("Percent for " + LOC[i] + " should be 100%, got " + result);
            }
        }
    }

    /**
     * @bug 4139940
     * Couldn't reproduce this bug -- probably was fixed earlier.
     *
     * ORIGINAL BUG REPORT:
     * -- basically, hungarian for monday shouldn't have an \u00f4
     * (o circumflex)in it instead it should be an o with 2 inclined
     * (right) lines over it..
     *
     * You may wonder -- why do all this -- why not just add a line to
     * LocaleData?  Well, I could see by inspection that the locale file had the
     * right character in it, so I wanted to check the rest of the pipeline -- a
     * very remote possibility, but I wanted to be sure.  The other possibility
     * is that something is wrong with the font mapping subsystem, but we can't
     * test that here.
     */
    @Test
    public void Test4139940() {
        Locale mylocale = Locale.of("hu");
        @SuppressWarnings("deprecation")
        Date mydate = new Date(98, 3, 13); // A Monday
        DateFormat df_full = new SimpleDateFormat("EEEE", mylocale);
        String str = df_full.format(mydate);
        // Make sure that o circumflex (\u00F4) is NOT there, and
        // o double acute (\u0151) IS.
        if (str.indexOf('\u0151') < 0 || str.indexOf('\u00F4') >= 0) {
            fail("Fail: Monday in Hungarian is wrong");
        }
    }

    /**
     * @bug 4143951
     * Russian first day of week should be Monday. Confirmed.
     */
    @Test
    public void Test4143951() {
        Calendar cal = Calendar.getInstance(Locale.of("ru", "RU"));
        if (cal.getFirstDayOfWeek() != Calendar.MONDAY) {
            fail("Fail: First day of week in Russia should be Monday");
        }
    }

    /**
     * @bug 4147315
     * java.util.Locale.getISO3Country() works wrong for non ISO-3166 codes.
     * Should throw an exception for unknown locales
     */
    @Test
    public void Test4147315() {
        // Try with codes that are the wrong length but happen to match text
        // at a valid offset in the mapping table
        Locale locale = Locale.of("aaa", "CCC");

        try {
            String result = locale.getISO3Country();

            fail("ERROR: getISO3Country() returns: " + result
                    + " for locale '" + locale + "' rather than exception");
        } catch (MissingResourceException e) {
        }
    }

    /**
     * @bug 4147317 4940539
     * java.util.Locale.getISO3Language() works wrong for non ISO-639 codes.
     * Should throw an exception for unknown locales, except they have three
     * letter language codes.
     */
    @Test
    public void Test4147317() {
        // Try a three letter language code, and check whether it is
        // returned as is.
        Locale locale = Locale.of("aaa", "CCC");

        String result = locale.getISO3Language();
        if (!result.equals("aaa")) {
            fail("ERROR: getISO3Language() returns: " + result
                    + " for locale '" + locale + "' rather than returning it as is");
        }

        // Try an invalid two letter language code, and check whether it
        // throws a MissingResourceException.
        locale = Locale.of("zz", "CCC");

        try {
            result = locale.getISO3Language();

            fail("ERROR: getISO3Language() returns: " + result
                    + " for locale '" + locale + "' rather than exception");
        } catch (MissingResourceException e) {
        }
    }

    /*
     * @bug 4147552 4778440 8030696
     */
    @Test
    public void Test4147552() {
        Locale[] locales = {Locale.of("no", "NO"), Locale.of("no", "NO", "B"),
            Locale.of("no", "NO", "NY"), Locale.of("nb", "NO"),
            Locale.of("nn", "NO")};
        String[] englishDisplayNames = {"Norwegian (Norway)",
            "Norwegian (Norway, Bokm\u00e5l)",
            "Norwegian (Norway, Nynorsk)",
            "Norwegian Bokm\u00e5l (Norway)",
            "Norwegian Nynorsk (Norway)"};
        String[] norwegianDisplayNames = {"norsk (Norge)",
            "norsk (Norge, Bokm\u00e5l)", "norsk (Noreg, Nynorsk)",
            "norsk bokm\u00e5l (Norge)", "norsk nynorsk (Noreg)"};

        for (int i = 0; i < locales.length; i++) {
            Locale loc = locales[i];
            if (!loc.getDisplayName(Locale.US).equals(englishDisplayNames[i])) {
                fail("English display-name mismatch: expected "
                        + englishDisplayNames[i] + ", got " + loc.getDisplayName());
            }
            if (!loc.getDisplayName(loc).equals(norwegianDisplayNames[i])) {
                fail("Norwegian display-name mismatch: expected "
                        + norwegianDisplayNames[i] + ", got "
                        + loc.getDisplayName(loc));
            }
        }
    }

    /*
     * @bug 8030696
     */
    @Test
    public void Test8030696() {
        List<Locale> av = Arrays.asList(Locale.getAvailableLocales());
        if (!av.contains(Locale.of("nb", "NO"))
                || !av.contains(Locale.of("nn", "NO"))) {
            fail("\"nb-NO\" and/or \"nn-NO\" locale(s) not returned from getAvailableLocales().");
        }
    }

    static String escapeUnicode(String s) {
        StringBuffer buf = new StringBuffer();
        for (int i = 0; i < s.length(); ++i) {
            char c = s.charAt(i);
            if (c >= 0x20 && c <= 0x7F) {
                buf.append(c);
            } else {
                buf.append("\\u");
                String h = "000" + Integer.toHexString(c);
                if (h.length() > 4) {
                    h = h.substring(h.length() - 4);
                }
                buf.append(h);
            }
        }
        return buf.toString();
    }
}
