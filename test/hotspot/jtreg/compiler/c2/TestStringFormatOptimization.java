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
 * @bug 8999999
 * @summary Test C2 optimization of String.format/formatted with simple specifiers
 * @run main/othervm -Xcomp -XX:-TieredCompilation TestStringFormatOptimization
 * @run main/othervm -XX:+TieredCompilation TestStringFormatOptimization
 */
public class TestStringFormatOptimization {

    // ========== String.format() - single specifier ==========

    static String testFormat_s(String s) { return String.format("%s", s); }
    static String testFormat_sPrefix(String s) { return String.format("value : %s", s); }
    static String testFormat_sNull() { return String.format("%s", (Object) null); }
    static String testFormat_dNull() { return String.format("%d", (Object) null); }
    static String testFormat_xNull() { return String.format("%x", (Object) null); }
    static String testFormat_XNull() { return String.format("%X", (Object) null); }
    // null with LEADING_SPACE flag - Formatter does NOT apply flag to null
    static String testFormat_dNullLeadingSpace() { return String.format("[% d]", (Object) null); }
    static String testFormat_dNullZeroPad() { return String.format("[%05d]", (Object) null); }
    static String testFormat_sFormattable(FormattableString fs) { return String.format("[%s]", fs); }
    static String testFormat_sFormattableWidth(FormattableString fs) { return String.format("[%14s]", fs); }
    // Single-digit width + Formattable: exercises format_1 path (width 1-9 only)
    static String testFormat_sFormattableWidth5(FormattableString fs) { return String.format("[%5s]", fs); }
    static String testFormat_sFormattableWidth9(FormattableString fs) { return String.format("[%9s]", fs); }
    static String testFormat_dInt(int v) { return String.format("count=%d", v); }
    static String testFormat_dShort(short v) { return String.format("short=%d", v); }
    static String testFormat_dByte(byte v) { return String.format("byte=%d", v); }
    static String testFormat_dNeg(int v) { return String.format("%d items", v); }
    static String testFormat_dLong(long v) { return String.format("long=%d", v); }
    static String testFormat_sObject(Object obj) { return String.format("obj=%s", obj); }
    static String testFormat_x(int v) { return String.format("0x%x", v); }
    static String testFormat_xShort(short v) { return String.format("short=0x%x", v); }
    static String testFormat_xByte(byte v) { return String.format("byte=0x%x", v); }
    static String testFormat_X(int v) { return String.format("0x%X", v); }

    // ========== BigInteger tests (supported by Formatter) ==========

    static String testFormat_dBigInteger(java.math.BigInteger v) { return String.format("big=%d", v); }
    static String testFormat_dBigIntegerNull() { return String.format("%d", (java.math.BigInteger) null); }
    static String testFormat_xBigInteger(java.math.BigInteger v) { return String.format("0x%x", v); }
    static String testFormat_xBigIntegerNull() { return String.format("%x", (java.math.BigInteger) null); }
    static String testFormat_XBigInteger(java.math.BigInteger v) { return String.format("0x%X", v); }
    static String testFormat_XBigIntegerNull() { return String.format("%X", (java.math.BigInteger) null); }
    // BigInteger negative hex tests (different from int/long which use 2's complement)
    static String testFormat_xBigIntegerNeg(java.math.BigInteger v) { return String.format("hex=%x", v); }
    static String testFormat_XBigIntegerNeg(java.math.BigInteger v) { return String.format("HEX=%X", v); }
    static String testFormat_xBigIntegerNegWidth(java.math.BigInteger v) { return String.format("[%08x]", v); }
    // Multi-specifier tests for format_arg path (3+ specifiers)
    static String testFormat_xNullZeroPadMulti() { return String.format("[%08x][%s][%s]", (Object) null, "a", "b"); }
    static String testFormat_xBigIntegerNegZeroPadMulti(java.math.BigInteger v) { return String.format("[%08x][%s][%s]", v, "a", "b"); }

    // ========== Exception tests (wrong arg types) ==========

    static void testFormat_dWrongType() { String.format("%d", "string"); }
    static void testFormat_xWrongType() { String.format("%x", "string"); }
    static void testFormat_XWrongType() { String.format("%X", "string"); }
    // Float/Double should also throw for %d (consistent with Formatter)
    static void testFormat_dFloat() { String.format("%d", 1.5f); }
    static void testFormat_dDouble() { String.format("%d", 1.5); }

