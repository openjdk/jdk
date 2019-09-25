/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.junit.Test;

// JDK-8228903
public final class StringCompareToAVX512Test extends GraalCompilerTest {

    public static int compareTo(String str1, String str2) {
        return str1.compareTo(str2);
    }

    @Test
    public void testLatin1VsUtf16BadStride() {
        String latin1 = "000000001111111122222222333333334444444455555555666666667777777\u0099888888889";
        String utf16 = "000000001111111122222222333333334444444455555555666666667777777\u0699888888889";
        test("compareTo", latin1, utf16);
        test("compareTo", utf16, latin1);
    }

    @Test
    public void testLatin1VsUtf16BadStride2() {
        String latin1 = "00000000111111112222222233333333444444445555555566666666777777778888888\u008799999999AAAAAAAAB";
        String utf16 = "00000000111111112222222233333333444444445555555566666666777777778888888\u058799999999AAAAAAAAB";
        test("compareTo", latin1, utf16);
        test("compareTo", utf16, latin1);
    }

    @Test
    public void testLatin1VsUtf16FalseEquality() {
        // java.lang.AssertionError: expected:<-1536> but was:<0>
        String latin1 = "00000000111111112222222233333333\u0099444444455555555";
        String utf16 = "00000000111111112222222233333333\u0699xxxxxxxxxxxxxxx";
        test("compareTo", latin1, utf16);
        test("compareTo", utf16, latin1);
    }

    @Test
    public void testLatin1BeyondRange() {
        StringBuilder latin1Builder = new StringBuilder();
        for (int j = 0; j <= 255; ++j) {
            latin1Builder.append((char) j);
        }
        String latin1 = latin1Builder.toString();
        test("compareTo", latin1, String.valueOf(latin1.toCharArray()));
    }
}
