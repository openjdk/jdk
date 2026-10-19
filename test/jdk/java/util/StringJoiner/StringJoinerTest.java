/*
 * Copyright (c) 2013, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 5015163 7172553 8249258
 * @summary tests StringJoinerTest
 * @modules java.base/jdk.internal.util
 * @requires vm.bits == "64" & os.maxMemory > 4G
 * @run junit/othervm -Xmx4g -XX:+CompactStrings StringJoinerTest
 * @author Jim Gish
 */

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.StringJoiner;

import static jdk.internal.util.ArraysSupport.SOFT_MAX_ARRAY_LENGTH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

@Tag("unit")
@Tag("string")
@Tag("util")
@Tag("libs")
public class StringJoinerTest {

    private static final String EMPTY = "EMPTY";
    private static final String ONE = "One";
    private static final int ONE_LEN = ONE.length();
    private static final String TWO = "Two";
    private static final int TWO_LEN = TWO.length();
    private static final String THREE = "Three";
    private static final String FOUR = "Four";
    private static final String FIVE = "Five";
    private static final String DASH = "-";
    private static final String MAX_STRING = "*".repeat(SOFT_MAX_ARRAY_LENGTH);

    @Test
    public void addAddAll() {
        StringJoiner sj = new StringJoiner(DASH, "{", "}");
        sj.add(ONE);

        ArrayList<String> nextOne = new ArrayList<>();
        nextOne.add(TWO);
        nextOne.add(THREE);
        nextOne.stream().forEachOrdered(sj::add);

        String expected = "{"+ONE+DASH+TWO+DASH+THREE+"}";
        assertEquals(expected, sj.toString());
    }

    void addAlladd() {
        StringJoiner sj = new StringJoiner(DASH, "{", "}");

        ArrayList<String> firstOne = new ArrayList<>();
        firstOne.add(ONE);
        firstOne.add(TWO);
        firstOne.stream().forEachOrdered(sj::add);

        sj.add(THREE);

        String expected = "{"+ONE+DASH+TWO+DASH+THREE+"}";
        assertEquals(expected, sj.toString());
    }

    // The following tests do two successive adds of different types
    @Test
    public void addAlladdAll() {
        StringJoiner sj = new StringJoiner(DASH, "{", "}");
        ArrayList<String> firstOne = new ArrayList<>();
        firstOne.add(ONE);
        firstOne.add(TWO);
        firstOne.add(THREE);
        firstOne.stream().forEachOrdered(sj::add);

        ArrayList<String> nextOne = new ArrayList<>();
        nextOne.add(FOUR);
        nextOne.add(FIVE);
        nextOne.stream().forEachOrdered(sj::add);

        String expected = "{"+ONE+DASH+TWO+DASH+THREE+DASH+FOUR+DASH+FIVE+"}";
        assertEquals(expected, sj.toString());
    }

    @Test
    public void addCharSequence() {
        StringJoiner sj = new StringJoiner(",");
        CharSequence cs_one = ONE;
        CharSequence cs_two = TWO;

        sj.add(cs_one);
        sj.add(cs_two);

        assertEquals(ONE + "," + TWO, sj.toString());

        sj = new StringJoiner(DASH, "{", "}");
        sj.add(cs_one);
        sj.add(cs_two);

        assertEquals("{" + ONE + DASH + TWO + "}", sj.toString());

        StringBuilder builder = new StringBuilder(ONE);
        StringBuffer buffer = new StringBuffer(THREE);
        sj = new StringJoiner(", ", "{ ", " }");
        sj.add(builder).add(buffer);
        builder.append(TWO);
        buffer.append(FOUR);
        assertEquals("{ " + ONE + ", " + THREE + " }", sj.toString(),
                "CharSequence is copied when add");
        sj.add(builder);
        assertEquals("{ " + ONE + ", " + THREE + ", " + ONE +
                TWO + " }", sj.toString());
    }