    // ========== Edge case tests ==========

    static String testFormat_sEmpty(String s) { return String.format("%s", s); }
    static String testFormatted_sFormattableWidth(FormattableString fs) { return "[%5s]".formatted(fs); }

    // ========== formatted() - single specifier (optimized) ==========

    static String testFormatted_s(String s) { return "formatted %s".formatted(s); }
    static String testFormatted_dInt(int v) { return "count=%d".formatted(v); }
    static String testFormatted_dLong(long v) { return "value=%d".formatted(v); }
    static String testFormatted_xInt(int v) { return "hex=%x".formatted(v); }
    static String testFormatted_xLong(long v) { return "hex=%x".formatted(v); }
    static String testFormatted_XInt(int v) { return "HEX=%X".formatted(v); }
    static String testFormatted_XLong(long v) { return "HEX=%X".formatted(v); }

    // ========== String.format() - two specifiers ==========

    static String testFormat_ss(String a, String b) { return String.format("%s %s", a, b); }
    static String testFormat_ssPrefix(String a, String b) { return String.format("x=%s, y=%s", a, b); }
    static String testFormat_ssNull() { return String.format("%s %s", (Object) null, "b"); }
    static String testFormat_sd(String s, int v) { return String.format("name=%s age=%d", s, v); }
    static String testFormat_ds(int v, String s) { return String.format("id=%d name=%s", v, s); }
    static String testFormat_dd(int a, int b) { return String.format("%d+%d", a, b); }
    // %x/%X uses format_2
    static String testFormat_xx(int a, int b) { return String.format("%x %x", a, b); }
    static String testFormat_XX(int a, int b) { return String.format("%X %X", a, b); }
    // Formattable with %x - tests format_2 path handles Formattable correctly
    static String testFormat_sxFormattable(FormattableString fs, int v) { return String.format("%s=%x", fs, v); }
    // Two-specifier Formattable + width: exercises format_ss/sd/ds paths with convLen fix
    static String testFormat_ssFormattableWidth(FormattableString fs, String s) { return String.format("[%5s|%3s]", fs, s); }
    static String testFormat_sdFormattableWidth(FormattableString fs, int v) { return String.format("[%5s|%3d]", fs, v); }
    static String testFormat_dsFormattableWidth(int v, FormattableString fs) { return String.format("[%3d|%5s]", v, fs); }

    // ========== formatted() - two specifiers (optimized for s/d only) ==========

    static String testFormatted_ss(String a, String b) { return "%s and %s".formatted(a, b); }
    static String testFormatted_sd(String s, int v) { return "%s=%d".formatted(s, v); }
    static String testFormatted_ds(int v, String s) { return "%d:%s".formatted(v, s); }
    static String testFormatted_dd(int a, int b) { return "%d+%d".formatted(a, b); }

    // ========== formatted() - two specifiers (x/X - fallback to Formatter) ==========

    static String testFormatted_xx(int a, int b) { return "%x %x".formatted(a, b); }
    static String testFormatted_XX(int a, int b) { return "%X %X".formatted(a, b); }

    // ========== String.format() - width tests ==========

    static String testFormat_sWidth(String s) { return String.format("[%5s]", s); }
    static String testFormat_sWidthNopad(String s) { return String.format("[%3s]", s); }
    static String testFormat_dWidth(int v) { return String.format("[%5d]", v); }
    static String testFormat_dWidthNeg(int v) { return String.format("[%8d]", v); }
    static String testFormat_xWidth(int v) { return String.format("[%4x]", v); }
    static String testFormat_XWidth(int v) { return String.format("[%4X]", v); }
    static String testFormat_ssWidth(String a, String b) { return String.format("[%5s|%3s]", a, b); }
    // Null with width tests
    static String testFormat_dWidthNull() { return String.format("[%5d]", (Object) null); }
    static String testFormat_xWidthNull() { return String.format("[%5x]", (Object) null); }
    static String testFormat_XWidthNull() { return String.format("[%5X]", (Object) null); }

    // ========== formatted() - width tests (optimized) ==========

    static String testFormatted_sWidth(String s) { return "[%8s]".formatted(s); }
    static String testFormatted_dWidth(int v) { return "[%5d]".formatted(v); }
    static String testFormatted_xWidth(int v) { return "[%4x]".formatted(v); }
    static String testFormatted_ssWidth(String a, String b) { return "[%5s|%3s]".formatted(a, b); }

