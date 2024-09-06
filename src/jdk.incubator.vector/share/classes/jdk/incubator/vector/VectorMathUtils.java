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


/**
 * Following class declares utility routines used in the fallback implementation
 * various vector APIs.
 *
 */
public class VectorMathUtils {

   /**
    * Default Constructor definition.
    */
    private VectorMathUtils() {
    }

    /**
     * Based on the unsigned comparison returns the greater of two {@code long} values.
     *
     * @param a the first operand
     * @param b the second operand
     * @return the greater of {@code a} and {@code b}
     * @see java.util.function.BinaryOperator
     * @since 24
     */
    static long maxUnsigned(long a, long b) {
        return Long.compareUnsigned(a, b) > 0 ? a : b;
    }

    /**
     * Based on the unsigned comparison returns the smaller of two {@code long} values.
     *
     * @param a the first operand
     * @param b the second operand
     * @return the smaller of {@code a} and {@code b}
     * @see java.util.function.BinaryOperator
     * @since 24
     */
    static long minUnsigned(long a, long b) {
        return Long.compareUnsigned(a, b) < 0 ? a : b;
    }

    /**
     * Saturating addition of two {@code long} values,
     * which returns a {@code Long.MIN_VALUE} in underflowing or
     * {@code Long.MAX_VALUE} in overflowing scenario.
     *
     * @param a the first operand
     * @param b the second operand
     * @return the sum of {@code a} and {@code b} iff within {@code long} value range else delimiting {@code Long.MIN_VALUE/MAX_VALUE} value.
     * @see java.util.function.BinaryOperator
     * @since 24
     */
    static long addSaturating(long a, long b) {
        long res = a + b;
        // HD 2-12 Overflow iff both arguments have the opposite sign of the result
        if (((a ^ res) & (b ^ res)) < 0) {
            return res < 0 ? Long.MAX_VALUE : Long.MIN_VALUE;
        } else {
            return res;
        }
    }

    /**
     * Saturating subtraction of two {@code long} values,
     * which returns a {@code Long.MIN_VALUE} in underflowing or
     * {@code Long.MAX_VALUE} in overflowing scenario.
     *
     * @param a the first operand
     * @param b the second operand
     * @return the difference between {@code a} and {@code b} iff within {@code long} value range else delimiting {@code Long.MIN_VALUE/MAX_VALUE} value.
     * @see java.util.function.BinaryOperator
     * @since 24
     */
    static long subSaturating(long a, long b) {
        long res = a - b;
        // HD 2-12 Overflow iff the arguments have different signs and
        // the sign of the result is different from the sign of a
        if (((a ^ b) & (a ^ res)) < 0) {
            return a < 0 ? Long.MIN_VALUE : Long.MAX_VALUE;
        } else {
            return res;
        }
    }

    /**
     * Saturating unsigned addition of two {@code long} values,
     * which returns maximum unsigned long value in overflowing scenario.
     *
     * @param a the first operand
     * @param b the second operand
     * @return the unsigned sum of {@code a} and {@code b} iff within unsigned value range else delimiting maximum unsigned long value.
     * @see java.util.function.BinaryOperator
     * @since 24
     */
    static long addSaturatingUnsigned(long a, long b) {
        long res = a + b;
        boolean overflow = Long.compareUnsigned(res, (a | b)) < 0;
        if (overflow) {
           return -1L;
        } else {
           return res;
        }
    }

    /**
     * Saturating unsigned subtraction of two {@code long} values,
     * which returns a zero in underflowing scenario.
     *
     * @param a the first operand
     * @param b the second operand
     * @return the unsigned difference between {@code a} and {@code b} iff within unsigned value range else delimiting zero value.
     * @see java.util.function.BinaryOperator
     * @since 24
     */
    static long subSaturatingUnsigned(long a, long b) {
        if (Long.compareUnsigned(b, a) < 0) {
           return a - b;
        } else {
           return 0;
        }
    }

    /**
     * Based on the unsigned comparison returns the greater of two {@code int} values.
     *
     * @param a the first operand
     * @param b the second operand
     * @return the greater of {@code a} and {@code b}
     * @see java.util.function.BinaryOperator
     * @since 24
     */
    static int maxUnsigned(int a, int b) {
        return Integer.compareUnsigned(a, b) > 0 ? a : b;
    }

    /**
     * Based on the unsigned comparison returns the smaller of two {@code int} values.
     *
     * @param a the first operand
     * @param b the second operand
     * @return the smaller of {@code a} and {@code b}
     * @see java.util.function.BinaryOperator
     * @since 24
     */
    static int minUnsigned(int a, int b) {
        return Integer.compareUnsigned(a, b) < 0 ? a : b;
    }


