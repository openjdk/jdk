/*
 * Copyright (c) 1997, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @test 1.1 02/09/11
 * @bug 4176141 4655819
 * @summary Regression tests for Japanese Collation
 * @modules jdk.localedata
 */

import java.text.*;
import java.util.*;

public class JapaneseTest {

    // NOTE:
    //   Golden data in this test case is locale data dependent and
    //   may need to be changed if the Japanese locale collation rules
    //   are changed.

    /*
     *                    | NO_DECOMP(default) | CANONICAL_DECOMP | FULL_DECOMP
     * -------------------+--------------------+------------------+-------------
     *  PRIMARY           | s1 < s2 (-1)       | s1 < s2 (-1)     | s1 < s2 (-1)
     *  SECONDARY         | s1 < s2 (-1)       | s1 < s2 (-1)     | s1 < s2 (-1)
     *  TERTIARY(default) | S1 < s2 (-1)       | s1 < s2 (-1)     | s1 < s2 (-1)
     */
    static final int[][] results1 = {
        { -1, -1, -1},
        { -1, -1, -1},
        { -1, -1, -1},
    };
    static final String[][] compData1 = {
        /*
         * Data to verify '<' relationship in LocaleElements_ja.java
         */
        {"や", "ユ",
         "Hiragana \"YA\"(0x3084) <---> Katakana \"YU\"(0x30E6)"},
        {"ユ", "よ",
         "Katakana \"YU\"(0x30E6) <---> Hiragana \"YO\"(0x3088)"},
        {"±", "≠",
         "Plus-Minus Sign(0x00B1) <---> Not Equal To(0x2260)"},
        {"】", "≠",
         "Right Black Lenticular Bracket(0x3011) <---> Not Equal To(0x2260)"},
        {"≠", "℃",
         "Not Equal To(0x2260) <---> Degree Celsius(0x2103)"},
        {"≠", "☆",
         "Not Equal To(0x2260) <---> White Star(0x2606)"},
        {"ヽ", "ゞ",
         "Katakana Iteration Mark(0x30FD) <---> Hiragana Voiced Iteration Mark(0x309E)"},
        {"すゝ", "すゞ",
         "Hiragana \"SU\"(0x3059)Hiragana Iteration Mark(0x309D) <---> Hiragana \"SU\"(0x3059)Hiragana Voiced Iteration Mark(0x309E)"},
        {"舞", "福",
         "CJK Unified Ideograph(0x821E) <---> CJK Unified Ideograph(0x798F)"},

        /*
         * Data to verify normalization
         */
        {"≠", "≟",
         "Not Equal To(0x2260) <---> Questioned Equal To(0x225F)"},
        {"≮", "≠",
         "Not Less-than(0x226E) <---> Not Equal To(0x2260)"},
        {"≮", "≭",
         "Not Less-than(0x226E) <---> Not Equivalent To(0x226D)"},
    };

    /*
     *                    | NO_DECOMP(default) | CANONICAL_DECOMP | FULL_DECOMP
     * -------------------+--------------------+------------------+-------------
     *  PRIMARY           | s1 = s2 (0)        | s1 = s2 (0)      | s1 = s2 (0)
     *  SECONDARY         | s1 < s2 (-1)       | s1 < s2 (-1)     | s1 < s2 (-1)
     *  TERTIARY(default) | S1 < s2 (-1)       | s1 < s2 (-1)     | s1 < s2 (-1)
     */
    static final int[][] results2 = {
        {  0,  0,  0},
        { -1, -1, -1},
        { -1, -1, -1},
    };
    static final String[][] compData2 = {
        /*
         * Data to verify ';' relationship in LocaleElements_ja.java
         */
        {"゙", "゚",
         "Combining Katakana-Hiragana Voiced Sound Mark(0x3099) <---> Combining Katakana-Hiragana Semi-voiced Sound Mark(0x309A)"},
        {"こうとう", "こうどう",
         "Hiragana \"KOUTOU\"(0x3053 0x3046 0x3068 0x3046) <---> Hiragana \"KOUTO\"(0x3053 0x3046 0x3068)Combining Katakana-Hiragana Voiced Sound Mark(0X3099)\"U\"(0x3046)"},
        {"こうとう", "こうどう",
         "Hiragana \"KOUTOU\"(0x3053 0x3046 0x3068 0x3046) <---> Hiragana \"KOUDOU\"(0x3053 0x3046 0x3069 0x3046)"},
        {"こうどう", "ごうとう",
         "Hiragana \"KOUTOU\"(0x3053 0x3046 0x3069 0x3046) <---> Hiragana \"GOUTOU\"(0x3054 0x3046 0x3068 0x3046)"},
        {"ごうとう", "ごうどう",
         "Hiragana \"GOUTOU\"(0x3054 0x3046 0x3068 0x3046) <---> Hiragana \"GOUDOU\"(0x3054 0x3046 0x3069 0x3046)"},
    };