    // ========== String.format() - flag tests (' ' and '0') ==========

    // LEADING_SPACE flag ' ' for %d (uses format_multi)
    static String testFormat_dLeadingSpace(int v) { return String.format("[% d]", v); }
    static String testFormat_dLeadingSpaceNeg(int v) { return String.format("[% d]", v); }
    // ZERO_PAD flag '0' for %d (uses format_multi)
    static String testFormat_dZeroPad(int v) { return String.format("[%05d]", v); }
    static String testFormat_dZeroPadNeg(int v) { return String.format("[%05d]", v); }
    // Combined flag and width for %x/%X (uses format_multi)
    static String testFormat_xZeroPad(int v) { return String.format("[%04x]", v); }
    static String testFormat_XZeroPad(int v) { return String.format("[%04X]", v); }

    // ========== formatted() - flag tests (fallback to Formatter) ==========

    static String testFormatted_dLeadingSpace(int v) { return "[% d]".formatted(v); }
    static String testFormatted_dZeroPad(int v) { return "[%05d]".formatted(v); }

    // ========== String.format() - 3+ specifiers (format_multi) ==========

    static String testFormat3s(String a, String b, String c) { return String.format("%s %s %s", a, b, c); }
    static String testFormat3d(int a, int b, int c) { return String.format("%d+%d=%d", a, b, c); }
    static String testFormat_d3(int a, int b, int c) { return String.format("[%05d][%05d][%05d]", a, b, c); }
    static String testFormatMixed(String s, int d, int x) { return String.format("name=%s id=%d hex=%x", s, d, x); }
    static String testFormat4s(String a, String b, String c, String d) { return String.format("[%s|%s|%s|%s]", a, b, c, d); }
    static String testFormatMultiWidth(String a, int b, String c) { return String.format("[%5s|%3d|%s]", a, b, c); }

    // ========== String.format() - 5-8 specifiers (format_multi boundary) ==========

    static String testFormat5(String a, String b, int c, String d, int e) { return String.format("%s-%s-%d-%s-%d", a, b, c, d, e); }
    static String testFormat6(String a, String b, String c, String d, String e, String f) { return String.format("%s|%s|%s|%s|%s|%s", a, b, c, d, e, f); }
    static String testFormat7(int a, int b, int c, int d, int e, int f, int g) { return String.format("%d%d%d%d%d%d%d", a, b, c, d, e, f, g); }
    static String testFormat8(int a, int b, int c, int d, int e, int f, int g, int h) { return String.format("%d%d%d%d%d%d%d%d", a, b, c, d, e, f, g, h); }
    // 9 specifiers: exceeds MAX_FORMAT_SPECS, falls back to Formatter
    static String testFormat9(int a, int b, int c, int d, int e, int f, int g, int h, int i) { return String.format("%d%d%d%d%d%d%d%d%d", a, b, c, d, e, f, g, h, i); }

    // ========== Width >= 10 (falls back to Formatter since only single-digit width 1-9 is parsed) ==========

    static String testFormat_sWidth10(String s) { return String.format("[%10s]", s); }
    static String testFormat_dWidth15(int v) { return String.format("[%15d]", v); }

    // ========== formatted() - 3+ specifiers (fallback to Formatter) ==========

    static String testFormatted3s(String a, String b, String c) { return "%s %s %s".formatted(a, b, c); }
    static String testFormatted3d(int a, int b, int c) { return "%d+%d=%d".formatted(a, b, c); }
    static String testFormattedMixed(String s, int d, int x) { return "name=%s id=%d hex=%x".formatted(s, d, x); }

    // ========== Non-optimized patterns ==========

    static String testFormatWithTag(int a, int b, int c) { return String.format("%+d %,d %-5d", a, b, c); }

    // ========== Specifier position boundary tests (specIndex < 256 required) ==========

    // specIndex = 128 (boundary, should work - tests no sign extension bug)
    static String testFormat_specIndex128(String s) { return String.format("x".repeat(128) + "%s", s); }
    // specIndex = 200 (should work)
    static String testFormat_specIndex200(String s) { return String.format("x".repeat(200) + "%s", s); }
    // specIndex = 255 (max valid, should work)
    static String testFormat_specIndex255(String s) { return String.format("x".repeat(255) + "%s", s); }
    // specIndex = 256 (should fallback to Formatter)
    static String testFormat_specIndex256(String s) { return String.format("x".repeat(256) + "%s", s); }

