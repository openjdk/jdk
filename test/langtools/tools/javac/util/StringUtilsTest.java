/*
 * Copyright (c) 2013, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8029800 8043186 8313693
 * @summary Unit test StringUtils
 * @modules jdk.compiler/com.sun.tools.javac.util
 * @run main StringUtilsTest
 */

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import com.sun.tools.javac.util.StringUtils;
import com.sun.tools.javac.util.StringUtils.DamerauLevenshteinDistance;

public class StringUtilsTest {
    public static void main(String... args) throws Exception {
        new StringUtilsTest().run();
    }

    void run() throws Exception {
        Locale.setDefault(Locale.of("tr", "TR"));

        //verify the properties of the default locale:
        assertEquals("\u0131", "I".toLowerCase());
        assertEquals("\u0130", "i".toUpperCase());

        //verify the StringUtils.toLowerCase/toUpperCase do what they should:
        assertEquals("i", StringUtils.toLowerCase("I"));
        assertEquals("I", StringUtils.toUpperCase("i"));

        //verify StringUtils.caseInsensitiveIndexOf works:
        assertEquals(2, StringUtils.indexOfIgnoreCase("  lookFor", "lookfor"));
        assertEquals(11, StringUtils.indexOfIgnoreCase("  lookFor  LOOKfor", "lookfor", 11));
        assertEquals(2, StringUtils.indexOfIgnoreCase("\u0130\u0130lookFor", "lookfor"));

        //verify Damerau-Levenshtein

        assertEquals(3, DamerauLevenshteinDistance.of("kitten", "sitting"));
        // note that the restricted Damerau-Levenshtein distance would be 3, not 2:
        assertEquals(2, DamerauLevenshteinDistance.of("ca", "abc"));
        //verify strings comprising only non-LATIN1 characters
        assertEquals(1, DamerauLevenshteinDistance.of("\u0438\u044e\u043d\u044c",
                "\u0438\u044e\u043b\u044c"));
        //verify strings comprising mixed characters: non-LATIN1 and ASCII
        // it's important to start with ASCII characters, so that we
        // test switching a storage (see current implementation)
        assertEquals(2, DamerauLevenshteinDistance.of("c\u043ede", "cod\u0435"));

        //verify metric properties
        for (String a : List.of("", "a", "b", "abc")) {
            for (String b : List.of("", "a", "b", "abc")) {
                assertNonNegativity(a, b);
                assertSymmetry(a, b);
            }
        }

        for (String a : List.of("", "a", "b", "c")) {
            for (String b : List.of("ab", "ac", "bc")) {
                for (String c : List.of("abc", "bca", "cab")) {
                    assertTriangleInequality(a, b, c);
                    assertTriangleInequality(b, c, a);
                    assertTriangleInequality(c, a, b);
                }
            }
        }
    }

    private void assertNonNegativity(String a, String b) {
        if (a.equals(b)) {
            assertEquals(0, DamerauLevenshteinDistance.of(a, b));
        } else {
            assertTrue(DamerauLevenshteinDistance.of(a, b) > 0);
        }
    }

    private void assertSymmetry(String a, String b) {
        assertEquals(DamerauLevenshteinDistance.of(a, b),
                DamerauLevenshteinDistance.of(b, a));
    }

    private void assertTriangleInequality(String a, String b, String c) {
        int ab = DamerauLevenshteinDistance.of(a, b);
        int bc = DamerauLevenshteinDistance.of(b, c);
        int ac = DamerauLevenshteinDistance.of(a, c);
        assertTrue(ab + bc >= ac);
    }

    void assertEquals(String expected, String actual) {
        if (!Objects.equals(expected, actual)) {
            throw new IllegalStateException("expected=" + expected + "; actual=" + actual);
        }
    }

    void assertEquals(int expected, int actual) {
        if (expected != actual) {
            throw new IllegalStateException("expected=" + expected + "; actual=" + actual);
        }
    }

    void assertTrue(boolean cond) {
        if (!cond) {
            throw new IllegalStateException();
        }
    }
}