    /*
     *                    | NO_DECOMP(default) | CANONICAL_DECOMP | FULL_DECOMP
     * -------------------+--------------------+------------------+-------------
     *  PRIMARY           | s1 = s2 (0)        | s1 = s2 (0)      | s1 = s2 (0)
     *  SECONDARY         | s1 = s2 (0)        | s1 = s2 (0)      | s1 = s2 (0)
     *  TERTIARY(default) | S1 < s2 (-1)       | s1 < s2 (-1)     | s1 < s2 (-1)
     */
    static final int[][] results3 = {
        {  0,  0,  0},
        {  0,  0,  0},
        { -1, -1, -1},
    };
    static final String[][] compData3 = {
        /*
         * Data to verify ',' relationship in LocaleElements_ja.java
         */
        {"あ", "ぁ",
         "Hiragana \"A\"(0x3042) <---> Hiragana \"a\"(0x3041)"},
        {"ぁ", "ア",
         "Hiragana \"a\"(0x3041) <---> Katakana \"A\"(0x30A2)"},
        {"ア", "ァ",
         "Katakana \"A\"(0x30A2) <---> Katakana \"a\"(0x30A1)"},
        {"ゔ", "ヴ",
         "Hiragana \"VU\"(0x3094) <---> Katakana \"VU\"(0x30F4)"},
        {"ゔ", "ヴ",
         "Hiragana \"VU\"(0x3094) <---> Katakana \"U\"(0x30A6)Combining Katakana-Hiragana Voiced Sound Mark(0x3099)"},
        {"ゔ", "ヴ",
         "Hiragana \"U\"(0x3046)Combining Katakana-Hiragana Voiced Sound Mark(0x3099) <---> Katakana \"VU\"(0x30F4)"},
        {"ゔ", "ヴ",
         "Hiragana \"U\"(0x3046)Combining Katakana-Hiragana Voiced Sound Mark(0x3099) <---> Katakana \"U\"(0x30A6)Combining Katakana-Hiragana Voiced Sound Mark(0x3099)"},
        {"カア", "カー",
         "Katakana \"KAA\"(0x30AB 0x30A2) <---> Katakana \"KA-\"(0x30AB 0x30FC)"},
        {"ニァア", "ニァー",
         "Katakana \"NyaA\"(0x30CB 0x30A1 0x30A2) <---> Katakana \"Nya-\"(0x30CB 0x30A1 0x30FC)"},
        {"コオヒイ", "コーヒー",
         "Katakana \"KOOHII\"(0x30B3 0x30AA 0x30D2 0x30A4) <---> Katakana \"KO-HI-\"(0x30B3 0x30FC 0x30D2 0x30FC)"},
        {"りよう", "りょう",
         "Hiragana \"RIYOU\"(0x308A 0x3088 0x3046) <---> Hiragana \"Ryou\"(0x308A 0x3087 0x3046)"},
        {"めつき", "めっき",
         "Hiragana \"METSUKI\"(0x3081 0x3064 0x304D) <---> Hiragana \"MEKKI\"(0x3081 0x3063 0x304D)"},
        {"ふあん", "ファン",
         "Hiragana \"FUAN\"(0x3075 0x3042 0x3093) <---> Katakana \"FUaN\"(0x30D5 0x30A1 0x30F3)"},
        {"ふぁん", "フアン",
         "Hiragana \"FUaN\"(0x3075 0x3041 0x3093) <---> Katakana \"FUAN\"(0x30D5 0x30A2 0x30F3)"},
        {"フアン", "ファン",
         "Katakana \"FUAN\"(0x30D5 0x30A2 0x30F3) <---> Katakana \"FUaN\"(0x30D5 0x30A1 0x30F3)"},
    };