    // ========== Escaped %% patterns (fallback to Formatter) ==========

    // %% before specifier
    static String testFormat_percentBeforeS(String s) { return String.format("%%%s", s); }
    static String testFormat_percentBeforeD(int v) { return String.format("100%% %d", v); }
    // %% after specifier
    static String testFormat_percentAfterS(String s) { return String.format("%s%%", s); }
    static String testFormat_percentAfterD(int v) { return String.format("%d%% complete", v); }
    // %% in middle of two specifiers
    static String testFormat_percentMiddle(String s, String t) { return String.format("%s%% %s", s, t); }
    // %% at start with specifier
    static String testFormat_percentStart(String s) { return String.format("progress:%%%s", s); }
    // %% at end after specifier
    static String testFormat_percentEnd(int v) { return String.format("value=%d%%", v); }
    // %% only (no specifiers)
    static String testFormat_percentOnly() { return String.format("100%%"); }

    public static void main(String[] args) {
        for (int i = 0; i < 20_000; i++) {
            // ===== String.format() - single specifier =====
            check("hello", testFormat_s("hello"));
            check("value : world", testFormat_sPrefix("world"));
            check("null", testFormat_sNull());
            // Null arguments for numeric conversions (consistent with Formatter behavior)
            check("null", testFormat_dNull());
            check("null", testFormat_xNull());
            check("NULL", testFormat_XNull());
            // null with LEADING_SPACE flag (no width) - flag is ignored for null
            check("[null]", testFormat_dNullLeadingSpace());
            // null with ZERO_PAD and width - ZERO_PAD ignored but width applied with space padding
            check("[ null]", testFormat_dNullZeroPad());
            // Formattable objects use formatTo() for %s
            check("[FORMATTABLE]", testFormat_sFormattable(new FormattableString("formattable")));
            check("[   FORMATTABLE]", testFormat_sFormattableWidth(new FormattableString("formattable")));
            // Formattable with single-digit width (format_1 path) - verifies convLen fix
            check("[   HI]", testFormat_sFormattableWidth5(new FormattableString("hi")));
            check("[    HELLO]", testFormat_sFormattableWidth9(new FormattableString("hello")));
            check("count=42", testFormat_dInt(42));
            check("short=100", testFormat_dShort((short) 100));
            check("byte=127", testFormat_dByte((byte) 127));
            check("-5 items", testFormat_dNeg(-5));
            check("long=123456789012345", testFormat_dLong(123456789012345L));
            check("obj=CustomObject", testFormat_sObject(new CustomObject()));
            check("0xff", testFormat_x(255));
            check("short=0xffff", testFormat_xShort((short) -1));
            check("byte=0xff", testFormat_xByte((byte) -1));
            check("0xFF", testFormat_X(255));

            // ===== BigInteger tests (consistent with Formatter) =====
            java.math.BigInteger bigInt = new java.math.BigInteger("123456789012345678901234567890");
            check("big=123456789012345678901234567890", testFormat_dBigInteger(bigInt));
            check("null", testFormat_dBigIntegerNull());
            check("0x18ee90ff6c373e0ee4e3f0ad2", testFormat_xBigInteger(bigInt));
            check("null", testFormat_xBigIntegerNull());
            check("0x18EE90FF6C373E0EE4E3F0AD2", testFormat_XBigInteger(bigInt));
            check("NULL", testFormat_XBigIntegerNull());
            // BigInteger negative hex (uses sign + magnitude, not 2's complement like int/long)
            java.math.BigInteger bigNeg = java.math.BigInteger.valueOf(-255);
            check("hex=-ff", testFormat_xBigIntegerNeg(bigNeg));
            check("HEX=-FF", testFormat_XBigIntegerNeg(bigNeg));
            check("[-00000ff]", testFormat_xBigIntegerNegWidth(bigNeg));
            // Multi-specifier path (format_arg) - null and negative BigInteger with ZERO_PAD
            check("[    null][a][b]", testFormat_xNullZeroPadMulti());
            check("[-00000ff][a][b]", testFormat_xBigIntegerNegZeroPadMulti(bigNeg));

            // ===== formatted() - single specifier (optimized) =====
            check("formatted test", testFormatted_s("test"));
            check("count=42", testFormatted_dInt(42));
            check("value=123456789012345", testFormatted_dLong(123456789012345L));
            check("hex=ff", testFormatted_xInt(255));
            check("hex=1fffffffffffff", testFormatted_xLong(0x1FFFFFFFFFFFFFL));
            check("HEX=FF", testFormatted_XInt(255));
            check("HEX=ABCDEF", testFormatted_XLong(0xABCDEFL));

            // ===== String.format() - two specifiers =====
            check("a b", testFormat_ss("a", "b"));
            check("x=1, y=2", testFormat_ssPrefix("1", "2"));
            check("null b", testFormat_ssNull());
            check("name=Alice age=30", testFormat_sd("Alice", 30));
            check("id=42 name=Bob", testFormat_ds(42, "Bob"));
            check("1+2", testFormat_dd(1, 2));
            check("ff 10", testFormat_xx(255, 16));
            check("FF 10", testFormat_XX(255, 16));
            // Formattable with format_2 path (%s=%x)
            check("FORMATTABLE=ff", testFormat_sxFormattable(new FormattableString("formattable"), 255));
            // Two-specifier Formattable + width (format_ss/sd/ds paths) - verifies convLen fix
            check("[   HI|  x]", testFormat_ssFormattableWidth(new FormattableString("hi"), "x"));
            check("[   HI|  1]", testFormat_sdFormattableWidth(new FormattableString("hi"), 1));
            check("[  1|   HI]", testFormat_dsFormattableWidth(1, new FormattableString("hi")));

            // ===== formatted() - two specifiers (optimized for s/d) =====
            check("foo and bar", testFormatted_ss("foo", "bar"));
            check("count=42", testFormatted_sd("count", 42));
            check("42:Bob", testFormatted_ds(42, "Bob"));
            check("1+2", testFormatted_dd(1, 2));

            // ===== formatted() - two specifiers (x/X - fallback) =====
            check("ff 10", testFormatted_xx(255, 16));
            check("FF 10", testFormatted_XX(255, 16));

            // ===== String.format() - width tests =====
            check("[   hi]", testFormat_sWidth("hi"));
            check("[hello]", testFormat_sWidthNopad("hello"));
            check("[   42]", testFormat_dWidth(42));
            check("[      -5]", testFormat_dWidthNeg(-5));
            check("[  ff]", testFormat_xWidth(255));
            check("[  FF]", testFormat_XWidth(255));
            check("[   ab|  c]", testFormat_ssWidth("ab", "c"));
            // Null with width
            check("[ null]", testFormat_dWidthNull());
            check("[ null]", testFormat_xWidthNull());
            check("[ NULL]", testFormat_XWidthNull());

            // ===== formatted() - width tests (optimized) =====
            check("[   hello]", testFormatted_sWidth("hello"));
            check("[   42]", testFormatted_dWidth(42));
            check("[  ff]", testFormatted_xWidth(255));
            check("[   ab|  c]", testFormatted_ssWidth("ab", "c"));

            // ===== String.format() - flag tests =====
            // LEADING_SPACE: positive gets leading space, negative gets minus
            check("[ 42]", testFormat_dLeadingSpace(42));
            check("[-5]", testFormat_dLeadingSpaceNeg(-5));
            // ZERO_PAD: pad with zeros
            check("[00042]", testFormat_dZeroPad(42));
            check("[-0005]", testFormat_dZeroPadNeg(-5));
            check("[00ff]", testFormat_xZeroPad(255));
            check("[00FF]", testFormat_XZeroPad(255));

            // ===== formatted() - flag tests (fallback) =====
            check("[ 42]", testFormatted_dLeadingSpace(42));
            check("[00042]", testFormatted_dZeroPad(42));

            // ===== String.format() - 3+ specifiers =====
            check("a b c", testFormat3s("a", "b", "c"));
            check("1+2=3", testFormat3d(1, 2, 3));
            check("name=Alice id=42 hex=ff", testFormatMixed("Alice", 42, 255));
            check("[a|b|c|d]", testFormat4s("a", "b", "c", "d"));
            check("[   hi|  7|end]", testFormatMultiWidth("hi", 7, "end"));

            // ===== String.format() - 5-8 specifiers (format_multi boundary) =====
            check("a-b-3-d-5", testFormat5("a", "b", 3, "d", 5));
            check("a|b|c|d|e|f", testFormat6("a", "b", "c", "d", "e", "f"));
            check("1234567", testFormat7(1, 2, 3, 4, 5, 6, 7));
            check("12345678", testFormat8(1, 2, 3, 4, 5, 6, 7, 8));
            // 9 specifiers: exceeds MAX_FORMAT_SPECS, falls back to Formatter
            check("123456789", testFormat9(1, 2, 3, 4, 5, 6, 7, 8, 9));

            // ===== Width >= 10 (falls back to Formatter) =====
            check("[       abc]", testFormat_sWidth10("abc"));
            check("[             42]", testFormat_dWidth15(42));

            // ===== formatted() - 3+ specifiers (fallback) =====
            check("a b c", testFormatted3s("a", "b", "c"));
            check("1+2=3", testFormatted3d(1, 2, 3));
            check("name=Alice id=42 hex=ff", testFormattedMixed("Alice", 42, 255));

            // ===== Non-optimized patterns =====
            check("+42 1,000 7    ", testFormatWithTag(42, 1000, 7));

            // ===== Specifier position boundary tests =====
            // specIndex = 128 (tests no sign extension bug)
            check("x".repeat(128) + "test", testFormat_specIndex128("test"));
            // specIndex = 200
            check("x".repeat(200) + "y", testFormat_specIndex200("y"));
            // specIndex = 255 (max valid)
            check("x".repeat(255) + "z", testFormat_specIndex255("z"));
            // specIndex = 256 (fallback to Formatter)
            check("x".repeat(256) + "w", testFormat_specIndex256("w"));

            // ===== Escaped %% patterns (fallback to Formatter) =====
            check("%done", testFormat_percentBeforeS("done"));
            check("100% 42", testFormat_percentBeforeD(42));
            check("test%", testFormat_percentAfterS("test"));
            check("75% complete", testFormat_percentAfterD(75));
            check("a% b", testFormat_percentMiddle("a", "b"));
            check("progress:%complete", testFormat_percentStart("complete"));
            check("value=100%", testFormat_percentEnd(100));
            check("100%", testFormat_percentOnly());

            // ===== Edge case tests =====
            // Empty string argument
            check("", testFormat_sEmpty(""));
            // formatted() with Formattable + width
            check("[   HI]", testFormatted_sFormattableWidth(new FormattableString("hi")));
        }

        // Non-optimized fallback patterns
        check("%s", String.format("%%s"));

        // ===== Exception tests (wrong arg types should throw) =====
        testException("d", () -> testFormat_dWrongType());
        testException("x", () -> testFormat_xWrongType());
        testException("X", () -> testFormat_XWrongType());
        // Float/Double should throw for %d (consistent with Formatter)
        testException("d", () -> testFormat_dFloat());
        testException("d", () -> testFormat_dDouble());

        // ===== Locale-specific tests =====
        testLocaleSpecific();

        System.out.println("ALL TESTS PASSED");
    }

