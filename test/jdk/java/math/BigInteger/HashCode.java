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

import java.math.BigInteger;

/*
 * @test
 * @bug 8310813
 * @summary Check hashCode implementation against reference values
 */
public class HashCode {

    // This test guards against inadvertent changes to BigInteger.hashCode,
    // by checking generated hashCode values against reference values
    // captured immediately before 8310813

    public static void main(String[] args) {
        equals(          0, BigInteger.ZERO);
        equals(          1, BigInteger.ONE);
        equals(          2, BigInteger.TWO);
        equals(         10, BigInteger.TEN);
        equals(       -128, BigInteger.valueOf(Byte.MIN_VALUE));
        equals(        127, BigInteger.valueOf(Byte.MAX_VALUE));
        equals(     -32768, BigInteger.valueOf(Short.MIN_VALUE));
        equals(      32767, BigInteger.valueOf(Short.MAX_VALUE));
        equals(          0, BigInteger.valueOf(Character.MIN_VALUE));
        equals(      65535, BigInteger.valueOf(Character.MAX_VALUE));
        equals(-2147483648, BigInteger.valueOf(Integer.MIN_VALUE));
        equals( 2147483647, BigInteger.valueOf(Integer.MAX_VALUE));
        equals(-2147483648, BigInteger.valueOf(Long.MIN_VALUE));
        equals( 2147483616, BigInteger.valueOf(Long.MAX_VALUE));
        equals(         -1, BigInteger.valueOf(-1));

        // a 37-byte negative number, generated at random
        equals( 1428257188, new BigInteger("""
                -5573526435790097067262357965922443376770234990700620666883\
                2705705469477701887396205062479"""));
        // a 123-byte positive number, generated at random
        equals( -412503667, new BigInteger("""
                13093241912251296135908856604398494061635394768699286753760\
                22827291528069076557720973813183142494646514532475660126948\
                43316474303725664231917408569680292008962577772928370936861\
                12952691245923210726443405774197400117701581498597123145452\
                15111774818054200162634242662445757757255702394598235971294\
                50"""));
    }

    private static void equals(int expectedHashCode, BigInteger i) {
        int actualHashCode = i.hashCode();
        if (expectedHashCode != actualHashCode)
            throw new AssertionError("%s: expectedHashCode=%s, actual=%s"
                    .formatted(i, expectedHashCode, actualHashCode));
    }
}
