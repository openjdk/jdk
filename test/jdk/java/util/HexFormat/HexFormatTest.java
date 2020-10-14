/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;

/*
 * @test
 * @summary Check Hex formatting and parsing
 * @run testng HexFormatTest
 */

@Test
public class HexFormatTest {

    @DataProvider(name = "HexFormattersParsers")
    Object[][] hexFormattersParsers() {
        String codeDelim = ", ";
        String codePrefix = "0x";
        String codeSuffix = "";
        return new Object[][]{
                {"", "", "", true,
                        HexFormat.of().withUpperCase()},
                {", ", "#", "L", false,
                        HexFormat.ofDelimiter(", ").withPrefix("#").withSuffix("L")},
                {"", "", "", false,
                        HexFormat.of().withPrefix("").withSuffix("")},
                {".", "", "", false,
                        HexFormat.ofDelimiter(".").withPrefix("").withSuffix("")},
                {",", "0x", "", true,
                        HexFormat.ofDelimiter(",").withUpperCase().withPrefix("0x").withSuffix("")},
                {codeDelim, codePrefix, codeSuffix, false,
                        HexFormat.ofDelimiter(codeDelim).withPrefix(codePrefix).withSuffix(codeSuffix)},

        };
    }

    @DataProvider(name = "HexStringsThrowing")
    Object[][] HexStringsThrowing() {
        return new Object[][]{
                {"0", ":", "", ""},         // wrong string length
                {"01:", ":", "", ""},       // wrong string length
                {"01:0", ":", "", ""},      // wrong string length
                {"0", ",", "", ""},         // wrong length and separator
                {"01:", ",", "", ""},       // wrong length and separator
                {"01:0", ",", "", ""},      // wrong length and separator
                {"01:00", ",", "", ""},     // wrong separator
                {"00]", ",", "[", "]"},     // missing prefix
                {"[00", ",", "[", "]"},     // missing suffix
                {"]", ",", "[", "]"},       // missing prefix
                {"[", ",", "[", "]"},       // missing suffix
                {"00", ",", "abc", ""},     // Prefix longer than string
                {"01", ",", "", "def"},     // Suffix longer than string
                {"abc00,", ",", "abc", ""},     // Prefix and delim but not another value
                {"01def,", ",", "", "def"},     // Suffix and delim but not another value
        };
    }

    @DataProvider(name = "BadBytesThrowing")
    Object[][] badBytesThrowing() {
        return new Object[][]{
                {new byte[1], 0, 2},        // bad length
                {new byte[1], 1, 1},        // bad offset + length
                {new byte[1], -1, 2},       // bad length
                {new byte[1], -1, 1},       // bad offset + length
                {new byte[1], 0, -1},       // bad length
                {new byte[1], 1, -1},       // bad offset + length
        };
    }

    @DataProvider(name = "BadParseHexThrowing")
    Object[][] badParseHexThrowing() {
        return new Object[][]{
                {"a", 0, 2, IndexOutOfBoundsException.class},        // bad length
                {"b", 1, 1, IndexOutOfBoundsException.class},        // bad offset + length
                {"a", -1, 2, IndexOutOfBoundsException.class},       // bad length
                {"b", -1, 1, IndexOutOfBoundsException.class},       // bad offset + length
                {"a", 0, -1, IndexOutOfBoundsException.class},       // bad length
                {"b", 1, -1, IndexOutOfBoundsException.class},       // bad offset + length
                {"76543210", 0, 7, IllegalArgumentException.class},  // odd number of digits
        };
    }

    @DataProvider(name = "BadFromHexDigitsThrowing")
    Object[][] badHexDigitsThrowing() {
        return new Object[][]{
                {"a", 0, 2, IndexOutOfBoundsException.class},        // bad length
                {"b", 1, 1, IndexOutOfBoundsException.class},        // bad offset + length
                {"a", -1, 2, IndexOutOfBoundsException.class},       // bad length
                {"b", -1, 1, IndexOutOfBoundsException.class},       // bad offset + length
                {"a", 0, -1, IndexOutOfBoundsException.class},       // bad length
                {"b", 1, -1, IndexOutOfBoundsException.class},       // bad offset + length
        };
    }

    static byte[] genBytes(int origin, int len) {
        byte[] bytes = new byte[len];
        for (int i = 0; i < len; i++)
            bytes[i] = (byte) (origin + i);
        return bytes;
    }

    @Test
    static void testToHex() {
        HexFormat hex = HexFormat.of();
        for (int i = 0; i < 32; i++) {
            char c = hex.toLowHexDigit((byte)i);
            String expected = Integer.toHexString(i & 0xf);
            Assert.assertEquals(c, expected.charAt(0), "toHex formatting");
        }
    }