    @Test
    public void addCharSequenceWithEmptyValue() {
        StringJoiner sj = new StringJoiner(",").setEmptyValue(EMPTY);
        CharSequence cs_one = ONE;
        CharSequence cs_two = TWO;

        sj.add(cs_one);
        sj.add(cs_two);

        assertEquals(ONE + "," + TWO, sj.toString());

        sj = new StringJoiner(DASH, "{", "}");
        sj.add(cs_one);
        sj.add(cs_two);
        assertEquals("{" + ONE + DASH + TWO + "}", sj.toString());

        sj = new StringJoiner(DASH, "{", "}");
        assertEquals("{}", sj.toString());

        sj = new StringJoiner("=", "{", "}").setEmptyValue("");
        assertEquals("", sj.toString());

        sj = new StringJoiner(DASH, "{", "}").setEmptyValue(EMPTY);
        assertEquals(EMPTY, sj.toString());

        sj.add(cs_one);
        sj.add(cs_two);
        assertEquals("{" + ONE + DASH + TWO + "}", sj.toString());
    }

    @Test
    public void addString() {
        StringJoiner sj = new StringJoiner(DASH);
        sj.add(ONE);
        assertEquals(ONE, sj.toString());

        sj = new StringJoiner(DASH, "{", "}");
        sj.add(ONE);
        assertEquals("{" + ONE + "}", sj.toString());

        sj.add(TWO);
        assertEquals("{" + ONE + DASH + TWO + "}", sj.toString());
    }

    @Test
    public void lengthWithCustomEmptyValue() {
        StringJoiner sj = new StringJoiner(DASH, "<", ">").setEmptyValue(EMPTY);
        assertEquals(EMPTY.length(), sj.length());
        sj.add("");
        assertEquals("<>".length(), sj.length());
        sj.add("");
        assertEquals("<->".length(), sj.length());
        sj.add(ONE);
        assertEquals(4 + ONE_LEN, sj.length());
        assertEquals(sj.length(), sj.toString().length());
        sj.add(TWO);
        assertEquals(5 + ONE_LEN + TWO_LEN, sj.length());
        assertEquals(sj.length(), sj.toString().length());
        sj = new StringJoiner("||", "<", "-->");
        assertEquals(4, sj.length());
        assertEquals(sj.length(), sj.toString().length());
        sj.add("abcdef");
        assertEquals(10, sj.length());
        assertEquals(sj.length(), sj.toString().length());
        sj.add("xyz");
        assertEquals(15, sj.length());
        assertEquals(sj.length(), sj.toString().length());
    }

    @Test
    public void noAddAndEmptyValue() {
        StringJoiner sj = new StringJoiner(DASH, "", "").setEmptyValue(EMPTY);
        assertEquals(EMPTY, sj.toString());

        sj = new StringJoiner(DASH, "<..", "");
        assertEquals("<..", sj.toString());

        sj = new StringJoiner(DASH, "<..", "");
        assertEquals("<..", sj.toString());

        sj = new StringJoiner(DASH, "", "==>");
        assertEquals("==>", sj.toString());

        sj = new StringJoiner(DASH, "{", "}");
        assertEquals("{}", sj.toString());
    }

    @Test
    public void setEmptyValueNull() {
        Assertions.assertThrows(NullPointerException.class,
                () -> new StringJoiner(DASH, "{", "}").setEmptyValue(null));
    }

    @Test
    public void setDelimiterNull() {
        Assertions.assertThrows(NullPointerException.class,
                () -> new StringJoiner(null));
    }

    @Test
    public void setPrefixNull() {
        Assertions.assertThrows(NullPointerException.class,
                () -> new StringJoiner(DASH, null, "}"));
    }

    @Test
    public void setSuffixNull() {
        Assertions.assertThrows(NullPointerException.class,
                () -> new StringJoiner(DASH, "{", null));
    }

    @Test
    public void stringFromtoString() {
        StringJoiner sj = new StringJoiner(", ");
        assertEquals("", sj.toString());
        sj = new StringJoiner(",", "{", "}");
        assertEquals("{}", sj.toString());

        sj = new StringJoiner(",");
        sj.add(ONE);
        assertEquals(ONE, sj.toString());

        sj.add(TWO);
        assertEquals(ONE + "," + TWO, sj.toString());

        sj = new StringJoiner(",", "{--", "--}");
        sj.add(ONE);
        sj.add(TWO);
        assertEquals("{--" + ONE + "," + TWO + "--}", sj.toString());

    }

