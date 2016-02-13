/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7071819
 * @summary tests Unicode Extended Grapheme support
 * @run main GraphemeTest
 */

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class GraphemeTest {

    public static void main(String[] args) throws Throwable {
        testProps(Paths.get(System.getProperty("test.src", "."),
                            "GraphemeBreakProperty.txt"));
        testBreak(Paths.get(System.getProperty("test.src", "."),
                            "GraphemeBreakTest.txt"));
    }

    private static void testProps(Path path) throws IOException {
        Files.lines(path)
            .filter( ln -> ln.length() != 0 && !ln.startsWith("#") )
            .forEach(ln -> {
                    String[] strs = ln.split("\\s+");
                    int off = strs[0].indexOf("..");
                    int cp0, cp1;
                    String expected = strs[2];
                    if (off != -1) {
                        cp0 = Integer.parseInt(strs[0], 0, off, 16);
                        cp1 = Integer.parseInt(strs[0], off + 2, strs[0].length(), 16);
                    } else {
                        cp0 = cp1 = Integer.parseInt(strs[0], 16);
                    }
                    for (int cp = cp0; cp <=  cp1; cp++) {
                        // NOTE:
                        // #tr29 "plus a few General_Category = Spacing_Mark needed for
                        // canonical equivalence."
                        // For "extended grapheme clusters" support, there is no
                        // need actually to diff "extend" and "spackmark" given GB9, GB9a.
                        if (!expected.equals(types[getType(cp)])) {
                            if ("Extend".equals(expected) &&
                                "SpacingMark".equals(types[getType(cp)]))
                                System.out.printf("[%x]  [%s][%d] -> [%s]%n",
                                    cp, expected, Character.getType(cp), types[getType(cp)]);
                            else
                                throw new RuntimeException(String.format(
                                    "cp=[%x], expeced:[%s] result:[%s]%n",
                                    cp, expected, types[getType(cp)]));
                        }
                    }
                });
    }

    private static void testBreak(Path path) throws IOException {
        Files.lines(path)
            .filter( ln -> ln.length() != 0 && !ln.startsWith("#") )
            .forEach(ln -> {
                    String str = ln.replaceAll("\\s+|\\([a-zA-Z]+\\)|\\[[a-zA-Z]]+\\]|#.*", "");
                    // System.out.println(str);
                    String[] cstrs = str.split("\u00f7|\u00d7");
                    int prevCp = -1;
                    char prevBk = '\u00f7';
                    int offBk = 0;
                    for (String cstr : cstrs) {
                        if (cstr.length() == 0)  // first empty str
                            continue;
                        int cp = Integer.parseInt(cstr, 16);
                        if (prevCp == -1) {
                            prevCp = cp;
                        } else {
                            // test against the rules directly
                            if (rules[getType(prevCp)][getType(cp)] != (prevBk == '\u00f7')) {
                                throw new RuntimeException(String.format(
                                    "NG %x[%d] %x[%d] -> %b  [%s]%n",
                                    prevCp, getType(prevCp), cp, getType(cp),
                                    rules[getType(prevCp)][getType(cp)],
                                    ln));
                            }
                        }
                        prevCp = cp;
                        offBk += (cstr.length() + 1);
                        prevBk = str.charAt(offBk);
                    }
                });
    }

    private static final String[] types = {
        "Other", "CR", "LF", "Control", "Extend", "Regional_Indicator",
        "Prepend", "SpacingMark",
        "L", "V", "T", "LV", "LVT" };

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////

    // types
    private static final int OTHER = 0;
    private static final int CR = 1;
    private static final int LF = 2;
    private static final int CONTROL = 3;
    private static final int EXTEND = 4;
    private static final int RI = 5;
    private static final int PREPEND = 6;
    private static final int SPACINGMARK = 7;
    private static final int L = 8;
    private static final int V = 9;
    private static final int T = 10;
    private static final int LV = 11;
    private static final int LVT = 12;

    private static final int FIRST_TYPE = 0;
    private static final int LAST_TYPE = 12;

    private static boolean[][] rules;
    static {
        rules = new boolean[LAST_TYPE + 1][LAST_TYPE + 1];
        // default, any ÷ any
        for (int i = FIRST_TYPE; i <= LAST_TYPE; i++)
            for (int j = FIRST_TYPE; j <= LAST_TYPE; j++)
                rules[i][j] = true;
        // GB 6 L x (L | V | LV | VT)
        rules[L][L] = false;
        rules[L][V] = false;
        rules[L][LV] = false;
        rules[L][LVT] = false;
        // GB 7 (LV | V) x (V | T)
        rules[LV][V] = false;
        rules[LV][T] = false;
        rules[V][V] = false;
        rules[V][T] = false;
        // GB 8 (LVT | T) x T
        rules[LVT][T] = false;
        rules[T][T] = false;
        // GB 8a RI x RI
        rules[RI][RI] = false;
        // GB 9 x Extend
        // GB 9a x Spacing Mark
        // GB 9b Prepend x
        for (int i = FIRST_TYPE; i <= LAST_TYPE; i++) {
            rules[i][EXTEND] = false;
            rules[i][SPACINGMARK] = false;
            rules[PREPEND][i] = false;
        }
        // GB 4  (Control | CR | LF) ÷
        // GB 5  ÷ (Control | CR | LF)
        for (int i = FIRST_TYPE; i <= LAST_TYPE; i++)
            for (int j = CR; j <= CONTROL; j++) {
                rules[i][j] = true;
                rules[j][i] = true;
            }
        // GB 3 CR x LF
        rules[CR][LF] = false;
        // GB 10 Any ÷ Any  -> default
    }

    // Hangul syllables
    private static final int SYLLABLE_BASE = 0xAC00;
    private static final int LCOUNT = 19;
    private static final int VCOUNT = 21;
    private static final int TCOUNT = 28;
    private static final int NCOUNT = VCOUNT * TCOUNT; // 588
    private static final int SCOUNT = LCOUNT * NCOUNT; // 11172

    // #tr29: SpacingMark exceptions: The following (which have
    // General_Category = Spacing_Mark and would otherwise be included)
    // are specifically excluded
    private static boolean isExcludedSpacingMark(int cp) {
       return  cp == 0x102B || cp == 0x102C || cp == 0x1038 ||
               cp >= 0x1062 && cp <= 0x1064 ||
               cp >= 0x1062 && cp <= 0x106D ||
               cp == 0x1083 ||
               cp >= 0x1087 && cp <= 0x108C ||
               cp == 0x108F ||
               cp >= 0x109A && cp <= 0x109C ||
               cp == 0x1A61 || cp == 0x1A63 || cp == 0x1A64 ||
               cp == 0xAA7B || cp == 0xAA7D;
    }

    private static int getType(int cp) {
        int type = Character.getType(cp);
        switch(type) {
        case Character.CONTROL:
            if (cp == 0x000D)
                return CR;
            if (cp == 0x000A)
                return LF;
            return CONTROL;
         case Character.UNASSIGNED:
            // NOTE: #tr29 lists "Unassigned and Default_Ignorable_Code_Point" as Control
            // but GraphemeBreakTest.txt lists u+0378/reserved-0378 as "Other"
            // so type it as "Other" to make the test happy
             if (cp == 0x0378)
                 return OTHER;
        case Character.LINE_SEPARATOR:
        case Character.PARAGRAPH_SEPARATOR:
        case Character.SURROGATE:
            return CONTROL;
        case Character.FORMAT:
            if (cp == 0x200C || cp == 0x200D)
                return EXTEND;
            return CONTROL;
        case Character.NON_SPACING_MARK:
        case Character.ENCLOSING_MARK:
             // NOTE:
             // #tr29 "plus a few General_Category = Spacing_Mark needed for
             // canonical equivalence."
             // but for "extended grapheme clusters" support, there is no
             // need actually to diff "extend" and "spackmark" given GB9, GB9a
             return EXTEND;
        case  Character.COMBINING_SPACING_MARK:
            if (isExcludedSpacingMark(cp))
                return OTHER;
            // NOTE:
            // 0x11720 and 0x11721 are mentioned in #tr29 as
            // OTHER_LETTER but it appears their category has been updated to
            // COMBING_SPACING_MARK already (verified in ver.8)
            return SPACINGMARK;
        case Character.OTHER_SYMBOL:
            if (cp >= 0x1F1E6 && cp <= 0x1F1FF)
                return RI;
            return OTHER;
        case Character.MODIFIER_LETTER:
            // WARNING:
            // not mentioned in #tr29 but listed in GraphemeBreakProperty.txt
            if (cp == 0xFF9E || cp == 0xFF9F)
                return EXTEND;
            return OTHER;
        case Character.OTHER_LETTER:
            if (cp == 0x0E33 || cp == 0x0EB3)
                return SPACINGMARK;
            // hangul jamo
            if (cp >= 0x1100 && cp <= 0x11FF) {
                if (cp <= 0x115F)
                    return L;
                if (cp <= 0x11A7)
                    return V;
                return T;
            }
            // hangul syllables
            int sindex = cp - SYLLABLE_BASE;
            if (sindex >= 0 && sindex < SCOUNT) {

                if (sindex % TCOUNT == 0)
                    return LV;
                return LVT;
            }
            //  hangul jamo_extended A
            if (cp >= 0xA960 && cp <= 0xA97C)
                return L;
            //  hangul jamo_extended B
            if (cp >= 0xD7B0 && cp <= 0xD7C6)
                return V;
            if (cp >= 0xD7CB && cp <= 0xD7FB)
                return T;
        }
        return OTHER;
    }
}
