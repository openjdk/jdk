/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, Rivos Inc. All rights reserved.
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

package compiler.lib.golden;

import jdk.internal.math.DoubleConsts;
import jdk.internal.math.FloatConsts;

public class GoldenRound {
  public static int golden_round(float a) {
    // below code is copied from java.base/share/classes/java/lang/Math.java
    //  public static int round(float a) { ... }

    int intBits = Float.floatToRawIntBits(a);
    int biasedExp = (intBits & FloatConsts.EXP_BIT_MASK)
            >> (FloatConsts.SIGNIFICAND_WIDTH - 1);
    int shift = (FloatConsts.SIGNIFICAND_WIDTH - 2
            + FloatConsts.EXP_BIAS) - biasedExp;
    if ((shift & -32) == 0) { // shift >= 0 && shift < 32
        // a is a finite number such that pow(2,-32) <= ulp(a) < 1
        int r = ((intBits & FloatConsts.SIGNIF_BIT_MASK)
                | (FloatConsts.SIGNIF_BIT_MASK + 1));
        if (intBits < 0) {
            r = -r;
        }
        // In the comments below each Java expression evaluates to the value
        // the corresponding mathematical expression:
        // (r) evaluates to a / ulp(a)
        // (r >> shift) evaluates to floor(a * 2)
        // ((r >> shift) + 1) evaluates to floor((a + 1/2) * 2)
        // (((r >> shift) + 1) >> 1) evaluates to floor(a + 1/2)
        return ((r >> shift) + 1) >> 1;
    } else {
        // a is either
        // - a finite number with abs(a) < exp(2,FloatConsts.SIGNIFICAND_WIDTH-32) < 1/2
        // - a finite number with ulp(a) >= 1 and hence a is a mathematical integer
        // - an infinity or NaN
        return (int) a;
    }
  }


  public static long golden_round(double a) {
    // below code is copied from java.base/share/classes/java/lang/Math.java
    //  public static int round(double a) { ... }

    long longBits = Double.doubleToRawLongBits(a);
    long biasedExp = (longBits & DoubleConsts.EXP_BIT_MASK)
            >> (DoubleConsts.SIGNIFICAND_WIDTH - 1);
    long shift = (DoubleConsts.SIGNIFICAND_WIDTH - 2
            + DoubleConsts.EXP_BIAS) - biasedExp;
    if ((shift & -64) == 0) { // shift >= 0 && shift < 64
        // a is a finite number such that pow(2,-64) <= ulp(a) < 1
        long r = ((longBits & DoubleConsts.SIGNIF_BIT_MASK)
                | (DoubleConsts.SIGNIF_BIT_MASK + 1));
        if (longBits < 0) {
            r = -r;
        }
        // In the comments below each Java expression evaluates to the value
        // the corresponding mathematical expression:
        // (r) evaluates to a / ulp(a)
        // (r >> shift) evaluates to floor(a * 2)
        // ((r >> shift) + 1) evaluates to floor((a + 1/2) * 2)
        // (((r >> shift) + 1) >> 1) evaluates to floor(a + 1/2)
        return ((r >> shift) + 1) >> 1;
    } else {
        // a is either
        // - a finite number with abs(a) < exp(2,DoubleConsts.SIGNIFICAND_WIDTH-64) < 1/2
        // - a finite number with ulp(a) >= 1 and hence a is a mathematical integer
        // - an infinity or NaN
        return (long) a;
    }
  }
}