    @Test
    public void stringFromtoStringWithEmptyValue() {
        StringJoiner sj = new StringJoiner(" ", "", "");
        assertEquals("", sj.toString());
        sj = new StringJoiner(", ");
        assertEquals("", sj.toString());
        sj = new StringJoiner(",", "{", "}");
        assertEquals("{}", sj.toString());

        sj = new StringJoiner(",", "{", "}").setEmptyValue("");
        assertEquals("", sj.toString());

        sj = new StringJoiner(",");
        sj.add(ONE);
        assertEquals(ONE, sj.toString());

        sj.add(TWO);
        assertEquals(ONE + "," + TWO, sj.toString());

        sj = new StringJoiner(",", "{--", "--}");
        sj.add(ONE);
        assertEquals("{--" + ONE + "--}", sj.toString() );

        sj.add(TWO);
        assertEquals("{--" + ONE + "," + TWO + "--}", sj.toString());

    }

    @Test
    public void toStringWithCustomEmptyValue() {
        StringJoiner sj = new StringJoiner(DASH, "<", ">").setEmptyValue(EMPTY);
        assertEquals(EMPTY, sj.toString());
        sj.add("");
        assertEquals("<>", sj.toString());
        sj.add("");
        assertEquals("<->", sj.toString());
    }

    private void testCombos(String infix, String prefix, String suffix) {
        StringJoiner sj = new StringJoiner(infix, prefix, suffix);
        assertEquals(prefix + suffix, sj.toString());
        assertEquals(sj.length(), sj.toString().length());
        // EmptyValue
        sj = new StringJoiner(infix, prefix, suffix).setEmptyValue("<NONE>");
        assertEquals("<NONE>", sj.toString());
        assertEquals(sj.length(), sj.toString().length());

        // empty in front
        sj.add("");
        assertEquals(prefix + suffix, sj.toString());
        // empty in middle
        sj.add("");
        assertEquals(prefix + infix + suffix, sj.toString());
        sj.add("1");
        assertEquals(prefix + infix + infix + "1" + suffix, sj.toString());
        // empty at end
        sj.add("");
        assertEquals(prefix + infix + infix + "1" + infix + suffix, sj.toString());

        sj = new StringJoiner(infix, prefix, suffix).setEmptyValue("<NONE>");
        sj.add("1");
        assertEquals(prefix + "1" + suffix, sj.toString());
        sj.add("2");
        assertEquals(prefix + "1" + infix + "2" + suffix, sj.toString());
        sj.add("");
        assertEquals(prefix + "1" + infix + "2" + infix + suffix, sj.toString());
        sj.add("3");
        assertEquals(prefix + "1" + infix + "2" + infix + infix + "3" + suffix, sj.toString());
    }

    @Test
    public void testDelimiterCombinations() {
        testCombos("", "", "");
        testCombos("", "<", "");
        testCombos("", "", ">");
        testCombos("", "<", ">");
        testCombos(",", "", "");
        testCombos(",", "<", "");
        testCombos(",", "", ">");
        testCombos(",", "<", ">");
    }

    @Test
    public void OOM1() {
        try {
            new StringJoiner(MAX_STRING, MAX_STRING, MAX_STRING).toString();
            fail("Should have thrown OutOfMemoryError");
        } catch (OutOfMemoryError ex) {
            // okay
        }
    }

    @Test
    public void OOM2() {
        try {
            new StringJoiner(MAX_STRING, MAX_STRING, "").toString();
            fail("Should have thrown OutOfMemoryError");
        } catch (OutOfMemoryError ex) {
            // okay
        }
    }

    @Test
    public void OOM3() {
        try {
            new StringJoiner(MAX_STRING, "", MAX_STRING).toString();
            fail("Should have thrown OutOfMemoryError");
        } catch (OutOfMemoryError ex) {
            // okay
        }
    }

    @Test
    public void OOM4() {
        try {
            new StringJoiner("", MAX_STRING, MAX_STRING).toString();
            fail("Should have thrown OutOfMemoryError");
        } catch (OutOfMemoryError ex) {
            // okay
        }
    }
}

