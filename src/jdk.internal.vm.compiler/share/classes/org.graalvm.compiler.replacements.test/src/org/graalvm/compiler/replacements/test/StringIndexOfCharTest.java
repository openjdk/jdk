/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.replacements.test;

import java.util.ArrayList;
import java.util.Collection;

import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class StringIndexOfCharTest extends GraalCompilerTest {

    @Parameterized.Parameters(name = "{0},{1},{2}")
    public static Collection<Object[]> data() {
        ArrayList<Object[]> tests = new ArrayList<>();
        String longString = "ab";
        for (int i = 0; i < 15; i++) {
            longString = longString + longString;
        }
        longString = longString + "xx";
        String longUTF16String = "\u03bb" + longString;
        String mediumString = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaax" +
                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        String mediumUTF16String = "\u03bbaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaax" +
                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        String[] targets = new String[]{"foobar", "foo", "bar", "\u03bbfoobar", mediumString, mediumUTF16String, longString, longUTF16String};
        int[] targetChars = new int[]{'f', 'o', 'r', 'x', Character.MIN_SUPPLEMENTARY_CODE_POINT};
        int[] targetOffsets = new int[12];
        for (int i = 0; i < targetOffsets.length; i++) {
            targetOffsets[i] = i - 1;
        }
        for (String source : targets) {
            for (int targetChar : targetChars) {
                for (int offset : targetOffsets) {
                    tests.add(new Object[]{source, targetChar, offset});
                }
            }
        }

        return tests;
    }

    protected final String sourceString;
    protected final int constantChar;
    protected final int fromIndex;

    public StringIndexOfCharTest(String sourceString, int constantChar, int fromIndex) {
        this.sourceString = sourceString;
        this.constantChar = constantChar;
        this.fromIndex = fromIndex;
    }

    public int testStringIndexOf(String a, int b) {
        return a.indexOf(b);
    }

    public int testStringIndexOfOffset(String a, int b, int offset) {
        return a.indexOf(b, offset);
    }

    @Test
    public void testStringIndexOfConstant() {
        test("testStringIndexOf", this.sourceString, this.constantChar);
    }

    @Test
    public void testStringIndexOfConstantOffset() {
        test("testStringIndexOfOffset", this.sourceString, this.constantChar, this.fromIndex);
    }
}