    static void testLocaleSpecific() {
        // Save original locale
        java.util.Locale original = java.util.Locale.getDefault(java.util.Locale.Category.FORMAT);

        try {
            // Test with Arabic locale (uses different digits and minus sign)
            java.util.Locale arabic = java.util.Locale.forLanguageTag("ar-EG");
            java.util.Locale.setDefault(java.util.Locale.Category.FORMAT, arabic);
            java.text.DecimalFormatSymbols arDfs = java.text.DecimalFormatSymbols.getInstance(arabic);
            char arZero = arDfs.getZeroDigit();
            char arOne = (char) (arZero + 1);
            char arTwo = (char) (arZero + 2);
            char arFour = (char) (arZero + 4);
            char arFive = (char) (arZero + 5);

            // Re-run tests that should be localized with exact content validation
            for (int i = 0; i < 5_000; i++) {
                // Test ZERO_PAD with negative - should use locale-specific digits
                String result = testFormat_dZeroPadNeg(-5);
                // Expected: [-٠٠٠٥] with Arabic digits, width 5 includes sign
                String expected = "[-" + arZero + arZero + arZero + arFive + "]";
                if (!result.equals(expected)) {
                    throw new AssertionError("Arabic ZERO_PAD negative: expected [" + expected + "] but got [" + result + "]");
                }

                // Test LEADING_SPACE with positive
                result = testFormat_dLeadingSpace(42);
                // Expected: [ ٤٢] with Arabic digits
                expected = "[ " + arFour + arTwo + "]";
                if (!result.equals(expected)) {
                    throw new AssertionError("Arabic LEADING_SPACE: expected [" + expected + "] but got [" + result + "]");
                }

                // Test %x/%X zero-padding should use ASCII '0', not localized digits
                result = testFormat_xZeroPad(255);
                expected = "[00ff]";  // ASCII zeros, not Arabic
                if (!result.equals(expected)) {
                    throw new AssertionError("Arabic %x ZERO_PAD should use ASCII '0': expected [" + expected + "] but got [" + result + "]");
                }

                result = testFormat_XZeroPad(255);
                expected = "[00FF]";  // ASCII zeros, not Arabic
                if (!result.equals(expected)) {
                    throw new AssertionError("Arabic %X ZERO_PAD should use ASCII '0': expected [" + expected + "] but got [" + result + "]");
                }
            }

            // Test with gsw locale (has zero='0', decSep='.', but minus='−' U+2212)
            // This tests the early-exit condition in format_localizeDigits
            // Note: Formatter does NOT localize the minus sign for %d, it uses ASCII '-'
            java.util.Locale gsw = java.util.Locale.forLanguageTag("gsw");
            java.util.Locale.setDefault(java.util.Locale.Category.FORMAT, gsw);
            java.text.DecimalFormatSymbols gswDfs = java.text.DecimalFormatSymbols.getInstance(gsw);

            for (int i = 0; i < 5_000; i++) {
                // Test ZERO_PAD with negative - uses ASCII minus sign (per Formatter spec)
                String result = testFormat_dZeroPadNeg(-5);
                // Expected: [-0005] with ASCII minus, not U+2212
                // Formatter.leadingSign() uses hardcoded '-', not localized
                char firstChar = result.charAt(1);
                if (firstChar != '-') {
                    throw new AssertionError("gsw ZERO_PAD negative: expected ASCII '-' at pos 1, got U+" + String.format("%04X", (int)firstChar));
                }
                // Verify the rest is "0005"
                String rest = result.substring(2, 6);
                if (!rest.equals("0005")) {
                    throw new AssertionError("gsw ZERO_PAD negative: expected [0005] after minus, got [" + rest + "]");
                }

                // Test positive number with zero padding - should work normally
                result = testFormat_dZeroPad(42);
                // Content should be "00042" regardless of internal encoding
                if (!result.contains("00042")) {
                    throw new AssertionError("gsw ZERO_PAD positive: expected to contain [00042], got [" + result + "]");
                }
            }

            // Test with Arabic locale again for format_arg (multi-specifier path)
            java.util.Locale.setDefault(java.util.Locale.Category.FORMAT, arabic);

            for (int i = 0; i < 5_000; i++) {
                // Test 3 specifiers (format_multi -> format_arg path)
                String result = testFormat_d3(-1, 42, -123);
                // Expected: [-٠٠٠١][٠٠٠٤٢][-٠١٢٣] with Arabic zero digit
                char arThree = (char) (arZero + 3);
                String expected = "[-" + arZero + arZero + arZero + arOne + "][" +
                                  arZero + arZero + arZero + arFour + arTwo + "][" +
                                  "-" + arZero + arOne + arTwo + arThree + "]";
                if (!result.equals(expected)) {
                    throw new AssertionError("Arabic format_arg: expected [" + expected + "] but got [" + result + "]");
                }
            }
        } finally {
            // Restore original locale
            java.util.Locale.setDefault(java.util.Locale.Category.FORMAT, original);
        }
    }

    static void check(String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("expected [" + expected + "] but got [" + actual + "]");
        }
    }

    static void testException(String conv, Runnable r) {
        try {
            r.run();
            throw new AssertionError("expected IllegalFormatConversionException for %" + conv);
        } catch (java.util.IllegalFormatConversionException e) {
            // Expected
        }
    }

    /** Custom object for testing %s with non-String arguments. */
    static class CustomObject {
        @Override
        public String toString() {
            return "CustomObject";
        }
    }

    /** Formattable object for testing %s with Formattable interface. */
    static class FormattableString implements java.util.Formattable {
        private final String value;

        FormattableString(String value) {
            this.value = value;
        }

        @Override
        public void formatTo(java.util.Formatter formatter, int flags, int width, int precision) {
            StringBuilder sb = new StringBuilder();
            String str = value.toUpperCase(java.util.Locale.ROOT);
            if (precision >= 0 && str.length() > precision) {
                str = str.substring(0, precision);
            }
            int padding = width - str.length();
            if (padding > 0) {
                // Right-justify by default (no LEFT_JUSTIFY flag)
                sb.repeat(' ', padding);
            }
            sb.append(str);
            formatter.format(sb.toString());
        }
    }
}
