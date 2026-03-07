/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 more details (a copy has been included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License
 * version 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit the Oracle website www.oracle.com if you need additional information.
 */

package compiler.c2.irTests;

import compiler.lib.ir_framework.*;

/*
 * @test
 * @bug 8999999
 * @summary IR test for C2 optimization of String.format/formatted with simple specifiers
 *          - Verify AllocateArray elimination (escape analysis)
 *          - Verify call redirection to optimized methods (format_s, format_d, format_ss, etc.)
 * @library /test/lib /
 * @run driver compiler.c2.irTests.TestStringFormatOptimization
 */
public class TestStringFormatOptimization {

    public static void main(String[] args) {
        TestFramework.run();
    }

    // ========== Single specifier %s - redirect to format_s ==========

    @Test
    @IR(failOn = { IRNode.ALLOC_ARRAY })
    @IR(counts = { IRNode.STATIC_CALL_OF_METHOD, "format_s", "= 1" })
    static String testSingle_s(String s) {
        return String.format("%s", s);
    }

    @Test
    @IR(failOn = { IRNode.ALLOC_ARRAY })
    @IR(counts = { IRNode.STATIC_CALL_OF_METHOD, "format_s", "= 1" })
    static String testSingle_sPrefix(String s) {
        return String.format("value: %s", s);
    }

    // ========== Single specifier %d - redirect to format_d ==========

    @Test
    @IR(failOn = { IRNode.ALLOC_ARRAY })
    @IR(counts = { IRNode.STATIC_CALL_OF_METHOD, "format_d", "= 1" })
    static String testSingle_d(int v) {
        return String.format("count=%d", v);
    }

    @Test
    @IR(failOn = { IRNode.ALLOC_ARRAY })
    @IR(counts = { IRNode.STATIC_CALL_OF_METHOD, "format_d", "= 1" })
    static String testSingle_dLong(long v) {
        return String.format("value=%d", v);
    }

    // ========== formatted() single specifier - redirect to formatted_s/d ==========

    @Test
    @IR(failOn = { IRNode.ALLOC_ARRAY })
    @IR(counts = { IRNode.STATIC_CALL_OF_METHOD, "formatted_s", "= 1" })
    static String testFormatted_s(String s) {
        return "value: %s".formatted(s);
    }

    @Test
    @IR(failOn = { IRNode.ALLOC_ARRAY })
    @IR(counts = { IRNode.STATIC_CALL_OF_METHOD, "formatted_d", "= 1" })
    static String testFormatted_d(int v) {
        return "count=%d".formatted(v);
    }

    // ========== Two specifiers %s %s - redirect to format_ss ==========

    @Test
    @IR(failOn = { IRNode.ALLOC_ARRAY })
    @IR(counts = { IRNode.STATIC_CALL_OF_METHOD, "format_ss", "= 1" })
    static String testTwo_ss(String a, String b) {
        return String.format("%s %s", a, b);
    }

    @Test
    @IR(failOn = { IRNode.ALLOC_ARRAY })
    @IR(counts = { IRNode.STATIC_CALL_OF_METHOD, "format_ss", "= 1" })
    static String testTwo_ssPrefix(String a, String b) {
        return String.format("x=%s, y=%s", a, b);
    }

    // ========== Two specifiers %s %d - redirect to format_sd ==========

    @Test
    @IR(failOn = { IRNode.ALLOC_ARRAY })
    @IR(counts = { IRNode.STATIC_CALL_OF_METHOD, "format_sd", "= 1" })
    static String testTwo_sd(String s, int v) {
        return String.format("name=%s age=%d", s, v);
    }

    // ========== Two specifiers %d %s - redirect to format_ds ==========

    @Test
    @IR(failOn = { IRNode.ALLOC_ARRAY })
    @IR(counts = { IRNode.STATIC_CALL_OF_METHOD, "format_ds", "= 1" })
    static String testTwo_ds(int v, String s) {
        return String.format("id=%d name=%s", v, s);
    }

    // ========== Two specifiers %d %d - redirect to format_dd ==========

    @Test
    @IR(failOn = { IRNode.ALLOC_ARRAY })
    @IR(counts = { IRNode.STATIC_CALL_OF_METHOD, "format_dd", "= 1" })
    static String testTwo_dd(int a, int b) {
        return String.format("%d+%d", a, b);
    }

    // ========== Two specifiers with %x/%X - redirect to format_2 ==========

    @Test
    @IR(failOn = { IRNode.ALLOC_ARRAY })
    @IR(counts = { IRNode.STATIC_CALL_OF_METHOD, "format_2", "= 1" })
    static String testTwo_xx(int a, int b) {
        return String.format("%x+%x", a, b);
    }

    @Test
    @IR(failOn = { IRNode.ALLOC_ARRAY })
    @IR(counts = { IRNode.STATIC_CALL_OF_METHOD, "format_2", "= 1" })
    static String testTwo_XX(int a, int b) {
        return String.format("%X+%X", a, b);
    }

    @Test
    @IR(failOn = { IRNode.ALLOC_ARRAY })
    @IR(counts = { IRNode.STATIC_CALL_OF_METHOD, "format_2", "= 1" })
    static String testTwo_sx(String s, int v) {
        return String.format("%s=%x", s, v);
    }

    @Test
    @IR(failOn = { IRNode.ALLOC_ARRAY })
    @IR(counts = { IRNode.STATIC_CALL_OF_METHOD, "format_2", "= 1" })
    static String testTwo_xd(int a, int b) {
        return String.format("hex=%x dec=%d", a, b);
    }

    // ========== formatted() two specifiers - redirect to formatted_ss/sd/ds/dd ==========

    @Test
    @IR(failOn = { IRNode.ALLOC_ARRAY })
    @IR(counts = { IRNode.STATIC_CALL_OF_METHOD, "formatted_ss", "= 1" })
    static String testFormatted_ss(String a, String b) {
        return "%s and %s".formatted(a, b);
    }

    @Test
    @IR(failOn = { IRNode.ALLOC_ARRAY })
    @IR(counts = { IRNode.STATIC_CALL_OF_METHOD, "formatted_sd", "= 1" })
    static String testFormatted_sd(String s, int v) {
        return "%s=%d".formatted(s, v);
    }

    @Test
    @IR(failOn = { IRNode.ALLOC_ARRAY })
    @IR(counts = { IRNode.STATIC_CALL_OF_METHOD, "formatted_ds", "= 1" })
    static String testFormatted_ds(int v, String s) {
        return "%d:%s".formatted(v, s);
    }

    @Test
    @IR(failOn = { IRNode.ALLOC_ARRAY })
    @IR(counts = { IRNode.STATIC_CALL_OF_METHOD, "formatted_dd", "= 1" })
    static String testFormatted_dd(int a, int b) {
        return "%d+%d".formatted(a, b);
    }

    // ========== formatted() two specifiers with %x/%X - redirect to formatted_2 ==========

    @Test
    @IR(failOn = { IRNode.ALLOC_ARRAY })
    @IR(counts = { IRNode.STATIC_CALL_OF_METHOD, "formatted_2", "= 1" })
    static String testFormatted_xx(int a, int b) {
        return "%x+%x".formatted(a, b);
    }

    @Test
    @IR(failOn = { IRNode.ALLOC_ARRAY })
    @IR(counts = { IRNode.STATIC_CALL_OF_METHOD, "formatted_2", "= 1" })
    static String testFormatted_sx(String s, int v) {
        return "%s=%x".formatted(s, v);
    }

    // ========== Single specifier with width/flags - redirect to format_1 ==========

    @Test
    @IR(failOn = { IRNode.ALLOC_ARRAY })
    @IR(counts = { IRNode.STATIC_CALL_OF_METHOD, "format_1", "= 1" })
    static String testSingle_width_s(String s) {
        return String.format("[%5s]", s);
    }

    @Test
    @IR(failOn = { IRNode.ALLOC_ARRAY })
    @IR(counts = { IRNode.STATIC_CALL_OF_METHOD, "format_1", "= 1" })
    static String testSingle_width_d(int v) {
        return String.format("[%5d]", v);
    }

    @Test
    @IR(failOn = { IRNode.ALLOC_ARRAY })
    @IR(counts = { IRNode.STATIC_CALL_OF_METHOD, "format_1", "= 1" })
    static String testSingle_flag0_d(int v) {
        return String.format("[%05d]", v);
    }

    // ========== Single specifier %x/%X - redirect to format_1 ==========

    @Test
    @IR(failOn = { IRNode.ALLOC_ARRAY })
    @IR(counts = { IRNode.STATIC_CALL_OF_METHOD, "format_1", "= 1" })
    static String testSingle_x(int v) {
        return String.format("0x%x", v);
    }

    @Test
    @IR(failOn = { IRNode.ALLOC_ARRAY })
    @IR(counts = { IRNode.STATIC_CALL_OF_METHOD, "format_1", "= 1" })
    static String testSingle_X(int v) {
        return String.format("0x%X", v);
    }

    @Test
    @IR(failOn = { IRNode.ALLOC_ARRAY })
    @IR(counts = { IRNode.STATIC_CALL_OF_METHOD, "format_1", "= 1" })
    static String testSingle_width_x(int v) {
        return String.format("[%5x]", v);
    }

    @Test
    @IR(failOn = { IRNode.ALLOC_ARRAY })
    @IR(counts = { IRNode.STATIC_CALL_OF_METHOD, "format_1", "= 1" })
    static String testSingle_width_X(int v) {
        return String.format("[%5X]", v);
    }

    // ========== formatted() single specifier with width/flags - redirect to formatted_1 ==========

    @Test
    @IR(failOn = { IRNode.ALLOC_ARRAY })
    @IR(counts = { IRNode.STATIC_CALL_OF_METHOD, "formatted_1", "= 1" })
    static String testFormatted_width_s(String s) {
        return "[%5s]".formatted(s);
    }

    @Test
    @IR(failOn = { IRNode.ALLOC_ARRAY })
    @IR(counts = { IRNode.STATIC_CALL_OF_METHOD, "formatted_1", "= 1" })
    static String testFormatted_width_d(int v) {
        return "[%5d]".formatted(v);
    }

    @Test
    @IR(failOn = { IRNode.ALLOC_ARRAY })
    @IR(counts = { IRNode.STATIC_CALL_OF_METHOD, "formatted_1", "= 1" })
    static String testFormatted_flag0_d(int v) {
        return "[%05d]".formatted(v);
    }

    @Test
    @IR(failOn = { IRNode.ALLOC_ARRAY })
    @IR(counts = { IRNode.STATIC_CALL_OF_METHOD, "formatted_1", "= 1" })
    static String testFormatted_x(int v) {
        return "0x%x".formatted(v);
    }

    @Test
    @IR(failOn = { IRNode.ALLOC_ARRAY })
    @IR(counts = { IRNode.STATIC_CALL_OF_METHOD, "formatted_1", "= 1" })
    static String testFormatted_X(int v) {
        return "0x%X".formatted(v);
    }

    // ========== Null argument tests ==========

    @Test
    @IR(failOn = { IRNode.ALLOC_ARRAY })
    @IR(counts = { IRNode.STATIC_CALL_OF_METHOD, "format_s", "= 1" })
    static String testSingle_null() {
        return String.format("%s", (Object) null);
    }

    @Test
    @IR(failOn = { IRNode.ALLOC_ARRAY })
    @IR(counts = { IRNode.STATIC_CALL_OF_METHOD, "format_ss", "= 1" })
    static String testTwo_nullFirst() {
        return String.format("%s %s", (Object) null, "b");
    }

    // ========== Three+ specifiers - uses format_multi ==========

    @Test
    @IR(counts = { IRNode.STATIC_CALL_OF_METHOD, "format_multi", "= 1" })
    static String testThree_sss(String a, String b, String c) {
        return String.format("%s %s %s", a, b, c);
    }

    @Test
    @IR(counts = { IRNode.STATIC_CALL_OF_METHOD, "format_multi", "= 1" })
    static String testThree_ddd(int a, int b, int c) {
        return String.format("%d+%d=%d", a, b, c);
    }

    @Test
    @IR(counts = { IRNode.STATIC_CALL_OF_METHOD, "format_multi", "= 1" })
    static String testFour_ssss(String a, String b, String c, String d) {
        return String.format("[%s|%s|%s|%s]", a, b, c, d);
    }

    // ========== Run methods to verify correctness ==========

    @Run(test = {"testSingle_s", "testSingle_sPrefix",
                 "testSingle_d", "testSingle_dLong",
                 "testSingle_null"})
    void runSingleTests() {
        assertEquals("hello", testSingle_s("hello"));
        assertEquals("value: hello", testSingle_sPrefix("hello"));
        assertEquals("count=42", testSingle_d(42));
        assertEquals("value=123456789012345", testSingle_dLong(123456789012345L));
        assertEquals("null", testSingle_null());
    }

    @Run(test = {"testFormatted_s", "testFormatted_d"})
    void runFormattedSingleTests() {
        assertEquals("value: test", testFormatted_s("test"));
        assertEquals("count=42", testFormatted_d(42));
    }

    @Run(test = {"testTwo_ss", "testTwo_ssPrefix",
                 "testTwo_sd", "testTwo_ds", "testTwo_dd",
                 "testTwo_xx", "testTwo_XX", "testTwo_sx", "testTwo_xd",
                 "testTwo_nullFirst"})
    void runTwoTests() {
        assertEquals("a b", testTwo_ss("a", "b"));
        assertEquals("x=a, y=b", testTwo_ssPrefix("a", "b"));
        assertEquals("name=Alice age=30", testTwo_sd("Alice", 30));
        assertEquals("id=42 name=Bob", testTwo_ds(42, "Bob"));
        assertEquals("1+2", testTwo_dd(1, 2));
        assertEquals("ff+10", testTwo_xx(255, 16));
        assertEquals("FF+10", testTwo_XX(255, 16));
        assertEquals("addr=ff", testTwo_sx("addr", 255));
        assertEquals("hex=ff dec=16", testTwo_xd(255, 16));
        assertEquals("null b", testTwo_nullFirst());
    }

    @Run(test = {"testFormatted_ss", "testFormatted_sd", "testFormatted_ds", "testFormatted_dd",
                 "testFormatted_xx", "testFormatted_sx"})
    void runFormattedTwoTests() {
        assertEquals("foo and bar", testFormatted_ss("foo", "bar"));
        assertEquals("count=42", testFormatted_sd("count", 42));
        assertEquals("42:Bob", testFormatted_ds(42, "Bob"));
        assertEquals("1+2", testFormatted_dd(1, 2));
        assertEquals("ff+10", testFormatted_xx(255, 16));
        assertEquals("addr=ff", testFormatted_sx("addr", 255));
    }

    @Run(test = {"testSingle_width_s", "testSingle_width_d", "testSingle_flag0_d",
                 "testSingle_x", "testSingle_X", "testSingle_width_x", "testSingle_width_X"})
    void runFormat1Tests() {
        assertEquals("[  abc]", testSingle_width_s("abc"));
        assertEquals("[   42]", testSingle_width_d(42));
        assertEquals("[00042]", testSingle_flag0_d(42));
        assertEquals("0xff", testSingle_x(255));
        assertEquals("0xFF", testSingle_X(255));
        assertEquals("[   ff]", testSingle_width_x(255));
        assertEquals("[   FF]", testSingle_width_X(255));
    }

    @Run(test = {"testFormatted_width_s", "testFormatted_width_d", "testFormatted_flag0_d",
                 "testFormatted_x", "testFormatted_X"})
    void runFormatted1Tests() {
        assertEquals("[  abc]", testFormatted_width_s("abc"));
        assertEquals("[   42]", testFormatted_width_d(42));
        assertEquals("[00042]", testFormatted_flag0_d(42));
        assertEquals("0xff", testFormatted_x(255));
        assertEquals("0xFF", testFormatted_X(255));
    }

    @Run(test = {"testThree_sss", "testThree_ddd", "testFour_ssss"})
    void runMultiTests() {
        assertEquals("a b c", testThree_sss("a", "b", "c"));
        assertEquals("1+2=3", testThree_ddd(1, 2, 3));
        assertEquals("[a|b|c|d]", testFour_ssss("a", "b", "c", "d"));
    }

    static void assertEquals(String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected [" + expected + "] but got [" + actual + "]");
        }
    }
}