    /*
     *                    | NO_DECOMP(default) | CANONICAL_DECOMP | FULL_DECOMP
     * -------------------+--------------------+------------------+-------------
     *  PRIMARY           | s1 = s2 (0)        | s1 = s2 (0)      | s1 = s2 (0)
     *  SECONDARY         | s1 = s2 (0)        | s1 = s2 (0)      | s1 = s2 (0)
     *  TERTIARY(default) | S1 = s2 (0)        | s1 = s2 (0)      | s1 = s2 (0)
     */
    static final int[][] results4 = {
        {  0,  0,  0},
        {  0,  0,  0},
        {  0,  0,  0},
    };
    static final String[][] compData4 = {
        /*
         * Data to verify Japanese normalization
         */
        {"ゞ", "ゞ",
         "Hiragana Voiced Iteration Mark(0x309E) <---> Hiragana Iteration Mark(0x309D)Combining Katakana-Hiragana Voiced Sound Mark(0x3099)"},
        {"ヾ", "ヾ",
         "Katakana Voiced Iteration Mark(0x30FE) <---> Katakana iteration mark(0x30FD)Combining Katakana-Hiragana Voiced Sound Mark(0x3099)"},
        {"ば", "ば",
         "Hiragana \"HA\"(0x306F)Combining Katakana-Hiragana Voiced Sound Mark(0x3099) <---> Hiragana \"BA\"(0x3070)"},
        {"ぱ", "ぱ",
         "Hiragana \"HA\"(0x306F)Combining Katakana-Hiragana Semi-voiced Sound Mark(0x309A) <---> Hiragana \"PA\"(0x3071)"},
        {"ヷ", "ヷ",
         "Katakana \"WA\"(0x306F)Combining Katakana-Hiragana Voiced Sound Mark(0x3099) <---> Katakana \"VA\"(0x30F7)"},
        {"ヸ", "ヸ",
         "Katakana \"WI\"(0x30F0)Combining Katakana-Hiragana Voiced Sound Mark(0x3099) <---> Katakana \"VI\"(0x30F8)"},
        {"ヹ", "ヹ",
         "Katakana \"WE\"(0x30F1)Combining Katakana-Hiragana Voiced Sound Mark(0x3099) <---> Katakana \"VE\"(0x30F9)"},
        {"ヺ", "ヺ",
         "Katakana \"WO\"(0x30F2)Combining Katakana-Hiragana Voiced Sound Mark(0x3099) <---> Katakana \"VO\"(0x30FA)"},
        {"ゔ", "ゔ",
         "Hiragana \"U\"(0x3046)Combining Katakana-Hiragana Voiced Sound Mark(0x3099) <---> Hiragana \"VU\"(0x3094)"},
        {"ヴ", "ヴ",
         "Katakana \"U\"(0x30A6)Combining Katakana-Hiragana Voiced Sound Mark(0x3099) <---> Katakana \"VU\"(0x30F4)"},

        // verify normalization
        {"≠", "≠",
         "Not Equal To(0x2260) <---> Equal(0x003D)Combining Long Solidus Overlay(0x0338)"},
        {"≢", "≢",
         "Not Identical To(0x2262) <---> Identical To(0x2261)Combining Long Solidus Overlay(0x0338)"},
        {"≮", "≮",
         "Not Less-than(0x226E) <---> Less-than Sign(0x003C)Combining Long Solidus Overlay(0x0338)"},

        // Verify a character which has been added since Unicode 2.1.X.
        {"福", "福",
         "CJK Unified Ideograph \"FUKU\"(0x798F) <---> CJK Compatibility Ideograph \"FUKU\"(0xFA1B)"},
    };