    @Test
    static void testToHexPair() {
        HexFormat hex = HexFormat.of();
        for (int i = 0; i < 256; i++) {
            String actual = hex.toHexDigits((byte)i);
            int expected = hex.fromHexDigits(actual);
            Assert.assertEquals(expected, i, "byteFromHex formatting");
            Assert.assertEquals(actual.charAt(0), hex.toHighHexDigit((byte)i),
                    "first char mismatch");
            Assert.assertEquals(actual.charAt(1), hex.toLowHexDigit((byte)i),
                    "second char mismatch");
        }
    }

    @Test
    static void testFromHex() {
        HexFormat hex = HexFormat.of();
        String chars = "0123456789ABCDEF0123456789abcdef";
        for (int i = 0; i < chars.length(); i++) {
            int v = hex.fromHexDigit(chars.charAt(i));
            Assert.assertEquals(v, i & 0xf, "fromHex decode");
        }
    }

    @Test
    static void testFromHexInvalid() {
        HexFormat hex = HexFormat.of();
        // An assortment of invalid characters
        String chars = "\u0000 /:\u0040G\u0060g\u007f";
        for (int i = 0; i < chars.length(); i++) {
            char ch = chars.charAt(i);
            Throwable ex = Assert.expectThrows(NumberFormatException.class,
                    () -> hex.fromHexDigit(ch));
            System.out.println(ex);
        }
    }

