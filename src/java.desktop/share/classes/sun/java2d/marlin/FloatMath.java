/*
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates. All rights reserved.
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

package sun.java2d.marlin;

/**
 * Faster Math ceil / floor routines derived from StrictMath
 */
public final class FloatMath implements MarlinConst {

    // overflow / NaN handling enabled:
    static final boolean CHECK_OVERFLOW = true;
    static final boolean CHECK_NAN = true;

    private FloatMath() {
        // utility class
    }

    // faster inlined min/max functions in the branch prediction is high
    static int max(final int a, final int b) {
        return (a >= b) ? a : b;
    }

    static int min(final int a, final int b) {
        return (a <= b) ? a : b;
    }

    /**
     * Faster alternative to ceil(double) optimized for the integer domain
     * and supporting NaN and +/-Infinity.
     *
     * @param a a value.
     * @return the largest (closest to positive infinity) integer value
     * that less than or equal to the argument and is equal to a mathematical
     * integer.
     */
    public static int ceil_int(final double a) {
        final int intpart = (int) a;

        if (a <= intpart
                || (CHECK_OVERFLOW && intpart == Integer.MAX_VALUE)
                || CHECK_NAN && Double.isNaN(a)) {
            return intpart;
        }
        return intpart + 1;
    }

    /**
     * Faster alternative to floor(double) optimized for the integer domain
     * and supporting NaN and +/-Infinity.
     *
     * @param a a value.
     * @return the largest (closest to positive infinity) floating-point value
     * that less than or equal to the argument and is equal to a mathematical
     * integer.
     */
    public static int floor_int(final double a) {
        final int intpart = (int) a;

        if (a >= intpart
                || (CHECK_OVERFLOW && intpart == Integer.MIN_VALUE)
                || CHECK_NAN && Double.isNaN(a)) {
            return intpart;
        }
        return intpart - 1;
    }
}
