/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8132995
 * @summary Tests to excercise the optimization described in the bug report.
 * @run junit ImmutableMatchResultTest
 */

import org.junit.jupiter.api.Test;

import java.nio.CharBuffer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ImmutableMatchResultTest {

    private static final int prefixLen = 3;
    private static final int infixLen = 5;
    private static final int suffixLen = 4;

    private static final String group1 = "abc";
    private static final String group2 = "wxyz";
    private static final String group0 = group1 + "-".repeat(infixLen) + group2;

    private static final String in = "-".repeat(prefixLen) + group0 + "-".repeat(suffixLen);

    private static void test(CharSequence cs) {
        Matcher m = Pattern.compile("(" + group1 + ")-*(" + group2 + ")").matcher(cs);
        assertTrue(m.find());

        assertEquals(prefixLen, m.start());
        assertEquals(prefixLen + group0.length(), m.end());
        assertEquals(group0, m.toMatchResult().group());

        assertEquals(prefixLen, m.start(1));
        assertEquals(prefixLen + group1.length(), m.end(1));
        assertEquals(group1, m.toMatchResult().group(1));

        assertEquals(prefixLen + group1.length() + infixLen, m.start(2));
        assertEquals(prefixLen + group1.length() + infixLen + group2.length(), m.end(2));
        assertEquals(group2, m.toMatchResult().group(2));
    }

    @Test
    void testString() {
        test(in);
    }

    @Test
    void testStringBuilder() {
        test(new StringBuilder(in));
    }

    @Test
    void testStringBuffer() {
        test(new StringBuffer(in));
    }

    @Test
    void testCharBuffer() {
        test(CharBuffer.wrap(in));
    }

}