    /**
     * Saturating addition of two {@code int} values,
     * which returns an {@code Integer.MIN_VALUE} in underflowing or
     * {@code Integer.MAX_VALUE} in overflowing scenario.
     *
     * @param a the first operand
     * @param b the second operand
     * @return the sum of {@code a} and {@code b} iff within {@code int} value range else delimiting {@code Integer.MIN_VALUE/MAX_VALUE} value.
     * @see java.util.function.BinaryOperator
     * @since 24
     */
    static int addSaturating(int a, int b) {
        long res = (long)a + (long)b;
        if (res > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        } else if (res < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        } else {
            return (int)res;
        }
    }

    /**
     * Saturating subtraction of two {@code int} values,
     * which returns an {@code Integer.MIN_VALUE} in underflowing or
     * {@code Integer.MAX_VALUE} in overflowing scenario.
     *
     * @param a the first operand
     * @param b the second operand
     * @return the difference between {@code a} and {@code b} iff within {@code int} value range else delimiting {@code Integer.MIN_VALUE/MAX_VALUE} value.
     * @see java.util.function.BinaryOperator
     * @since 24
     */
    static int subSaturating(int a, int b) {
        long res = (long)a - (long)b;
        if (res > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        } else if (res < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        } else {
            return (int)res;
        }
    }

    /**
     * Saturating unsigned addition of two {@code int} values,
     * which returns maximum unsigned int value in overflowing scenario.
     *
     * @param a the first operand
     * @param b the second operand
     * @return the unsigned sum of {@code a} and {@code b} iff within unsigned value range else delimiting maximum unsigned int value.
     * @see java.util.function.BinaryOperator
     * @since 24
     */
    static int addSaturatingUnsigned(int a, int b) {
        int res = a + b;
        boolean overflow = Integer.compareUnsigned(res, (a | b)) < 0;
        if (overflow)  {
           return -1;
        } else {
           return res;
        }
    }


    /**
     * Based on the unsigned comparison returns the greater of two {@code short} values.
     *
     * @param a the first operand
     * @param b the second operand
     * @return the greater of {@code a} and {@code b}
     * @see java.util.function.BinaryOperator
     * @since 24
     */
    static short maxUnsigned(short a, short b) {
        return Short.compareUnsigned(a, b) > 0 ? a : b;
    }

    /**
     * Based on the unsigned comparison returns the smaller of two {@code short} values.
     *
     * @param a the first operand
     * @param b the second operand
     * @return the smaller of {@code a} and {@code b}
     * @see java.util.function.BinaryOperator
     * @since 24
     */
    static short minUnsigned(short a, short b) {
        return Short.compareUnsigned(a, b) < 0 ? a : b;
    }

    /**
     * Saturating addition of two {@code short} values,
     * which returns a {@code Short.MIN_VALUE} in underflowing or
     * {@code Short.MAX_VALUE} in overflowing scenario.
     *
     * @param a the first operand
     * @param b the second operand
     * @return the sum of {@code a} and {@code b} iff within {@code short} value range else delimiting {@code Short.MIN_VALUE/MAX_VALUE} value.
     * @see java.util.function.BinaryOperator
     * @since 24
     */
    static short addSaturating(short a, short b) {
        int res = a + b;
        if (res > Short.MAX_VALUE) {
            return Short.MAX_VALUE;
        } else if (res < Short.MIN_VALUE) {
            return Short.MIN_VALUE;
        } else {
            return (short)res;
        }
    }

    /**
     * Saturating subtraction of two {@code short} values,
     * which returns a {@code Short.MIN_VALUE} in underflowing or
     * {@code Short.MAX_VALUE} in overflowing scenario.
     *
     * @param a the first operand
     * @param b the second operand
     * @return the difference between {@code a} and {@code b} iff within {@code short} value range else delimiting {@code Short.MIN_VALUE/MAX_VALUE} value.
     * @see java.util.function.BinaryOperator
     * @since 24
     */
    static short subSaturating(short a, short b) {
        int res = a - b;
        if (res > Short.MAX_VALUE) {
            return Short.MAX_VALUE;
        } else if (res < Short.MIN_VALUE) {
            return Short.MIN_VALUE;
        } else {
            return (short)res;
        }
    }

