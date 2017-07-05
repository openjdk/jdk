/*
 * Copyright 2003 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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

package sun.misc;

/**
 * This class contains additional constants documenting limits of the
 * <code>double</code> type.
 *
 * @author Joseph D. Darcy
 */

public class DoubleConsts {
    /**
     * Don't let anyone instantiate this class.
     */
    private DoubleConsts() {}

    public static final double POSITIVE_INFINITY = java.lang.Double.POSITIVE_INFINITY;
    public static final double NEGATIVE_INFINITY = java.lang.Double.NEGATIVE_INFINITY;
    public static final double NaN = java.lang.Double.NaN;
    public static final double MAX_VALUE = java.lang.Double.MAX_VALUE;
    public static final double MIN_VALUE = java.lang.Double.MIN_VALUE;

    /**
     * A constant holding the smallest positive normal value of type
     * <code>double</code>, 2<sup>-1022</sup>.  It is equal to the
     * value returned by
     * <code>Double.longBitsToDouble(0x0010000000000000L)</code>.
     *
     * @since 1.5
     */
    public static final double  MIN_NORMAL      = 2.2250738585072014E-308;


    /**
     * The number of logical bits in the significand of a
     * <code>double</code> number, including the implicit bit.
     */
    public static final int SIGNIFICAND_WIDTH   = 53;

    /**
     * Maximum exponent a finite <code>double</code> number may have.
     * It is equal to the value returned by
     * <code>Math.ilogb(Double.MAX_VALUE)</code>.
     */
    public static final int     MAX_EXPONENT    = 1023;

    /**
     * Minimum exponent a normalized <code>double</code> number may
     * have.  It is equal to the value returned by
     * <code>Math.ilogb(Double.MIN_NORMAL)</code>.
     */
    public static final int     MIN_EXPONENT    = -1022;

    /**
     * The exponent the smallest positive <code>double</code>
     * subnormal value would have if it could be normalized.  It is
     * equal to the value returned by
     * <code>FpUtils.ilogb(Double.MIN_VALUE)</code>.
     */
    public static final int     MIN_SUB_EXPONENT = MIN_EXPONENT -
                                                   (SIGNIFICAND_WIDTH - 1);

    /**
     * Bias used in representing a <code>double</code> exponent.
     */
    public static final int     EXP_BIAS        = 1023;

    /**
     * Bit mask to isolate the sign bit of a <code>double</code>.
     */
    public static final long    SIGN_BIT_MASK   = 0x8000000000000000L;

    /**
     * Bit mask to isolate the exponent field of a
     * <code>double</code>.
     */
    public static final long    EXP_BIT_MASK    = 0x7FF0000000000000L;

    /**
     * Bit mask to isolate the significand field of a
     * <code>double</code>.
     */
    public static final long    SIGNIF_BIT_MASK = 0x000FFFFFFFFFFFFFL;

    static {
        // verify bit masks cover all bit positions and that the bit
        // masks are non-overlapping
        assert(((SIGN_BIT_MASK | EXP_BIT_MASK | SIGNIF_BIT_MASK) == ~0L) &&
               (((SIGN_BIT_MASK & EXP_BIT_MASK) == 0L) &&
                ((SIGN_BIT_MASK & SIGNIF_BIT_MASK) == 0L) &&
                ((EXP_BIT_MASK & SIGNIF_BIT_MASK) == 0L)));
    }
}
