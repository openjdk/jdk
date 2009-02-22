/*
 * Copyright 2005 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * @test
 * @bug 5037596
 * @summary Verify bitwise conversion works for non-canonical NaN values
 * @author Joseph D. Darcy
 */

import static java.lang.Float.*;
import static sun.misc.FloatConsts.*;

public class BitwiseConversion {
    static int testNanCase(int x) {
        int errors  = 0;
        // Strip out sign and exponent bits
        int y = x & SIGNIF_BIT_MASK;

        float values[] = {
            intBitsToFloat(EXP_BIT_MASK | y),
            intBitsToFloat(SIGN_BIT_MASK | EXP_BIT_MASK | y)
        };

        for(float value: values) {
            if (!isNaN(value)) {
                throw new RuntimeException("Invalid input " + y +
                                           "yielded non-NaN" + value);
            }
            int converted = floatToIntBits(value);
            if (converted != 0x7fc00000) {
                errors++;
                System.err.format("Non-canoncial NaN bits returned: %x%n",
                                  converted);
            }
        }
        return errors;
    }

    public static void main(String... argv) {
        int errors = 0;

        for (int i = 0; i < SIGNIFICAND_WIDTH-1; i++) {
            errors += testNanCase(1<<i);
        }

        if (floatToIntBits(Float.POSITIVE_INFINITY)
                != 0x7F800000) {
            errors++;
            System.err.println("Bad conversion for +infinity.");
        }

        if (floatToIntBits(Float.NEGATIVE_INFINITY)
                != 0xFF800000) {
            errors++;
            System.err.println("Bad conversion for -infinity.");
        }

        if (errors > 0)
            throw new RuntimeException();
    }
}