    /**
     * Saturating unsigned addition of two {@code short} values,
     * which returns maximum unsigned short value in overflowing scenario.
     *
     * @param a the first operand
     * @param b the second operand
     * @return the unsigned sum of {@code a} and {@code b} iff within unsigned value range else delimiting maximum unsigned short value.
     * @see java.util.function.BinaryOperator
     * @since 24
     */
    static short addSaturatingUnsigned(short a, short b) {
        short res = (short)(a + b);
        boolean overflow = Short.compareUnsigned(res, (short)(a | b)) < 0;
        if (overflow) {
           return (short)(-1);
        } else {
           return res;
        }
    }


    /**
     * Saturating unsigned subtraction of two {@code short} values,
     * which returns a zero in underflowing scenario.
     *
     * @param a the first operand
     * @param b the second operand
     * @return the unsigned difference between {@code a} and {@code b} iff within unsigned value range else delimiting zero value.
     * @see java.util.function.BinaryOperator
     * @since 24
     */
    static short subSaturatingUnsigned(short a, short b) {
        if (Short.compareUnsigned(b, a) < 0) {
            return (short)(a - b);
        } else {
            return 0;
        }
    }

    /**
     * Based on the unsigned comparison returns the greater of two {@code byte} values.
     *
     * @param a the first operand
     * @param b the second operand
     * @return the greater of {@code a} and {@code b}
     * @see java.util.function.BinaryOperator
     * @since 24
     */
    static byte maxUnsigned(byte a, byte b) {
        return Byte.compareUnsigned(a, b) > 0 ? a : b;
    }

    /**
     * Based on the unsigned comparison returns the smaller of two {@code byte} values.
     *
     * @param a the first operand
     * @param b the second operand
     * @return the smaller of {@code a} and {@code b}
     * @see java.util.function.BinaryOperator
     * @since 24
     */
    static byte minUnsigned(byte a, byte b) {
        return Byte.compareUnsigned(a, b) < 0 ? a : b;
    }

    /**
     * Saturating addition of two {@code byte} values,
     * which returns a {@code Byte.MIN_VALUE} in underflowing or
     * {@code Byte.MAX_VALUE} in overflowing scenario.
     *
     * @param a the first operand
     * @param b the second operand
     * @return the sum of {@code a} and {@code b} iff within {@code byte} value range else delimiting {@code Byte.MIN_VALUE/MAX_VALUE} value.
     * @see java.util.function.BinaryOperator
     * @since 24
     */
    static byte addSaturating(byte a, byte b) {
        int res = a + b;
        if (res > Byte.MAX_VALUE) {
            return Byte.MAX_VALUE;
        } else if (res < Byte.MIN_VALUE) {
            return Byte.MIN_VALUE;
        } else {
           return (byte)res;
        }
    }

    /**
     * Saturating subtraction of two {@code byte} values,
     * which returns a {@code Byte.MIN_VALUE} in underflowing or
     * {@code Byte.MAX_VALUE} in overflowing scenario.
     *
     * @param a the first operand
     * @param b the second operand
     * @return the difference between {@code a} and {@code b} iff within {@code byte} value range else delimiting {@code Byte.MIN_VALUE/MAX_VALUE} value.
     * @see java.util.function.BinaryOperator
     * @since 24
     */
    static byte subSaturating(byte a, byte b) {
        int res = a - b;
        if (res > Byte.MAX_VALUE) {
            return Byte.MAX_VALUE;
        } else if (res < Byte.MIN_VALUE) {
            return Byte.MIN_VALUE;
        } else {
            return (byte)res;
        }
    }

    /**
     * Saturating unsigned addition of two {@code byte} values,
     * which returns an maximum unsigned byte value (0xFF) in overflowing scenario.
     *
     * @param a the first operand
     * @param b the second operand
     * @return the unsigned sum of {@code a} and {@code b} iff within unsigned value range else delimiting maximum unsigned byte value.
     * @see java.util.function.BinaryOperator
     * @since 24
     */
    static byte addSaturatingUnsigned(byte a, byte b) {
        byte res = (byte)(a + b);
        boolean overflow = Byte.compareUnsigned(res, (byte)(a | b)) < 0;
        if (overflow) {
           return (byte)(-1);
        } else {
           return res;
        }
    }

    /**
     * Saturating unsigned subtraction of two {@code byte} values,
     * which returns a zero in underflowing scenario.
     *
     * @param a the first operand
     * @param b the second operand
     * @return the unsigned difference between {@code a} and {@code b} iff within unsigned value range else delimiting zero value.
     * @see java.util.function.BinaryOperator
     * @since 24
     */
   static byte subSaturatingUnsigned(byte a, byte b) {
        if (Byte.compareUnsigned(b, a) < 0) {
            return (byte)(a - b);
        } else {
            return 0;
        }
    }
}