    @Test
    static void testAppendHexByte() {
        HexFormat hex = HexFormat.of();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 256; i++) {
            sb.setLength(0);
            hex.toHexDigits(sb, (byte)i);
            Assert.assertEquals(sb.length(), 2, "wrong length after append: " + i);
            Assert.assertEquals(sb.charAt(0), hex.toHighHexDigit((byte)i), "MSB converted wrong");
            Assert.assertEquals(sb.charAt(1), hex.toLowHexDigit((byte)i), "LSB converted wrong");

            Assert.assertEquals(hex.fromHexDigits(sb), i, "hex.format(sb, byte) wrong");
        }
    }

    @Test
    static void testFromHexPairInvalid() {
                HexFormat hex = HexFormat.of();

        // An assortment of invalid characters
        String chars = "-0--0-";
        for (int i = 0; i < chars.length(); i += 2) {
            final int ndx = i;
            Throwable ex = Assert.expectThrows(NumberFormatException.class,
                    () -> hex.fromHexDigits(chars.subSequence(ndx, ndx+2)));
            System.out.println(ex);
        }
    }

    @Test(dataProvider = "HexStringsThrowing")
    static void testToBytesThrowing(String value, String sep, String prefix, String suffix) {
        HexFormat hex = HexFormat.ofDelimiter(sep).withPrefix(prefix).withSuffix(suffix);
        Throwable ex = Assert.expectThrows(IllegalArgumentException.class,
                () -> {
                    byte[] v = hex.parseHex(value);
                    System.out.println("str: " + value + ", actual: " + v + ", bytes: " +
                                    Arrays.toString(v));
                });
        System.out.println("ex: " + ex);
    }

    @Test
    static void testFactoryNPE() {
        Assert.assertThrows(NullPointerException.class, () -> HexFormat.ofDelimiter(null));
        Assert.assertThrows(NullPointerException.class, () -> HexFormat.of().withDelimiter(null));
        Assert.assertThrows(NullPointerException.class, () -> HexFormat.of().withPrefix(null));
        Assert.assertThrows(NullPointerException.class, () -> HexFormat.of().withSuffix(null));
    }

    @Test
    static void testFormatHexNPE() {
        Assert.assertThrows(NullPointerException.class,
                () -> HexFormat.of().formatHex(null));
        Assert.assertThrows(NullPointerException.class,
                () -> HexFormat.of().formatHex(null, 0, 1));
        Assert.assertThrows(NullPointerException.class,
                () -> HexFormat.of().formatHex(null, null));
        Assert.assertThrows(NullPointerException.class,
                () -> HexFormat.of().formatHex(null, null, 0, 0));
        StringBuilder sb = new StringBuilder();
        Assert.assertThrows(NullPointerException.class,
                () -> HexFormat.of().formatHex(sb, null));
        Assert.assertThrows(NullPointerException.class,
                () -> HexFormat.of().formatHex(sb, null, 0, 1));
    }

    @Test
    static void testParseHexNPE() {
        Assert.assertThrows(NullPointerException.class,
                () -> HexFormat.of().parseHex(null));
        Assert.assertThrows(NullPointerException.class,
                () -> HexFormat.of().parseHex((String)null, 0, 0));
        Assert.assertThrows(NullPointerException.class,
                () -> HexFormat.of().parseHex((char[])null, 0, 0));
    }

    @Test
    static void testFromHexNPE() {
        Assert.assertThrows(NullPointerException.class,
                () -> HexFormat.of().fromHexDigits(null));
        Assert.assertThrows(NullPointerException.class,
                () -> HexFormat.of().fromHexDigits(null, 0, 0));
        Assert.assertThrows(NullPointerException.class,
                () -> HexFormat.of().fromHexDigitsToLong(null));
        Assert.assertThrows(NullPointerException.class,
                () -> HexFormat.of().fromHexDigitsToLong(null, 0, 0));
    }

    @Test
    static void testToHexDigitsNPE() {
        Assert.assertThrows(NullPointerException.class,
                () -> HexFormat.of().toHexDigits(null, (byte)0));
    }

    @Test(dataProvider = "BadParseHexThrowing")
    static void badParseHex(String string, int offset, int length,
                            Class<? extends Throwable> exClass) {
        Assert.assertThrows(exClass,
                () -> HexFormat.of().parseHex(string, offset, length));
        char[] chars = string.toCharArray();
        Assert.assertThrows(exClass,
                () -> HexFormat.of().parseHex(chars, offset, length));
    }

    @Test(dataProvider = "BadFromHexDigitsThrowing")
    static void badFromHexDigits(String string, int offset, int length,
                           Class<? extends Throwable> exClass) {
        Assert.assertThrows(exClass,
                () -> HexFormat.of().fromHexDigits(string, offset, length));
        Assert.assertThrows(exClass,
                () -> HexFormat.of().fromHexDigitsToLong(string, offset, length));
    }

    // Verify IAE for strings that are too long for the target primitive type
    // or the number of requested digits is too large.
    @Test
    static void wrongNumberDigits() {
        Assert.assertThrows(IllegalArgumentException.class,
                () -> HexFormat.of().fromHexDigits("9876543210"));
        Assert.assertThrows(IllegalArgumentException.class,
                () -> HexFormat.of().fromHexDigits("9876543210", 0, 9));
        Assert.assertThrows(IllegalArgumentException.class,
                () -> HexFormat.of().fromHexDigitsToLong("98765432109876543210"));
        Assert.assertThrows(IllegalArgumentException.class,
                () -> HexFormat.of().fromHexDigitsToLong("98765432109876543210", 0, 17));
    }

    @Test(dataProvider="HexFormattersParsers")
    static void testFormatter(String delimiter, String prefix, String suffix,
                                   boolean uppercase,
                                   HexFormat hex) {
        byte[] expected = genBytes('A', 15);
        String res = hex.formatHex(expected);
        Assert.assertTrue(res.startsWith(prefix), "Prefix not found");
        Assert.assertTrue(res.endsWith(suffix), "Suffix not found");
        int expectedLen = expected.length * (2 + prefix.length() +
                delimiter.length() + suffix.length()) - delimiter.length();
        Assert.assertEquals(res.length(), expectedLen, "String length");

        if (expected.length > 1) {
            // check prefix and suffix is present for each hex pair
            for (int i = 0; i < expected.length; i++) {
                int valueChars = prefix.length() + 2 + suffix.length();
                int offset = i * (valueChars + delimiter.length());
                String value = res.substring(offset, offset + valueChars);
                Assert.assertTrue(value.startsWith(prefix), "wrong prefix");
                Assert.assertTrue(value.endsWith(suffix), "wrong suffix");

                // Check case of digits
                String cc = value.substring(prefix.length(), prefix.length() + 2);
                Assert.assertEquals(cc,
                        (uppercase) ? cc.toUpperCase(Locale.ROOT) : cc.toLowerCase(Locale.ROOT),
                        "Case mismatch");
                if (i < expected.length - 1 && !delimiter.isEmpty()) {
                    // Check the delimiter is present for each pair except the last
                    Assert.assertEquals(res.substring(offset + valueChars,
                            offset + valueChars + delimiter.length()), delimiter);
                }
            }
        }
    }

    @Test(dataProvider="HexFormattersParsers")
    static void testDecoderString(String unused1, String unused2, String unused3,
                                   boolean unused4, HexFormat hex) {
        byte[] expected = genBytes('A', 15);
        String s = hex.formatHex(expected);
        System.out.println("    formatted: " + s);

        byte[] actual = hex.parseHex(s);
        System.out.println("    parsed as: " + Arrays.toString(expected));
        int mismatch = Arrays.mismatch(expected, actual);
        Assert.assertEquals(actual, expected, "encode/decode cycle failed, mismatch: " + mismatch);
    }

    @Test(dataProvider="HexFormattersParsers")
    static void testDecoderCharArray(String unused1, String unused2, String unused3,
                                     boolean unused4, HexFormat hex) {
        byte[] expected = genBytes('A', 15);
        String s = hex.formatHex(expected);
        System.out.println("    formatted: " + s);

        char[] chars = s.toCharArray();
        byte[] actual = hex.parseHex(chars, 0, chars.length);
        System.out.println("    parsed as: " + Arrays.toString(expected));
        int mismatch = Arrays.mismatch(expected, actual);
        Assert.assertEquals(actual, expected, "format/parse cycle failed, mismatch: " + mismatch);
    }

    @Test(dataProvider="HexFormattersParsers")
    static void testFormatterToString(String delimiter, String prefix, String suffix,
                                    boolean uppercase,
                                    HexFormat hex) {
        String actual = String.format(
                "uppercase: %s, delimiter: \"%s\", prefix: \"%s\", suffix: \"%s\"",
                uppercase, escapeNL(delimiter), escapeNL(prefix), escapeNL(suffix));
        System.out.println("    hex: " + actual);
        Assert.assertEquals(actual, hex.toString(), "Formatter toString mismatch");
    }

    private static String escapeNL(String string) {
        return string.replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    @Test
    static void testVariableLength() {
        HexFormat hex = HexFormat.of();

        String allHex = "fedcba9876543210";
        final long orig = 0xfedcba9876543210L;
        for (int digits = 0; digits <= 16; digits++) {
            String s = hex.toHexDigits(orig, digits);
            long actual = hex.fromHexDigitsToLong(s, 0, digits);
            System.out.printf("    digits: %2d, formatted: \"%s\", parsed as: 0x%016xL%n",
                    digits, s, actual);
            Assert.assertEquals(s, allHex.substring(16 - digits, 16));
            long expected = (digits < 16) ? orig & ~(0xffffffffffffffffL << (4 * digits)) : orig;
            Assert.assertEquals(actual, expected);
        }
    }

    /**
     * Example code from the HexFormat javadoc.
     * Showing simple usage of the API using "assert" to express the correct results
     * when shown in the javadoc.
     * The additional TestNG asserts verify the correctness of the same code.
     */
    @Test
    private static void samples() {
        {
            // Primitive formatting and parsing.
            HexFormat hex = HexFormat.of();

            byte b = 127;
            String byteStr = hex.toHexDigits(b);
            System.out.println("    " + byteStr);

            int byteVal = hex.fromHexDigits(byteStr);
            assert(byteStr.equals("7f"));
            assert(b == byteVal);
            Assert.assertTrue(byteStr.equals("7f"));
            Assert.assertTrue(b == byteVal);


            char c = 'A';
            String charStr = hex.toHexDigits(c);
            System.out.println("    " + charStr);
            int charVal = hex.fromHexDigits(charStr);
            assert(c == charVal);
            Assert.assertTrue(c == charVal);

            int i = 12345;
            String intStr = hex.toHexDigits(i);
            System.out.println("    " + intStr);
            int intVal = hex.fromHexDigits(intStr);
            assert(i == intVal);
            Assert.assertTrue(i == intVal);

            long l = Long.MAX_VALUE;
            String longStr = hex.toHexDigits(l, 16);
            long longVal = hex.fromHexDigitsToLong(longStr, 0, 16);
            System.out.println("    " + longStr + ", " + longVal);
            assert(l == longVal);
            Assert.assertTrue(l == longVal);
        }

        {
            // RFC 4752 Fingerprint
            HexFormat formatFingerprint = HexFormat.ofDelimiter(":").withUpperCase();
            byte[] bytes = {0, 1, 2, 3, 124, 125, 126, 127};
            String str = formatFingerprint.formatHex(bytes);
            System.out.println("    Formatted: " + str);

            byte[] parsed = formatFingerprint.parseHex(str);
            System.out.println("    Parsed: " + Arrays.toString(parsed));
            assert(Arrays.equals(bytes, parsed));
            Assert.assertTrue(Arrays.equals(bytes, parsed));
        }

        {
            // Comma separated formatting
            HexFormat commaFormat = HexFormat.ofDelimiter(",");
            byte[] bytes = {0, 1, 2, 3, 124, 125, 126, 127};
            String str = commaFormat.formatHex(bytes);
            System.out.println("    Formatted: " + str);

            byte[] parsed = commaFormat.parseHex(str);
            System.out.println("    Parsed: " + Arrays.toString(parsed));
            assert(Arrays.equals(bytes, parsed));
            Assert.assertTrue(Arrays.equals(bytes, parsed));
        }
        {
            // Text formatting
            HexFormat commaFormat = HexFormat.ofDelimiter(", ").withPrefix("#");
            byte[] bytes = {0, 1, 2, 3, 124, 125, 126, 127};
            String str = commaFormat.formatHex(bytes);
            System.out.println("    Formatted: " + str);

            byte[] parsed = commaFormat.parseHex(str);
            System.out.println("    Parsed:    " + Arrays.toString(parsed));
            assert(Arrays.equals(bytes, parsed));
            Assert.assertTrue(Arrays.equals(bytes, parsed));
        }
    }
}
