/*
 * Copyright (c) 2009, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6860973
 * @summary Project Coin: Underscores in literals
 */


public class UnderscoreLiterals {
    public static void main(String... args) throws Exception {
        new UnderscoreLiterals().run();
    }

    public void run() throws Exception {
        // decimal
        test(1, 1);
        test(10, 10);
        test(1_0, 10);
        test(1__0, 10);
        test(1_0_0, 100);
        test(1__0__0, 100);
        test(123_456_789, 123456789);

        // long
        test(1l, 1l);
        test(10l, 10l);
        test(1_0l, 10l);
        test(1__0l, 10l);
        test(1_0_0l, 100l);
        test(1__0__0l, 100l);
        test(123_456_789l, 123456789l);

        // float
        test(.1f, .1f);
        test(.10f, .10f);
        test(.1_0f, .10f);
        test(.1__0f, .10f);
        test(.1_0_0f, .100f);
        test(.1__0__0f, .100f);
        test(1e1, 1e1);
        test(1e10, 1e10);
        test(1e1_0, 1e10);
        test(1e1__0, 1e10);
        test(1e1_0_0, 1e100);
        test(1e1__0__0, 1e100);
        test(.123_456_789f, .123456789f);
        test(0.1f, 0.1f);
        test(0.10f, 0.10f);
        test(0.1_0f, 0.10f);
        test(0.1__0f, 0.10f);
        test(0.1_0_0f, 0.100f);
        test(0.1__0__0f, 0.100f);
        test(0.123_456_789f, 0.123456789f);
        test(1_1.1f, 1_1.1f);
        test(1_1.10f, 1_1.10f);
        test(1_1.1_0f, 1_1.10f);
        test(1_1.1__0f, 1_1.10f);
        test(1_1.1_0_0f, 1_1.100f);
        test(1_1.1__0__0f, 1_1.100f);
        test(1_1.123_456_789f, 1_1.123456789f);

        // double
        test(.1d, .1d);
        test(.10d, .10d);
        test(.1_0d, .10d);
        test(.1__0d, .10d);
        test(.1_0_0d, .100d);
        test(.1__0__0d, .100d);
        test(1e1, 1e1);
        test(1e10, 1e10);
        test(1e1_0, 1e10);
        test(1e1__0, 1e10);
        test(1e1_0_0, 1e100);
        test(1e1__0__0, 1e100);
        test(.123_456_789d, .123456789d);
        test(0.1d, 0.1d);
        test(0.10d, 0.10d);
        test(0.1_0d, 0.10d);
        test(0.1__0d, 0.10d);
        test(0.1_0_0d, 0.100d);
        test(0.1__0__0d, 0.100d);
        test(0.123_456_789d, 0.123456789d);
        test(1_1.1d, 1_1.1d);
        test(1_1.10d, 1_1.10d);
        test(1_1.1_0d, 1_1.10d);
        test(1_1.1__0d, 1_1.10d);
        test(1_1.1_0_0d, 1_1.100d);
        test(1_1.1__0__0d, 1_1.100d);
        test(1_1.123_456_789d, 1_1.123456789d);

        // binary
        test(0b1, 1);
        test(0b10, 2);
        test(0b1_0, 2);
        test(0b1__0, 2);
        test(0b1_0_0, 4);
        test(0b1__0__0, 4);
        test(0b0001_0010_0011, 0x123);

        // octal
        test(01, 1);
        test(010, 8);
        test(01_0, 8);
        test(01__0, 8);
        test(01_0_0, 64);
        test(01__0__0, 64);
        test(0_1, 1);
        test(0_10, 8);
        test(0_1_0, 8);
        test(0_1__0, 8);
        test(0_1_0_0, 64);
        test(0_1__0__0, 64);
        test(0_001_002_003, 01002003);

        // hexadecimal
        test(0x1, 1);
        test(0x10, 16);
        test(0x1_0, 16);
        test(0x1__0, 16);
        test(0x1_0_0, 256);
        test(0x1__0__0, 256);
        test(0x01_02_03_04, 0x1020304);

        // misc
        long creditCardNumber = 1234_5678_9012_3456L;
        test(creditCardNumber, 1234567890123456L);
        long socialSecurityNumbers = 999_99_9999L;
        test(socialSecurityNumbers, 999999999L);
        double monetaryAmount = 12_345_132.12d;
        test(monetaryAmount, 12345132.12d);
        long hexBytes = 0xFF_EC_DE_5E;
        test(hexBytes, 0xffecde5e);
        long hexWords = 0xFFEC_DE5E;
        test(hexWords, 0xffecde5e);
        long maxLong = 0x7fff_ffff_ffff_ffffL;
        test(maxLong, 0x7fffffffffffffffL);
        long maxLongDecimal = 9223372036854775807L;
        long alsoMaxLong = 9_223_372_036_854_775_807L;
        test(alsoMaxLong, maxLongDecimal);
        double whyWouldYouEverDoThis = 0x1.ffff_ffff_ffff_fp10_23;
        double whyWouldYouEverDoEvenThis = 0x1.fffffffffffffp1023;
        test(whyWouldYouEverDoThis, whyWouldYouEverDoEvenThis);

        if (errors > 0)
             throw new Exception(errors + " errors found");
    }

    void test(int value, int expect) {
        count++;
        if (value != expect)
            error("test " + count + "\nexpected: 0x" + Integer.toHexString(expect) + "\n   found: 0x" + Integer.toHexString(value));
    }

    void test(double value, double expect) {
        count++;
        if (value != expect)
            error("test " + count + "\nexpected: 0x" + expect + "\n   found: 0x" + value);
    }

    void test(long value, long expect) {
        count++;
        if (value != expect)
            error("test " + count + "\nexpected: 0x" + Long.toHexString(expect) + "\n   found: 0x" + Long.toHexString(value));
    }

    void error(String message) {
        System.out.println(message);
        errors++;
    }

    int count;
    int errors;
}
