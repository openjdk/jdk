/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 8058779 8054307 8222955
 * @library /test/lib
 * @build jdk.test.lib.RandomFactory
 * @run testng LiteralReplace
 * @summary Basic tests of String.replace(CharSequence, CharSequence)
 * @key randomness
 */

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Random;

import jdk.test.lib.RandomFactory;

import org.testng.annotations.Test;
import org.testng.annotations.DataProvider;
import static org.testng.Assert.fail;

public class LiteralReplace {

    @Test(dataProvider="sourceTargetReplacementExpected")
    public void testExpected(String source, String target,
             String replacement, String expected)
    {
        String canonical = canonicalReplace(source, target, replacement);
        if (!canonical.equals(expected)) {
            fail("Canonical: " + canonical + " != " + expected);
        }
        test0(source, target, replacement, expected);
    }

    @Test(dataProvider="sourceTargetReplacement")
    public void testCanonical(String source, String target,
            String replacement)
    {
        String canonical = canonicalReplace(source, target, replacement);
        test0(source, target, replacement, canonical);
    }

    private void test0(String source, String target, String replacement,
            String expected)
    {
        String result = source.replace(target, replacement);
        if (!result.equals(expected)) {
            fail(result + " != " + expected);
        }
    }

    @Test(dataProvider="sourceTargetReplacementWithNull",
            expectedExceptions = {NullPointerException.class})
    public void testNPE(String source, String target, String replacement) {
        source.replace(target, replacement);
    }

    @Test(expectedExceptions = {OutOfMemoryError.class})
    public void testOOM() {
        "1".repeat(65537).replace("1", "2".repeat(65537));
    }

