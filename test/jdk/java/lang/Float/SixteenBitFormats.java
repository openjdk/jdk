/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8289551
 * @summary Verify conversion between float and 16-bit formats
 * @library ../Math
 * @build FloatConsts
 * @run main SixteenBitFormats
 */

import static java.lang.Float.*;

public class SixteenBitFormats {
    public static void main(String... argv) {
        int errors = 0;
        errors += binary16RoundTrip();
        if (errors > 0)
            throw new RuntimeException(errors + " errors");
    }

    /*
     * Put all 16-bit values through a conversion loop and make sure
     * the values are preserved. (NaN bit patterns notwithstanding.)
     */
    private static int binary16RoundTrip() {
        int errors = 0;
        for (int i = Short.MIN_VALUE; i < Short.MAX_VALUE; i++) {
            short s = (short)i;
            float f = Float.binary16AsShortBitsToFloat(s);
            short s2 = Float.floatToBinary16AsShortBits(f);
            if (Binary16.compare(s, s2) != 0) {
                errors++;
                System.out.println("Roundtrip failure on " +
                                   Integer.toHexString((int)s) +
                                   "\t got back " + Integer.toHexString((int)s2));
            }
        }
        return errors;
    }

    public static class Binary16 {
        public static final short POSITIVE_INFINITY = (short)0x7c00;
        public static final short NEGATIVE_INFINITY = (short)0xfc00;
        public static final short MAX_VALUE = 0x7bff;
        public static final short ONE = 0x3c00;
        public static final short MIN_NORMAL = 0x0400;
        public static final short MIN_VALUE = 0x0001;
        public static final short NEGATIVE_ZERO = (short)0x8000;
        public static final short POSITIVE_ZERO = 0x0000;

        public static boolean isNaN(short binary16) {
            return ((binary16 & 0x7c00) == 0x7c00) // Max exponent and... 
                && ((binary16 & 0x03ff) != 0 );    // significand nonzero.
        }

        public static int compare(short bin16_1, short bin16_2) {
            if (bin16_1 == bin16_2) {
                return 0;
            } else {
                if (isNaN(bin16_1)) {
                    return isNaN(bin16_2) ? 0 : 1;
                } else {
                    if (isNaN(bin16_2)) {
                        return -1;
                    }
                    return Integer.compare((int)bin16_1, (int) bin16_2);
                }
            }
        }
    }
}
