/*
 * Copyright (c) 1999, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @summary tests RegExp framework (use -Dseed=X to set PRNG seed)
 * @bug 4481568 4482696 4495089 4504687 4527731 4599621 4631553 4619345
 * 4630911 4672616 4711773 4727935 4750573 4792284 4803197 4757029 4808962
 * 4872664 4803179 4892980 4900747 4945394 4938995 4979006 4994840 4997476
 * 5013885 5003322 4988891 5098443 5110268 6173522 4829857 5027748 6376940
 * 6358731 6178785 6284152 6231989 6497148 6486934 6233084 6504326 6635133
 * 6350801 6676425 6878475 6919132 6931676 6948903 6990617 7014645 7039066
 * 7067045 7014640 7189363 8007395 8013252 8013254 8012646 8023647 6559590
 * 8027645 8035076 8039124 8035975 8074678 6854417 8143854 8147531 7071819
 * 8151481 4867170 7080302 6728861 6995635 6736245 4916384 6328855 6192895
 * 6345469 6988218 6693451 7006761 8140212 8143282 8158482 8176029 8184706
 * 8194667 8197462 8184692 8221431 8224789 8228352 8230829 8236034 8235812
 * 8216332 8214245 8237599 8241055 8247546 8258259 8037397 8269753 8276694
 * 8280403 8264160 8281315
 * @library /test/lib
 * @library /lib/testlibrary/java/lang
 * @build jdk.test.lib.RandomFactory
 * @author Mike McCloskey
 * @run testng RegExTest
 * @key randomness
 */

import java.io.*;
import java.math.BigInteger;
import java.nio.CharBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.testng.annotations.Test;
import org.testng.Assert;


import jdk.test.lib.RandomFactory;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;
import static org.testng.Assert.expectThrows;

/**
 * This is a test class created to check the operation of
 * the Pattern and Matcher classes.
 */
public class RegExTest {

    private static final Random generator = RandomFactory.getRandom();

    // Utility functions

    private static String getRandomAlphaString(int length) {

        StringBuilder buf = new StringBuilder(length);
        for (int i=0; i<length; i++) {
            char randChar = (char)(97 + generator.nextInt(26));
            buf.append(randChar);
        }
        return buf.toString();
    }

    private static void check(Matcher m, String expected) {
        m.find();
        assertEquals(m.group(), expected);
    }

    private static void check(Matcher m, String result, boolean expected) {
        m.find();
        assertEquals(m.group().equals(result), expected);
    }

    private static void check(Pattern p, String s, boolean expected) {
        assertEquals(p.matcher(s).find(), expected);
    }

    private static void check(String p, String s, boolean expected) {
        Matcher matcher = Pattern.compile(p).matcher(s);
        assertEquals(matcher.find(), expected);
    }

    private static void check(String p, char c, boolean expected) {
        String propertyPattern = expected ? "\\p" + p : "\\P" + p;
        Pattern pattern = Pattern.compile(propertyPattern);
        char[] ca = new char[1]; ca[0] = c;
        Matcher matcher = pattern.matcher(new String(ca));
        assertTrue(matcher.find());
    }

    private static void check(String p, int codePoint, boolean expected) {
        String propertyPattern = expected ? "\\p" + p : "\\P" + p;
        Pattern pattern = Pattern.compile(propertyPattern);
        char[] ca = Character.toChars(codePoint);
        Matcher matcher = pattern.matcher(new String(ca));
        assertTrue(matcher.find());
    }

    private static void check(String p, int flag, String input, String s,
                              boolean expected)
    {
        Pattern pattern = Pattern.compile(p, flag);
        Matcher matcher = pattern.matcher(input);
        if (expected)
            check(matcher, s, expected);
        else
            check(pattern, input, expected);
    }

    private static void check(Pattern p, String s, String g, String expected) {
        Matcher m = p.matcher(s);
        m.find();
        assertFalse(!m.group(g).equals(expected) ||
                s.charAt(m.start(g)) != expected.charAt(0) ||
                s.charAt(m.end(g) - 1) != expected.charAt(expected.length() - 1));
    }
    private static void checkReplaceFirst(String p, String s, String r, String expected)
    {
        assertEquals(expected, Pattern.compile(p).matcher(s).replaceFirst(r));
    }

    private static void checkReplaceAll(String p, String s, String r, String expected)
    {
        assertEquals(expected, Pattern.compile(p).matcher(s).replaceAll(r));
    }

    private static void checkExpectedFail(String p) {
        assertThrows(PatternSyntaxException.class, () ->
                Pattern.compile(p));
    }

