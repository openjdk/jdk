/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package jdk.incubator.vector;

import static jdk.incubator.vector.Float16.MIN_EXPONENT;
import static jdk.incubator.vector.Float16.PRECISION;
import static jdk.incubator.vector.Float16.SIZE;

/**
 * This class contains additional constants documenting limits of the
 * {@code Float16} type.
 */

class Float16Consts {
    /**
     * Don't let anyone instantiate this class.
     */
    private Float16Consts() {}

    /**
     * The number of logical bits in the significand of a
     * {@code Float16} number, including the implicit bit.
     */
    public static final int SIGNIFICAND_WIDTH = PRECISION;

    /**
     * The exponent the smallest positive {@code Float16}
     * subnormal value would have if it could be normalized.
     */
    public static final int MIN_SUB_EXPONENT =
            MIN_EXPONENT - (SIGNIFICAND_WIDTH - 1); // -24

    /**
     * Bias used in representing a {@code Float16} exponent.
     */
    public static final int EXP_BIAS =
            (1 << (SIZE - SIGNIFICAND_WIDTH - 1)) - 1; // 15

    /**
     * Bit mask to isolate the sign bit of a {@code Float16}.
     */
    public static final int SIGN_BIT_MASK = 1 << (SIZE - 1);

    /**
     * Bit mask to isolate the exponent field of a {@code Float16}.
     */
    public static final int EXP_BIT_MASK =
            ((1 << (SIZE - SIGNIFICAND_WIDTH)) - 1) << (SIGNIFICAND_WIDTH - 1);

    /**
     * Bit mask to isolate the significand field of a {@code Float16}.
     */
    public static final int SIGNIF_BIT_MASK = (1 << (SIGNIFICAND_WIDTH - 1)) - 1;

    /**
     * Bit mask to isolate the magnitude bits (combined exponent and
     * significand fields) of a {@code Float16}.
     */
    public static final int MAG_BIT_MASK = EXP_BIT_MASK | SIGNIF_BIT_MASK;

    static {
        // verify bit masks cover all bit positions and that the bit
        // masks are non-overlapping
        assert(((SIGN_BIT_MASK | EXP_BIT_MASK | SIGNIF_BIT_MASK) == 0xFFFF) &&
               (((SIGN_BIT_MASK & EXP_BIT_MASK) == 0) &&
                ((SIGN_BIT_MASK & SIGNIF_BIT_MASK) == 0) &&
                ((EXP_BIT_MASK & SIGNIF_BIT_MASK) == 0)) &&
                ((SIGN_BIT_MASK | MAG_BIT_MASK) == 0xFFFF));
    }
}