    @DataProvider
    public static Object[][] sourceTargetReplacementExpected() {
        return new Object[][] {
            {"aaa", "aa", "b", "ba"},
            {"abcdefgh", "def", "DEF", "abcDEFgh"},
            {"abcdefgh", "123", "DEF", "abcdefgh"},
            {"abcdefgh", "abcdefghi", "DEF", "abcdefgh"},
            {"abcdefghabc", "abc", "DEF", "DEFdefghDEF"},
            {"abcdefghdef", "def", "", "abcgh"},
            {"abcdefgh", "", "_", "_a_b_c_d_e_f_g_h_"},
            {"", "", "", ""},
            {"", "a", "b", ""},
            {"", "", "abc", "abc"},
            {"abcdefgh", "abcdefgh", "abcdefgh", "abcdefgh"},
            {"abcdefgh", "abcdefgh", "abcdefghi", "abcdefghi"},
            {"abcdefgh", "abcdefgh", "", ""},
            {"abcdabcd", "abcd", "", ""},
            {"aaaaaaaaa", "aa", "_X_", "_X__X__X__X_a"},
            {"aaaaaaaaa", "aa", "aaa", "aaaaaaaaaaaaa"},
            {"aaaaaaaaa", "aa", "aa", "aaaaaaaaa"},
            {"a.c.e.g.", ".", "-", "a-c-e-g-"},
            {"abcdefgh", "[a-h]", "X", "abcdefgh"},
            {"aa+", "a+", "", "a"},
            {"^abc$", "abc", "x", "^x$"},
            {"abc", "b", "_", "a_c"},
            {"abc", "bc", "_", "a_"},
            {"abc".repeat(65537) + "end", "b", "_XYZ_", "a_XYZ_c".repeat(65537) + "end"},
            {"abc".repeat(65537) + "end", "a", "_", "_bc".repeat(65537) + "end"},
            {"a".repeat(65537), "a", "", ""},
            {"ab".repeat(65537), "a", "", "b".repeat(65537)},
            {"ab".repeat(65537), "ab", "", ""},
            {"b" + "ab".repeat(65537), "ab", "", "b"},

            // more with non-latin1 characters
            {"一一一",
             "一一",
             "丁",
             "丁一"},

            {"一丁丂七丄丅丆万丈",
             "七丄丅",
             "丐丑丒",
             "一丁丂丐丑丒丆万丈"},

            {"一丁丂七丄丅丆万丈",
             "ABC",
             "丐丑丒",
             "一丁丂七丄丅丆万丈"},

            {"一丁丂七丄丂七万丈",
             "丂七",
             "丒专",
             "一丁丒专丄丒专万丈"},

            {"一丁丂七丄丂七万丈",
             "丂七",
             "ab",
             "一丁ab丄ab万丈"},

            {"一丁丂七丄丅丆万",
             "",
             "_",
             "_一_丁_丂_七_丄_丅_丆_万_"},
            {"^一丁丂$",
             "一丁丂",
             "七",
             "^七$"},

            {"", "一", "丁", ""},
            {"", "", "一丁丂", "一丁丂"},

            {"^一丁丂$",
             "一丁丂",
             "X",
             "^X$"},

            {"abcdefgh",
             "def",
             "丁",
             "abc丁gh"},

            {"abcdefgh",
             "def",
             "丁丂",
             "abc丁丂gh"},

            {"abcdefabcgh",
             "abc",
             "丁丂",
             "丁丂def丁丂gh"},

            {"abcdefabcghabc",
             "abc",
             "丁丂",
             "丁丂def丁丂gh丁丂"},

            {"一丁丂七丄丅",
             "一丁丂七丄丅",
             "abcd",
             "abcd"},

            {"一丁",
             "一丁",
             "abcdefg",
             "abcdefg"},

            {"一丁xyz",
             "一丁",
             "abcdefg",
             "abcdefgxyz"},

            {"一一一一一一",
             "一一",
             "一一一",
             "一一一一一一一一一"},

            {"一一一一一一",
             "一一一",
             "一一",
             "一一一一"},

            {"一.丁.丂.七.丄.",
             ".",
             "-",
             "一-丁-丂-七-丄-"},

            {"一一一一一一",
             "一",
             "",
             ""},

            {"一丁丂七丄丅",
             "一丁丂七丄丅",
             "",
             ""},

            {"丁丂七", "丂", "丂", "丁丂七"},
            {"丁丂七", "丂", "丄", "丁丄七"},
            {"丁丂七", "丂", "_", "丁_七"},
            {"a丂c", "丂", "_", "a_c"},
            {"丁@七", "@", "_", "丁_七"},
            {"丁@七", "@", "丂", "丁丂七"},
            {"丁丂七", "丂七", "丂七", "丁丂七"},
            {"丁丂七", "丂七", "丄丅", "丁丄丅"},
            {"丁丂七", "丂七", "丆", "丁丆"},
            {"丁丂七", "丂七", "<>", "丁<>"},
            {"@丂七", "丂七", "<>", "@<>"},
            {"丁@@", "丁@", "", "@"},
            {"丁@@", "丁@", "#", "#@"},
            {"丁@@", "丁@", "三", "三@"},
            {"丁@@", "丁@", "#三", "#三@"},
            {"丁丂七".repeat(32771) + "end", "丂", "丂", "丁丂七".repeat(32771) + "end"},
            {"丁丂七".repeat(32771) + "end", "丂", "丄", "丁丄七".repeat(32771) + "end"},
            {"丁丂七".repeat(32771) + "end", "丂", "丄丅", "丁丄丅七".repeat(32771) + "end"},
            {"丁丂七".repeat(32771) + "end", "丂", "_", "丁_七".repeat(32771) + "end"},
            {"丁_七".repeat(32771) + "end", "_", "_", "丁_七".repeat(32771) + "end"},
            {"丁_七".repeat(32771) + "end", "_", "丆", "丁丆七".repeat(32771) + "end"},
            {"丁_七".repeat(32771) + "end", "_", "丆丆", "丁丆丆七".repeat(32771) + "end"},
            {"X_Y".repeat(32771) + "end", "_", "万", "X万Y".repeat(32771) + "end"},
            {"X_Y".repeat(32771) + "end", "_", "万丈", "X万丈Y".repeat(32771) + "end"},
            {"X_Y".repeat(32771) + "end", "_", ".丈.", "X.丈.Y".repeat(32771) + "end"},
            {"上".repeat(32771), "上", "", ""},
            {"上下".repeat(32771), "上", "", "下".repeat(32771)},
            {"上下".repeat(32771), "下", "", "上".repeat(32771)},
            {"上下".repeat(32771), "上下", "", ""},
            {"下" + "上下".repeat(32771), "上下", "", "下"},
            {"上下".repeat(32771) + "上", "上下", "", "上"},
            {"下" + "上下".repeat(32771) + "上", "上下", "", "下上"},
            {"b" + "上下".repeat(32771), "上下", "", "b"},
            {"上下".repeat(32771) + "a", "上下", "", "a"},
            {"b" + "上下".repeat(32771) + "a", "上下", "", "ba"},
        };
    }