    /*
     *                    | NO_DECOMP(default) | CANONICAL_DECOMP | FULL_DECOMP
     * -------------------+--------------------+------------------+-------------
     *  PRIMARY           | s1 > s2 (1)        | s1 = s2 (0)      | s1 = s2 (0)
     *  SECONDARY         | s1 > s2 (1)        | s1 = s2 (0)      | s1 = s2 (0)
     *  TERTIARY(default) | S1 > s2 (1)        | s1 = s2 (0)      | s1 = s2 (0)
     */
    static final int[][] results5 = {
        {  1,  0,  0},
        {  1,  0,  0},
        {  1,  0,  0},
    };
    static final String[][] compData5 = {
        /*
         * Data to verify normalization
         */
        {"≭", "≭",
         "Not Equivalent To(0x226D) <---> Equivalent To(0x224D)Combining Long Solidus Overlay(0x0338)"},
    };

    static final int[][] results6 = {
        {  1, -1, -1},
        {  1, -1, -1},
        {  1, -1, -1},
    };
    static final String[][] compData6 = {
        /*
         * Data to verify normalization
         */
        {"≭", "≬",
         "Not Equivalent To(0x226D) <---> Between(0x226C)"},
        {"≭", "≟",
         "Not Equivalent To(0x226D) <---> Questioned Equal To(0x225F)"},
    };


    /*
     * The following data isn't used at the moment because iteration marks
     * aren't supported now.
     */
    static final String[][] compData0 = {
        {"みみ", "みゝ",
         "Hiragana \"MIMI\"(0x307F 0x307F) <---> Hiragana \"MI\"(0x307F)Hiragana Iteration Mark(0x309D)"},
        {"いすず", "いすゞ",
         "Hiragana \"ISUZU\"(0x3044 0x3059 0x305A) <---> Hiragana \"ISU\"(0x3044 0x3059)Hiragana Voiced Iteration Mark(0x309E)"},
        {"ミミ", "ミヽ",
         "Katakana \"MIMI\"(0x30DF 0x30DF) <---> Katakana \"MI\"(0x30DF)Katakana Iteration Mark(0x30FD)"},
        {"イスズ", "イスヾ",
         "Katakana \"ISUZU\"(0x30A4 0x30B9 0x30BA) <---> Katakana \"ISU\"(0x30A4 0x30B9)Katakana Voiced Iteration Mark(0x30FE)"},
    };


    static final String[] decomp_name = {
        "NO_DECOMP", "CANONICAL_DECOMP", "FULL_DECOMP"
    };

    static final String[] strength_name = {
        "PRIMARY", "SECONDARY", "TERTIARY"
    };


    Collator col = Collator.getInstance(Locale.JAPAN);
    int result = 0;

    public static void main(String[] args) throws Exception {
        new JapaneseTest().run();
    }

    public void run() {
        // Use all available localse on the initial testing....
        // Locale[] locales = Locale.getAvailableLocales();
        Locale[] locales = { Locale.getDefault() };

        for (int l = 0; l < locales.length; l++) {
            Locale.setDefault(locales[l]);

            for (int decomp = 0; decomp < 3; decomp++) {// See decomp_name.
                col.setDecomposition(decomp);

                for (int strength = 0; strength < 3; strength++) {// See strength_name.
//                  System.err.println("\n" + locales[l] + ": " + strength_name[strength] + " --- " + decomp_name[decomp]);

                    col.setStrength(strength);
                    doCompare(compData1, results1[strength][decomp], strength, decomp);
                    doCompare(compData2, results2[strength][decomp], strength, decomp);
                    doCompare(compData3, results3[strength][decomp], strength, decomp);
                    doCompare(compData4, results4[strength][decomp], strength, decomp);
                    doCompare(compData5, results5[strength][decomp], strength, decomp);
                    doCompare(compData6, results6[strength][decomp], strength, decomp);
                }
            }
        }

        /* Check result */
        if (result !=0) {
            throw new RuntimeException("Unexpected results on Japanese collation.");
        }
    }

    void doCompare(String[][] s, int expectedValue, int strength, int decomp) {
        int value;
        for (int i=0; i < s.length; i++) {
            if ((value = col.compare(s[i][0], s[i][1])) != expectedValue) {
                result++;
                System.err.println(strength_name[strength] +
                                   ": compare() returned unexpected value.(" +
                                   value + ") on " + decomp_name[decomp] +
                                   "     Expected(" + expectedValue +
                                   ") for " + s[i][2]);
            }
        }
    }
}
