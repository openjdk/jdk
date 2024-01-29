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

import jdk.test.lib.RandomFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.CharBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/*
 * @test
 * @bug 8132995 8312976
 * @key randomness
 *
 * @summary Tests to exercise the optimization described in the bug report.
 * @library /test/lib
 * @run junit ImmutableMatchResultTest
 */

public class ImmutableMatchResultTest {

    private static final int prefixLen;
    private static final int infixLen;
    private static final int suffixLen;
    private static final String group1 = "abc";
    private static final String group2 = "wxyz";
    private static final String group0;
    private static final String in;
    private static final String groupResults = "(([a-z]+)([0-9]*))";
    private static final String inResults;
    private static final String letters1 = "abcd";
    private static final String digits1 = "12";
    private static final String letters2 = "pqr";
    private static final String digits2 = "";

    static {
        Random rnd = RandomFactory.getRandom();
        prefixLen = rnd.nextInt(10);
        infixLen = rnd.nextInt(10);
        suffixLen = rnd.nextInt(10);
        group0 = group1 + "-".repeat(infixLen) + group2;
        in = "-".repeat(prefixLen) + group0 + "-".repeat(suffixLen);
        inResults = " ".repeat(prefixLen) + letters1 + digits1 + " ".repeat(infixLen) + letters2 + digits2 + " ".repeat(suffixLen);
    }

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

    private static void testResultsStream(CharSequence cs) {
        Matcher m = Pattern.compile(groupResults).matcher(cs);
        List<MatchResult> results = m.results().toList();
        assertEquals(2, results.size());

        int startLetters1 = prefixLen;
        int endLetters1 = startLetters1 + letters1.length();
        int startDigits1 = endLetters1;
        int endDigits1 = startDigits1 + digits1.length();

        assertEquals(startLetters1, results.get(0).start());
        assertEquals(startLetters1, results.get(0).start(0));
        assertEquals(startLetters1, results.get(0).start(1));
        assertEquals(startLetters1, results.get(0).start(2));
        assertEquals(startDigits1, results.get(0).start(3));

        assertEquals(endDigits1, results.get(0).end());
        assertEquals(endDigits1, results.get(0).end(0));
        assertEquals(endDigits1, results.get(0).end(1));
        assertEquals(endLetters1, results.get(0).end(2));
        assertEquals(endDigits1, results.get(0).end(3));

        assertEquals(letters1 + digits1, results.get(0).group());
        assertEquals(letters1 + digits1, results.get(0).group(0));
        assertEquals(letters1 + digits1, results.get(0).group(1));
        assertEquals(letters1, results.get(0).group(2));
        assertEquals(digits1, results.get(0).group(3));

        int startLetters2 = endDigits1 + infixLen;
        int endLetters2 = startLetters2 + letters2.length();
        int startDigits2 = endLetters2;
        int endDigits2 = startDigits2 + digits2.length();

        assertEquals(startLetters2, results.get(1).start());
        assertEquals(startLetters2, results.get(1).start(0));
        assertEquals(startLetters2, results.get(1).start(1));
        assertEquals(startLetters2, results.get(1).start(2));
        assertEquals(startDigits2, results.get(1).start(3));

        assertEquals(endDigits2, results.get(1).end());
        assertEquals(endDigits2, results.get(1).end(0));
        assertEquals(endDigits2, results.get(1).end(1));
        assertEquals(endLetters2, results.get(1).end(2));
        assertEquals(endDigits2, results.get(1).end(3));

        assertEquals(letters2 + digits2, results.get(1).group());
        assertEquals(letters2 + digits2, results.get(1).group(0));
        assertEquals(letters2 + digits2, results.get(1).group(1));
        assertEquals(letters2, results.get(1).group(2));
        assertEquals(digits2, results.get(1).group(3));
    }

    @Test
    void testResultsStreamString() {
        testResultsStream(inResults);
    }

    @Test
    void testResultsStreamStringBuilder() {
        testResultsStream(new StringBuilder(inResults));
    }

    @Test
    void testResultsStreamStringBuffer() {
        testResultsStream(new StringBuffer(inResults));
    }

    @Test
    void testResultsStreamCharBuffer() {
        testResultsStream(CharBuffer.wrap(inResults));
    }

    static Arguments[] testGroupsOutsideMatch() {
        return new Arguments[]{
                arguments("(?<=(\\d{3}))\\D*(?=(\\d{4}))", "-1234abcxyz5678-"),
                arguments("(?<=(\\d{3}))\\D*(?=(\\1))", "-1234abcxyz2348-"),
                arguments("(?<!(\\d{4}))\\D+(?=(\\d{4}))", "123abcxyz5678-"),
        };
    }

    @ParameterizedTest
    @MethodSource
    void testGroupsOutsideMatch(String pattern, String text) {
        char[] data = text.toCharArray();
        Matcher m = Pattern.compile(pattern)
                .matcher(CharBuffer.wrap(data));

        assertEquals(2, m.groupCount());
        assertTrue(m.find());

        int start = m.start();
        int end = m.end();
        String group = m.group();

        int prefixStart = m.start(1);
        int prefixEnd = m.end(1);
        String prefixGroup = m.group(1);

        int suffixStart = m.start(2);
        int suffixEnd = m.end(2);
        String suffixGroup = m.group(2);

        MatchResult mr = m.toMatchResult();
        Arrays.fill(data, '*');  // spoil original input

        assertEquals(start, mr.start());
        assertEquals(end, mr.end());
        assertEquals(group, mr.group());

        assertEquals(prefixStart, mr.start(1));
        assertEquals(prefixEnd, mr.end(1));
        assertEquals(prefixGroup, mr.group(1));

        assertEquals(suffixStart, mr.start(2));
        assertEquals(suffixEnd, mr.end(2));
        assertEquals(suffixGroup, mr.group(2));
    }

}
