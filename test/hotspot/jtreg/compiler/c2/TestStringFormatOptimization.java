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
    static String testFormat_sFormattable(FormattableString fs) { return String.format("[%s]", fs); }
    static String testFormat_sFormattableWidth(FormattableString fs) { return String.format("[%14s]", fs); }
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

    // ========== Exception tests (wrong arg types) ==========

    static void testFormat_dWrongType() { String.format("%d", "string"); }
    static void testFormat_xWrongType() { String.format("%x", "string"); }
    static void testFormat_XWrongType() { String.format("%X", "string"); }

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
    // %x/%X uses format_multi
    static String testFormat_xx(int a, int b) { return String.format("%x %x", a, b); }
    static String testFormat_XX(int a, int b) { return String.format("%X %X", a, b); }

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
    static String testFormatMixed(String s, int d, int x) { return String.format("name=%s id=%d hex=%x", s, d, x); }
    static String testFormat4s(String a, String b, String c, String d) { return String.format("[%s|%s|%s|%s]", a, b, c, d); }
    static String testFormatMultiWidth(String a, int b, String c) { return String.format("[%5s|%3d|%s]", a, b, c); }

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
            // Formattable objects use formatTo() for %s
            check("[FORMATTABLE]", testFormat_sFormattable(new FormattableString("formattable")));
            check("[   FORMATTABLE]", testFormat_sFormattableWidth(new FormattableString("formattable")));
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
        }

        // Non-optimized fallback patterns
        check("%s", String.format("%%s"));

        // ===== Exception tests (wrong arg types should throw) =====
        testException("d", () -> testFormat_dWrongType());
        testException("x", () -> testFormat_xWrongType());
        testException("X", () -> testFormat_XWrongType());

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

            // Re-run tests that should be localized
            for (int i = 0; i < 5_000; i++) {
                // Test ZERO_PAD with negative - should use locale-specific minus sign
                String result = testFormat_dZeroPadNeg(-5);
                // Arabic uses locale-specific digits (٠١٢٣٤٥٦٧٨٩) and minus sign
                // Verify the result has correct structure: [sign + 3 zeros + 5]
                // Length should be 7: [ + sign + 3 padding zeros + digit 5 + ]
                if (result.length() != 7) {
                    throw new AssertionError("ZERO_PAD negative length wrong: " + result.length() + " - " + result);
                }

                // Test LEADING_SPACE with positive
                result = testFormat_dLeadingSpace(42);
                // Should have space prefix and locale-specific digits
                if (result.length() != 5) {
                    throw new AssertionError("LEADING_SPACE length wrong: " + result.length() + " - " + result);
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