    /**
     * Converts ASCII alphabet characters [A-Za-z] in the given 's' to
     * supplementary characters. This method does NOT fully take care
     * of the regex syntax.
     */
    public static String toSupplementaries(String s) {
        int length = s.length();
        StringBuilder sb = new StringBuilder(length * 2);

        for (int i = 0; i < length; ) {
            char c = s.charAt(i++);
            if (c == '\\') {
                sb.append(c);
                if (i < length) {
                    c = s.charAt(i++);
                    sb.append(c);
                    if (c == 'u') {
                        // assume no syntax error
                        sb.append(s.charAt(i++));
                        sb.append(s.charAt(i++));
                        sb.append(s.charAt(i++));
                        sb.append(s.charAt(i++));
                    }
                }
            } else if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')) {
                sb.append('\ud800').append((char)('\udc00'+c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    // Regular expression tests
    //Following three tests execute from a file.
    @Test
    public static void processTestCases() throws IOException {
        processFile("TestCases.txt");
    }

    @Test
    public static void processBMPTestCases() throws IOException {
        processFile("BMPTestCases.txt");
    }

    @Test
    public static void processSupplementaryTestCases() throws IOException {
        processFile("SupplementaryTestCases.txt");
    }


    @Test
    public static void nullArgumentTest() {

        assertThrows(NullPointerException.class, () -> Pattern.compile(null));
        assertThrows(NullPointerException.class, () -> Pattern.matches(null, null));
        assertThrows(NullPointerException.class, () -> Pattern.matches("xyz", null));
        assertThrows(NullPointerException.class, () -> Pattern.quote(null));
        assertThrows(NullPointerException.class, () -> Pattern.compile("xyz").split(null));
        assertThrows(NullPointerException.class, () -> Pattern.compile("xyz").matcher(null));

        final Matcher m = Pattern.compile("xyz").matcher("xyz");
        m.matches();
        assertThrows(NullPointerException.class, () -> m.appendTail((StringBuffer) null));
        assertThrows(NullPointerException.class, () -> m.appendTail((StringBuilder)null));
        assertThrows(NullPointerException.class, () -> m.replaceAll((String) null));
        assertThrows(NullPointerException.class, () -> m.replaceAll((Function<MatchResult, String>)null));
        assertThrows(NullPointerException.class, () -> m.replaceFirst((String)null));
        assertThrows(NullPointerException.class, () -> m.replaceFirst((Function<MatchResult, String>) null));
        assertThrows(NullPointerException.class, () -> m.appendReplacement((StringBuffer)null, null));
        assertThrows(NullPointerException.class, () -> m.appendReplacement((StringBuilder)null, null));
        assertThrows(NullPointerException.class, () -> m.reset(null));
        assertThrows(NullPointerException.class, () -> Matcher.quoteReplacement(null));
        //check(() -> m.usePattern(null));

    }

    // This is for bug6635133
    // Test if surrogate pair in Unicode escapes can be handled correctly.
    @Test
    public static void surrogatesInClassTest() {
        Pattern pattern = Pattern.compile("[\\ud834\\udd21-\\ud834\\udd24]");
        Matcher matcher = pattern.matcher("\ud834\udd22");

        assertTrue(matcher.find(), "Surrogate pair in Unicode escape");
    }

    // This is for bug6990617
    // Test if Pattern.RemoveQEQuoting works correctly if the octal unicode
    // char encoding is only 2 or 3 digits instead of 4 and the first quoted
    // char is an octal digit.
    @Test
    public static void removeQEQuotingTest() {
        Pattern pattern =
            Pattern.compile("\\011\\Q1sometext\\E\\011\\Q2sometext\\E");
        Matcher matcher = pattern.matcher("\t1sometext\t2sometext");

        assertTrue(matcher.find(), "Remove Q/E Quoting");
    }

    // This is for bug 4988891
    // Test toMatchResult to see that it is a copy of the Matcher
    // that is not affected by subsequent operations on the original
    @Test
    public static void toMatchResultTest() {
        Pattern pattern = Pattern.compile("squid");
        Matcher matcher = pattern.matcher(
            "agiantsquidofdestinyasmallsquidoffate");
        matcher.find();

        int matcherStart1 = matcher.start();
        MatchResult mr = matcher.toMatchResult();
        assertNotSame(mr, matcher, "Matcher toMatchResult is identical object");

        int resultStart1 = mr.start();
        assertEquals(matcherStart1, resultStart1, "equal matchers don't have equal start indices");
        matcher.find();

        int matcherStart2 = matcher.start();
        int resultStart2 = mr.start();
        assertNotEquals(matcherStart2, resultStart2, "Matcher2 and Result2 should not be equal");
        assertEquals(resultStart1, resultStart2, "Second match result should have the same state");
        MatchResult mr2 = matcher.toMatchResult();
        assertNotSame(mr, mr2, "Second Matcher copy should not be identical to the first.");
        assertEquals(mr2.start(), matcherStart2, "mr2 index should equal matcher index");
    }

    // This is for bug 8074678
    // Test the result of toMatchResult throws ISE if no match is availble
    @Test
    public static void toMatchResultTest2() {
        Matcher matcher = Pattern.compile("nomatch").matcher("hello world");
        matcher.find();
        MatchResult mr = matcher.toMatchResult();

        assertThrows(IllegalStateException.class, mr::start);
        assertThrows(IllegalStateException.class, () -> mr.start(2));
        assertThrows(IllegalStateException.class, mr::end);
        assertThrows(IllegalStateException.class, () -> mr.end(2));
        assertThrows(IllegalStateException.class, mr::group);
        assertThrows(IllegalStateException.class, () -> mr.group(2));

        matcher = Pattern.compile("(match)").matcher("there is a match");
        matcher.find();
        MatchResult mr2 = matcher.toMatchResult();
        assertThrows(IndexOutOfBoundsException.class, () -> mr2.start(2));
        assertThrows(IndexOutOfBoundsException.class, () -> mr2.end(2));
        assertThrows(IndexOutOfBoundsException.class, () -> mr2.group(2));
    }

    // This is for bug 5013885
    // Must test a slice to see if it reports hitEnd correctly
    @Test
    public static void hitEndTest() {
        // Basic test of Slice node
        Pattern p = Pattern.compile("^squidattack");
        Matcher m = p.matcher("squack");
        m.find();
        assertFalse(m.hitEnd(), "Matcher should not be at end of sequence");
        m.reset("squid");
        m.find();
        assertTrue(m.hitEnd(), "Matcher should be at the end of sequence");

        // Test Slice, SliceA and SliceU nodes
        for (int i=0; i<3; i++) {
            int flags = 0;
            if (i==1) flags = Pattern.CASE_INSENSITIVE;
            if (i==2) flags = Pattern.UNICODE_CASE;
            p = Pattern.compile("^abc", flags);
            m = p.matcher("ad");
            m.find();
            assertFalse(m.hitEnd(), "Slice node test");
            m.reset("ab");
            m.find();
            assertTrue(m.hitEnd(), "Slice node test");
        }

        // Test Boyer-Moore node
        p = Pattern.compile("catattack");
        m = p.matcher("attack");
        m.find();
        assertTrue(m.hitEnd(), "Boyer-Moore node test");

        p = Pattern.compile("catattack");
        m = p.matcher("attackattackattackcatatta");
        m.find();
        assertTrue(m.hitEnd(), "Boyer-More node test");

        // 8184706: Matching u+0d at EOL against \R should hit-end
        p = Pattern.compile("...\\R");
        m = p.matcher("cat" + (char)0x0a);
        m.find();
        assertFalse(m.hitEnd());

        m = p.matcher("cat" + (char)0x0d);
        m.find();
        assertTrue(m.hitEnd());

        m = p.matcher("cat" + (char)0x0d + (char)0x0a);
        m.find();
        assertFalse(m.hitEnd());
    }

    // This is for bug 4997476
    // It is weird code submitted by customer demonstrating a regression
    @Test
    public static void wordSearchTest() {
        String testString = "word1 word2 word3";
        Pattern p = Pattern.compile("\\b");
        Matcher m = p.matcher(testString);
        int position = 0;
        int start;
        while (m.find(position)) {
            start = m.start();
            if (start == testString.length())
                break;
            if (m.find(start+1)) {
                position = m.start();
            } else {
                position = testString.length();
            }
            if (testString.substring(start, position).equals(" "))
                continue;
            assertTrue(testString.substring(start, position-1).startsWith("word"));
        }
    }

    // This is for bug 4994840
    @Test
    public static void caretAtEndTest() {
        // Problem only occurs with multiline patterns
        // containing a beginning-of-line caret "^" followed
        // by an expression that also matches the empty string.
        Pattern pattern = Pattern.compile("^x?", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher("\r");
        matcher.find();
        matcher.find();
    }

    // This test is for 4979006
    // Check to see if word boundary construct properly handles unicode
    // non spacing marks
    @Test
    public static void unicodeWordBoundsTest() {
        String spaces = "  ";
        String wordChar = "a";
        String nsm = "\u030a";

        assert (Character.getType('\u030a') == Character.NON_SPACING_MARK);

        Pattern pattern = Pattern.compile("\\b");
        Matcher matcher = pattern.matcher("");
        // S=other B=word character N=non spacing mark .=word boundary
        // SS.BB.SS
        String input = spaces + wordChar + wordChar + spaces;
        twoFindIndexes(input, matcher, 2, 4);
        // SS.BBN.SS
        input = spaces + wordChar +wordChar + nsm + spaces;
        twoFindIndexes(input, matcher, 2, 5);
        // SS.BN.SS
        input = spaces + wordChar + nsm + spaces;
        twoFindIndexes(input, matcher, 2, 4);
        // SS.BNN.SS
        input = spaces + wordChar + nsm + nsm + spaces;
        twoFindIndexes(input, matcher, 2, 5);
        // SSN.BB.SS
        input = spaces + nsm + wordChar + wordChar + spaces;
        twoFindIndexes(input, matcher, 3, 5);
        // SS.BNB.SS
        input = spaces + wordChar + nsm + wordChar + spaces;
        twoFindIndexes(input, matcher, 2, 5);
        // SSNNSS
        input = spaces + nsm + nsm + spaces;
        matcher.reset(input);
        assertFalse(matcher.find());
        // SSN.BBN.SS
        input = spaces + nsm + wordChar + wordChar + nsm + spaces;
        twoFindIndexes(input, matcher, 3, 6);
    }

    private static void twoFindIndexes(String input, Matcher matcher, int a,
                                       int b)
    {
        matcher.reset(input);
        matcher.find();
        assertEquals(matcher.start(), a);
        matcher.find();
        assertEquals(matcher.start(), b);
    }

    // This test is for 6284152
    private static void check(String regex, String input, String[] expected) {
        List<String> result = new ArrayList<>();
        Pattern p = Pattern.compile(regex);
        Matcher m = p.matcher(input);
        while (m.find()) {
            result.add(m.group());
        }
        assertEquals(Arrays.asList(expected), result);
    }

    @Test
    public static void lookbehindTest() {
        //Positive
        check("(?<=%.{0,5})foo\\d",
              "%foo1\n%bar foo2\n%bar  foo3\n%blahblah foo4\nfoo5",
              new String[]{"foo1", "foo2", "foo3"});

        //boundary at end of the lookbehind sub-regex should work consistently
        //with the boundary just after the lookbehind sub-regex
        check("(?<=.*\\b)foo", "abcd foo", new String[]{"foo"});
        check("(?<=.*)\\bfoo", "abcd foo", new String[]{"foo"});
        check("(?<!abc )\\bfoo", "abc foo", new String[0]);
        check("(?<!abc \\b)foo", "abc foo", new String[0]);

        //Negative
        check("(?<!%.{0,5})foo\\d",
              "%foo1\n%bar foo2\n%bar  foo3\n%blahblah foo4\nfoo5",
              new String[] {"foo4", "foo5"});

        //Positive greedy
        check("(?<=%b{1,4})foo", "%bbbbfoo", new String[] {"foo"});

        //Positive reluctant
        check("(?<=%b{1,4}?)foo", "%bbbbfoo", new String[] {"foo"});

        //supplementary
        check("(?<=%b{1,4})fo\ud800\udc00o", "%bbbbfo\ud800\udc00o",
              new String[] {"fo\ud800\udc00o"});
        check("(?<=%b{1,4}?)fo\ud800\udc00o", "%bbbbfo\ud800\udc00o",
              new String[] {"fo\ud800\udc00o"});
        check("(?<!%b{1,4})fo\ud800\udc00o", "%afo\ud800\udc00o",
              new String[] {"fo\ud800\udc00o"});
        check("(?<!%b{1,4}?)fo\ud800\udc00o", "%afo\ud800\udc00o",
              new String[] {"fo\ud800\udc00o"});
    }

    // This test is for 4938995
    // Check to see if weak region boundaries are transparent to
    // lookahead and lookbehind constructs
    @Test
    public static void boundsTest() {
        String fullMessage = "catdogcat";
        Pattern pattern = Pattern.compile("(?<=cat)dog(?=cat)");
        Matcher matcher = pattern.matcher("catdogca");
        matcher.useTransparentBounds(true);

        assertFalse(matcher.find());
        matcher.reset("atdogcat");

        assertFalse(matcher.find());
        matcher.reset(fullMessage);

        assertTrue(matcher.find());
        matcher.reset(fullMessage);
        matcher.region(0,9);

        assertTrue(matcher.find());
        matcher.reset(fullMessage);
        matcher.region(0,6);

        assertTrue(matcher.find());
        matcher.reset(fullMessage);
        matcher.region(3,6);

        assertTrue(matcher.find());
        matcher.useTransparentBounds(false);
        assertFalse(matcher.find());

        // Negative lookahead/lookbehind
        pattern = Pattern.compile("(?<!cat)dog(?!cat)");
        matcher = pattern.matcher("dogcat");
        matcher.useTransparentBounds(true);
        matcher.region(0,3);

        assertFalse(matcher.find());
        matcher.reset("catdog");
        matcher.region(3,6);

        assertFalse(matcher.find());
        matcher.useTransparentBounds(false);
        matcher.reset("dogcat");
        matcher.region(0,3);

        assertTrue(matcher.find());
        matcher.reset("catdog");
        matcher.region(3,6);
        assertTrue(matcher.find());

    }

    // This test is for 4945394
    @Test
    public static void findFromTest() {
        String message = "This is 40 $0 message.";
        Pattern pat = Pattern.compile("\\$0");
        Matcher match = pat.matcher(message);
        assertTrue(match.find());
        assertFalse(match.find());
        assertFalse(match.find());
    }

    // This test is for 4872664 and 4892980
    @Test
    public static void negatedCharClassTest() {
        Pattern pattern = Pattern.compile("[^>]");
        Matcher matcher = pattern.matcher("\u203A");
        assertTrue(matcher.matches());

        pattern = Pattern.compile("[^fr]");
        matcher = pattern.matcher("a");
        assertTrue(matcher.find());

        matcher.reset("\u203A");
        assertTrue(matcher.find());
        String s = "for";
        String[] result = s.split("[^fr]");
        assertEquals(result[0], "f");
        assertEquals(result[1], "r");
        s = "f\u203Ar";
        result = s.split("[^fr]");
        assertEquals(result[0], "f");
        assertEquals(result[1], "r");

        // Test adding to bits, subtracting a node, then adding to bits again
        pattern = Pattern.compile("[^f\u203Ar]");
        matcher = pattern.matcher("a");
        assertTrue(matcher.find());
        matcher.reset("f");
        assertFalse(matcher.find());
        matcher.reset("\u203A");
        assertFalse(matcher.find());
        matcher.reset("r");
        assertFalse(matcher.find());
        matcher.reset("\u203B");
        assertTrue(matcher.find());

        // Test subtracting a node, adding to bits, subtracting again
        pattern = Pattern.compile("[^\u203Ar\u203B]");
        matcher = pattern.matcher("a");
        assertTrue(matcher.find());
        matcher.reset("\u203A");
        assertFalse(matcher.find());
        matcher.reset("r");
        assertFalse(matcher.find());
        matcher.reset("\u203B");
        assertFalse(matcher.find());
        matcher.reset("\u203C");
        assertTrue(matcher.find());
    }

    // This test is for 4628291
    @Test
    public static void toStringTest() {
        Pattern pattern = Pattern.compile("b+");
        assertEquals(pattern.toString(), "b+");
        Matcher matcher = pattern.matcher("aaabbbccc");
        String matcherString = matcher.toString(); // unspecified
        matcher.find();
        matcher.toString(); // unspecified
        matcher.region(0,3);
        matcher.toString(); // unspecified
        matcher.reset();
        matcher.toString(); // unspecified
    }

    // This test is for 4808962
    @Test
    public static void literalPatternTest() {
        int flags = Pattern.LITERAL;

        Pattern pattern = Pattern.compile("abc\\t$^", flags);
        check(pattern, "abc\\t$^", true);

        pattern = Pattern.compile(Pattern.quote("abc\\t$^"));
        check(pattern, "abc\\t$^", true);

        pattern = Pattern.compile("\\Qa^$bcabc\\E", flags);
        check(pattern, "\\Qa^$bcabc\\E", true);
        check(pattern, "a^$bcabc", false);

        pattern = Pattern.compile("\\\\Q\\\\E");
        check(pattern, "\\Q\\E", true);

        pattern = Pattern.compile("\\Qabc\\Eefg\\\\Q\\\\Ehij");
        check(pattern, "abcefg\\Q\\Ehij", true);

        pattern = Pattern.compile("\\\\\\Q\\\\E");
        check(pattern, "\\\\\\\\", true);

        pattern = Pattern.compile(Pattern.quote("\\Qa^$bcabc\\E"));
        check(pattern, "\\Qa^$bcabc\\E", true);
        check(pattern, "a^$bcabc", false);

        pattern = Pattern.compile(Pattern.quote("\\Qabc\\Edef"));
        check(pattern, "\\Qabc\\Edef", true);
        check(pattern, "abcdef", false);

        pattern = Pattern.compile(Pattern.quote("abc\\Edef"));
        check(pattern, "abc\\Edef", true);
        check(pattern, "abcdef", false);

        pattern = Pattern.compile(Pattern.quote("\\E"));
        check(pattern, "\\E", true);

        pattern = Pattern.compile("((((abc.+?:)", flags);
        check(pattern, "((((abc.+?:)", true);

        flags |= Pattern.MULTILINE;

        pattern = Pattern.compile("^cat$", flags);
        check(pattern, "abc^cat$def", true);
        check(pattern, "cat", false);

        flags |= Pattern.CASE_INSENSITIVE;

        pattern = Pattern.compile("abcdef", flags);
        check(pattern, "ABCDEF", true);
        check(pattern, "AbCdEf", true);

        flags |= Pattern.DOTALL;

        pattern = Pattern.compile("a...b", flags);
        check(pattern, "A...b", true);
        check(pattern, "Axxxb", false);

        flags |= Pattern.CANON_EQ;

        //Note: Possible issue
        Pattern p = Pattern.compile("testa\u030a", flags);
        check(pattern, "testa\u030a", false);
        check(pattern, "test\u00e5", false);

        // Supplementary character test
        flags = Pattern.LITERAL;

        pattern = Pattern.compile(toSupplementaries("abc\\t$^"), flags);
        check(pattern, toSupplementaries("abc\\t$^"), true);

        pattern = Pattern.compile(Pattern.quote(toSupplementaries("abc\\t$^")));
        check(pattern, toSupplementaries("abc\\t$^"), true);

        pattern = Pattern.compile(toSupplementaries("\\Qa^$bcabc\\E"), flags);
        check(pattern, toSupplementaries("\\Qa^$bcabc\\E"), true);
        check(pattern, toSupplementaries("a^$bcabc"), false);

        pattern = Pattern.compile(Pattern.quote(toSupplementaries("\\Qa^$bcabc\\E")));
        check(pattern, toSupplementaries("\\Qa^$bcabc\\E"), true);
        check(pattern, toSupplementaries("a^$bcabc"), false);

        pattern = Pattern.compile(Pattern.quote(toSupplementaries("\\Qabc\\Edef")));
        check(pattern, toSupplementaries("\\Qabc\\Edef"), true);
        check(pattern, toSupplementaries("abcdef"), false);

        pattern = Pattern.compile(Pattern.quote(toSupplementaries("abc\\Edef")));
        check(pattern, toSupplementaries("abc\\Edef"), true);
        check(pattern, toSupplementaries("abcdef"), false);

        pattern = Pattern.compile(toSupplementaries("((((abc.+?:)"), flags);
        check(pattern, toSupplementaries("((((abc.+?:)"), true);

        flags |= Pattern.MULTILINE;

        pattern = Pattern.compile(toSupplementaries("^cat$"), flags);
        check(pattern, toSupplementaries("abc^cat$def"), true);
        check(pattern, toSupplementaries("cat"), false);

        flags |= Pattern.DOTALL;

        // note: this is case-sensitive.
        pattern = Pattern.compile(toSupplementaries("a...b"), flags);
        check(pattern, toSupplementaries("a...b"), true);
        check(pattern, toSupplementaries("axxxb"), false);

        flags |= Pattern.CANON_EQ;

        String t = toSupplementaries("test");
        //Note: Possible issue
        p = Pattern.compile(t + "a\u030a", flags);
        check(pattern, t + "a\u030a", false);
        check(pattern, t + "\u00e5", false);
    }

    // This test is for 4803179
    // This test is also for 4808962, replacement parts
    @Test
    public static void literalReplacementTest() {
        int flags = Pattern.LITERAL;

        Pattern pattern = Pattern.compile("abc", flags);
        Matcher matcher = pattern.matcher("zzzabczzz");
        String replaceTest = "$0";
        String result = matcher.replaceAll(replaceTest);
        assertEquals(result, "zzzabczzz");

        matcher.reset();
        String literalReplacement = Matcher.quoteReplacement(replaceTest);
        result = matcher.replaceAll(literalReplacement);
        assertEquals(result, "zzz$0zzz");

        matcher.reset();
        replaceTest = "\\t$\\$";
        literalReplacement = Matcher.quoteReplacement(replaceTest);
        result = matcher.replaceAll(literalReplacement);
        assertEquals(result, "zzz\\t$\\$zzz");

        // Supplementary character test
        pattern = Pattern.compile(toSupplementaries("abc"), flags);
        matcher = pattern.matcher(toSupplementaries("zzzabczzz"));
        replaceTest = "$0";
        result = matcher.replaceAll(replaceTest);
        assertEquals(result, toSupplementaries("zzzabczzz"));

        matcher.reset();
        literalReplacement = Matcher.quoteReplacement(replaceTest);
        result = matcher.replaceAll(literalReplacement);
        assertEquals(result, toSupplementaries("zzz$0zzz"));

        matcher.reset();
        replaceTest = "\\t$\\$";
        literalReplacement = Matcher.quoteReplacement(replaceTest);
        result = matcher.replaceAll(literalReplacement);
        assertEquals(result, toSupplementaries("zzz\\t$\\$zzz"));

        // IAE should be thrown if backslash or '$' is the last character
        // in replacement string
        assertThrows(IllegalArgumentException.class, () -> "\uac00".replaceAll("\uac00", "$"));
        assertThrows(IllegalArgumentException.class, () -> "\uac00".replaceAll("\uac00", "\\"));
    }

    // This test is for 4757029
    @Test
    public static void regionTest() {
        Pattern pattern = Pattern.compile("abc");
        Matcher matcher = pattern.matcher("abcdefabc");

        matcher.region(0,9);
        assertTrue(matcher.find());
        assertTrue(matcher.find());
        matcher.region(0,3);
        assertTrue(matcher.find());
        matcher.region(3,6);
        assertFalse(matcher.find());
        matcher.region(0,2);
        assertFalse(matcher.find());

        expectRegionFail(matcher, 1, -1);
        expectRegionFail(matcher, -1, -1);
        expectRegionFail(matcher, -1, 1);
        expectRegionFail(matcher, 5, 3);
        expectRegionFail(matcher, 5, 12);
        expectRegionFail(matcher, 12, 12);

        pattern = Pattern.compile("^abc$");
        matcher = pattern.matcher("zzzabczzz");
        matcher.region(0,9);
        assertFalse(matcher.find());
        matcher.region(3,6);
        assertTrue(matcher.find());
        matcher.region(3,6);
        matcher.useAnchoringBounds(false);
        assertFalse(matcher.find());

        // Supplementary character test
        pattern = Pattern.compile(toSupplementaries("abc"));
        matcher = pattern.matcher(toSupplementaries("abcdefabc"));
        matcher.region(0,9*2);
        assertTrue(matcher.find());
        assertTrue(matcher.find());
        matcher.region(0,3*2);
        assertTrue(matcher.find());
        matcher.region(1,3*2);
        assertFalse(matcher.find());
        matcher.region(3*2,6*2);
        assertFalse(matcher.find());
        matcher.region(0,2*2);
        assertFalse(matcher.find());
        matcher.region(0,2*2+1);
        assertFalse(matcher.find());

        expectRegionFail(matcher, 2, -1);
        expectRegionFail(matcher, -1, -1);
        expectRegionFail(matcher, -1, 2);
        expectRegionFail(matcher, 5*2, 3*2);
        expectRegionFail(matcher, 5*2, 12*2);
        expectRegionFail(matcher, 12*2, 12*2);

        pattern = Pattern.compile(toSupplementaries("^abc$"));
        matcher = pattern.matcher(toSupplementaries("zzzabczzz"));
        matcher.region(0,9*2);
        assertFalse(matcher.find());
        matcher.region(3*2,6*2);
        assertTrue(matcher.find());
        matcher.region(3*2+1,6*2);
        assertFalse(matcher.find());
        matcher.region(3*2,6*2-1);
        assertFalse(matcher.find());
        matcher.region(3*2,6*2);
        matcher.useAnchoringBounds(false);
        assertFalse(matcher.find());

        // JDK-8230829
        pattern = Pattern.compile("\\ud800\\udc61");
        matcher = pattern.matcher("\ud800\udc61");
        matcher.region(0, 1);
        assertFalse(matcher.find(), "Matched a surrogate pair" +
                " that crosses border of region");

        assertTrue(matcher.hitEnd(), "Expected to hit the end when" +
                " matching a surrogate pair crossing region");
    }

    private static void expectRegionFail(Matcher matcher, int index1,
                                         int index2)
    {

        try {
            matcher.region(index1, index2);
            fail();
        } catch (IndexOutOfBoundsException | IllegalStateException ioobe) {
            // Correct result
        } catch (Exception e) {
            fail();
        }
    }

    // This test is for 4803197
    @Test
    public static void escapedSegmentTest() {

        Pattern pattern = Pattern.compile("\\Qdir1\\dir2\\E");
        check(pattern, "dir1\\dir2", true);

        pattern = Pattern.compile("\\Qdir1\\dir2\\\\E");
        check(pattern, "dir1\\dir2\\", true);

        pattern = Pattern.compile("(\\Qdir1\\dir2\\\\E)");
        check(pattern, "dir1\\dir2\\", true);

        // Supplementary character test
        pattern = Pattern.compile(toSupplementaries("\\Qdir1\\dir2\\E"));
        check(pattern, toSupplementaries("dir1\\dir2"), true);

        pattern = Pattern.compile(toSupplementaries("\\Qdir1\\dir2")+"\\\\E");
        check(pattern, toSupplementaries("dir1\\dir2\\"), true);

        pattern = Pattern.compile(toSupplementaries("(\\Qdir1\\dir2")+"\\\\E)");
        check(pattern, toSupplementaries("dir1\\dir2\\"), true);
    }

    // This test is for 4792284
    @Test
    public static void nonCaptureRepetitionTest() {
        String input = "abcdefgh;";

        String[] patterns = new String[] {
            "(?:\\w{4})+;",
            "(?:\\w{8})*;",
            "(?:\\w{2}){2,4};",
            "(?:\\w{4}){2,};",   // only matches the
            ".*?(?:\\w{5})+;",   //     specified minimum
            ".*?(?:\\w{9})*;",   //     number of reps - OK
            "(?:\\w{4})+?;",     // lazy repetition - OK
            "(?:\\w{4})++;",     // possessive repetition - OK
            "(?:\\w{2,}?)+;",    // non-deterministic - OK
            "(\\w{4})+;",        // capturing group - OK
        };

        for (String pattern : patterns) {
            // Check find()
            check(pattern, 0, input, input, true);
            // Check matches()
            Pattern p = Pattern.compile(pattern);
            Matcher m = p.matcher(input);

            assertTrue(m.matches());
            assertEquals(m.group(0), input);
        }
    }

    // This test is for 6358731
    @Test
    public static void notCapturedGroupCurlyMatchTest() {
        Pattern pattern = Pattern.compile("(abc)+|(abcd)+");
        Matcher matcher = pattern.matcher("abcd");

        boolean condition = !matcher.matches() ||
             matcher.group(1) != null ||
             !matcher.group(2).equals("abcd");

        assertFalse(condition);
    }

    // This test is for 4706545
    @Test
    public static void javaCharClassTest() {
        for (int i=0; i<1000; i++) {
            char c = (char)generator.nextInt();
            check("{javaLowerCase}", c, Character.isLowerCase(c));
            check("{javaUpperCase}", c, Character.isUpperCase(c));
            check("{javaUpperCase}+", c, Character.isUpperCase(c));
            check("{javaTitleCase}", c, Character.isTitleCase(c));
            check("{javaDigit}", c, Character.isDigit(c));
            check("{javaDefined}", c, Character.isDefined(c));
            check("{javaLetter}", c, Character.isLetter(c));
            check("{javaLetterOrDigit}", c, Character.isLetterOrDigit(c));
            check("{javaJavaIdentifierStart}", c,
                  Character.isJavaIdentifierStart(c));
            check("{javaJavaIdentifierPart}", c,
                  Character.isJavaIdentifierPart(c));
            check("{javaUnicodeIdentifierStart}", c,
                  Character.isUnicodeIdentifierStart(c));
            check("{javaUnicodeIdentifierPart}", c,
                  Character.isUnicodeIdentifierPart(c));
            check("{javaIdentifierIgnorable}", c,
                  Character.isIdentifierIgnorable(c));
            check("{javaSpaceChar}", c, Character.isSpaceChar(c));
            check("{javaWhitespace}", c, Character.isWhitespace(c));
            check("{javaISOControl}", c, Character.isISOControl(c));
            check("{javaMirrored}", c, Character.isMirrored(c));

        }

        // Supplementary character test
        for (int i=0; i<1000; i++) {
            int c = generator.nextInt(Character.MAX_CODE_POINT
                                      - Character.MIN_SUPPLEMENTARY_CODE_POINT)
                        + Character.MIN_SUPPLEMENTARY_CODE_POINT;
            check("{javaLowerCase}", c, Character.isLowerCase(c));
            check("{javaUpperCase}", c, Character.isUpperCase(c));
            check("{javaUpperCase}+", c, Character.isUpperCase(c));
            check("{javaTitleCase}", c, Character.isTitleCase(c));
            check("{javaDigit}", c, Character.isDigit(c));
            check("{javaDefined}", c, Character.isDefined(c));
            check("{javaLetter}", c, Character.isLetter(c));
            check("{javaLetterOrDigit}", c, Character.isLetterOrDigit(c));
            check("{javaJavaIdentifierStart}", c,
                  Character.isJavaIdentifierStart(c));
            check("{javaJavaIdentifierPart}", c,
                  Character.isJavaIdentifierPart(c));
            check("{javaUnicodeIdentifierStart}", c,
                  Character.isUnicodeIdentifierStart(c));
            check("{javaUnicodeIdentifierPart}", c,
                  Character.isUnicodeIdentifierPart(c));
            check("{javaIdentifierIgnorable}", c,
                  Character.isIdentifierIgnorable(c));
            check("{javaSpaceChar}", c, Character.isSpaceChar(c));
            check("{javaWhitespace}", c, Character.isWhitespace(c));
            check("{javaISOControl}", c, Character.isISOControl(c));
            check("{javaMirrored}", c, Character.isMirrored(c));
        }
    }

    // This test is for 4523620
    /*
    private static void numOccurrencesTest() throws Exception {
        Pattern pattern = Pattern.compile("aaa");

        if (pattern.numOccurrences("aaaaaa", false) != 2)
            failCount++;
        if (pattern.numOccurrences("aaaaaa", true) != 4)
            failCount++;

        pattern = Pattern.compile("^");
        if (pattern.numOccurrences("aaaaaa", false) != 1)
            failCount++;
        if (pattern.numOccurrences("aaaaaa", true) != 1)
            failCount++;

        report("Number of Occurrences");
    }
    */

    // This test is for 4776374
    @Test
    public static void caretBetweenTerminatorsTest() {
        int flags1 = Pattern.DOTALL;
        int flags2 = Pattern.DOTALL | Pattern.UNIX_LINES;
        int flags3 = Pattern.DOTALL | Pattern.UNIX_LINES | Pattern.MULTILINE;
        int flags4 = Pattern.DOTALL | Pattern.MULTILINE;

        check("^....", flags1, "test\ntest", "test", true);
        check(".....^", flags1, "test\ntest", "test", false);
        check(".....^", flags1, "test\n", "test", false);
        check("....^", flags1, "test\r\n", "test", false);

        check("^....", flags2, "test\ntest", "test", true);
        check("....^", flags2, "test\ntest", "test", false);
        check(".....^", flags2, "test\n", "test", false);
        check("....^", flags2, "test\r\n", "test", false);

        check("^....", flags3, "test\ntest", "test", true);
        check(".....^", flags3, "test\ntest", "test\n", true);
        check(".....^", flags3, "test\u0085test", "test\u0085", false);
        check(".....^", flags3, "test\n", "test", false);
        check(".....^", flags3, "test\r\n", "test", false);
        check("......^", flags3, "test\r\ntest", "test\r\n", true);

        check("^....", flags4, "test\ntest", "test", true);
        check(".....^", flags3, "test\ntest", "test\n", true);
        check(".....^", flags4, "test\u0085test", "test\u0085", true);
        check(".....^", flags4, "test\n", "test\n", false);
        check(".....^", flags4, "test\r\n", "test\r", false);

        // Supplementary character test
        String t = toSupplementaries("test");
        check("^....", flags1, t+"\n"+t, t, true);
        check(".....^", flags1, t+"\n"+t, t, false);
        check(".....^", flags1, t+"\n", t, false);
        check("....^", flags1, t+"\r\n", t, false);

        check("^....", flags2, t+"\n"+t, t, true);
        check("....^", flags2, t+"\n"+t, t, false);
        check(".....^", flags2, t+"\n", t, false);
        check("....^", flags2, t+"\r\n", t, false);

        check("^....", flags3, t+"\n"+t, t, true);
        check(".....^", flags3, t+"\n"+t, t+"\n", true);
        check(".....^", flags3, t+"\u0085"+t, t+"\u0085", false);
        check(".....^", flags3, t+"\n", t, false);
        check(".....^", flags3, t+"\r\n", t, false);
        check("......^", flags3, t+"\r\n"+t, t+"\r\n", true);

        check("^....", flags4, t+"\n"+t, t, true);
        check(".....^", flags3, t+"\n"+t, t+"\n", true);
        check(".....^", flags4, t+"\u0085"+t, t+"\u0085", true);
        check(".....^", flags4, t+"\n", t+"\n", false);
        check(".....^", flags4, t+"\r\n", t+"\r", false);
    }

    // This test is for 4727935
    @Test
    public static void dollarAtEndTest() {
        int flags1 = Pattern.DOTALL;
        int flags2 = Pattern.DOTALL | Pattern.UNIX_LINES;
        int flags3 = Pattern.DOTALL | Pattern.MULTILINE;

        check("....$", flags1, "test\n", "test", true);
        check("....$", flags1, "test\r\n", "test", true);
        check(".....$", flags1, "test\n", "test\n", true);
        check(".....$", flags1, "test\u0085", "test\u0085", true);
        check("....$", flags1, "test\u0085", "test", true);

        check("....$", flags2, "test\n", "test", true);
        check(".....$", flags2, "test\n", "test\n", true);
        check(".....$", flags2, "test\u0085", "test\u0085", true);
        check("....$", flags2, "test\u0085", "est\u0085", true);

        check("....$.blah", flags3, "test\nblah", "test\nblah", true);
        check(".....$.blah", flags3, "test\n\nblah", "test\n\nblah", true);
        check("....$blah", flags3, "test\nblah", "!!!!", false);
        check(".....$blah", flags3, "test\nblah", "!!!!", false);

        // Supplementary character test
        String t = toSupplementaries("test");
        String b = toSupplementaries("blah");
        check("....$", flags1, t+"\n", t, true);
        check("....$", flags1, t+"\r\n", t, true);
        check(".....$", flags1, t+"\n", t+"\n", true);
        check(".....$", flags1, t+"\u0085", t+"\u0085", true);
        check("....$", flags1, t+"\u0085", t, true);

        check("....$", flags2, t+"\n", t, true);
        check(".....$", flags2, t+"\n", t+"\n", true);
        check(".....$", flags2, t+"\u0085", t+"\u0085", true);
        check("....$", flags2, t+"\u0085", toSupplementaries("est\u0085"), true);

        check("....$."+b, flags3, t+"\n"+b, t+"\n"+b, true);
        check(".....$."+b, flags3, t+"\n\n"+b, t+"\n\n"+b, true);
        check("....$"+b, flags3, t+"\n"+b, "!!!!", false);
        check(".....$"+b, flags3, t+"\n"+b, "!!!!", false);
    }

    // This test is for 4711773
    @Test
    public static void multilineDollarTest() {
        Pattern findCR = Pattern.compile("$", Pattern.MULTILINE);
        Matcher matcher = findCR.matcher("first bit\nsecond bit");
        matcher.find();
        assertEquals(matcher.start(), 9);
        matcher.find();
        assertEquals(matcher.start(0), 20);

        // Supplementary character test
        matcher = findCR.matcher(toSupplementaries("first  bit\n second  bit")); // double BMP chars
        matcher.find();
        assertEquals(matcher.start(0), 9*2);
        matcher.find();
        assertEquals(matcher.start(0), 20*2);
    }

    @Test
    public static void reluctantRepetitionTest() {
        Pattern p = Pattern.compile("1(\\s\\S+?){1,3}?[\\s,]2");
        check(p, "1 word word word 2", true);
        check(p, "1 wor wo w 2", true);
        check(p, "1 word word 2", true);
        check(p, "1 word 2", true);
        check(p, "1 wo w w 2", true);
        check(p, "1 wo w 2", true);
        check(p, "1 wor w 2", true);

        p = Pattern.compile("([a-z])+?c");
        Matcher m = p.matcher("ababcdefdec");
        check(m, "ababc");

        // Supplementary character test
        p = Pattern.compile(toSupplementaries("([a-z])+?c"));
        m = p.matcher(toSupplementaries("ababcdefdec"));
        check(m, toSupplementaries("ababc"));
    }

    public static Pattern serializedPattern(Pattern p) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(p);
        oos.close();
        try (ObjectInputStream ois = new ObjectInputStream(
                new ByteArrayInputStream(baos.toByteArray()))) {
            return (Pattern)ois.readObject();
        }
    }

    @Test
    public static void serializeTest() throws Exception {
        String patternStr = "(b)";
        String matchStr = "b";
        Pattern pattern = Pattern.compile(patternStr);
        Pattern serializedPattern = serializedPattern(pattern);
        Matcher matcher = serializedPattern.matcher(matchStr);
        assertTrue(matcher.matches());
        assertEquals(matcher.groupCount(), 1);

        pattern = Pattern.compile("a(?-i)b", Pattern.CASE_INSENSITIVE);
        serializedPattern = serializedPattern(pattern);
        assertTrue(serializedPattern.matcher("Ab").matches());
        assertFalse(serializedPattern.matcher("AB").matches());
    }

    @Test
    public static void gTest() {
        Pattern pattern = Pattern.compile("\\G\\w");
        Matcher matcher = pattern.matcher("abc#x#x");
        matcher.find();
        matcher.find();
        matcher.find();
        assertFalse(matcher.find());

        pattern = Pattern.compile("\\GA*");
        matcher = pattern.matcher("1A2AA3");
        matcher.find();
        assertFalse(matcher.find());

        pattern = Pattern.compile("\\GA*");
        matcher = pattern.matcher("1A2AA3");
        assertTrue(matcher.find(1));
        matcher.find();
        assertFalse(matcher.find());
    }

    @Test
    public static void zTest() {
        Pattern pattern = Pattern.compile("foo\\Z");
        // Positives
        check(pattern, "foo\u0085", true);
        check(pattern, "foo\u2028", true);
        check(pattern, "foo\u2029", true);
        check(pattern, "foo\n", true);
        check(pattern, "foo\r", true);
        check(pattern, "foo\r\n", true);
        // Negatives
        check(pattern, "fooo", false);
        check(pattern, "foo\n\r", false);

        pattern = Pattern.compile("foo\\Z", Pattern.UNIX_LINES);
        // Positives
        check(pattern, "foo", true);
        check(pattern, "foo\n", true);
        // Negatives
        check(pattern, "foo\r", false);
        check(pattern, "foo\u0085", false);
        check(pattern, "foo\u2028", false);
        check(pattern, "foo\u2029", false);
    }

    @Test
    public static void replaceFirstTest() {
        Pattern pattern = Pattern.compile("(ab)(c*)");
        Matcher matcher = pattern.matcher("abccczzzabcczzzabccc");
        assertEquals(matcher.replaceFirst("test"), "testzzzabcczzzabccc");

        matcher.reset("zzzabccczzzabcczzzabccczzz");
        assertEquals(matcher.replaceFirst("test"), "zzztestzzzabcczzzabccczzz");

        matcher.reset("zzzabccczzzabcczzzabccczzz");
        String result = matcher.replaceFirst("$1");
        assertEquals(result,"zzzabzzzabcczzzabccczzz");

        matcher.reset("zzzabccczzzabcczzzabccczzz");
        result = matcher.replaceFirst("$2");
        assertEquals(result, "zzzccczzzabcczzzabccczzz");

        pattern = Pattern.compile("a*");
        matcher = pattern.matcher("aaaaaaaaaa");
        assertEquals(matcher.replaceFirst("test"), "test");

        pattern = Pattern.compile("a+");
        matcher = pattern.matcher("zzzaaaaaaaaaa");
        assertEquals(matcher.replaceFirst("test"), "zzztest");

        // Supplementary character test
        pattern = Pattern.compile(toSupplementaries("(ab)(c*)"));
        matcher = pattern.matcher(toSupplementaries("abccczzzabcczzzabccc"));
        result = matcher.replaceFirst(toSupplementaries("test"));
        assertEquals(result, toSupplementaries("testzzzabcczzzabccc"));

        matcher.reset(toSupplementaries("zzzabccczzzabcczzzabccczzz"));
        result = matcher.replaceFirst(toSupplementaries("test"));
        assertEquals(result, toSupplementaries("zzztestzzzabcczzzabccczzz"));

        matcher.reset(toSupplementaries("zzzabccczzzabcczzzabccczzz"));
        result = matcher.replaceFirst("$1");
        assertEquals(result, toSupplementaries("zzzabzzzabcczzzabccczzz"));

        matcher.reset(toSupplementaries("zzzabccczzzabcczzzabccczzz"));
        result = matcher.replaceFirst("$2");
        assertEquals(result, toSupplementaries("zzzccczzzabcczzzabccczzz"));

        pattern = Pattern.compile(toSupplementaries("a*"));
        matcher = pattern.matcher(toSupplementaries("aaaaaaaaaa"));

        result = matcher.replaceFirst(toSupplementaries("test"));
        assertEquals(result,toSupplementaries("test"));

        pattern = Pattern.compile(toSupplementaries("a+"));
        matcher = pattern.matcher(toSupplementaries("zzzaaaaaaaaaa"));
        result = matcher.replaceFirst(toSupplementaries("test"));
        assertEquals(result, toSupplementaries("zzztest"));
    }

    @Test
    public static void unixLinesTest() {
        Pattern pattern = Pattern.compile(".*");
        Matcher matcher = pattern.matcher("aa\u2028blah");
        matcher.find();
        assertEquals(matcher.group(0), "aa");

        pattern = Pattern.compile(".*", Pattern.UNIX_LINES);
        matcher = pattern.matcher("aa\u2028blah");
        matcher.find();
        assertEquals(matcher.group(0), "aa\u2028blah");

        pattern = Pattern.compile("[az]$",
                                  Pattern.MULTILINE | Pattern.UNIX_LINES);
        matcher = pattern.matcher("aa\u2028zz");
        check(matcher, "a\u2028", false);

        // Supplementary character test
        pattern = Pattern.compile(".*");
        matcher = pattern.matcher(toSupplementaries("aa\u2028blah"));
        matcher.find();
        assertEquals(matcher.group(0), toSupplementaries("aa"));

        pattern = Pattern.compile(".*", Pattern.UNIX_LINES);
        matcher = pattern.matcher(toSupplementaries("aa\u2028blah"));
        matcher.find();
        assertEquals(matcher.group(0), toSupplementaries("aa\u2028blah"));

        pattern = Pattern.compile(toSupplementaries("[az]$"),
                                  Pattern.MULTILINE | Pattern.UNIX_LINES);
        matcher = pattern.matcher(toSupplementaries("aa\u2028zz"));
        check(matcher, toSupplementaries("a\u2028"), false);
    }

    @Test
    public static void commentsTest() {
        int flags = Pattern.COMMENTS;

        Pattern pattern = Pattern.compile("aa \\# aa", flags);
        Matcher matcher = pattern.matcher("aa#aa");
        assertTrue(matcher.matches());

        pattern = Pattern.compile("aa  # blah", flags);
        matcher = pattern.matcher("aa");
        assertTrue(matcher.matches());

        pattern = Pattern.compile("aa blah", flags);
        matcher = pattern.matcher("aablah");
        assertTrue(matcher.matches());

        pattern = Pattern.compile("aa  # blah blech  ", flags);
        matcher = pattern.matcher("aa");
        assertTrue(matcher.matches());

        pattern = Pattern.compile("aa  # blah\n  ", flags);
        matcher = pattern.matcher("aa");
        assertTrue(matcher.matches());

        pattern = Pattern.compile("aa  # blah\nbc # blech", flags);
        matcher = pattern.matcher("aabc");
        assertTrue(matcher.matches());

        pattern = Pattern.compile("aa  # blah\nbc# blech", flags);
        matcher = pattern.matcher("aabc");
        assertTrue(matcher.matches());

        pattern = Pattern.compile("aa  # blah\nbc\\# blech", flags);
        matcher = pattern.matcher("aabc#blech");
        assertTrue(matcher.matches());

        // Supplementary character test
        pattern = Pattern.compile(toSupplementaries("aa \\# aa"), flags);
        matcher = pattern.matcher(toSupplementaries("aa#aa"));
        assertTrue(matcher.matches());

        pattern = Pattern.compile(toSupplementaries("aa  # blah"), flags);
        matcher = pattern.matcher(toSupplementaries("aa"));
        assertTrue(matcher.matches());

        pattern = Pattern.compile(toSupplementaries("aa blah"), flags);
        matcher = pattern.matcher(toSupplementaries("aablah"));
        assertTrue(matcher.matches());

        pattern = Pattern.compile(toSupplementaries("aa  # blah blech  "), flags);
        matcher = pattern.matcher(toSupplementaries("aa"));
        assertTrue(matcher.matches());

        pattern = Pattern.compile(toSupplementaries("aa  # blah\n  "), flags);
        matcher = pattern.matcher(toSupplementaries("aa"));
        assertTrue(matcher.matches());

        pattern = Pattern.compile(toSupplementaries("aa  # blah\nbc # blech"), flags);
        matcher = pattern.matcher(toSupplementaries("aabc"));
        assertTrue(matcher.matches());

        pattern = Pattern.compile(toSupplementaries("aa  # blah\nbc# blech"), flags);
        matcher = pattern.matcher(toSupplementaries("aabc"));
        assertTrue(matcher.matches());

        pattern = Pattern.compile(toSupplementaries("aa  # blah\nbc\\# blech"), flags);
        matcher = pattern.matcher(toSupplementaries("aabc#blech"));
        assertTrue(matcher.matches());
    }

    @Test
    public static void caseFoldingTest() { // bug 4504687
        int flags = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        Pattern pattern = Pattern.compile("aa", flags);
        Matcher matcher = pattern.matcher("ab");
        assertFalse(matcher.matches());

        pattern = Pattern.compile("aA", flags);
        matcher = pattern.matcher("ab");
        assertFalse(matcher.matches());

        pattern = Pattern.compile("aa", flags);
        matcher = pattern.matcher("aB");
        assertFalse(matcher.matches());

        matcher = pattern.matcher("Ab");
        assertFalse(matcher.matches());

        // ASCII               "a"
        // Latin-1 Supplement  "a" + grave
        // Cyrillic            "a"
        String[] patterns = new String[] {
            //single
            "a", "\u00e0", "\u0430",
            //slice
            "ab", "\u00e0\u00e1", "\u0430\u0431",
            //class single
            "[a]", "[\u00e0]", "[\u0430]",
            //class range
            "[a-b]", "[\u00e0-\u00e5]", "[\u0430-\u0431]",
            //back reference
            "(a)\\1", "(\u00e0)\\1", "(\u0430)\\1"
        };

        String[] texts = new String[] {
            "A", "\u00c0", "\u0410",
            "AB", "\u00c0\u00c1", "\u0410\u0411",
            "A", "\u00c0", "\u0410",
            "B", "\u00c2", "\u0411",
            "aA", "\u00e0\u00c0", "\u0430\u0410"
        };

        boolean[] expected = new boolean[] {
            true, false, false,
            true, false, false,
            true, false, false,
            true, false, false,
            true, false, false
        };

        flags = Pattern.CASE_INSENSITIVE;
        for (int i = 0; i < patterns.length; i++) {
            pattern = Pattern.compile(patterns[i], flags);
            matcher = pattern.matcher(texts[i]);
            assertEquals(matcher.matches(), expected[i], "<1> Failed at " + i);
        }

        flags = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        for (int i = 0; i < patterns.length; i++) {
            pattern = Pattern.compile(patterns[i], flags);
            matcher = pattern.matcher(texts[i]);
            assertTrue(matcher.matches(), "<2> Failed at " + i);
        }
        // flag unicode_case alone should do nothing
        flags = Pattern.UNICODE_CASE;
        for (int i = 0; i < patterns.length; i++) {
            pattern = Pattern.compile(patterns[i], flags);
            matcher = pattern.matcher(texts[i]);
            assertFalse(matcher.matches(), "<3> Failed at " + i);
        }

        // Special cases: i, I, u+0131 and u+0130
        flags = Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE;
        pattern = Pattern.compile("[h-j]+", flags);
        assertTrue(pattern.matcher("\u0131\u0130").matches());
    }

    @Test
    public static void appendTest() {
        Pattern pattern = Pattern.compile("(ab)(cd)");
        Matcher matcher = pattern.matcher("abcd");
        String result = matcher.replaceAll("$2$1");
        assertEquals(result, "cdab");

        String  s1 = "Swap all: first = 123, second = 456";
        String  s2 = "Swap one: first = 123, second = 456";
        String  r  = "$3$2$1";
        pattern = Pattern.compile("([a-z]+)( *= *)([0-9]+)");
        matcher = pattern.matcher(s1);

        result = matcher.replaceAll(r);
        assertEquals(result, "Swap all: 123 = first, 456 = second");

        matcher = pattern.matcher(s2);

        if (matcher.find()) {
            StringBuffer sb = new StringBuffer();
            matcher.appendReplacement(sb, r);
            matcher.appendTail(sb);
            result = sb.toString();
            assertEquals(result, "Swap one: 123 = first, second = 456");
        }

        // Supplementary character test
        pattern = Pattern.compile(toSupplementaries("(ab)(cd)"));
        matcher = pattern.matcher(toSupplementaries("abcd"));
        result = matcher.replaceAll("$2$1");
        assertEquals(result, toSupplementaries("cdab"));

        s1 = toSupplementaries("Swap all: first = 123, second = 456");
        s2 = toSupplementaries("Swap one: first = 123, second = 456");
        r  = toSupplementaries("$3$2$1");
        pattern = Pattern.compile(toSupplementaries("([a-z]+)( *= *)([0-9]+)"));
        matcher = pattern.matcher(s1);

        result = matcher.replaceAll(r);
        assertEquals(result, toSupplementaries("Swap all: 123 = first, 456 = second"));

        matcher = pattern.matcher(s2);

        if (matcher.find()) {
            StringBuffer sb = new StringBuffer();
            matcher.appendReplacement(sb, r);
            matcher.appendTail(sb);
            result = sb.toString();
            assertEquals(result, toSupplementaries("Swap one: 123 = first, second = 456"));
        }
    }

    @Test
    public static void splitTest() {
        Pattern pattern = Pattern.compile(":");
        String[] result = pattern.split("foo:and:boo", 2);
        assertEquals(result[0], "foo");
        assertEquals(result[1], "and:boo");
        // Supplementary character test
        Pattern patternX = Pattern.compile(toSupplementaries("X"));
        result = patternX.split(toSupplementaries("fooXandXboo"), 2);
        assertEquals(result[0], toSupplementaries("foo"));
        assertEquals(result[1], toSupplementaries("andXboo"));

        CharBuffer cb = CharBuffer.allocate(100);
        cb.put("foo:and:boo");
        cb.flip();
        result = pattern.split(cb);
        assertEquals(result[0], "foo");
        assertEquals(result[1], "and");
        assertEquals(result[2], "boo");

        // Supplementary character test
        CharBuffer cbs = CharBuffer.allocate(100);
        cbs.put(toSupplementaries("fooXandXboo"));
        cbs.flip();
        result = patternX.split(cbs);
        assertEquals(result[0], toSupplementaries("foo"));
        assertEquals(result[1], toSupplementaries("and"));
        assertEquals(result[2], toSupplementaries("boo"));

        String source = "0123456789";
        for (int limit=-2; limit<3; limit++) {
            for (int x=0; x<10; x++) {
                result = source.split(Integer.toString(x), limit);
                int expectedLength = limit < 1 ? 2 : limit;

                if ((limit == 0) && (x == 9)) {
                    // expected dropping of ""
                    assertEquals(result.length, 1);
                    assertEquals(result[0], "012345678");
                } else {
                    assertEquals(result.length, expectedLength);

                    if (!result[0].equals(source.substring(0,x))) {
                        assertEquals(limit, 1);
                        assertEquals(result[0], source.substring(0,10));
                    }
                    if (expectedLength > 1) { // Check segment 2
                        assertEquals(result[1], source.substring(x+1,10));
                    }
                }
            }
        }
        // Check the case for no match found
        for (int limit=-2; limit<3; limit++) {
            result = source.split("e", limit);
            assertEquals(result.length, 1);
            assertEquals(result[0], source);
        }
        // Check the case for limit == 0, source = "";
        // split() now returns 0-length for empty source "" see #6559590
        source = "";
        result = source.split("e", 0);
        assertEquals(result.length, 1);
        assertEquals(result[0], source);

        // Check both split() and splitAsStraem(), especially for zero-lenth
        // input and zero-lenth match cases
        String[][] input = new String[][] {
            { " ",           "Abc Efg Hij" },   // normal non-zero-match
            { " ",           " Abc Efg Hij" },  // leading empty str for non-zero-match
            { " ",           "Abc  Efg Hij" },  // non-zero-match in the middle
            { "(?=\\p{Lu})", "AbcEfgHij" },     // no leading empty str for zero-match
            { "(?=\\p{Lu})", "AbcEfg" },
            { "(?=\\p{Lu})", "Abc" },
            { " ",           "" },              // zero-length input
            { ".*",          "" },

            // some tests from PatternStreamTest.java
            { "4",       "awgqwefg1fefw4vssv1vvv1" },
            { "\u00a3a", "afbfq\u00a3abgwgb\u00a3awngnwggw\u00a3a\u00a3ahjrnhneerh" },
            { "1",       "awgqwefg1fefw4vssv1vvv1" },
            { "1",       "a\u4ebafg1fefw\u4eba4\u9f9cvssv\u9f9c1v\u672c\u672cvv" },
            { "\u56da",  "1\u56da23\u56da456\u56da7890" },
            { "\u56da",  "1\u56da23\u9f9c\u672c\u672c\u56da456\u56da\u9f9c\u672c7890" },
            { "\u56da",  "" },
            { "[ \t,:.]","This is,testing: with\tdifferent separators." }, //multiple septs
            { "o",       "boo:and:foo" },
            { "o",       "booooo:and:fooooo" },
            { "o",       "fooooo:" },
        };

        String[][] expected = new String[][] {
            { "Abc", "Efg", "Hij" },
            { "", "Abc", "Efg", "Hij" },
            { "Abc", "", "Efg", "Hij" },
            { "Abc", "Efg", "Hij" },
            { "Abc", "Efg" },
            { "Abc" },
            { "" },
            { "" },

            { "awgqwefg1fefw", "vssv1vvv1" },
            { "afbfq", "bgwgb", "wngnwggw", "", "hjrnhneerh" },
            { "awgqwefg", "fefw4vssv", "vvv" },
            { "a\u4ebafg", "fefw\u4eba4\u9f9cvssv\u9f9c", "v\u672c\u672cvv" },
            { "1", "23", "456", "7890" },
            { "1", "23\u9f9c\u672c\u672c", "456", "\u9f9c\u672c7890" },
            { "" },
            { "This", "is", "testing", "", "with", "different", "separators" },
            { "b", "", ":and:f" },
            { "b", "", "", "", "", ":and:f" },
            { "f", "", "", "", "", ":" },
        };
        for (int i = 0; i < input.length; i++) {
            pattern = Pattern.compile(input[i][0]);
            assertTrue(Arrays.equals(pattern.split(input[i][1]), expected[i]));

            assertFalse(input[i][1].length() > 0 &&  // splitAsStream() return empty resulting
                                             // array for zero-length input for now
                !Arrays.equals(pattern.splitAsStream(input[i][1]).toArray(),
                               expected[i]));
        }
    }

    @Test
    public static void negationTest() {
        Pattern pattern = Pattern.compile("[\\[@^]+");
        Matcher matcher = pattern.matcher("@@@@[[[[^^^^");
        assertTrue(matcher.find());
        assertEquals(matcher.group(0), "@@@@[[[[^^^^");

        pattern = Pattern.compile("[@\\[^]+");
        matcher = pattern.matcher("@@@@[[[[^^^^");
        assertTrue(matcher.find());
        assertEquals(matcher.group(0), "@@@@[[[[^^^^");

        pattern = Pattern.compile("[@\\[^@]+");
        matcher = pattern.matcher("@@@@[[[[^^^^");
        assertTrue(matcher.find());
        assertEquals(matcher.group(0), "@@@@[[[[^^^^");

        pattern = Pattern.compile("\\)");
        matcher = pattern.matcher("xxx)xxx");
        assertTrue(matcher.find());
    }

    @Test
    public static void ampersandTest() {
        Pattern pattern = Pattern.compile("[&@]+");
        check(pattern, "@@@@&&&&", true);

        pattern = Pattern.compile("[@&]+");
        check(pattern, "@@@@&&&&", true);

        pattern = Pattern.compile("[@\\&]+");
        check(pattern, "@@@@&&&&", true);
    }

    @Test
    public static void octalTest() {
        Pattern pattern = Pattern.compile("\\u0007");
        Matcher matcher = pattern.matcher("\u0007");
        assertTrue(matcher.matches());
        pattern = Pattern.compile("\\07");
        matcher = pattern.matcher("\u0007");
        assertTrue(matcher.matches());
        pattern = Pattern.compile("\\007");
        matcher = pattern.matcher("\u0007");
        assertTrue(matcher.matches());
        pattern = Pattern.compile("\\0007");
        matcher = pattern.matcher("\u0007");
        assertTrue(matcher.matches());
        pattern = Pattern.compile("\\040");
        matcher = pattern.matcher("\u0020");
        assertTrue(matcher.matches());
        pattern = Pattern.compile("\\0403");
        matcher = pattern.matcher("\u00203");
        assertTrue(matcher.matches());
        pattern = Pattern.compile("\\0103");
        matcher = pattern.matcher("\u0043");
        assertTrue(matcher.matches());
    }

    @Test
    public static void longPatternTest() {
        try {
            Pattern.compile(
                "a 32-character-long pattern xxxx");
            Pattern.compile("a 33-character-long pattern xxxxx");
            Pattern.compile("a thirty four character long regex");
            StringBuilder patternToBe = new StringBuilder(101);
            for (int i=0; i<100; i++)
                patternToBe.append((char)(97 + i%26));
            Pattern.compile(patternToBe.toString());
        } catch (PatternSyntaxException e) {
            fail();
        }

        // Supplementary character test
        try {
            Pattern.compile(
                toSupplementaries("a 32-character-long pattern xxxx"));
            Pattern.compile(toSupplementaries("a 33-character-long pattern xxxxx"));
            Pattern.compile(toSupplementaries("a thirty four character long regex"));
            StringBuilder patternToBe = new StringBuilder(101*2);
            for (int i=0; i<100; i++)
                patternToBe.append(Character.toChars(Character.MIN_SUPPLEMENTARY_CODE_POINT
                                                     + 97 + i%26));
            Pattern.compile(patternToBe.toString());
        } catch (PatternSyntaxException e) {
            fail();
        }
    }

    @Test
    public static void group0Test() {
        Pattern pattern = Pattern.compile("(tes)ting");
        Matcher matcher = pattern.matcher("testing");
        check(matcher, "testing");

        matcher.reset("testing");
        assertTrue(matcher.lookingAt());
        assertEquals(matcher.group(0), "testing");

        matcher.reset("testing");
        assertTrue(matcher.matches());
        assertEquals(matcher.group(0), "testing");

        pattern = Pattern.compile("(tes)ting");
        matcher = pattern.matcher("testing");
        assertTrue(matcher.lookingAt());
        assertEquals(matcher.group(0), "testing");

        pattern = Pattern.compile("^(tes)ting");
        matcher = pattern.matcher("testing");
        assertTrue(matcher.matches());
        assertEquals(matcher.group(0), "testing");

        // Supplementary character test
        pattern = Pattern.compile(toSupplementaries("(tes)ting"));
        matcher = pattern.matcher(toSupplementaries("testing"));
        check(matcher, toSupplementaries("testing"));

        matcher.reset(toSupplementaries("testing"));
        assertTrue(matcher.lookingAt());
        assertEquals(matcher.group(0), toSupplementaries("testing"));

        matcher.reset(toSupplementaries("testing"));
        assertTrue(matcher.matches());
        assertEquals(matcher.group(0), toSupplementaries("testing"));

        pattern = Pattern.compile(toSupplementaries("(tes)ting"));
        matcher = pattern.matcher(toSupplementaries("testing"));
        assertTrue(matcher.lookingAt());
        assertEquals(matcher.group(0), toSupplementaries("testing"));

        pattern = Pattern.compile(toSupplementaries("^(tes)ting"));
        matcher = pattern.matcher(toSupplementaries("testing"));

        assertTrue(matcher.matches());
        assertEquals(matcher.group(0), toSupplementaries("testing"));
    }

    @Test
    public static void findIntTest() {
        Pattern p = Pattern.compile("blah");
        Matcher m = p.matcher("zzzzblahzzzzzblah");
        boolean result = m.find(2);

        assertTrue(result);

        final Pattern p2 = Pattern.compile("$");
        final Matcher m2 = p2.matcher("1234567890");
        result = m2.find(10);
        assertTrue(result);
        assertThrows(IndexOutOfBoundsException.class, () -> m2.find(11));

        // Supplementary character test
        p = Pattern.compile(toSupplementaries("blah"));
        m = p.matcher(toSupplementaries("zzzzblahzzzzzblah"));
        result = m.find(2);
        assertTrue(result);
    }

    @Test
    public static void emptyPatternTest() {
        Pattern p = Pattern.compile("");
        final Matcher m = p.matcher("foo");

        // Should find empty pattern at beginning of input
        boolean result = m.find();
        assertTrue(result);
        assertEquals(m.start(), 0);

        // Should not match entire input if input is not empty
        m.reset();
        result = m.matches();
        assertFalse(result);

        assertThrows(IllegalStateException.class, () -> m.start(0));

        // Should match entire input if input is empty
        m.reset("");
        result = m.matches();
        assertTrue(result);

        result = Pattern.matches("", "");
        assertTrue(result);

        result = Pattern.matches("", "foo");
        assertFalse(result);
    }

    @Test
    public static void charClassTest() {
        Pattern pattern = Pattern.compile("blah[ab]]blech");
        check(pattern, "blahb]blech", true);

        pattern = Pattern.compile("[abc[def]]");
        check(pattern, "b", true);

        // Supplementary character tests
        pattern = Pattern.compile(toSupplementaries("blah[ab]]blech"));
        check(pattern, toSupplementaries("blahb]blech"), true);

        pattern = Pattern.compile(toSupplementaries("[abc[def]]"));
        check(pattern, toSupplementaries("b"), true);

        // u00ff when UNICODE_CASE
        pattern = Pattern.compile("[ab\u00ffcd]",
                                  Pattern.CASE_INSENSITIVE|
                                  Pattern.UNICODE_CASE);
        check(pattern, "ab\u00ffcd", true);
        check(pattern, "Ab\u0178Cd", true);

        // u00b5 when UNICODE_CASE
        pattern = Pattern.compile("[ab\u00b5cd]",
                                  Pattern.CASE_INSENSITIVE|
                                  Pattern.UNICODE_CASE);
        check(pattern, "ab\u00b5cd", true);
        check(pattern, "Ab\u039cCd", true);

        /* Special cases
           (1)LatinSmallLetterLongS u+017f
           (2)LatinSmallLetterDotlessI u+0131
           (3)LatineCapitalLetterIWithDotAbove u+0130
           (4)KelvinSign u+212a
           (5)AngstromSign u+212b
        */
        int flags = Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE;
        pattern = Pattern.compile("[sik\u00c5]+", flags);
        assertTrue(pattern.matcher("\u017f\u0130\u0131\u212a\u212b").matches());

    }

    @Test
    public static void caretTest() {
        Pattern pattern = Pattern.compile("\\w*");
        Matcher matcher = pattern.matcher("a#bc#def##g");
        check(matcher, "a");
        check(matcher, "");
        check(matcher, "bc");
        check(matcher, "");
        check(matcher, "def");
        check(matcher, "");
        check(matcher, "");
        check(matcher, "g");
        check(matcher, "");
        assertFalse(matcher.find());

        pattern = Pattern.compile("^\\w*");
        matcher = pattern.matcher("a#bc#def##g");
        check(matcher, "a");
        assertFalse(matcher.find());

        pattern = Pattern.compile("\\w");
        matcher = pattern.matcher("abc##x");
        check(matcher, "a");
        check(matcher, "b");
        check(matcher, "c");
        check(matcher, "x");
        assertFalse(matcher.find());

        pattern = Pattern.compile("^\\w");
        matcher = pattern.matcher("abc##x");
        check(matcher, "a");
        assertFalse(matcher.find());

        pattern = Pattern.compile("\\A\\p{Alpha}{3}");
        matcher = pattern.matcher("abcdef-ghi\njklmno");
        check(matcher, "abc");
        assertFalse(matcher.find());

        pattern = Pattern.compile("^\\p{Alpha}{3}", Pattern.MULTILINE);
        matcher = pattern.matcher("abcdef-ghi\njklmno");
        check(matcher, "abc");
        check(matcher, "jkl");
        assertFalse(matcher.find());

        pattern = Pattern.compile("^", Pattern.MULTILINE);
        matcher = pattern.matcher("this is some text");
        String result = matcher.replaceAll("X");
        assertEquals(result, "Xthis is some text");

        pattern = Pattern.compile("^");
        matcher = pattern.matcher("this is some text");
        result = matcher.replaceAll("X");
        assertEquals(result, "Xthis is some text");

        pattern = Pattern.compile("^", Pattern.MULTILINE | Pattern.UNIX_LINES);
        matcher = pattern.matcher("this is some text\n");
        result = matcher.replaceAll("X");
        assertEquals(result, "Xthis is some text\n");
    }

    @Test
    public static void groupCaptureTest() {
        // Independent group
        assertThrows(IndexOutOfBoundsException.class, () -> {
                    Pattern pattern = Pattern.compile("x+(?>y+)z+");
                    Matcher matcher = pattern.matcher("xxxyyyzzz");
                    matcher.find();
                    matcher.group(1);
       });

        // Pure group
        assertThrows(IndexOutOfBoundsException.class, () -> {
            Pattern pattern = Pattern.compile("x+(?:y+)z+");
            Matcher matcher = pattern.matcher("xxxyyyzzz");
            matcher.find();
            String blah = matcher.group(1);
        });

        // Supplementary character tests
        // Independent group
        assertThrows(IndexOutOfBoundsException.class, () -> {
            Pattern pattern = Pattern.compile(toSupplementaries("x+(?>y+)z+"));
            Matcher matcher = pattern.matcher(toSupplementaries("xxxyyyzzz"));
            matcher.find();
            String blah = matcher.group(1);
        });

        // Pure group
        assertThrows(IndexOutOfBoundsException.class, () -> {
            Pattern pattern = Pattern.compile(toSupplementaries("x+(?:y+)z+"));
            Matcher matcher = pattern.matcher(toSupplementaries("xxxyyyzzz"));
            matcher.find();
            String blah = matcher.group(1);
        });
    }

    @Test
    public static void backRefTest() {
        Pattern pattern = Pattern.compile("(a*)bc\\1");
        check(pattern, "zzzaabcazzz", true);

        pattern = Pattern.compile("(a*)bc\\1");
        check(pattern, "zzzaabcaazzz", true);

        pattern = Pattern.compile("(abc)(def)\\1");
        check(pattern, "abcdefabc", true);

        pattern = Pattern.compile("(abc)(def)\\3");
        check(pattern, "abcdefabc", false);

        for (int i = 1; i < 10; i++) {
            // Make sure backref 1-9 are always accepted
            pattern = Pattern.compile("abcdef\\" + i);
            // and fail to match if the target group does not exit
            check(pattern, "abcdef", false);
        }

        pattern = Pattern.compile("(a)(b)(c)(d)(e)(f)(g)(h)(i)(j)\\11");
        check(pattern, "abcdefghija", false);
        check(pattern, "abcdefghija1", true);

        pattern = Pattern.compile("(a)(b)(c)(d)(e)(f)(g)(h)(i)(j)(k)\\11");
        check(pattern, "abcdefghijkk", true);

        pattern = Pattern.compile("(a)bcdefghij\\11");
        check(pattern, "abcdefghija1", true);

        // Supplementary character tests
        pattern = Pattern.compile(toSupplementaries("(a*)bc\\1"));
        check(pattern, toSupplementaries("zzzaabcazzz"), true);

        pattern = Pattern.compile(toSupplementaries("(a*)bc\\1"));
        check(pattern, toSupplementaries("zzzaabcaazzz"), true);

        pattern = Pattern.compile(toSupplementaries("(abc)(def)\\1"));
        check(pattern, toSupplementaries("abcdefabc"), true);

        pattern = Pattern.compile(toSupplementaries("(abc)(def)\\3"));
        check(pattern, toSupplementaries("abcdefabc"), false);

        pattern = Pattern.compile(toSupplementaries("(a)(b)(c)(d)(e)(f)(g)(h)(i)(j)\\11"));
        check(pattern, toSupplementaries("abcdefghija"), false);
        check(pattern, toSupplementaries("abcdefghija1"), true);

        pattern = Pattern.compile(toSupplementaries("(a)(b)(c)(d)(e)(f)(g)(h)(i)(j)(k)\\11"));
        check(pattern, toSupplementaries("abcdefghijkk"), true);
    }

    /**
     * Unicode Technical Report #18, section 2.6 End of Line
     * There is no empty line to be matched in the sequence \u000D\u000A
     * but there is an empty line in the sequence \u000A\u000D.
     */
    @Test
    public static void anchorTest() {
        Pattern p = Pattern.compile("^.*$", Pattern.MULTILINE);
        Matcher m = p.matcher("blah1\r\nblah2");
        m.find();
        m.find();
        assertEquals(m.group(), "blah2");

        m.reset("blah1\n\rblah2");
        m.find();
        m.find();
        m.find();
        assertEquals(m.group(), "blah2");

        // Test behavior of $ with \r\n at end of input
        p = Pattern.compile(".+$");
        m = p.matcher("blah1\r\n");
        assertTrue(m.find());
        assertEquals(m.group(), "blah1");
        assertFalse(m.find());

        // Test behavior of $ with \r\n at end of input in multiline
        p = Pattern.compile(".+$", Pattern.MULTILINE);
        m = p.matcher("blah1\r\n");
        assertTrue(m.find());
        assertFalse(m.find());

        // Test for $ recognition of \u0085 for bug 4527731
        p = Pattern.compile(".+$", Pattern.MULTILINE);
        m = p.matcher("blah1\u0085");
        assertTrue(m.find());

        // Supplementary character test
        p = Pattern.compile("^.*$", Pattern.MULTILINE);
        m = p.matcher(toSupplementaries("blah1\r\nblah2"));
        m.find();
        m.find();
        assertEquals(m.group(), toSupplementaries("blah2"));

        m.reset(toSupplementaries("blah1\n\rblah2"));
        m.find();
        m.find();
        m.find();

        assertEquals(m.group(), toSupplementaries("blah2"));

        // Test behavior of $ with \r\n at end of input
        p = Pattern.compile(".+$");
        m = p.matcher(toSupplementaries("blah1\r\n"));
        assertTrue(m.find());
        assertEquals(m.group(), toSupplementaries("blah1"));
        assertFalse(m.find());

        // Test behavior of $ with \r\n at end of input in multiline
        p = Pattern.compile(".+$", Pattern.MULTILINE);
        m = p.matcher(toSupplementaries("blah1\r\n"));
        assertTrue(m.find());
        assertFalse(m.find());

        // Test for $ recognition of \u0085 for bug 4527731
        p = Pattern.compile(".+$", Pattern.MULTILINE);
        m = p.matcher(toSupplementaries("blah1\u0085"));
        assertTrue(m.find());
    }

    /**
     * A basic sanity test of Matcher.lookingAt().
     */
    @Test
    public static void lookingAtTest() {
        Pattern p = Pattern.compile("(ab)(c*)");
        Matcher m = p.matcher("abccczzzabcczzzabccc");

        assertTrue(m.lookingAt());

        assertEquals(m.group(), m.group(0));

        m = p.matcher("zzzabccczzzabcczzzabccczzz");
        assertFalse(m.lookingAt());

        // Supplementary character test
        p = Pattern.compile(toSupplementaries("(ab)(c*)"));
        m = p.matcher(toSupplementaries("abccczzzabcczzzabccc"));

        assertTrue(m.lookingAt());

        assertEquals(m.group(), m.group(0));

        m = p.matcher(toSupplementaries("zzzabccczzzabcczzzabccczzz"));
        assertFalse(m.lookingAt());
    }

    /**
     * A basic sanity test of Matcher.matches().
     */
    @Test
    public static void matchesTest() {
        // matches()
        Pattern p = Pattern.compile("ulb(c*)");
        Matcher m = p.matcher("ulbcccccc");
        assertTrue(m.matches());

        // find() but not matches()
        m.reset("zzzulbcccccc");
        assertFalse(m.matches());

        // lookingAt() but not matches()
        m.reset("ulbccccccdef");
        assertFalse(m.matches());

        // matches()
        p = Pattern.compile("a|ad");
        m = p.matcher("ad");
        assertTrue(m.matches());

        // Supplementary character test
        // matches()
        p = Pattern.compile(toSupplementaries("ulb(c*)"));
        m = p.matcher(toSupplementaries("ulbcccccc"));
        assertTrue(m.matches());

        // find() but not matches()
        m.reset(toSupplementaries("zzzulbcccccc"));
        assertFalse(m.matches());

        // lookingAt() but not matches()
        m.reset(toSupplementaries("ulbccccccdef"));
        assertFalse(m.matches());

        // matches()
        p = Pattern.compile(toSupplementaries("a|ad"));
        m = p.matcher(toSupplementaries("ad"));
        assertTrue(m.matches());
    }

    /**
     * A basic sanity test of Pattern.matches().
     */
    @Test
    public static void patternMatchesTest() {
        // matches()
        assertTrue(Pattern.matches(toSupplementaries("ulb(c*)"),
                                    toSupplementaries("ulbcccccc")));

        // find() but not matches()
        assertFalse(Pattern.matches(toSupplementaries("ulb(c*)"),
                                    toSupplementaries("zzzulbcccccc")));

        // lookingAt() but not matches()
        assertFalse(Pattern.matches(toSupplementaries("ulb(c*)"),
                                   toSupplementaries("ulbccccccdef")));

        // Supplementary character test
        // matches()
        assertTrue(Pattern.matches(toSupplementaries("ulb(c*)"),
                                   toSupplementaries("ulbcccccc")));

        // find() but not matches()
        assertFalse(Pattern.matches(toSupplementaries("ulb(c*)"),
                                    toSupplementaries("zzzulbcccccc")));

        // lookingAt() but not matches()
        assertFalse(Pattern.matches(toSupplementaries("ulb(c*)"),
                                    toSupplementaries("ulbccccccdef")));
    }

    /**
     * Canonical equivalence testing. Tests the ability of the engine
     * to match sequences that are not explicitly specified in the
     * pattern when they are considered equivalent by the Unicode Standard.
     */
    @Test
    public static void ceTest() {
        // Decomposed char outside char classes
        Pattern p = Pattern.compile("testa\u030a", Pattern.CANON_EQ);
        Matcher m = p.matcher("test\u00e5");
        assertTrue(m.matches());

        m.reset("testa\u030a");
        assertTrue(m.matches());

        // Composed char outside char classes
        p = Pattern.compile("test\u00e5", Pattern.CANON_EQ);
        m = p.matcher("test\u00e5");
        assertTrue(m.matches());

        m.reset("testa\u030a");
        assertTrue(m.find());

        // Decomposed char inside a char class
        p = Pattern.compile("test[abca\u030a]", Pattern.CANON_EQ);
        m = p.matcher("test\u00e5");
        assertTrue(m.find());

        m.reset("testa\u030a");
        assertTrue(m.find());

        // Composed char inside a char class
        p = Pattern.compile("test[abc\u00e5def\u00e0]", Pattern.CANON_EQ);
        m = p.matcher("test\u00e5");
        assertTrue(m.find());

        m.reset("testa\u0300");
        assertTrue(m.find());

        m.reset("testa\u030a");
        assertTrue(m.find());

        // Marks that cannot legally change order and be equivalent
        p = Pattern.compile("testa\u0308\u0300", Pattern.CANON_EQ);
        check(p, "testa\u0308\u0300", true);
        check(p, "testa\u0300\u0308", false);

        // Marks that can legally change order and be equivalent
        p = Pattern.compile("testa\u0308\u0323", Pattern.CANON_EQ);
        check(p, "testa\u0308\u0323", true);
        check(p, "testa\u0323\u0308", true);

        // Test all equivalences of the sequence a\u0308\u0323\u0300
        p = Pattern.compile("testa\u0308\u0323\u0300", Pattern.CANON_EQ);
        check(p, "testa\u0308\u0323\u0300", true);
        check(p, "testa\u0323\u0308\u0300", true);
        check(p, "testa\u0308\u0300\u0323", true);
        check(p, "test\u00e4\u0323\u0300", true);
        check(p, "test\u00e4\u0300\u0323", true);

        Object[][] data = new Object[][] {

        // JDK-4867170
        { "[\u1f80-\u1f82]", "ab\u1f80cd",             "f", true },
        { "[\u1f80-\u1f82]", "ab\u1f81cd",             "f", true },
        { "[\u1f80-\u1f82]", "ab\u1f82cd",             "f", true },
        { "[\u1f80-\u1f82]", "ab\u03b1\u0314\u0345cd", "f", true },
        { "[\u1f80-\u1f82]", "ab\u03b1\u0345\u0314cd", "f", true },
        { "[\u1f80-\u1f82]", "ab\u1f01\u0345cd",       "f", true },
        { "[\u1f80-\u1f82]", "ab\u1f00\u0345cd",       "f", true },

        { "\\p{IsGreek}",    "ab\u1f80cd",             "f", true },
        { "\\p{IsGreek}",    "ab\u1f81cd",             "f", true },
        { "\\p{IsGreek}",    "ab\u1f82cd",             "f", true },
        { "\\p{IsGreek}",    "ab\u03b1\u0314\u0345cd", "f", true },
        { "\\p{IsGreek}",    "ab\u1f01\u0345cd",       "f", true },

        // backtracking, force to match "\u1f80", instead of \u1f82"
        { "ab\\p{IsGreek}\u0300cd", "ab\u03b1\u0313\u0345\u0300cd", "m", true },

        { "[\\p{IsGreek}]",  "\u03b1\u0314\u0345",     "m", true },
        { "\\p{IsGreek}",    "\u03b1\u0314\u0345",     "m", true },

        { "[^\u1f80-\u1f82]","\u1f81",                 "m", false },
        { "[^\u1f80-\u1f82]","\u03b1\u0314\u0345",     "m", false },
        { "[^\u1f01\u0345]", "\u1f81",                 "f", false },

        { "[^\u1f81]+",      "\u1f80\u1f82",           "f", true },
        { "[\u1f80]",        "ab\u1f80cd",             "f", true },
        { "\u1f80",          "ab\u1f80cd",             "f", true },
        { "\u1f00\u0345\u0300",  "\u1f82", "m", true },
        { "\u1f80",          "-\u1f00\u0345\u0300-",   "f", true },
        { "\u1f82",          "\u1f00\u0345\u0300",     "m", true },
        { "\u1f82",          "\u1f80\u0300",           "m", true },

        // JDK-7080302       # compile failed
        { "a(\u0041\u0301\u0328)", "a\u0041\u0301\u0328", "m", true},

        // JDK-6728861, same cause as above one
        { "\u00e9\u00e9n", "e\u0301e\u0301n", "m", true},

        // JDK-6995635
        { "(\u00e9)", "e\u0301", "m", true },

        // JDK-6736245
        // intereting special case, nfc(u2add+u0338) -> u2add+u0338) NOT u2adc
        { "\u2ADC", "\u2ADC", "m", true},          // NFC
        { "\u2ADC", "\u2ADD\u0338", "m", true},    // NFD

        //  4916384.
        // Decomposed hangul (jamos) works inside clazz
        { "[\u1100\u1161]", "\u1100\u1161", "m", true},
        { "[\u1100\u1161]", "\uac00", "m", true},

        { "[\uac00]", "\u1100\u1161", "m", true},
        { "[\uac00]", "\uac00", "m", true},

        // Decomposed hangul (jamos)
        { "\u1100\u1161", "\u1100\u1161", "m", true},
        { "\u1100\u1161", "\uac00", "m", true},

        // Composed hangul
        { "\uac00",  "\u1100\u1161", "m", true },
        { "\uac00",  "\uac00", "m", true },

        /* Need a NFDSlice to nfd the source to solve this issue
           u+1d1c0 -> nfd: <u+1d1ba><u+1d165><u+1d16f>  -> nfc: <u+1d1ba><u+1d165><u+1d16f>
           u+1d1bc -> nfd: <u+1d1ba><u+1d165>           -> nfc: <u+1d1ba><u+1d165>
           <u+1d1bc><u+1d16f> -> nfd: <u+1d1ba><u+1d165><u+1d16f> -> nfc: <u+1d1ba><u+1d165><u+1d16f>

        // Decomposed supplementary outside char classes
        // { "test\ud834\uddbc\ud834\udd6f", "test\ud834\uddc0", "m", true },
        // Composed supplementary outside char classes
        // { "test\ud834\uddc0", "test\ud834\uddbc\ud834\udd6f", "m", true },
        */
        { "test\ud834\uddbc\ud834\udd6f", "test\ud834\uddbc\ud834\udd6f", "m", true },
        //{ "test\ud834\uddc0",             "test\ud834\uddbc\ud834\udd6f", "m", true }, //problem

        { "test\ud834\uddc0",             "test\ud834\uddc0",             "m", true },
        //{ "test\ud834\uddbc\ud834\udd6f", "test\ud834\uddc0",             "m", true }, //problem
        };

        for (Object[] d : data) {
            String pn = (String)d[0];
            String tt = (String)d[1];
            boolean isFind = "f".equals((d[2]));
            boolean expected = (boolean)d[3];
            boolean ret = isFind ? Pattern.compile(pn, Pattern.CANON_EQ).matcher(tt).find()
                                 : Pattern.compile(pn, Pattern.CANON_EQ).matcher(tt).matches();
            if (ret != expected) {
                fail("pn: " + pn + "\ntt: " + tt + "\nexpected: " + expected + "\nret: " + ret);
            }
        }
    }

    /**
     * A basic sanity test of Matcher.replaceAll().
     */
    @Test
    public static void globalSubstitute() {
        // Global substitution with a literal
        Pattern p = Pattern.compile("(ab)(c*)");
        Matcher m = p.matcher("abccczzzabcczzzabccc");
        assertEquals(m.replaceAll("test"), "testzzztestzzztest");

        m.reset("zzzabccczzzabcczzzabccczzz");
        assertEquals(m.replaceAll("test"), "zzztestzzztestzzztestzzz");

        // Global substitution with groups
        m.reset("zzzabccczzzabcczzzabccczzz");
        String result = m.replaceAll("$1");
        assertEquals(result, "zzzabzzzabzzzabzzz");

        // Supplementary character test
        // Global substitution with a literal
        p = Pattern.compile(toSupplementaries("(ab)(c*)"));
        m = p.matcher(toSupplementaries("abccczzzabcczzzabccc"));
        assertEquals(m.replaceAll(toSupplementaries("test")),
                                  toSupplementaries("testzzztestzzztest"));

        m.reset(toSupplementaries("zzzabccczzzabcczzzabccczzz"));
        assertEquals(m.replaceAll(toSupplementaries("test")),
                              toSupplementaries("zzztestzzztestzzztestzzz"));

        // Global substitution with groups
        m.reset(toSupplementaries("zzzabccczzzabcczzzabccczzz"));
        result = m.replaceAll("$1");
        assertEquals(result,toSupplementaries("zzzabzzzabzzzabzzz"));
    }

    /**
     * Tests the usage of Matcher.appendReplacement() with literal
     * and group substitutions.
     */
    @Test
    public static void stringBufferSubstituteLiteral() {
        // SB substitution with literal
        final String blah = "zzzblahzzz";
        final Pattern p = Pattern.compile("blah");
        final Matcher m = p.matcher(blah);
        final StringBuffer result = new StringBuffer();

        assertThrows(IllegalStateException.class, () -> m.appendReplacement(result, "blech"));

        m.find();
        m.appendReplacement(result, "blech");
        assertEquals(result.toString(), "zzzblech");

        m.appendTail(result);
        assertEquals(result.toString(), "zzzblechzzz");

    }

    @Test
    public static void stringBufferSubtituteWithGroups() {
        // SB substitution with groups
        final String blah = "zzzabcdzzz";
        final Pattern p = Pattern.compile("(ab)(cd)*");
        final Matcher m = p.matcher(blah);
        final StringBuffer result = new StringBuffer();
        assertThrows(IllegalStateException.class, () -> m.appendReplacement(result, "$1"));
        m.find();
        m.appendReplacement(result, "$1");
        assertEquals(result.toString(), "zzzab");

        m.appendTail(result);
        assertEquals(result.toString(), "zzzabzzz");
    }

    @Test
    public static void stringBufferThreeSubstitution() {
        // SB substitution with 3 groups
        final String blah = "zzzabcdcdefzzz";
        final Pattern p = Pattern.compile("(ab)(cd)*(ef)");
        final Matcher m = p.matcher(blah);
        final StringBuffer result = new StringBuffer();
        assertThrows(IllegalStateException.class, () -> m.appendReplacement(result, "$1w$2w$3"));
        m.find();
        m.appendReplacement(result, "$1w$2w$3");
        assertEquals(result.toString(), "zzzabwcdwef");

        m.appendTail(result);
        assertEquals(result.toString(), "zzzabwcdwefzzz");

    }

    @Test
    public static void stringBufferSubstituteGroupsThreeMatches() {
        // SB substitution with groups and three matches
        // skipping middle match
        final String blah = "zzzabcdzzzabcddzzzabcdzzz";
        final Pattern p = Pattern.compile("(ab)(cd*)");
        final Matcher m = p.matcher(blah);
        final StringBuffer result = new StringBuffer();
        assertThrows(IllegalStateException.class, () -> m.appendReplacement(result, "$1"));

        m.find();
        m.appendReplacement(result, "$1");
        assertEquals(result.toString(), "zzzab");

        m.find();
        m.find();
        m.appendReplacement(result, "$2");
        assertEquals(result.toString(), "zzzabzzzabcddzzzcd");

        m.appendTail(result);
        assertEquals(result.toString(), "zzzabzzzabcddzzzcdzzz");


    }

    @Test
    public static void stringBufferEscapedDollar() {
        // Check to make sure escaped $ is ignored
        String blah = "zzzabcdcdefzzz";
        Pattern p = Pattern.compile("(ab)(cd)*(ef)");
        Matcher m = p.matcher(blah);
        StringBuffer result = new StringBuffer();
        m.find();
        m.appendReplacement(result, "$1w\\$2w$3");
        assertEquals(result.toString(), "zzzabw$2wef");

        m.appendTail(result);
        assertEquals(result.toString(), "zzzabw$2wefzzz");
    }

    @Test
    public static void stringBufferNonExistentGroup() {
        // Check to make sure a reference to nonexistent group causes error
        final String blah = "zzzabcdcdefzzz";
        final Pattern p = Pattern.compile("(ab)(cd)*(ef)");
        final Matcher m = p.matcher(blah);
        final StringBuffer result = new StringBuffer();
        m.find();
        assertThrows(IndexOutOfBoundsException.class,
                () -> m.appendReplacement(result, "$1w$5w$3"));
    }

    @Test
    public static void stringBufferCheckDoubleDigitGroupReferences() {

        // Check double digit group references
        String blah = "zzz123456789101112zzz";
        Pattern p = Pattern.compile("(1)(2)(3)(4)(5)(6)(7)(8)(9)(10)(11)");
        Matcher m = p.matcher(blah);
        StringBuffer result = new StringBuffer();
        m.find();
        m.appendReplacement(result, "$1w$11w$3");
        assertEquals(result.toString(), "zzz1w11w3");

    }

    @Test
    public static void stringBufferBackoff() {
        // Check to make sure it backs off $15 to $1 if only three groups
        String blah = "zzzabcdcdefzzz";
        Pattern p = Pattern.compile("(ab)(cd)*(ef)");
        Matcher m = p.matcher(blah);
        StringBuffer result = new StringBuffer();
        m.find();
        m.appendReplacement(result, "$1w$15w$3");
        assertEquals(result.toString(), "zzzabwab5wef");
    }

    @Test
    public static void stringBufferSupplementaryCharacter(){
        // Supplementary character test
        // SB substitution with literal
        final String blah = toSupplementaries("zzzblahzzz");
        final Pattern p = Pattern.compile(toSupplementaries("blah"));
        final Matcher m = p.matcher(blah);
        final StringBuffer result = new StringBuffer();
        assertThrows(IllegalStateException.class,
                () -> m.appendReplacement(result, toSupplementaries("blech")));
        m.find();
        m.appendReplacement(result, toSupplementaries("blech"));
        assertEquals(result.toString(), toSupplementaries("zzzblech"));

        m.appendTail(result);
        assertEquals(result.toString(), toSupplementaries("zzzblechzzz"));
    }

    @Test
    public static void stringBufferSubstitutionWithGroups() {
        // SB substitution with groups
        final String blah = toSupplementaries("zzzabcdzzz");
        final Pattern p = Pattern.compile(toSupplementaries("(ab)(cd)*"));
        final Matcher m = p.matcher(blah);
        final StringBuffer result = new StringBuffer();
        assertThrows(IllegalStateException.class,
                () -> m.appendReplacement(result, "$1"));
        m.find();
        m.appendReplacement(result, "$1");
        assertEquals(result.toString(), toSupplementaries("zzzab"));

        m.appendTail(result);
        assertEquals(result.toString(), toSupplementaries("zzzabzzz"));
    }

    @Test
    public static void stringBufferSubstituteWithThreeGroups() {
        // SB substitution with 3 groups
        final String blah = toSupplementaries("zzzabcdcdefzzz");
        final Pattern p = Pattern.compile(toSupplementaries("(ab)(cd)*(ef)"));
        final Matcher m = p.matcher(blah);
        final StringBuffer result = new StringBuffer();
        assertThrows(IllegalStateException.class,
                () -> m.appendReplacement(result, toSupplementaries("$1w$2w$3")));

        m.find();
        m.appendReplacement(result, toSupplementaries("$1w$2w$3"));
        assertEquals(result.toString(), toSupplementaries("zzzabwcdwef"));

        m.appendTail(result);
        assertEquals(result.toString(), toSupplementaries("zzzabwcdwefzzz"));
    }

    @Test
    public static void stringBufferWithGroupsAndThreeMatches() {
        // SB substitution with groups and three matches
        // skipping middle match
        final String blah = toSupplementaries("zzzabcdzzzabcddzzzabcdzzz");
        final Pattern p = Pattern.compile(toSupplementaries("(ab)(cd*)"));
        final Matcher m = p.matcher(blah);
        final StringBuffer result = new StringBuffer();
        assertThrows(IllegalStateException.class, () ->
            m.appendReplacement(result, "$1"));

        m.find();
        m.appendReplacement(result, "$1");
        assertEquals(result.toString(), toSupplementaries("zzzab"));

        m.find();
        m.find();
        m.appendReplacement(result, "$2");
        assertEquals(result.toString(), toSupplementaries("zzzabzzzabcddzzzcd"));

        m.appendTail(result);
        assertEquals(result.toString(), toSupplementaries("zzzabzzzabcddzzzcdzzz"));
    }

    @Test
    public static void stringBufferEnsureDollarIgnored() {
        // Check to make sure escaped $ is ignored
        String blah = toSupplementaries("zzzabcdcdefzzz");
        Pattern p = Pattern.compile(toSupplementaries("(ab)(cd)*(ef)"));
        Matcher m = p.matcher(blah);
        StringBuffer result = new StringBuffer();
        m.find();
        m.appendReplacement(result, toSupplementaries("$1w\\$2w$3"));
        assertEquals(result.toString(), toSupplementaries("zzzabw$2wef"));

        m.appendTail(result);
        assertEquals(result.toString(), toSupplementaries("zzzabw$2wefzzz"));
    }

    @Test
    public static void stringBufferCheckNonexistentGroupReference() {
        // Check to make sure a reference to nonexistent group causes error
        final String blah = toSupplementaries("zzzabcdcdefzzz");
        final Pattern p = Pattern.compile(toSupplementaries("(ab)(cd)*(ef)"));
        final Matcher m = p.matcher(blah);
        final StringBuffer result = new StringBuffer();
        m.find();
        assertThrows(IndexOutOfBoundsException.class, () ->
                m.appendReplacement(result, toSupplementaries("$1w$5w$3")));
    }

    @Test
    public static void stringBufferCheckSupplementalDoubleDigitGroupReferences() {
        // Check double digit group references
        String blah = toSupplementaries("zzz123456789101112zzz");
        Pattern p = Pattern.compile("(1)(2)(3)(4)(5)(6)(7)(8)(9)(10)(11)");
        Matcher m = p.matcher(blah);
        StringBuffer result = new StringBuffer();
        m.find();
        m.appendReplacement(result, toSupplementaries("$1w$11w$3"));
        assertEquals(result.toString(), toSupplementaries("zzz1w11w3"));
    }

    @Test
    public static void stringBufferBackoffSupplemental() {
        // Check to make sure it backs off $15 to $1 if only three groups
        String blah = toSupplementaries("zzzabcdcdefzzz");
        Pattern p = Pattern.compile(toSupplementaries("(ab)(cd)*(ef)"));
        Matcher m = p.matcher(blah);
        StringBuffer result = new StringBuffer();
        m.find();
        m.appendReplacement(result, toSupplementaries("$1w$15w$3"));
        assertEquals(result.toString(), toSupplementaries("zzzabwab5wef"));
    }

    @Test
    public static void stringBufferCheckAppendException() {
        // Check nothing has been appended into the output buffer if
        // the replacement string triggers IllegalArgumentException.
        Pattern p = Pattern.compile("(abc)");
        Matcher m = p.matcher("abcd");
        StringBuffer result = new StringBuffer();
        m.find();
        expectThrows(IllegalArgumentException.class,
                () -> m.appendReplacement(result, ("xyz$g")));
        assertEquals(result.length(), 0);

    }
    /**
     * Tests the usage of Matcher.appendReplacement() with literal
     * and group substitutions.
     */
    @Test
    public static void stringBuilderSubstitutionWithLiteral() {
        // SB substitution with literal
        final String blah = "zzzblahzzz";
        final Pattern p = Pattern.compile("blah");
        final Matcher m = p.matcher(blah);
        final StringBuilder result = new StringBuilder();
        assertThrows(IllegalStateException.class, () ->
            m.appendReplacement(result, "blech"));

        m.find();
        m.appendReplacement(result, "blech");
        assertEquals(result.toString(), "zzzblech");

        m.appendTail(result);
        assertEquals(result.toString(), "zzzblechzzz");
    }

    @Test
    public static void stringBuilderSubstitutionWithGroups() {
        // SB substitution with groups
        final String blah = "zzzabcdzzz";
        final Pattern p = Pattern.compile("(ab)(cd)*");
        final Matcher m = p.matcher(blah);
        final StringBuilder result = new StringBuilder();
        assertThrows(IllegalStateException.class, () ->
            m.appendReplacement(result, "$1"));
        m.find();
        m.appendReplacement(result, "$1");
        assertEquals(result.toString(), "zzzab");

        m.appendTail(result);
        assertEquals(result.toString(), "zzzabzzz");
    }

    @Test
    public static void stringBuilderSubstitutionWithThreeGroups() {
        // SB substitution with 3 groups
        final String blah = "zzzabcdcdefzzz";
        final Pattern p = Pattern.compile("(ab)(cd)*(ef)");
        final Matcher m = p.matcher(blah);
        final StringBuilder result = new StringBuilder();
        assertThrows(IllegalStateException.class, () ->
            m.appendReplacement(result, "$1w$2w$3"));

        m.find();
        m.appendReplacement(result, "$1w$2w$3");
        assertEquals(result.toString(), "zzzabwcdwef");

        m.appendTail(result);
        assertEquals(result.toString(), "zzzabwcdwefzzz");
    }

    @Test
    public static void stringBuilderSubstitutionThreeMatch() {
        // SB substitution with groups and three matches
        // skipping middle match
        final String blah = "zzzabcdzzzabcddzzzabcdzzz";
        final Pattern p = Pattern.compile("(ab)(cd*)");
        final Matcher m = p.matcher(blah);
        final StringBuilder result = new StringBuilder();
        assertThrows(IllegalStateException.class, () ->
            m.appendReplacement(result, "$1"));
        m.find();
        m.appendReplacement(result, "$1");
        assertEquals(result.toString(), "zzzab");

        m.find();
        m.find();
        m.appendReplacement(result, "$2");
        assertEquals(result.toString(), "zzzabzzzabcddzzzcd");

        m.appendTail(result);
        assertEquals(result.toString(), "zzzabzzzabcddzzzcdzzz");
    }

    @Test
    public static void stringBuilderSubtituteCheckEscapedDollar() {
        // Check to make sure escaped $ is ignored
        final String blah = "zzzabcdcdefzzz";
        final Pattern p = Pattern.compile("(ab)(cd)*(ef)");
        final Matcher m = p.matcher(blah);
        final StringBuilder result = new StringBuilder();
        m.find();
        m.appendReplacement(result, "$1w\\$2w$3");
        assertEquals(result.toString(), "zzzabw$2wef");

        m.appendTail(result);
        assertEquals(result.toString(), "zzzabw$2wefzzz");
    }

    @Test
    public static void stringBuilderNonexistentGroupError() {
        // Check to make sure a reference to nonexistent group causes error
        final String blah = "zzzabcdcdefzzz";
        final Pattern p = Pattern.compile("(ab)(cd)*(ef)");
        final Matcher m = p.matcher(blah);
        final StringBuilder result = new StringBuilder();
        m.find();
        assertThrows(IndexOutOfBoundsException.class, () ->
            m.appendReplacement(result, "$1w$5w$3"));
    }

    @Test
    public static void stringBuilderDoubleDigitGroupReferences() {
        // Check double digit group references
        final String blah = "zzz123456789101112zzz";
        final Pattern p = Pattern.compile("(1)(2)(3)(4)(5)(6)(7)(8)(9)(10)(11)");
        final Matcher m = p.matcher(blah);
        final StringBuilder result = new StringBuilder();
        m.find();
        m.appendReplacement(result, "$1w$11w$3");
        assertEquals(result.toString(), "zzz1w11w3");
    }

    @Test
    public static void stringBuilderCheckBackoff() {
        // Check to make sure it backs off $15 to $1 if only three groups
        final String blah = "zzzabcdcdefzzz";
        final Pattern p = Pattern.compile("(ab)(cd)*(ef)");
        final Matcher m = p.matcher(blah);
        final StringBuilder result = new StringBuilder();
        m.find();
        m.appendReplacement(result, "$1w$15w$3");
        assertEquals(result.toString(), "zzzabwab5wef");
    }

    @Test
    public static void stringBuilderSupplementalLiteralSubstitution() {
        // Supplementary character test
        // SB substitution with literal
        final String blah = toSupplementaries("zzzblahzzz");
        final Pattern p = Pattern.compile(toSupplementaries("blah"));
        final Matcher m = p.matcher(blah);
        final StringBuilder result = new StringBuilder();
        assertThrows(IllegalStateException.class,
                () -> m.appendReplacement(result, toSupplementaries("blech")));
        m.find();
        m.appendReplacement(result, toSupplementaries("blech"));
        assertEquals(result.toString(), toSupplementaries("zzzblech"));
        m.appendTail(result);
        assertEquals(result.toString(), toSupplementaries("zzzblechzzz"));
    }

    @Test
    public static void stringBuilderSupplementalSubstitutionWithGroups() {
        // SB substitution with groups
        final String blah = toSupplementaries("zzzabcdzzz");
        final Pattern p = Pattern.compile(toSupplementaries("(ab)(cd)*"));
        final Matcher m = p.matcher(blah);
        final StringBuilder result = new StringBuilder();
        assertThrows(IllegalStateException.class,
                () -> m.appendReplacement(result, "$1"));
        m.find();
        m.appendReplacement(result, "$1");
        assertEquals(result.toString(), toSupplementaries("zzzab"));

        m.appendTail(result);
        assertEquals(result.toString(), toSupplementaries("zzzabzzz"));
    }

    @Test
    public static void stringBuilderSupplementalSubstitutionThreeGroups() {
        // SB substitution with 3 groups
        final String blah = toSupplementaries("zzzabcdcdefzzz");
        final Pattern p = Pattern.compile(toSupplementaries("(ab)(cd)*(ef)"));
        final Matcher m = p.matcher(blah);
        final StringBuilder result = new StringBuilder();
        assertThrows(IllegalStateException.class, () ->
            m.appendReplacement(result, toSupplementaries("$1w$2w$3")));
        m.find();
        m.appendReplacement(result, toSupplementaries("$1w$2w$3"));
        assertEquals(result.toString(), toSupplementaries("zzzabwcdwef"));

        m.appendTail(result);
        assertEquals(result.toString(), toSupplementaries("zzzabwcdwefzzz"));
    }

    @Test
    public static void stringBuilderSubstitutionSupplementalSkipMiddleThreeMatch() {
        // SB substitution with groups and three matches
        // skipping middle match
        final String blah = toSupplementaries("zzzabcdzzzabcddzzzabcdzzz");
        final Pattern p = Pattern.compile(toSupplementaries("(ab)(cd*)"));
        final Matcher m = p.matcher(blah);
        final StringBuilder result = new StringBuilder();
        assertThrows(IllegalStateException.class, () ->
                m.appendReplacement(result, "$1"));
        m.find();
        m.appendReplacement(result, "$1");
        assertEquals(result.toString(), toSupplementaries("zzzab"));

        m.find();
        m.find();
        m.appendReplacement(result, "$2");
        assertEquals(result.toString(), toSupplementaries("zzzabzzzabcddzzzcd"));

        m.appendTail(result);
        assertEquals(result.toString(), toSupplementaries("zzzabzzzabcddzzzcdzzz"));
    }

    @Test
    public static void stringBuilderSupplementalEscapedDollar() {
        // Check to make sure escaped $ is ignored
        final String blah = toSupplementaries("zzzabcdcdefzzz");
        final Pattern p = Pattern.compile(toSupplementaries("(ab)(cd)*(ef)"));
        final Matcher m = p.matcher(blah);
        final StringBuilder result = new StringBuilder();
        m.find();
        m.appendReplacement(result, toSupplementaries("$1w\\$2w$3"));
        assertEquals(result.toString(), toSupplementaries("zzzabw$2wef"));

        m.appendTail(result);
        assertEquals(result.toString(), toSupplementaries("zzzabw$2wefzzz"));
    }

    @Test
    public static void stringBuilderSupplementalNonExistentGroupError() {
        // Check to make sure a reference to nonexistent group causes error
        final String blah = toSupplementaries("zzzabcdcdefzzz");
        final Pattern p = Pattern.compile(toSupplementaries("(ab)(cd)*(ef)"));
        final Matcher m = p.matcher(blah);
        final StringBuilder result = new StringBuilder();
        m.find();
        assertThrows(IndexOutOfBoundsException.class, () ->
            m.appendReplacement(result, toSupplementaries("$1w$5w$3")));
    }

    @Test
    public static void stringBuilderSupplementalCheckDoubleDigitGroupReferences() {
        // Check double digit group references
        final String blah = toSupplementaries("zzz123456789101112zzz");
        final Pattern p = Pattern.compile("(1)(2)(3)(4)(5)(6)(7)(8)(9)(10)(11)");
        final Matcher m = p.matcher(blah);
        final StringBuilder result = new StringBuilder();
        m.find();
        m.appendReplacement(result, toSupplementaries("$1w$11w$3"));
        assertEquals(result.toString(), toSupplementaries("zzz1w11w3"));
    }

    @Test
    public static void stringBuilderSupplementalCheckBackoff() {
        // Check to make sure it backs off $15 to $1 if only three groups
        final String blah = toSupplementaries("zzzabcdcdefzzz");
        final Pattern p = Pattern.compile(toSupplementaries("(ab)(cd)*(ef)"));
        final Matcher m = p.matcher(blah);
        final StringBuilder result = new StringBuilder();
        m.find();
        m.appendReplacement(result, toSupplementaries("$1w$15w$3"));
        assertEquals(result.toString(), toSupplementaries("zzzabwab5wef"));
    }

    @Test
    public static void stringBuilderCheckIllegalArgumentException() {
        // Check nothing has been appended into the output buffer if
        // the replacement string triggers IllegalArgumentException.
        final Pattern p = Pattern.compile("(abc)");
        final Matcher m = p.matcher("abcd");
        final StringBuilder result = new StringBuilder();
        m.find();
        assertThrows(IllegalArgumentException.class, () ->
            m.appendReplacement(result, ("xyz$g")));
        assertEquals(result.length(), 0);
    }

    /*
     * 5 groups of characters are created to make a substitution string.
     * A base string will be created including random lead chars, the
     * substitution string, and random trailing chars.
     * A pattern containing the 5 groups is searched for and replaced with:
     * random group + random string + random group.
     * The results are checked for correctness.
     */
    @Test
    public static void substitutionBasher() {
        for (int runs = 0; runs<1000; runs++) {
            // Create a base string to work in
            int leadingChars = generator.nextInt(10);
            StringBuilder baseBuffer = new StringBuilder(100);
            String leadingString = getRandomAlphaString(leadingChars);
            baseBuffer.append(leadingString);

            // Create 5 groups of random number of random chars
            // Create the string to substitute
            // Create the pattern string to search for
            StringBuilder bufferToSub = new StringBuilder(25);
            StringBuilder bufferToPat = new StringBuilder(50);
            String[] groups = new String[5];
            for(int i=0; i<5; i++) {
                int aGroupSize = generator.nextInt(5)+1;
                groups[i] = getRandomAlphaString(aGroupSize);
                bufferToSub.append(groups[i]);
                bufferToPat.append('(');
                bufferToPat.append(groups[i]);
                bufferToPat.append(')');
            }
            String stringToSub = bufferToSub.toString();
            String pattern = bufferToPat.toString();

            // Place sub string into working string at random index
            baseBuffer.append(stringToSub);

            // Append random chars to end
            int trailingChars = generator.nextInt(10);
            String trailingString = getRandomAlphaString(trailingChars);
            baseBuffer.append(trailingString);
            String baseString = baseBuffer.toString();

            // Create test pattern and matcher
            Pattern p = Pattern.compile(pattern);
            Matcher m = p.matcher(baseString);

            // Reject candidate if pattern happens to start early
            m.find();
            if (m.start() < leadingChars)
                continue;

            // Reject candidate if more than one match
            if (m.find())
                continue;

            // Construct a replacement string with :
            // random group + random string + random group
            StringBuilder bufferToRep = new StringBuilder();
            int groupIndex1 = generator.nextInt(5);
            bufferToRep.append("$").append(groupIndex1 + 1);
            String randomMidString = getRandomAlphaString(5);
            bufferToRep.append(randomMidString);
            int groupIndex2 = generator.nextInt(5);
            bufferToRep.append("$").append(groupIndex2 + 1);
            String replacement = bufferToRep.toString();

            // Do the replacement
            String result = m.replaceAll(replacement);

            // Construct expected result
            String expectedResult = leadingString +
                    groups[groupIndex1] +
                    randomMidString +
                    groups[groupIndex2] +
                    trailingString;

            // Check results
            assertEquals(result, expectedResult);
        }
    }

    /*
     * 5 groups of characters are created to make a substitution string.
     * A base string will be created including random lead chars, the
     * substitution string, and random trailing chars.
     * A pattern containing the 5 groups is searched for and replaced with:
     * random group + random string + random group.
     * The results are checked for correctness.
     */
    @Test
    public static void substitutionBasher2() {
        for (int runs = 0; runs<1000; runs++) {
            // Create a base string to work in
            int leadingChars = generator.nextInt(10);
            StringBuilder baseBuffer = new StringBuilder(100);
            String leadingString = getRandomAlphaString(leadingChars);
            baseBuffer.append(leadingString);

            // Create 5 groups of random number of random chars
            // Create the string to substitute
            // Create the pattern string to search for
            StringBuilder bufferToSub = new StringBuilder(25);
            StringBuilder bufferToPat = new StringBuilder(50);
            String[] groups = new String[5];
            for(int i=0; i<5; i++) {
                int aGroupSize = generator.nextInt(5)+1;
                groups[i] = getRandomAlphaString(aGroupSize);
                bufferToSub.append(groups[i]);
                bufferToPat.append('(');
                bufferToPat.append(groups[i]);
                bufferToPat.append(')');
            }
            String stringToSub = bufferToSub.toString();
            String pattern = bufferToPat.toString();

            // Place sub string into working string at random index
            baseBuffer.append(stringToSub);

            // Append random chars to end
            int trailingChars = generator.nextInt(10);
            String trailingString = getRandomAlphaString(trailingChars);
            baseBuffer.append(trailingString);
            String baseString = baseBuffer.toString();

            // Create test pattern and matcher
            Pattern p = Pattern.compile(pattern);
            Matcher m = p.matcher(baseString);

            // Reject candidate if pattern happens to start early
            m.find();
            if (m.start() < leadingChars)
                continue;

            // Reject candidate if more than one match
            if (m.find())
                continue;

            // Construct a replacement string with :
            // random group + random string + random group
            StringBuilder bufferToRep = new StringBuilder();
            int groupIndex1 = generator.nextInt(5);
            bufferToRep.append("$").append(groupIndex1 + 1);
            String randomMidString = getRandomAlphaString(5);
            bufferToRep.append(randomMidString);
            int groupIndex2 = generator.nextInt(5);
            bufferToRep.append("$").append(groupIndex2 + 1);
            String replacement = bufferToRep.toString();

            // Do the replacement
            String result = m.replaceAll(replacement);

            // Construct expected result
            String expectedResult = leadingString +
                    groups[groupIndex1] +
                    randomMidString +
                    groups[groupIndex2] +
                    trailingString;

            // Check results
            assertEquals(result, expectedResult);
        }
    }

    /**
     * Checks the handling of some escape sequences that the Pattern
     * class should process instead of the java compiler. These are
     * not in the file because the escapes should be be processed
     * by the Pattern class when the regex is compiled.
     */
    @Test
    public static void escapes() {
        Pattern p = Pattern.compile("\\043");
        Matcher m = p.matcher("#");
        assertTrue(m.find());

        p = Pattern.compile("\\x23");
        m = p.matcher("#");
        assertTrue(m.find());

        p = Pattern.compile("\\u0023");
        m = p.matcher("#");
        assertTrue(m.find());
    }

    /**
     * Checks the handling of blank input situations. These
     * tests are incompatible with my test file format.
     */
    @Test
    public static void blankInput() {
        Pattern p = Pattern.compile("abc", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher("");
        assertFalse(m.find());

        p = Pattern.compile("a*", Pattern.CASE_INSENSITIVE);
        m = p.matcher("");
        assertTrue(m.find());

        p = Pattern.compile("abc");
        m = p.matcher("");
        assertFalse(m.find());

        p = Pattern.compile("a*");
        m = p.matcher("");
        assertTrue(m.find());
    }

    /**
     * Tests the Boyer-Moore pattern matching of a character sequence
     * on randomly generated patterns.
     */
    @Test
    public static void bm() {
        doBnM('a');

        doBnM(Character.MIN_SUPPLEMENTARY_CODE_POINT - 10);
    }

    private static void doBnM(int baseCharacter) {
        for (int i=0; i<100; i++) {
            // Create a short pattern to search for
            int patternLength = generator.nextInt(7) + 4;
            StringBuilder patternBuffer = new StringBuilder(patternLength);
            String pattern;
            retry: for (;;) {
                for (int x=0; x<patternLength; x++) {
                    int ch = baseCharacter + generator.nextInt(26);
                    if (Character.isSupplementaryCodePoint(ch)) {
                        patternBuffer.append(Character.toChars(ch));
                    } else {
                        patternBuffer.append((char)ch);
                    }
                }
                pattern = patternBuffer.toString();

                // Avoid patterns that start and end with the same substring
                // See JDK-6854417
                for (int x=1; x < pattern.length(); x++) {
                    if (pattern.startsWith(pattern.substring(x)))
                        continue retry;
                }
                break;
            }
            Pattern p = Pattern.compile(pattern);

            // Create a buffer with random ASCII chars that does
            // not match the sample
            String toSearch;
            StringBuffer s;
            Matcher m = p.matcher("");
            do {
                s = new StringBuffer(100);
                for (int x=0; x<100; x++) {
                    int ch = baseCharacter + generator.nextInt(26);
                    if (Character.isSupplementaryCodePoint(ch)) {
                        s.append(Character.toChars(ch));
                    } else {
                        s.append((char)ch);
                    }
                }
                toSearch = s.toString();
                m.reset(toSearch);
            } while (m.find());

            // Insert the pattern at a random spot
            int insertIndex = generator.nextInt(99);
            if (Character.isLowSurrogate(s.charAt(insertIndex)))
                insertIndex++;
            s.insert(insertIndex, pattern);
            toSearch = s.toString();

            // Make sure that the pattern is found
            m.reset(toSearch);
            assertTrue(m.find());

            // Make sure that the match text is the pattern
            assertEquals(m.group(), pattern);

            // Make sure match occured at insertion point
            assertEquals(m.start(), insertIndex);
        }
    }

    /**
     * Tests the matching of slices on randomly generated patterns.
     * The Boyer-Moore optimization is not done on these patterns
     * because it uses unicode case folding.
     */
    @Test
    public static void slice() {
        doSlice(Character.MAX_VALUE);

        doSlice(Character.MAX_CODE_POINT);
    }

    private static void doSlice(int maxCharacter) {
        for (int i=0; i<100; i++) {
            // Create a short pattern to search for
            int patternLength = generator.nextInt(7) + 4;
            StringBuilder patternBuffer = new StringBuilder(patternLength);
            for (int x=0; x<patternLength; x++) {
                int randomChar = 0;
                while (!Character.isLetterOrDigit(randomChar))
                    randomChar = generator.nextInt(maxCharacter);
                if (Character.isSupplementaryCodePoint(randomChar)) {
                    patternBuffer.append(Character.toChars(randomChar));
                } else {
                    patternBuffer.append((char) randomChar);
                }
            }
            String pattern =  patternBuffer.toString();
            Pattern p = Pattern.compile(pattern, Pattern.UNICODE_CASE);

            // Create a buffer with random chars that does not match the sample
            String toSearch = null;
            StringBuffer s = null;
            Matcher m = p.matcher("");
            do {
                s = new StringBuffer(100);
                for (int x=0; x<100; x++) {
                    int randomChar = 0;
                    while (!Character.isLetterOrDigit(randomChar))
                        randomChar = generator.nextInt(maxCharacter);
                    if (Character.isSupplementaryCodePoint(randomChar)) {
                        s.append(Character.toChars(randomChar));
                    } else {
                        s.append((char) randomChar);
                    }
                }
                toSearch = s.toString();
                m.reset(toSearch);
            } while (m.find());

            // Insert the pattern at a random spot
            int insertIndex = generator.nextInt(99);
            if (Character.isLowSurrogate(s.charAt(insertIndex)))
                insertIndex++;
            s.insert(insertIndex, pattern);
            toSearch = s.toString();

            // Make sure that the pattern is found
            m.reset(toSearch);
            assertTrue(m.find());

            // Make sure that the match text is the pattern
            assertEquals(m.group(), pattern);

            // Make sure match occured at insertion point
            assertEquals(m.start(), insertIndex);
        }
    }

    // Testing examples from a file

    /**
     * Goes through the file "TestCases.txt" and creates many patterns
     * described in the file, matching the patterns against input lines in
     * the file, and comparing the results against the correct results
     * also found in the file. The file format is described in comments
     * at the head of the file.
     */
    public static void processFile(String fileName) throws IOException {
        File testCases = new File(System.getProperty("test.src", "."),
                                  fileName);
        FileInputStream in = new FileInputStream(testCases);
        BufferedReader r = new BufferedReader(new InputStreamReader(in));

        // Process next test case.
        String aLine;
        while((aLine = r.readLine()) != null) {
            // Read a line for pattern
            String patternString = grabLine(r);
            Pattern p = null;
            try {
                p = compileTestPattern(patternString);
            } catch (PatternSyntaxException e) {
                String dataString = grabLine(r);
                String expectedResult = grabLine(r);
                if (expectedResult.startsWith("error"))
                    continue;
                String line1 = "----------------------------------------";
                String line2 = "Pattern = " + patternString;
                String line3 = "Data = " + dataString;
                fail(line1 + System.lineSeparator() + line2 + System.lineSeparator() + line3 + System.lineSeparator());
                continue;
            }

            // Read a line for input string
            String dataString = grabLine(r);
            Matcher m = p.matcher(dataString);
            StringBuilder result = new StringBuilder();

            // Check for IllegalStateExceptions before a match
            preMatchInvariants(m);

            boolean found = m.find();

            if (found)
                postTrueMatchInvariants(m);
            else
                postFalseMatchInvariants(m);

            if (found) {
                result.append("true ");
                result.append(m.group(0)).append(" ");
            } else {
                result.append("false ");
            }

            result.append(m.groupCount());

            if (found) {
                for (int i=1; i<m.groupCount()+1; i++)
                    if (m.group(i) != null)
                        result.append(" ").append(m.group(i));
            }

            // Read a line for the expected result
            String expectedResult = grabLine(r);

            assertEquals(result.toString(), expectedResult,
                "Pattern = " + patternString +
                System.lineSeparator() +
                "Data = " + dataString +
                System.lineSeparator() +
                "Expected = " + expectedResult +
                System.lineSeparator() +
                "Actual   = " + result.toString());
        }
    }

    private static void preMatchInvariants(Matcher m) {
        assertThrows(IllegalStateException.class, m::start);
        assertThrows(IllegalStateException.class, m::end);
        assertThrows(IllegalStateException.class, m::group);
    }

    private static void postFalseMatchInvariants(Matcher m) {
        assertThrows(IllegalStateException.class, m::group);
        assertThrows(IllegalStateException.class, m::start);
        assertThrows(IllegalStateException.class, m::end);
    }

    private static void postTrueMatchInvariants(Matcher m) {
        assertEquals(m.start(), m.start(0));
        assertEquals(m.start(), m.start(0));
        assertEquals(m.group(), m.group(0));
        assertThrows(IndexOutOfBoundsException.class, () -> m.group(50));
    }

    private static Pattern compileTestPattern(String patternString) {
        if (!patternString.startsWith("'")) {
            return Pattern.compile(patternString);
        }
        int break1 = patternString.lastIndexOf("'");
        String flagString = patternString.substring(break1+1);
        patternString = patternString.substring(1, break1);

        if (flagString.equals("i"))
            return Pattern.compile(patternString, Pattern.CASE_INSENSITIVE);

        if (flagString.equals("m"))
            return Pattern.compile(patternString, Pattern.MULTILINE);

        return Pattern.compile(patternString);
    }

    /**
     * Reads a line from the input file. Keeps reading lines until a non
     * empty non comment line is read. If the line contains a \n then
     * these two characters are replaced by a newline char. If a \\uxxxx
     * sequence is read then the sequence is replaced by the unicode char.
     */
    public static String grabLine(BufferedReader r) throws IOException {
        int index = 0;
        String line = r.readLine();
        while (line.startsWith("//") || line.length() < 1)
            line = r.readLine();
        while ((index = line.indexOf("\\n")) != -1) {
            StringBuilder temp = new StringBuilder(line);
            temp.replace(index, index+2, "\n");
            line = temp.toString();
        }
        while ((index = line.indexOf("\\u")) != -1) {
            StringBuilder temp = new StringBuilder(line);
            String value = temp.substring(index+2, index+6);
            char aChar = (char)Integer.parseInt(value, 16);
            String unicodeChar = "" + aChar;
            temp.replace(index, index+6, unicodeChar);
            line = temp.toString();
        }

        return line;
    }



    @Test
    public static void namedGroupCaptureTest() {
        check(Pattern.compile("x+(?<gname>y+)z+"),
              "xxxyyyzzz",
              "gname",
              "yyy");

        check(Pattern.compile("x+(?<gname8>y+)z+"),
              "xxxyyyzzz",
              "gname8",
              "yyy");

        //backref
        Pattern pattern = Pattern.compile("(a*)bc\\1");
        check(pattern, "zzzaabcazzz", true);  // found "abca"

        check(Pattern.compile("(?<gname>a*)bc\\k<gname>"),
              "zzzaabcaazzz", true);

        check(Pattern.compile("(?<gname>abc)(def)\\k<gname>"),
              "abcdefabc", true);

        check(Pattern.compile("(a)(b)(c)(d)(e)(f)(g)(h)(i)(j)(?<gname>k)\\k<gname>"),
              "abcdefghijkk", true);

        // Supplementary character tests
        check(Pattern.compile("(?<gname>" + toSupplementaries("a*)bc") + "\\k<gname>"),
              toSupplementaries("zzzaabcazzz"), true);

        check(Pattern.compile("(?<gname>" + toSupplementaries("a*)bc") + "\\k<gname>"),
              toSupplementaries("zzzaabcaazzz"), true);

        check(Pattern.compile("(?<gname>" + toSupplementaries("abc)(def)") + "\\k<gname>"),
              toSupplementaries("abcdefabc"), true);

        check(Pattern.compile(toSupplementaries("(a)(b)(c)(d)(e)(f)(g)(h)(i)(j)") +
                              "(?<gname>" +
                              toSupplementaries("k)") + "\\k<gname>"),
              toSupplementaries("abcdefghijkk"), true);

        check(Pattern.compile("x+(?<gname>y+)z+\\k<gname>"),
              "xxxyyyzzzyyy",
              "gname",
              "yyy");

        //replaceFirst/All
        checkReplaceFirst("(?<gn>ab)(c*)",
                          "abccczzzabcczzzabccc",
                          "${gn}",
                          "abzzzabcczzzabccc");

        checkReplaceAll("(?<gn>ab)(c*)",
                        "abccczzzabcczzzabccc",
                        "${gn}",
                        "abzzzabzzzab");


        checkReplaceFirst("(?<gn>ab)(c*)",
                          "zzzabccczzzabcczzzabccczzz",
                          "${gn}",
                          "zzzabzzzabcczzzabccczzz");

        checkReplaceAll("(?<gn>ab)(c*)",
                        "zzzabccczzzabcczzzabccczzz",
                        "${gn}",
                        "zzzabzzzabzzzabzzz");

        checkReplaceFirst("(?<gn1>ab)(?<gn2>c*)",
                          "zzzabccczzzabcczzzabccczzz",
                          "${gn2}",
                          "zzzccczzzabcczzzabccczzz");

        checkReplaceAll("(?<gn1>ab)(?<gn2>c*)",
                        "zzzabccczzzabcczzzabccczzz",
                        "${gn2}",
                        "zzzccczzzcczzzccczzz");

        //toSupplementaries("(ab)(c*)"));
        checkReplaceFirst("(?<gn1>" + toSupplementaries("ab") +
                           ")(?<gn2>" + toSupplementaries("c") + "*)",
                          toSupplementaries("abccczzzabcczzzabccc"),
                          "${gn1}",
                          toSupplementaries("abzzzabcczzzabccc"));


        checkReplaceAll("(?<gn1>" + toSupplementaries("ab") +
                        ")(?<gn2>" + toSupplementaries("c") + "*)",
                        toSupplementaries("abccczzzabcczzzabccc"),
                        "${gn1}",
                        toSupplementaries("abzzzabzzzab"));

        checkReplaceFirst("(?<gn1>" + toSupplementaries("ab") +
                           ")(?<gn2>" + toSupplementaries("c") + "*)",
                          toSupplementaries("abccczzzabcczzzabccc"),
                          "${gn2}",
                          toSupplementaries("ccczzzabcczzzabccc"));


        checkReplaceAll("(?<gn1>" + toSupplementaries("ab") +
                        ")(?<gn2>" + toSupplementaries("c") + "*)",
                        toSupplementaries("abccczzzabcczzzabccc"),
                        "${gn2}",
                        toSupplementaries("ccczzzcczzzccc"));

        checkReplaceFirst("(?<dog>Dog)AndCat",
                          "zzzDogAndCatzzzDogAndCatzzz",
                          "${dog}",
                          "zzzDogzzzDogAndCatzzz");


        checkReplaceAll("(?<dog>Dog)AndCat",
                          "zzzDogAndCatzzzDogAndCatzzz",
                          "${dog}",
                          "zzzDogzzzDogzzz");

        // backref in Matcher & String
        assertTrue("abcdefghij".replaceFirst("cd(?<gn>ef)gh", "${gn}").equals("abefij") &&
                   "abbbcbdbefgh".replaceAll("(?<gn>[a-e])b", "${gn}").equals("abcdefgh"));

        // negative
        checkExpectedFail("(?<groupnamehasnoascii.in>abc)(def)");
        checkExpectedFail("(?<groupnamehasnoascii_in>abc)(def)");
        checkExpectedFail("(?<6groupnamestartswithdigit>abc)(def)");
        checkExpectedFail("(?<gname>abc)(def)\\k<gnameX>");
        checkExpectedFail("(?<gname>abc)(?<gname>def)\\k<gnameX>");

        Matcher iaeMatcher = Pattern.compile("(?<gname>abc)(def)").matcher("abcdef");
        iaeMatcher.find();
        assertThrows(IllegalArgumentException.class, () -> iaeMatcher.group("gnameX"));
        assertThrows(IllegalArgumentException.class, () -> iaeMatcher.start("gnameX"));
        assertThrows(IllegalArgumentException.class, () -> iaeMatcher.start("gnameX"));

        Matcher npeMatcher = Pattern.compile("(?<gname>abc)(def)").matcher("abcdef");
        npeMatcher.find();
        assertThrows(NullPointerException.class, () -> npeMatcher.group(null));
        assertThrows(NullPointerException.class, () -> npeMatcher.start(null));
        assertThrows(NullPointerException.class, () -> npeMatcher.end(null));
    }

    // This is for bug 6919132
    @Test
    public static void nonBmpClassComplementTest() {
        Pattern p = Pattern.compile("\\P{Lu}");
        Matcher m = p.matcher(new String(new int[] {0x1d400}, 0, 1));

        assertFalse(m.find() && m.start() == 1);

        // from a unicode category
        p = Pattern.compile("\\P{Lu}");
        m = p.matcher(new String(new int[] {0x1d400}, 0, 1));
        assertFalse(m.find());
        assertTrue(m.hitEnd());

        // block
        p = Pattern.compile("\\P{InMathematicalAlphanumericSymbols}");
        m = p.matcher(new String(new int[] {0x1d400}, 0, 1));
        assertFalse(m.find() && m.start() == 1);

        p = Pattern.compile("\\P{sc=GRANTHA}");
        m = p.matcher(new String(new int[] {0x11350}, 0, 1));
        assertFalse(m.find() && m.start() == 1);
    }

    @Test
    public static void unicodePropertiesTest() {
        // different forms
        assertFalse(!Pattern.compile("\\p{IsLu}").matcher("A").matches() ||
                    !Pattern.compile("\\p{Lu}").matcher("A").matches() ||
                    !Pattern.compile("\\p{gc=Lu}").matcher("A").matches() ||
                    !Pattern.compile("\\p{general_category=Lu}").matcher("A").matches() ||
                    !Pattern.compile("\\p{IsLatin}").matcher("B").matches() ||
                    !Pattern.compile("\\p{sc=Latin}").matcher("B").matches() ||
                    !Pattern.compile("\\p{script=Latin}").matcher("B").matches() ||
                    !Pattern.compile("\\p{InBasicLatin}").matcher("c").matches() ||
                    !Pattern.compile("\\p{blk=BasicLatin}").matcher("c").matches() ||
                    !Pattern.compile("\\p{block=BasicLatin}").matcher("c").matches());

        Matcher common  = Pattern.compile("\\p{script=Common}").matcher("");
        Matcher unknown = Pattern.compile("\\p{IsUnknown}").matcher("");
        Matcher lastSM  = common;
        Character.UnicodeScript lastScript = Character.UnicodeScript.of(0);

        Matcher latin  = Pattern.compile("\\p{block=basic_latin}").matcher("");
        Matcher greek  = Pattern.compile("\\p{InGreek}").matcher("");
        Matcher lastBM = latin;
        Character.UnicodeBlock lastBlock = Character.UnicodeBlock.of(0);

        for (int cp = 1; cp < Character.MAX_CODE_POINT; cp++) {
            if (cp >= 0x30000 && (cp & 0x70) == 0){
                continue;  // only pick couple code points, they are the same
            }

            // Unicode Script
            Character.UnicodeScript script = Character.UnicodeScript.of(cp);
            Matcher m;
            String str = new String(Character.toChars(cp));
            if (script == lastScript) {
                 m = lastSM;
                 m.reset(str);
            } else {
                 m  = Pattern.compile("\\p{Is" + script.name() + "}").matcher(str);
            }
            assertTrue(m.matches());

            Matcher other = (script == Character.UnicodeScript.COMMON)? unknown : common;
            other.reset(str);
            assertFalse(other.matches());
            lastSM = m;
            lastScript = script;

            // Unicode Block
            Character.UnicodeBlock block = Character.UnicodeBlock.of(cp);
            if (block == null) {
                //System.out.printf("Not a Block: cp=%x%n", cp);
                continue;
            }
            if (block == lastBlock) {
                 m = lastBM;
                 m.reset(str);
            } else {
                 m  = Pattern.compile("\\p{block=" + block.toString() + "}").matcher(str);
            }
            assertTrue(m.matches());
            other = (block == Character.UnicodeBlock.BASIC_LATIN)? greek : latin;
            other.reset(str);
            assertFalse(other.matches());
            lastBM = m;
            lastBlock = block;
        }
    }

    @Test
    public static void unicodeHexNotationTest() {

        // negative
        checkExpectedFail("\\x{-23}");
        checkExpectedFail("\\x{110000}");
        checkExpectedFail("\\x{}");
        checkExpectedFail("\\x{AB[ef]");

        // codepoint
        check("^\\x{1033c}$",              "\uD800\uDF3C", true);
        check("^\\xF0\\x90\\x8C\\xBC$",    "\uD800\uDF3C", false);
        check("^\\x{D800}\\x{DF3c}+$",     "\uD800\uDF3C", false);
        check("^\\xF0\\x90\\x8C\\xBC$",    "\uD800\uDF3C", false);

        // in class
        check("^[\\x{D800}\\x{DF3c}]+$",   "\uD800\uDF3C", false);
        check("^[\\xF0\\x90\\x8C\\xBC]+$", "\uD800\uDF3C", false);
        check("^[\\x{D800}\\x{DF3C}]+$",   "\uD800\uDF3C", false);
        check("^[\\x{DF3C}\\x{D800}]+$",   "\uD800\uDF3C", false);
        check("^[\\x{D800}\\x{DF3C}]+$",   "\uDF3C\uD800", true);
        check("^[\\x{DF3C}\\x{D800}]+$",   "\uDF3C\uD800", true);

        for (int cp = 0; cp <= 0x10FFFF; cp++) {
             String s = "A" + new String(Character.toChars(cp)) + "B";
             String hexUTF16 = (cp <= 0xFFFF)? String.format("\\u%04x", cp)
                                             : String.format("\\u%04x\\u%04x",
                                               (int) Character.toChars(cp)[0],
                                               (int) Character.toChars(cp)[1]);
             String hexCodePoint = "\\x{" + Integer.toHexString(cp) + "}";
             assertTrue(Pattern.matches("A" + hexUTF16 + "B", s));
             assertTrue(Pattern.matches("A[" + hexUTF16 + "]B", s));
             assertTrue(Pattern.matches("A" + hexCodePoint + "B", s));
             assertTrue(Pattern.matches("A[" + hexCodePoint + "]B", s));
         }
    }

    @Test
    public static void unicodeClassesTest() {

        Matcher lower  = Pattern.compile("\\p{Lower}").matcher("");
        Matcher upper  = Pattern.compile("\\p{Upper}").matcher("");
        Matcher ASCII  = Pattern.compile("\\p{ASCII}").matcher("");
        Matcher alpha  = Pattern.compile("\\p{Alpha}").matcher("");
        Matcher digit  = Pattern.compile("\\p{Digit}").matcher("");
        Matcher alnum  = Pattern.compile("\\p{Alnum}").matcher("");
        Matcher punct  = Pattern.compile("\\p{Punct}").matcher("");
        Matcher graph  = Pattern.compile("\\p{Graph}").matcher("");
        Matcher print  = Pattern.compile("\\p{Print}").matcher("");
        Matcher blank  = Pattern.compile("\\p{Blank}").matcher("");
        Matcher cntrl  = Pattern.compile("\\p{Cntrl}").matcher("");
        Matcher xdigit = Pattern.compile("\\p{XDigit}").matcher("");
        Matcher space  = Pattern.compile("\\p{Space}").matcher("");
        Matcher bound  = Pattern.compile("\\b").matcher("");
        Matcher word   = Pattern.compile("\\w++").matcher("");
        // UNICODE_CHARACTER_CLASS
        Matcher lowerU  = Pattern.compile("\\p{Lower}", Pattern.UNICODE_CHARACTER_CLASS).matcher("");
        Matcher upperU  = Pattern.compile("\\p{Upper}", Pattern.UNICODE_CHARACTER_CLASS).matcher("");
        Matcher ASCIIU  = Pattern.compile("\\p{ASCII}", Pattern.UNICODE_CHARACTER_CLASS).matcher("");
        Matcher alphaU  = Pattern.compile("\\p{Alpha}", Pattern.UNICODE_CHARACTER_CLASS).matcher("");
        Matcher digitU  = Pattern.compile("\\p{Digit}", Pattern.UNICODE_CHARACTER_CLASS).matcher("");
        Matcher alnumU  = Pattern.compile("\\p{Alnum}", Pattern.UNICODE_CHARACTER_CLASS).matcher("");
        Matcher punctU  = Pattern.compile("\\p{Punct}", Pattern.UNICODE_CHARACTER_CLASS).matcher("");
        Matcher graphU  = Pattern.compile("\\p{Graph}", Pattern.UNICODE_CHARACTER_CLASS).matcher("");
        Matcher printU  = Pattern.compile("\\p{Print}", Pattern.UNICODE_CHARACTER_CLASS).matcher("");
        Matcher blankU  = Pattern.compile("\\p{Blank}", Pattern.UNICODE_CHARACTER_CLASS).matcher("");
        Matcher cntrlU  = Pattern.compile("\\p{Cntrl}", Pattern.UNICODE_CHARACTER_CLASS).matcher("");
        Matcher xdigitU = Pattern.compile("\\p{XDigit}", Pattern.UNICODE_CHARACTER_CLASS).matcher("");
        Matcher spaceU  = Pattern.compile("\\p{Space}", Pattern.UNICODE_CHARACTER_CLASS).matcher("");
        Matcher boundU  = Pattern.compile("\\b", Pattern.UNICODE_CHARACTER_CLASS).matcher("");
        Matcher wordU   = Pattern.compile("\\w", Pattern.UNICODE_CHARACTER_CLASS).matcher("");
        // embedded flag (?U)
        Matcher lowerEU  = Pattern.compile("(?U)\\p{Lower}", Pattern.UNICODE_CHARACTER_CLASS).matcher("");
        Matcher graphEU  = Pattern.compile("(?U)\\p{Graph}", Pattern.UNICODE_CHARACTER_CLASS).matcher("");
        Matcher wordEU   = Pattern.compile("(?U)\\w", Pattern.UNICODE_CHARACTER_CLASS).matcher("");

        Matcher bwb    = Pattern.compile("\\b\\w\\b").matcher("");
        Matcher bwbU   = Pattern.compile("\\b\\w++\\b", Pattern.UNICODE_CHARACTER_CLASS).matcher("");
        Matcher bwbEU  = Pattern.compile("(?U)\\b\\w++\\b", Pattern.UNICODE_CHARACTER_CLASS).matcher("");
        // properties
        Matcher lowerP  = Pattern.compile("\\p{IsLowerCase}").matcher("");
        Matcher upperP  = Pattern.compile("\\p{IsUpperCase}").matcher("");
        Matcher titleP  = Pattern.compile("\\p{IsTitleCase}").matcher("");
        Matcher letterP = Pattern.compile("\\p{IsLetter}").matcher("");
        Matcher alphaP  = Pattern.compile("\\p{IsAlphabetic}").matcher("");
        Matcher ideogP  = Pattern.compile("\\p{IsIdeographic}").matcher("");
        Matcher cntrlP  = Pattern.compile("\\p{IsControl}").matcher("");
        Matcher spaceP  = Pattern.compile("\\p{IsWhiteSpace}").matcher("");
        Matcher definedP = Pattern.compile("\\p{IsAssigned}").matcher("");
        Matcher nonCCPP = Pattern.compile("\\p{IsNoncharacterCodePoint}").matcher("");
        Matcher joinCrtl = Pattern.compile("\\p{IsJoinControl}").matcher("");
        // javaMethod
        Matcher lowerJ  = Pattern.compile("\\p{javaLowerCase}").matcher("");
        Matcher upperJ  = Pattern.compile("\\p{javaUpperCase}").matcher("");
        Matcher alphaJ  = Pattern.compile("\\p{javaAlphabetic}").matcher("");
        Matcher ideogJ  = Pattern.compile("\\p{javaIdeographic}").matcher("");
        // GC/C
        Matcher gcC  = Pattern.compile("\\p{C}").matcher("");

        for (int cp = 1; cp < 0x30000; cp++) {
            String str = new String(Character.toChars(cp));
            int type = Character.getType(cp);
            if (// lower
                POSIX_ASCII.isLower(cp)   != lower.reset(str).matches()  ||
                Character.isLowerCase(cp) != lowerU.reset(str).matches() ||
                Character.isLowerCase(cp) != lowerP.reset(str).matches() ||
                Character.isLowerCase(cp) != lowerEU.reset(str).matches()||
                Character.isLowerCase(cp) != lowerJ.reset(str).matches()||
                // upper
                POSIX_ASCII.isUpper(cp)   != upper.reset(str).matches()  ||
                POSIX_Unicode.isUpper(cp) != upperU.reset(str).matches() ||
                Character.isUpperCase(cp) != upperP.reset(str).matches() ||
                Character.isUpperCase(cp) != upperJ.reset(str).matches() ||
                // alpha
                POSIX_ASCII.isAlpha(cp)   != alpha.reset(str).matches()  ||
                POSIX_Unicode.isAlpha(cp) != alphaU.reset(str).matches() ||
                Character.isAlphabetic(cp)!= alphaP.reset(str).matches() ||
                Character.isAlphabetic(cp)!= alphaJ.reset(str).matches() ||
                // digit
                POSIX_ASCII.isDigit(cp)   != digit.reset(str).matches()  ||
                Character.isDigit(cp)     != digitU.reset(str).matches() ||
                // alnum
                POSIX_ASCII.isAlnum(cp)   != alnum.reset(str).matches()  ||
                POSIX_Unicode.isAlnum(cp) != alnumU.reset(str).matches() ||
                // punct
                POSIX_ASCII.isPunct(cp)   != punct.reset(str).matches()  ||
                POSIX_Unicode.isPunct(cp) != punctU.reset(str).matches() ||
                // graph
                POSIX_ASCII.isGraph(cp)   != graph.reset(str).matches()  ||
                POSIX_Unicode.isGraph(cp) != graphU.reset(str).matches() ||
                POSIX_Unicode.isGraph(cp) != graphEU.reset(str).matches()||
                // blank
                POSIX_ASCII.isType(cp, POSIX_ASCII.BLANK)
                                          != blank.reset(str).matches()  ||
                POSIX_Unicode.isBlank(cp) != blankU.reset(str).matches() ||
                // print
                POSIX_ASCII.isPrint(cp)   != print.reset(str).matches()  ||
                POSIX_Unicode.isPrint(cp) != printU.reset(str).matches() ||
                // cntrl
                POSIX_ASCII.isCntrl(cp)   != cntrl.reset(str).matches()  ||
                POSIX_Unicode.isCntrl(cp) != cntrlU.reset(str).matches() ||
                (Character.CONTROL == type) != cntrlP.reset(str).matches() ||
                // hexdigit
                POSIX_ASCII.isHexDigit(cp)   != xdigit.reset(str).matches()  ||
                POSIX_Unicode.isHexDigit(cp) != xdigitU.reset(str).matches() ||
                // space
                POSIX_ASCII.isSpace(cp)   != space.reset(str).matches()  ||
                POSIX_Unicode.isSpace(cp) != spaceU.reset(str).matches() ||
                POSIX_Unicode.isSpace(cp) != spaceP.reset(str).matches() ||
                // word
                POSIX_ASCII.isWord(cp)   != word.reset(str).matches()  ||
                POSIX_Unicode.isWord(cp) != wordU.reset(str).matches() ||
                POSIX_Unicode.isWord(cp) != wordEU.reset(str).matches()||
                // bwordb
                POSIX_ASCII.isWord(cp) != bwb.reset(str).matches() ||
                POSIX_Unicode.isWord(cp) != bwbU.reset(str).matches() ||
                // properties
                Character.isTitleCase(cp) != titleP.reset(str).matches() ||
                Character.isLetter(cp)    != letterP.reset(str).matches()||
                Character.isIdeographic(cp) != ideogP.reset(str).matches() ||
                Character.isIdeographic(cp) != ideogJ.reset(str).matches() ||
                (Character.UNASSIGNED == type) == definedP.reset(str).matches() ||
                POSIX_Unicode.isNoncharacterCodePoint(cp) != nonCCPP.reset(str).matches() ||
                POSIX_Unicode.isJoinControl(cp) != joinCrtl.reset(str).matches() ||
                // gc_C
                (Character.CONTROL == type || Character.FORMAT == type ||
                 Character.PRIVATE_USE == type || Character.SURROGATE == type ||
                 Character.UNASSIGNED == type)
                != gcC.reset(str).matches()) {
                fail();
            }
        }

        // bounds/word align
        twoFindIndexes(" \u0180sherman\u0400 ", boundU, 1, 10);
        assertTrue(bwbU.reset("\u0180sherman\u0400").matches());
        twoFindIndexes(" \u0180sh\u0345erman\u0400 ", boundU, 1, 11);
        assertTrue(bwbU.reset("\u0180sh\u0345erman\u0400").matches());
        twoFindIndexes(" \u0724\u0739\u0724 ", boundU, 1, 4);
        assertTrue(bwbU.reset("\u0724\u0739\u0724").matches());
        assertTrue(bwbEU.reset("\u0724\u0739\u0724").matches());
    }

    @Test
    public static void unicodeCharacterNameTest() {

        for (int cp = 0; cp < Character.MAX_CODE_POINT; cp++) {
            if (!Character.isValidCodePoint(cp) ||
                Character.getType(cp) == Character.UNASSIGNED)
                continue;
            String str = new String(Character.toChars(cp));
            // single
            String p = "\\N{" + Character.getName(cp) + "}";
            assertTrue(Pattern.compile(p).matcher(str).matches());
            // class[c]
            p = "[\\N{" + Character.getName(cp) + "}]";
            assertTrue(Pattern.compile(p).matcher(str).matches());
        }

        // range
        for (int i = 0; i < 10; i++) {
            int start = generator.nextInt(20);
            int end = start + generator.nextInt(200);
            String p = "[\\N{" + Character.getName(start) + "}-\\N{" + Character.getName(end) + "}]";
            String str;
            for (int cp = start; cp < end; cp++) {
                str = new String(Character.toChars(cp));
                assertTrue(Pattern.compile(p).matcher(str).matches());
            }
            str = new String(Character.toChars(end + 10));
            assertFalse(Pattern.compile(p).matcher(str).matches());
        }

        // slice
        for (int i = 0; i < 10; i++) {
            int n = generator.nextInt(256);
            int[] buf = new int[n];
            StringBuilder sb = new StringBuilder(1024);
            for (int j = 0; j < n; j++) {
                int cp = generator.nextInt(1000);
                if (!Character.isValidCodePoint(cp) ||
                    Character.getType(cp) == Character.UNASSIGNED)
                    cp = 0x4e00;    // just use 4e00
                sb.append("\\N{").append(Character.getName(cp)).append("}");
                buf[j] = cp;
            }
            String p = sb.toString();
            String str = new String(buf, 0, buf.length);
            assertTrue(Pattern.compile(p).matcher(str).matches());
        }
    }

    @Test
    public static void horizontalAndVerticalWSTest() {
        String hws = new String (new char[] {
                                     0x09, 0x20, 0xa0, 0x1680, 0x180e,
                                     0x2000, 0x2001, 0x2002, 0x2003, 0x2004, 0x2005,
                                     0x2006, 0x2007, 0x2008, 0x2009, 0x200a,
                                     0x202f, 0x205f, 0x3000 });
        String vws = new String (new char[] {
                                     0x0a, 0x0b, 0x0c, 0x0d, 0x85, 0x2028, 0x2029 });
        assertTrue(Pattern.compile("\\h+").matcher(hws).matches() &&
                   Pattern.compile("[\\h]+").matcher(hws).matches());
        assertTrue(!Pattern.compile("\\H").matcher(hws).find() &&
                   !Pattern.compile("[\\H]").matcher(hws).find());
        assertTrue(Pattern.compile("\\v+").matcher(vws).matches() &&
                   Pattern.compile("[\\v]+").matcher(vws).matches());
        assertTrue(!Pattern.compile("\\V").matcher(vws).find() &&
                   !Pattern.compile("[\\V]").matcher(vws).find());
        String prefix = "abcd";
        String suffix = "efgh";
        String ng = "A";
        for (int i = 0; i < hws.length(); i++) {
            String c = String.valueOf(hws.charAt(i));
            Matcher m = Pattern.compile("\\h").matcher(prefix + c + suffix);
            assertTrue(m.find() && c.equals(m.group()));
            m = Pattern.compile("[\\h]").matcher(prefix + c + suffix);
            assertTrue(m.find() && c.equals(m.group()));

            String matcherSubstring = hws.substring(0, i) + ng + hws.substring(i);

            m = Pattern.compile("\\H").matcher(matcherSubstring);
            assertTrue(m.find() && ng.equals(m.group()));
            m = Pattern.compile("[\\H]").matcher(matcherSubstring);
            assertTrue(m.find() && ng.equals(m.group()));
        }
        for (int i = 0; i < vws.length(); i++) {
            String c = String.valueOf(vws.charAt(i));
            Matcher m = Pattern.compile("\\v").matcher(prefix + c + suffix);
            assertTrue(m.find() && c.equals(m.group()));
            m = Pattern.compile("[\\v]").matcher(prefix + c + suffix);
            assertTrue(m.find() && c.equals(m.group()));

            String matcherSubstring = vws.substring(0, i) + ng + vws.substring(i);
            m = Pattern.compile("\\V").matcher(matcherSubstring);
            assertTrue(m.find() && ng.equals(m.group()));
            m = Pattern.compile("[\\V]").matcher(matcherSubstring);
            assertTrue(m.find() && ng.equals(m.group()));
        }
        // \v in range is interpreted as 0x0B. This is the undocumented behavior
        assertTrue(Pattern.compile("[\\v-\\v]").matcher(String.valueOf((char)0x0B)).matches());
    }

    @Test
    public static void linebreakTest() {
        String linebreaks = new String (new char[] {
            0x0A, 0x0B, 0x0C, 0x0D, 0x85, 0x2028, 0x2029 });
        String crnl = "\r\n";
        assertTrue((Pattern.compile("\\R+").matcher(linebreaks).matches() &&
              Pattern.compile("\\R").matcher(crnl).matches() &&
              Pattern.compile("\\Rabc").matcher(crnl + "abc").matches() &&
              Pattern.compile("\\Rabc").matcher("\rabc").matches() &&
              Pattern.compile("\\R\\R").matcher(crnl).matches() &&  // backtracking
              Pattern.compile("\\R\\n").matcher(crnl).matches()) || // backtracking
              Pattern.compile("((?<!\\R)\\s)*").matcher(crnl).matches()); // #8176029
    }

    // #7189363
    @Test
    public static void branchTest() {
        assertFalse(!Pattern.compile("(a)?bc|d").matcher("d").find() ||     // greedy
                    !Pattern.compile("(a)+bc|d").matcher("d").find() ||
                    !Pattern.compile("(a)*bc|d").matcher("d").find() ||
                    !Pattern.compile("(a)??bc|d").matcher("d").find() ||    // reluctant
                    !Pattern.compile("(a)+?bc|d").matcher("d").find() ||
                    !Pattern.compile("(a)*?bc|d").matcher("d").find() ||
                    !Pattern.compile("(a)?+bc|d").matcher("d").find() ||    // possessive
                    !Pattern.compile("(a)++bc|d").matcher("d").find() ||
                    !Pattern.compile("(a)*+bc|d").matcher("d").find() ||
                    !Pattern.compile("(a)?bc|d").matcher("d").matches() ||  // greedy
                    !Pattern.compile("(a)+bc|d").matcher("d").matches() ||
                    !Pattern.compile("(a)*bc|d").matcher("d").matches() ||
                    !Pattern.compile("(a)??bc|d").matcher("d").matches() || // reluctant
                    !Pattern.compile("(a)+?bc|d").matcher("d").matches() ||
                    !Pattern.compile("(a)*?bc|d").matcher("d").matches() ||
                    !Pattern.compile("(a)?+bc|d").matcher("d").matches() || // possessive
                    !Pattern.compile("(a)++bc|d").matcher("d").matches() ||
                    !Pattern.compile("(a)*+bc|d").matcher("d").matches() ||
                    !Pattern.compile("(a)?bc|de").matcher("de").find() ||   // others
                    !Pattern.compile("(a)??bc|de").matcher("de").find() ||
                    !Pattern.compile("(a)?bc|de").matcher("de").matches() ||
                    !Pattern.compile("(a)??bc|de").matcher("de").matches());
    }

    // This test is for 8007395
    @Test
    public static void groupCurlyNotFoundSuppTest() {
        String input = "test this as \ud83d\ude0d";
        for (String pStr : new String[] { "test(.)+(@[a-zA-Z.]+)",
                                          "test(.)*(@[a-zA-Z.]+)",
                                          "test([^B])+(@[a-zA-Z.]+)",
                                          "test([^B])*(@[a-zA-Z.]+)",
                                          "test(\\P{IsControl})+(@[a-zA-Z.]+)",
                                          "test(\\P{IsControl})*(@[a-zA-Z.]+)",
                                        }) {
            Matcher m = Pattern.compile(pStr, Pattern.CASE_INSENSITIVE)
                               .matcher(input);
            assertFalse(m.find());
        }
    }

    // This test is for 8023647
    @Test
    public static void groupCurlyBackoffTest() {
        assertFalse(!"abc1c".matches("(\\w)+1\\1") ||
                    "abc11".matches("(\\w)+1\\1"));
    }

    // This test is for 8012646
    @Test
    public static void patternAsPredicate() {
        Predicate<String> p = Pattern.compile("[a-z]+").asPredicate();

        assertFalse(p.test(""));
        assertTrue(p.test("word"));
        assertFalse(p.test("1234"));
        assertTrue(p.test("word1234"));
    }

    // This test is for 8184692
    @Test
    public static void patternAsMatchPredicate() {
        Predicate<String> p = Pattern.compile("[a-z]+").asMatchPredicate();

        assertFalse(p.test(""));
        assertTrue(p.test("word"));
        assertFalse(p.test("1234word"));
        assertFalse(p.test("1234"));
    }


    // This test is for 8035975
    @Test
    public static void invalidFlags() {
        for (int flag = 1; flag != 0; flag <<= 1) {
            switch (flag) {
            case Pattern.CASE_INSENSITIVE:
            case Pattern.MULTILINE:
            case Pattern.DOTALL:
            case Pattern.UNICODE_CASE:
            case Pattern.CANON_EQ:
            case Pattern.UNIX_LINES:
            case Pattern.LITERAL:
            case Pattern.UNICODE_CHARACTER_CLASS:
            case Pattern.COMMENTS:
                // valid flag, continue
                break;
            default:
                int finalFlag = flag;
                assertThrows(IllegalArgumentException.class, () ->
                    Pattern.compile(".", finalFlag));
            }
        }
    }

    // This test is for 8158482
    @Test
    public static void embeddedFlags() {
            //Runs without exception.
            Pattern.compile("(?i).(?-i).");
            Pattern.compile("(?m).(?-m).");
            Pattern.compile("(?s).(?-s).");
            Pattern.compile("(?d).(?-d).");
            Pattern.compile("(?u).(?-u).");
            Pattern.compile("(?c).(?-c).");
            Pattern.compile("(?x).(?-x).");
            Pattern.compile("(?U).(?-U).");
            Pattern.compile("(?imsducxU).(?-imsducxU).");
    }

    @Test
    public static void grapheme() throws Exception {
        final int[] lineNumber = new int[1];
        Stream.concat(Files.lines(UCDFiles.GRAPHEME_BREAK_TEST),
                Files.lines(Paths.get(System.getProperty("test.src", "."), "GraphemeTestCases.txt")))
            .forEach( ln -> {
                    lineNumber[0]++;
                    if (ln.length() == 0 || ln.startsWith("#")) {
                        return;
                    }
                    ln = ln.replaceAll("\\s+|\\([a-zA-Z]+\\)|\\[[a-zA-Z]]+\\]|#.*", "");
                    // System.out.println(str);
                    String[] strs = ln.split("\u00f7|\u00d7");
                    StringBuilder src = new StringBuilder();
                    ArrayList<String> graphemes = new ArrayList<>();
                    StringBuilder buf = new StringBuilder();
                    int offBk = 0;
                    for (String str : strs) {
                        if (str.length() == 0)  // first empty str
                            continue;
                        int cp = Integer.parseInt(str, 16);
                        src.appendCodePoint(cp);
                        buf.appendCodePoint(cp);
                        offBk += (str.length() + 1);
                        if (ln.charAt(offBk) == '\u00f7') {    // DIV
                            graphemes.add(buf.toString());
                            buf = new StringBuilder();
                        }
                    }
                    Pattern p = Pattern.compile("\\X");
                    // (1) test \X directly
                    Matcher m = p.matcher(src.toString());
                    for (String g : graphemes) {
                        // System.out.printf("     grapheme:=[%s]%n", g);
                        String group = null;
                        if (!m.find() || !(group = m.group()).equals(g)) {
                                 fail("Failed pattern \\X [" + ln + "] : "
                                    + "expected: " + g + " - actual: " + group
                                    + "(line " + lineNumber[0] + ")");
                        }
                    }
                    assertFalse(m.find());
                    // test \b{g} without \X via Pattern
                    Pattern pbg = Pattern.compile("\\b{g}");
                    m = pbg.matcher(src.toString());
                    m.find();
                    int prev = m.end();
                    for (String g : graphemes) {
                        String group = null;
                        if (!m.find() || !(group = src.substring(prev, m.end())).equals(g)) {
                                 fail("Failed pattern \\b{g} [" + ln + "] : "
                                    + "expected: " + g + " - actual: " + group
                                    + "(line " + lineNumber[0] + ")");
                        }
                        assertEquals("", m.group());
                        prev = m.end();
                    }
                    assertFalse(m.find());
                    // (2) test \b{g} + \X  via Scanner
                    Scanner s = new Scanner(src.toString()).useDelimiter("\\b{g}");
                    for (String g : graphemes) {
                        String next = null;
                        if (!s.hasNext(p) || !(next = s.next(p)).equals(g)) {
                                 fail("Failed \\b{g} [" + ln + "] : "
                                    + "expected: " + g + " - actual: " + next
                                    + " (line " + lineNumber[0] + ")");
                        }
                    }
                    assertFalse(s.hasNext(p));
                    // test \b{g} without \X via Scanner
                    s = new Scanner(src.toString()).useDelimiter("\\b{g}");
                    for (String g : graphemes) {
                        String next = null;
                        if (!s.hasNext() || !(next = s.next()).equals(g)) {
                                 fail("Failed \\b{g} [" + ln + "] : "
                                    + "expected: " + g + " - actual: " + next
                                    + " (line " + lineNumber[0] + ")");
                        }
                    }
                    assertFalse(s.hasNext());
                });
        // some sanity checks
        assertTrue(Pattern.compile("\\X{10}").matcher("abcdefghij").matches() &&
                   Pattern.compile("\\b{g}(?:\\X\\b{g}){5}\\b{g}").matcher("abcde").matches() &&
                   Pattern.compile("(?:\\X\\b{g}){2}").matcher("\ud800\udc00\ud801\udc02").matches());
        // make sure "\b{n}" still works
        assertTrue(Pattern.compile("\\b{1}hello\\b{1} \\b{1}world\\b{1}").matcher("hello world").matches());
    }

    // hangup/timeout if go into exponential backtracking
    @Test
    public static void expoBacktracking() {

        Object[][] patternMatchers = {
            // 6328855
            { "(.*\n*)*",
              "this little fine string lets\r\njava.lang.String.matches\r\ncrash\r\n(We don't know why but adding \r* to the regex makes it work again)",
              false },
            // 6192895
            { " *([a-zA-Z0-9/\\-\\?:\\(\\)\\.,'\\+\\{\\}]+ *)+",
              "Hello World this is a test this is a test this is a test A",
              true },
            { " *([a-zA-Z0-9/\\-\\?:\\(\\)\\.,'\\+\\{\\}]+ *)+",
              "Hello World this is a test this is a test this is a test \u4e00 ",
              false },
            { " *([a-z0-9]+ *)+",
              "hello world this is a test this is a test this is a test A",
              false },
            // 4771934 [FIXED] #5013651?
            { "^(\\w+([\\.-]?\\w+)*@\\w+([\\.-]?\\w+)*(\\.\\w{2,4})+[,;]?)+$",
              "abc@efg.abc,efg@abc.abc,abc@xyz.mno;abc@sdfsd.com",
              true },
            // 4866249 [FIXED]
            { "<\\s*" + "(meta|META)" + "(\\s|[^>])+" + "(CHARSET|charset)=" + "(\\s|[^>])+>",
              "<META http-equiv=\"Content-Type\" content=\"text/html; charset=ISO-8859-5\">",
              true },
            { "^(\\w+([\\.-]?\\w+)*@\\w+([\\.-]?\\w+)*(\\.\\w{2,4})+[,;]?)+$",
              "abc@efg.abc,efg@abc.abc,abc@xyz.mno;sdfsd.com",
              false },
            // 6345469
            { "((<[^>]+>)?(((\\s)?)*(\\&nbsp;)?)*((\\s)?)*)+",
              "&nbsp;&nbsp; < br/> &nbsp; < / p> <p> <html> <adfasfdasdf>&nbsp; </p>",
              true }, // --> matched
            { "((<[^>]+>)?(((\\s)?)*(\\&nbsp;)?)*((\\s)?)*)+",
              "&nbsp;&nbsp; < br/> &nbsp; < / p> <p> <html> <adfasfdasdf>&nbsp; p </p>",
              false },
            // 5026912
            { "^\\s*" + "(\\w|\\d|[\\xC0-\\xFF]|/)+" + "\\s+|$",
              "156580451111112225588087755221111111566969655555555",
              false},
            // 6988218
            { "^([+-]?((0[xX](\\p{XDigit}+))|(((\\p{Digit}+)(\\.)?((\\p{Digit}+)?)([eE][+-]?(\\p{Digit}+))?)|(\\.((\\p{Digit}+))([eE][+-]?(\\p{Digit}+))?)))|[n|N]?'([^']*(?:'')*[^']*)*')",
              "'%)) order by ANGEBOT.ID",
              false},    // find
            // 6693451
            { "^(\\s*foo\\s*)*$",
              "foo foo foo foo foo foo foo foo foo foo foo foo foo foo foo foo foo foo foo foo foo foo foo foo foo foo foo foo foo foo foo foo foo foo foo foo foo foo foo foo",
              true },
            { "^(\\s*foo\\s*)*$",
              "foo foo foo foo foo foo foo foo foo foo foo foo foo foo foo foo foo foo foo foo foo foo foo foo foo foo foo foo foo foo foo foo foo foo foo foo foo foo foo fo",
              false
            },
            // 7006761
            { "(([0-9A-Z]+)([_]?+)*)*", "FOOOOO_BAAAR_FOOOOOOOOO_BA_", true},
            { "(([0-9A-Z]+)([_]?+)*)*", "FOOOOO_BAAAR_FOOOOOOOOO_BA_ ", false},
            // 8140212
            { "(?<before>.*)\\{(?<reflection>\\w+):(?<innerMethod>\\w+(\\.?\\w+(\\(((?<args>(('[^']*')|((/|\\w)+))(,(('[^']*')|((/|\\w)+)))*))?\\))?)*)\\}(?<after>.*)",
              "{CeGlobal:getSodCutoff.getGui.getAmqp.getSimpleModeEnabled()",
              false
            },
            { "^(a+)+$", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", true},
            { "^(a+)+$", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa!", false},

            { "(x+)*y",  "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxy", true },
            { "(x+)*y",  "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxz", false},

            { "(x+x+)+y", "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxy", true},
            { "(x+x+)+y", "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxz", false},

            { "(([0-9A-Z]+)([_]?+)*)*", "--------------------------------------", false},

            /* not fixed
            //8132141   --->    second level exponential backtracking
            { "(h|h|ih(((i|a|c|c|a|i|i|j|b|a|i|b|a|a|j))+h)ahbfhba|c|i)*",
              "hchcchicihcchciiicichhcichcihcchiihichiciiiihhcchicchhcihchcihiihciichhccciccichcichiihcchcihhicchcciicchcccihiiihhihihihichicihhcciccchihhhcchichchciihiicihciihcccciciccicciiiiiiiiicihhhiiiihchccchchhhhiiihchihcccchhhiiiiiiiicicichicihcciciihichhhhchihciiihhiccccccciciihhichiccchhicchicihihccichicciihcichccihhiciccccccccichhhhihihhcchchihihiihhihihihicichihiiiihhhhihhhchhichiicihhiiiiihchccccchichci" },
            */
        };

        for (Object[] pm : patternMatchers) {
            String p = (String)pm[0];
            String s = (String)pm[1];
            boolean r = (Boolean)pm[2];
            assertEquals(r, Pattern.compile(p).matcher(s).matches());
        }
    }

    @Test
    public static void invalidGroupName() {
        // Invalid start of a group name
        for (String groupName : List.of("", ".", "0", "\u0040", "\u005b",
                "\u0060", "\u007b", "\u0416")) {
            for (String pat : List.of("(?<" + groupName + ">)",
                    "\\k<" + groupName + ">")) {
                var e = expectThrows(PatternSyntaxException.class, () -> Pattern.compile(pat));
                assertTrue(e.getMessage().startsWith(
                            "capturing group name does not start with a"
                            + " Latin letter"));
            }
        }
        // Invalid char in a group name
        for (String groupName : List.of("a.", "b\u0040", "c\u005b",
                "d\u0060", "e\u007b", "f\u0416")) {
            for (String pat : List.of("(?<" + groupName + ">)",
                    "\\k<" + groupName + ">")) {
                var e = expectThrows(PatternSyntaxException.class, () ->
                    Pattern.compile(pat));
                    assertTrue(e.getMessage().startsWith(
                            "named capturing group is missing trailing '>'"));
            }
        }
    }

    @Test
    public static void illegalRepetitionRange() {
        // huge integers > (2^31 - 1)
        String n = BigInteger.valueOf(1L << 32)
            .toString();
        String m = BigInteger.valueOf(1L << 31)
            .add(new BigInteger(80, generator))
            .toString();
        for (String rep : List.of("", "x", ".", ",", "-1", "2,1",
                n, n + ",", "0," + n, n + "," + m, m, m + ",", "0," + m)) {
            String pat = ".{" + rep + "}";
            var e = expectThrows(PatternSyntaxException.class, () ->
                    Pattern.compile(pat));
            assertTrue(e.getMessage().startsWith("Illegal repetition"));
        }
    }

    @Test
    public static void surrogatePairWithCanonEq() {
        //Runs without exception
        Pattern.compile("\ud834\udd21", Pattern.CANON_EQ);
    }

    public static String s2x(String s) {
        StringBuilder sb = new StringBuilder();
        for (char ch : s.toCharArray()) {
            sb.append(String.format("\\u%04x", (int)ch));
        }
        return sb.toString();
    }

    // This test is for 8235812, with cases excluded by 8258259
    @Test
    public static void lineBreakWithQuantifier() {
        // key:    pattern
        // value:  lengths of input that must match the pattern
        Map<String, List<Integer>> cases = Map.ofEntries(
            Map.entry("\\R?",      List.of(0, 1)),
            Map.entry("\\R*",      List.of(0, 1, 2, 3)),
            Map.entry("\\R+",      List.of(1, 2, 3)),
            Map.entry("\\R{0}",    List.of(0)),
            Map.entry("\\R{1}",    List.of(1)),
//          Map.entry("\\R{2}",    List.of(2)),            // 8258259
//          Map.entry("\\R{3}",    List.of(3)),            // 8258259
            Map.entry("\\R{0,}",   List.of(0, 1, 2, 3)),
            Map.entry("\\R{1,}",   List.of(1, 2, 3)),
//          Map.entry("\\R{2,}",   List.of(2, 3)),         // 8258259
//          Map.entry("\\R{3,}",   List.of(3)),            // 8258259
            Map.entry("\\R{0,0}",  List.of(0)),
            Map.entry("\\R{0,1}",  List.of(0, 1)),
            Map.entry("\\R{0,2}",  List.of(0, 1, 2)),
            Map.entry("\\R{0,3}",  List.of(0, 1, 2, 3)),
            Map.entry("\\R{1,1}",  List.of(1)),
            Map.entry("\\R{1,2}",  List.of(1, 2)),
            Map.entry("\\R{1,3}",  List.of(1, 2, 3)),
//          Map.entry("\\R{2,2}",  List.of(2)),            // 8258259
//          Map.entry("\\R{2,3}",  List.of(2, 3)),         // 8258259
//          Map.entry("\\R{3,3}",  List.of(3)),            // 8258259
            Map.entry("\\R",       List.of(1)),
            Map.entry("\\R\\R",    List.of(2)),
            Map.entry("\\R\\R\\R", List.of(3))
        );

        // key:    length of input
        // value:  all possible inputs of given length
        Map<Integer, List<String>> inputs = new HashMap<>();
        String[] Rs = { "\r\n", "\r", "\n",
                        "\u000B", "\u000C", "\u0085", "\u2028", "\u2029" };
        StringBuilder sb = new StringBuilder();
        for (int len = 0; len <= 3; ++len) {
            int[] idx = new int[len + 1];
            do {
                sb.setLength(0);
                for (int j = 0; j < len; ++j)
                    sb.append(Rs[idx[j]]);
                inputs.computeIfAbsent(len, ArrayList::new).add(sb.toString());
                idx[0]++;
                for (int j = 0; j < len; ++j) {
                    if (idx[j] < Rs.length)
                        break;
                    idx[j] = 0;
                    idx[j+1]++;
                }
            } while (idx[len] == 0);
        }

        // exhaustive testing
        for (String patStr : cases.keySet()) {
            Pattern[] pats = patStr.endsWith("R")
                ? new Pattern[] { Pattern.compile(patStr) }  // no quantifiers
                : new Pattern[] { Pattern.compile(patStr),          // greedy
                                  Pattern.compile(patStr + "?") };  // reluctant
            Matcher m = pats[0].matcher("");
            for (Pattern p : pats) {
                m.usePattern(p);
                for (int len : cases.get(patStr)) {
                    for (String in : inputs.get(len)) {
                        assertTrue(m.reset(in).matches(), "Expected to match '"
                                + s2x(in) + "' =~ /" + p + "/");
                    }
                }
            }
        }
    }

    // This test is for 8214245
    @Test
    public static void caseInsensitivePMatch() {
        for (String input : List.of("abcd", "AbCd", "ABCD")) {
            for (String pattern : List.of("abcd", "aBcD", "[a-d]{4}",
                    "(?:a|b|c|d){4}", "\\p{Lower}{4}", "\\p{Ll}{4}",
                    "\\p{IsLl}{4}", "\\p{gc=Ll}{4}",
                    "\\p{general_category=Ll}{4}", "\\p{IsLowercase}{4}",
                    "\\p{javaLowerCase}{4}", "\\p{Upper}{4}", "\\p{Lu}{4}",
                    "\\p{IsLu}{4}", "\\p{gc=Lu}{4}", "\\p{general_category=Lu}{4}",
                    "\\p{IsUppercase}{4}", "\\p{javaUpperCase}{4}",
                    "\\p{Lt}{4}", "\\p{IsLt}{4}", "\\p{gc=Lt}{4}",
                    "\\p{general_category=Lt}{4}", "\\p{IsTitlecase}{4}",
                    "\\p{javaTitleCase}{4}", "[\\p{Lower}]{4}", "[\\p{Ll}]{4}",
                    "[\\p{IsLl}]{4}", "[\\p{gc=Ll}]{4}",
                    "[\\p{general_category=Ll}]{4}", "[\\p{IsLowercase}]{4}",
                    "[\\p{javaLowerCase}]{4}", "[\\p{Upper}]{4}", "[\\p{Lu}]{4}",
                    "[\\p{IsLu}]{4}", "[\\p{gc=Lu}]{4}",
                    "[\\p{general_category=Lu}]{4}", "[\\p{IsUppercase}]{4}",
                    "[\\p{javaUpperCase}]{4}", "[\\p{Lt}]{4}", "[\\p{IsLt}]{4}",
                    "[\\p{gc=Lt}]{4}", "[\\p{general_category=Lt}]{4}",
                    "[\\p{IsTitlecase}]{4}", "[\\p{javaTitleCase}]{4}"))
            {
                assertTrue(Pattern.compile(pattern, Pattern.CASE_INSENSITIVE)
                            .matcher(input)
                            .matches(),"Expected to match: " + "'" + input +
                        "' =~ /" + pattern + "/");
            }
        }

        for (String input : List.of("\u01c7", "\u01c8", "\u01c9")) {
            for (String pattern : List.of("\u01c7", "\u01c8", "\u01c9",
                    "[\u01c7\u01c8]", "[\u01c7\u01c9]", "[\u01c8\u01c9]",
                    "[\u01c7-\u01c8]", "[\u01c8-\u01c9]", "[\u01c7-\u01c9]",
                    "\\p{Lower}", "\\p{Ll}", "\\p{IsLl}", "\\p{gc=Ll}",
                    "\\p{general_category=Ll}", "\\p{IsLowercase}",
                    "\\p{javaLowerCase}", "\\p{Upper}", "\\p{Lu}",
                    "\\p{IsLu}", "\\p{gc=Lu}", "\\p{general_category=Lu}",
                    "\\p{IsUppercase}", "\\p{javaUpperCase}",
                    "\\p{Lt}", "\\p{IsLt}", "\\p{gc=Lt}",
                    "\\p{general_category=Lt}", "\\p{IsTitlecase}",
                    "\\p{javaTitleCase}", "[\\p{Lower}]", "[\\p{Ll}]",
                    "[\\p{IsLl}]", "[\\p{gc=Ll}]",
                    "[\\p{general_category=Ll}]", "[\\p{IsLowercase}]",
                    "[\\p{javaLowerCase}]", "[\\p{Upper}]", "[\\p{Lu}]",
                    "[\\p{IsLu}]", "[\\p{gc=Lu}]",
                    "[\\p{general_category=Lu}]", "[\\p{IsUppercase}]",
                    "[\\p{javaUpperCase}]", "[\\p{Lt}]", "[\\p{IsLt}]",
                    "[\\p{gc=Lt}]", "[\\p{general_category=Lt}]",
                    "[\\p{IsTitlecase}]", "[\\p{javaTitleCase}]"))
            {
                assertTrue(Pattern.compile(pattern, Pattern.CASE_INSENSITIVE
                                            | Pattern.UNICODE_CHARACTER_CLASS)
                            .matcher(input)
                            .matches(), "Expected to match: " +
                        "'" + input + "' =~ /" + pattern + "/");
            }
        }
    }

    // This test is for 8237599
    @Test
    public static void surrogatePairOverlapRegion() {
        String input = "\ud801\udc37";

        Pattern p = Pattern.compile(".+");
        Matcher m = p.matcher(input);
        m.region(0, 1);

        boolean ok = m.find();
        if (!ok || !m.group(0).equals(input.substring(0, 1)))
        {
            String errMessage = "Input \"" + input + "\".substr(0, 1)" +
                    " expected to match pattern \"" + p + "\"";
            if (ok) {
                fail(errMessage + System.lineSeparator() +
                        "group(0): \"" + m.group(0) + "\"");
            } else {
                fail(errMessage);
            }
        } else if (!m.hitEnd()) {
            fail("Expected m.hitEnd() == true");
        }

        p = Pattern.compile(".*(.)");
        m = p.matcher(input);
        m.region(1, 2);

        ok = m.find();
        if (!ok || !m.group(0).equals(input.substring(1, 2))
                || !m.group(1).equals(input.substring(1, 2)))
        {
            String errMessage = "Input \"" + input + "\".substr(1, 2)" +
                    " expected to match pattern \"" + p + "\"";
            if (ok) {
                String msg1 = "group(0): \"" + m.group(0) + "\"";
                String msg2 = "group(1): \"" + m.group(1) + "\"";
                fail(errMessage + System.lineSeparator() + msg1 +
                        System.lineSeparator() + msg2);
            } else {
                fail(errMessage);
            }
        }
    }

    //This test is for 8037397
    //Ensure we don't drop nested interior character classes to the right of an
    //intersection operator.
    @Test
    public static void droppedClassesWithIntersection() {
        String rx = "[A-Z&&[A-Z]0-9]";
        String ry = "[A-Z&&[A-F][G-Z]0-9]";

        Stream<Character> letterChars = IntStream.range('A', 'Z').mapToObj((i) -> (char) i);
        Stream<Character> digitChars = IntStream.range('0', '9').mapToObj((i) -> (char) i);

        boolean letterCharsMatch = letterChars.allMatch((ch) -> {
            String chString = ch.toString();
            return chString.matches(rx) && chString.matches(ry);
        });

        boolean digitCharsDontMatch = digitChars.noneMatch((ch) -> {
            String chString = ch.toString();
            return chString.matches(rx) && chString.matches(ry);
        });


        assertTrue(letterCharsMatch, "Compiling intersection pattern is " +
                "dropping a character class in its matcher");

        assertTrue(digitCharsDontMatch, "Compiling intersection pattern is " +
                "matching digits where it should not");
    }

    //This test is for 8269753
    //This is for ensuring that the caret doesn't point at the wrong character
    //in a syntax exception message because we previously didn't compensate for
    //tabs when rendering the offending string that contained tab characters.
    @Test
    public static void errorMessageCaretIndentation() {
        String pattern = "\t**";
        var e = expectThrows(PatternSyntaxException.class, () ->
                Pattern.compile(pattern));
        var sep = System.lineSeparator();
        assertTrue(e.getMessage().contains(sep + "\t ^"));
    }

    //This test is for 8276694
    //Ensure our error message indicates we have an unescaped backslash when we
    //encounter one.
    @Test
    public static void unescapedBackslash() {
        String pattern = "\\";
        var e = expectThrows(PatternSyntaxException.class, () ->
                Pattern.compile(pattern));
        assertTrue(e.getMessage().contains("Unescaped trailing backslash"));
    }

    //This test is for 8280403
    //Given bad intersection syntax, we should throw a PatternSyntaxException.
    @Test
    public static void badIntersectionSyntax() {
        String pattern = "[˜\\H +F&&]";
        var e = expectThrows(PatternSyntaxException.class, () ->
                Pattern.compile(pattern));
        assertTrue(e.getMessage().contains("Bad intersection syntax"));
    }

    //This test is for 8264160
    //Here we check for inconsistencies between the behavior of \w and the
    //behavior of \b. Prior to this fix, the two flags did not behave in a
    //consistent way ie \b would recognize non-\w characters as part of a word
    //in some cases. This test verifies that the two behave consistently
    //for all codepoints we support.
    @Test
    public static void wordBoundaryInconsistencies() {
        Pattern basicWordCharPattern = Pattern.compile("\\w");
        Pattern basicWordCharBoundaryPattern =
                Pattern.compile(";\\b.", Pattern.DOTALL);

        Pattern unicodeWordCharPattern =
                Pattern.compile("\\w", Pattern.UNICODE_CHARACTER_CLASS);

        Pattern unicodeWordCharBoundaryPattern =
                Pattern.compile(";\\b.",
                        Pattern.DOTALL | Pattern.UNICODE_CHARACTER_CLASS);

        IntFunction<Boolean> basicWordCharCheck =
                (cp) -> cpMatches(basicWordCharPattern, cp, false);

        IntFunction<Boolean> basicBoundaryCharCheck =
                (cp) -> cpMatches(basicWordCharBoundaryPattern,
                                  cp, true);

        IntFunction<Boolean> unicodeWordCharCheck =
                (cp) -> cpMatches(unicodeWordCharPattern, cp, false);

        IntFunction<Boolean> unicodeBoundaryCharCheck =
                (cp) -> cpMatches(unicodeWordCharBoundaryPattern,
                                  cp,true);

        //basic pattern comparison
        for(int cp = 0; cp <= Character.MAX_CODE_POINT; cp++){
            assertEquals(basicWordCharCheck.apply(cp),
                    basicBoundaryCharCheck.apply(cp),
                    "Codepoint: " + cp);
            assertEquals(unicodeWordCharCheck.apply(cp),
                    unicodeBoundaryCharCheck.apply(cp),
                    "Codepoint: " + cp);
        }
    }

    private static boolean cpMatches(Pattern p, int cp, boolean boundary) {
        String cpString;
        if (Character.isBmpCodePoint(cp)) {
            cpString = "" + ((char) cp);
        } else {
            cpString = "" + Character.highSurrogate(cp) +
                    Character.lowSurrogate(cp);
        }

        if (boundary) {
            return p.matcher(";" + cpString).matches();
        } else {
            return p.matcher(cpString).matches();
        }
    }

    //This test is for 8281560
    //Checks that when the Canonical Equivalence flag is set, the behavior for
    //Matcher::hitEnd is equivalent for these similar, patterns that saw
    //inconsistencies.
    @Test
    public static void prematureHitEndInNFCCharProperty() {
        var testInput = "a1a1";
        var pat1 = "(a+|1+)";
        var pat2 = "([a]+|[1]+)";

        var matcher1 = Pattern.compile(pat1, Pattern.CANON_EQ).matcher(testInput);
        var matcher2 = Pattern.compile(pat2, Pattern.CANON_EQ).matcher(testInput);

        ArrayList<Boolean> results1 = new ArrayList<>();
        ArrayList<Boolean> results2 = new ArrayList<>();

        while (matcher1.find()) {
            results1.add(matcher1.hitEnd());
        }

        while (matcher2.find()) {
            results2.add(matcher2.hitEnd());
        }

        assertEquals(results1, results2);
    }

    //This test is for 8281315
    //Checks that we are able to correctly match this case with a backref
    //without encountering an IndexOutOfBoundsException.
    @Test
    public static void iOOBForCIBackrefs(){
        String line = "\ud83d\udc95\ud83d\udc95\ud83d\udc95";
        var pattern2 = Pattern.compile("(?i)(.)\\1{2,}");
        assertTrue(pattern2.matcher(line).find());
    }
}

