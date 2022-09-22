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
 * @summary Verify NaN sign and significand bits are preserved across conversions
 */

/*
 * The behavior tested below is an implementation property not
 * required by the specification. It would be acceptable for this
 * information to not be preserved (as long as a NaN is returned) if,
 * say, a intrinsified version using native hardware instructions
 * behaved differently.
 *
 * If that is the case, this test should be modified to disable
 * intrinsics or to otherwise not run on platforms with an differently
 * behaving intrinsic.
 */
public class Binary16ConversionNaN {
    public static void main(String... argv) {
        int errors = 0;
        errors += binary16NaNRoundTrip();

        if (errors > 0)
            throw new RuntimeException(errors + " errors");
    }

    /*
     * Put all 16-bit NaN values through a conversion loop and make
     * sure the significand, sign, and exponent are all preserved.
     */
    private static int binary16NaNRoundTrip() {
        int errors = 0;
        final int NAN_EXPONENT = 0x7c00;
        final int SIGN_BIT     = 0x8000;

        // A NaN has a nonzero significand
        for (int i = 1; i <= 0x3ff; i++) {
            short binary16NaN = (short)(NAN_EXPONENT | i);
            assert isNaN(binary16NaN);
            errors += testRoundTrip(                   binary16NaN);
            errors += testRoundTrip((short)(SIGN_BIT | binary16NaN));
        }
        return errors;
    }

    private static boolean isNaN(short binary16) {
        return ((binary16 & 0x7c00) == 0x7c00) // Max exponent and...
            && ((binary16 & 0x03ff) != 0 );    // significand nonzero.
    }

    private static int testRoundTrip(int i) {
        int errors = 0;
        short s = (short)i;
        float f =  Float.float16ToFloat(s);
        short s2 = Float.floatToFloat16(f);

        if (s != s2) {
            errors++;
            System.out.println("Roundtrip failure on NaN value " +
                               Integer.toHexString(0xFFFF & (int)s) +
                               "\t got back " + Integer.toHexString(0xFFFF & (int)s2));
        }
        return errors;
    }
}