    @DataProvider
    public static Iterator<Object[]> sourceTargetReplacement() {
        ArrayList<Object[]> list = new ArrayList<>();
        for (int maxSrcLen = 1; maxSrcLen <= (1 << 10); maxSrcLen <<= 1) {
            for (int maxTrgLen = 1; maxTrgLen <= (1 << 10); maxTrgLen <<= 1) {
                for (int maxPrlLen = 1; maxPrlLen <= (1 << 10); maxPrlLen <<= 1) {
                    list.add(makeArray(makeRandomString(maxSrcLen),
                                       makeRandomString(maxTrgLen),
                                       makeRandomString(maxPrlLen)));

                    String source = makeRandomString(maxSrcLen);
                    list.add(makeArray(source,
                                       mekeRandomSubstring(source, maxTrgLen),
                                       makeRandomString(maxPrlLen)));
                }
            }
        }
        return list.iterator();
    }

    @DataProvider
    public static Iterator<Object[]> sourceTargetReplacementWithNull() {
        ArrayList<Object[]> list = new ArrayList<>();
        Object[] arr = {null, "", "a", "b", "string", "str", "ababstrstr"};
        for (int i = 0; i < arr.length; ++i) {
            for (int j = 0; j < arr.length; ++j) {
                for (int k = 0; k < arr.length; ++k) {
                    if (arr[i] != null && (arr[j] == null || arr[k] == null)) {
                        list.add(makeArray(arr[i], arr[j], arr[k]));
                    }
                }
            }
        }
        return list.iterator();
    }

    // utilities

    /**
     * How the String.replace(CharSequence, CharSequence) used to be implemented
     */
    private static String canonicalReplace(String source, String target, String replacement) {
        return Pattern.compile(target.toString(), Pattern.LITERAL).matcher(
                source).replaceAll(Matcher.quoteReplacement(replacement.toString()));
    }

    private static final Random random = RandomFactory.getRandom();

    private static final char[] CHARS = ("qwertyuiop[]12345678" +
        "90-=\\`asdfghjkl;'zxcvbnm,./~!@#$%^&*()_+|QWERTYUIOP{" +
        "}ASDFGHJKL:\"ZXCVBNM<>?\n\r\tфыва").toCharArray();

    private static String makeRandomString(int maxLen) {
        int len = random.nextInt(maxLen);
        char[] buf = new char[len];
        for (int i = 0; i < len; ++i) {
            buf[i] = CHARS[random.nextInt(CHARS.length)];
        }
        return new String(buf);
    }

    private static String mekeRandomSubstring(String source, int maxLen) {
        if (source.isEmpty()) {
            return source;
        }
        int pos = random.nextInt(source.length());
        int len = Integer.min(source.length() - pos,
                              random.nextInt(maxLen));
        return source.substring(pos, pos + len);
    }

    private static Object[] makeArray(Object... array) {
         return array;
    }
}
