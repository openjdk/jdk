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
 * The class {@code VectorMath} contains methods for performing
 * scalar numeric operations in support of vector numeric operations.
 * @since   24
 */
public final class VectorMath {

    private VectorMath() {
    }

    /**
     * Returns the smaller of two {@code long} values numerically treating
     * the values as unsigned. That is, the result is the operand closer
     * to the value of the expression {@code 0L}. If the operands have the
     * same value, the result is that same value.
     *
     * @param a the first operand.
     * @param b the second operand.
     * @return the smaller of {@code a} and {@code b}.
     * @see VectorOperators#UMIN
     */
    public static long minUnsigned(long a, long b) {
        return Long.compareUnsigned(a, b) < 0 ? a : b;
    }

    /**
     * Returns the greater of two {@code long} values numerically treating
     * the values as unsigned. That is, the result is the operand closer
     * to the value of the expression {@code 0xFFFFFFFF_FFFFFFFFL} numerically
     * treating it as unsigned. If the operands have the same value,
     * the result is that same value.
     *
     * @param a the first operand.
     * @param b the second operand.
     * @return the larger of {@code a} and {@code b}.
     * @see VectorOperators#UMAX
     */
    public static long maxUnsigned(long a, long b) {
        return Long.compareUnsigned(a, b) > 0 ? a : b;
    }

    /**
     * Adds two {@code long} values using saturation
     * arithemetic. The lower and upper (inclusive) bounds are
     * {@code Long.MIN_VALUE} and {@code Long.MAX_VALUE}, respectively.
     * <p>
     * If the result of the addition would otherwise overflow from
     * a positive value to a negative value then the result is clamped
     * to the upper bound {@code Long.MAX_VALUE}.
     * If the result of the addition would otherwise underflow from
     * a negative value to a positive value then the result is clamped
     * to lower bound {@code Long.MIN_VALUE}.
     *
     * @param a the first operand.
     * @param b the second operand.
     * @return the saturating addition of the operands.
     * @see VectorOperators#SADD
     */
    public static long addSaturating(long a, long b) {
        long res = a + b;
        // HD 2-12 Overflow iff both arguments have the opposite sign of the result
        if (((a ^ res) & (b ^ res)) < 0) {
            return res < 0 ? Long.MAX_VALUE : Long.MIN_VALUE;
        } else {
            return res;
        }
    }

    /**
     * Subtracts two {@code long} values using saturation
     * arithemetic. The lower and upper (inclusive) bounds are
     * {@code Long.MIN_VALUE} and {@code Long.MAX_VALUE}, respectively.
     * <p>
     * If the result of the subtraction would otherwise overflow from
     * a positive value to a negative value then the result is clamped
     * to the upper bound {@code Long.MAX_VALUE}.
     * If the result of the subtraction would otherwise underflow from
     * a negative value to a positive value then the result is clamped
     * to lower bound {@code Long.MIN_VALUE}.
     *
     * @param a the first operand.
     * @param b the second operand.
     * @return the saturating difference of the operands.
     * @see VectorOperators#SSUB
     */
    public static long subSaturating(long a, long b) {
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
     * Adds two {@code long} values using saturation
     * arithemetic and numerically treating the values
     * as unsigned. The lower and upper (inclusive) bounds
     * are {@code 0L} and {@code 0xFFFFFFFF_FFFFFFFFL}, respectively,
     * numerically treating them as unsigned.
     * <p>
     * If the result of the unsigned addition would otherwise overflow
     * from the greater of the two operands to a lesser value then the
     * result is clamped to the upper bound {@code 0xFFFFFFFF_FFFFFFFFL}.
     *
     * @param a the first operand.
     * @param b the second operand.
     * @return the saturating addition of the operands.
     * @see VectorOperators#SUADD
     */
    public static long addSaturatingUnsigned(long a, long b) {
        long res = a + b;
        boolean overflow = Long.compareUnsigned(res, (a | b)) < 0;
        if (overflow) {
           return -1L;
        } else {
           return res;
        }
    }

    /**
     * Subtracts two {@code long} values using saturation
     * arithemetic and numerically treating the values
     * as unsigned. The lower and upper (inclusive) bounds
     * are {@code 0L} and {@code 0xFFFFFFFF_FFFFFFFFL}, respectively,
     * numerically treating them as unsigned.
     * <p>
     * If the result of the unsigned subtraction would otherwise underflow
     * from the lesser of the two operands to a greater value then the
     * result is clamped to the lower bound {@code 0L}.
     *
     * @param a the first operand.
     * @param b the second operand.
     * @return the saturating difference of the operands.
     * @see VectorOperators#SUSUB
     */
    public static long subSaturatingUnsigned(long a, long b) {
        if (Long.compareUnsigned(b, a) < 0) {
           return a - b;
        } else {
           return 0;
        }
    }


    /**
     * Returns the smaller of two {@code int} values numerically treating
     * the values as unsigned. That is, the result is the operand closer
     * to the value of the expression {@code 0}. If the operands have the
     * same value, the result is that same value.
     *
     * @param a the first operand.
     * @param b the second operand.
     * @return the smaller of {@code a} and {@code b}.
     * @see VectorOperators#UMIN
     */
    public static int minUnsigned(int a, int b) {
        return Integer.compareUnsigned(a, b) < 0 ? a : b;
    }

    /**
     * Returns the greater of two {@code int} values numerically treating
     * the values as unsigned. That is, the result is the operand closer
     * to the value of the expression {@code 0xFFFFFFFF} numerically
     * treating it as unsigned. If the operands have the same value,
     * the result is that same value.
     *
     * @param a the first operand.
     * @param b the second operand.
     * @return the larger of {@code a} and {@code b}.
     * @see VectorOperators#UMAX
     */
    public static int maxUnsigned(int a, int b) {
        return Integer.compareUnsigned(a, b) > 0 ? a : b;
    }

    /**
     * Adds two {@code int} values using saturation
     * arithemetic. The lower and upper (inclusive) bounds are
     * {@code Integer.MIN_VALUE} and {@code Integer.MAX_VALUE}, respectively.
     * <p>
     * If the result of the addition would otherwise overflow from
     * a positive value to a negative value then the result is clamped
     * to the upper bound {@code Integer.MAX_VALUE}.
     * If the result of the addition would otherwise underflow from
     * a negative value to a positive value then the result is clamped
     * to lower bound {@code Integer.MIN_VALUE}.
     *
     * @param a the first operand.
     * @param b the second operand.
     * @return the saturating addition of the operands.
     * @see VectorOperators#SADD
     */
    public static int addSaturating(int a, int b) {
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
     * Subtracts two {@code int} values using saturation
     * arithemetic. The lower and upper (inclusive) bounds are
     * {@code Integer.MIN_VALUE} and {@code Integer.MAX_VALUE}, respectively.
     * <p>
     * If the result of the subtraction would otherwise overflow from
     * a positive value to a negative value then the result is clamped
     * to the upper bound {@code Integer.MAX_VALUE}.
     * If the result of the subtraction would otherwise underflow from
     * a negative value to a positive value then the result is clamped
     * to lower bound {@code Integer.MIN_VALUE}.
     *
     * @param a the first operand.
     * @param b the second operand.
     * @return the saturating difference of the operands.
     * @see VectorOperators#SSUB
     */
    public static int subSaturating(int a, int b) {
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
     * Adds two {@code int} values using saturation
     * arithemetic and numerically treating the values
     * as unsigned. The lower and upper (inclusive) bounds
     * are {@code 0} and {@code 0xFFFFFFFF}, respectively,
     * numerically treating them as unsigned.
     * <p>
     * If the result of the unsigned addition would otherwise overflow
     * from the greater of the two operands to a lesser value then the
     * result is clamped to the upper bound {@code 0xFFFFFFFF}.
     *
     * @param a the first operand.
     * @param b the second operand.
     * @return the saturating addition of the operands.
     * @see VectorOperators#SUADD
     */
    public static int addSaturatingUnsigned(int a, int b) {
        int res = a + b;
        boolean overflow = Integer.compareUnsigned(res, (a | b)) < 0;
        if (overflow)  {
            return -1;
        } else {
            return res;
        }
    }

    /**
     * Subtracts two {@code int} values using saturation
     * arithemetic and numerically treating the values
     * as unsigned. The lower and upper (inclusive) bounds
     * are {@code 0} and {@code -0xFFFFFFFF}, respectively,
     * numerically treating them as unsigned.
     * <p>
     * If the result of the unsigned subtraction would otherwise underflow
     * from the lesser of the two operands to a greater value then the
     * result is clamped to the lower bound {@code 0}.
     *
     * @param a the first operand.
     * @param b the second operand.
     * @return the saturating difference of the operands.
     * @see VectorOperators#SUSUB
     */
    public static int subSaturatingUnsigned(int a, int b) {
        if (Integer.compareUnsigned(b, a) < 0) {
            return a - b;
        } else {
            return 0;
        }
    }


    /**
     * Returns the smaller of two {@code short} values numerically treating
     * the values as unsigned. That is, the result is the operand closer
     * to the value of the expression {@code 0}. If the operands have the
     * same value, the result is that same value.
     *
     * @param a the first operand.
     * @param b the second operand.
     * @return the smaller of {@code a} and {@code b}.
     * @see VectorOperators#UMIN
     */
    public static short minUnsigned(short a, short b) {
        return Short.compareUnsigned(a, b) < 0 ? a : b;
    }

    /**
     * Returns the greater of two {@code short} values numerically treating
     * the values as unsigned. That is, the result is the operand closer
     * to the value of the expression {@code 0xFFFF} numerically
     * treating it as unsigned. If the operands have the same value,
     * the result is that same value.
     *
     * @param a the first operand.
     * @param b the second operand.
     * @return the larger of {@code a} and {@code b}.
     * @see VectorOperators#UMAX
     */
    public static short maxUnsigned(short a, short b) {
        return Short.compareUnsigned(a, b) > 0 ? a : b;
    }

    /**
     * Adds two {@code short} values using saturation
     * arithemetic. The lower and upper (inclusive) bounds are
     * {@code Short.MIN_VALUE} and {@code Short.MAX_VALUE}, respectively.
     * <p>
     * If the result of the addition would otherwise overflow from
     * a positive value to a negative value then the result is clamped
     * to the upper bound {@code Short.MAX_VALUE}.
     * If the result of the addition would otherwise underflow from
     * a negative value to a positive value then the result is clamped
     * to lower bound {@code Short.MIN_VALUE}.
     *
     * @param a the first operand.
     * @param b the second operand.
     * @return the saturating addition of the operands.
     * @see VectorOperators#SADD
     */
    public static short addSaturating(short a, short b) {
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
     * Subtracts two {@code short} values using saturation
     * arithemetic. The lower and upper (inclusive) bounds are
     * {@code Short.MIN_VALUE} and {@code Short.MAX_VALUE}, respectively.
     * <p>
     * If the result of the subtraction would otherwise overflow from
     * a positive value to a negative value then the result is clamped
     * to the upper bound {@code Short.MAX_VALUE}.
     * If the result of the subtraction would otherwise underflow from
     * a negative value to a positive value then the result is clamped
     * to lower bound {@code Short.MIN_VALUE}.
     *
     * @param a the first operand.
     * @param b the second operand.
     * @return the saturating difference of the operands.
     * @see VectorOperators#SSUB
     */
    public static short subSaturating(short a, short b) {
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
     * Adds two {@code short} values using saturation
     * arithemetic and numerically treating the values
     * as unsigned. The lower and upper (inclusive) bounds
     * are {@code 0} and {@code 0xFFFF}, respectively,
     * numerically treating them as unsigned.
     * <p>
     * If the result of the unsigned addition would otherwise overflow
     * from the greater of the two operands to a lesser value then the
     * result is clamped to the upper bound {@code 0xFFFF}.
     *
     * @param a the first operand.
     * @param b the second operand.
     * @return the saturating addition of the operands.
     * @see VectorOperators#SUADD
     */
    public static short addSaturatingUnsigned(short a, short b) {
        short res = (short)(a + b);
        boolean overflow = Short.compareUnsigned(res, (short)(a | b)) < 0;
        if (overflow) {
           return (short)(-1);
        } else {
           return res;
        }
    }

    /**
     * Subtracts two {@code short} values using saturation
     * arithemetic and numerically treating the values
     * as unsigned. The lower and upper (inclusive) bounds
     * are {@code 0} and {@code 0xFFFF}, respectively,
     * numerically treating them as unsigned.
     * <p>
     * If the result of the unsigned subtraction would otherwise underflow
     * from the lesser of the two operands to a greater value then the
     * result is clamped to the lower bound {@code 0}.
     *
     * @param a the first operand.
     * @param b the second operand.
     * @return the saturating difference of the operands.
     * @see VectorOperators#SUSUB
     */
    public static short subSaturatingUnsigned(short a, short b) {
        if (Short.compareUnsigned(b, a) < 0) {
            return (short)(a - b);
        } else {
            return 0;
        }
    }


    /**
     * Returns the smaller of two {@code byte} values numerically treating
     * the values as unsigned. That is, the result is the operand closer
     * to the value of the expression {@code 0}. If the operands have the
     * same value, the result is that same value.
     *
     * @param a the first operand.
     * @param b the second operand.
     * @return the smaller of {@code a} and {@code b}.
     * @see VectorOperators#UMIN
     */
    public static byte minUnsigned(byte a, byte b) {
        return Byte.compareUnsigned(a, b) < 0 ? a : b;
    }

    /**
     * Returns the greater of two {@code byte} values numerically treating
     * the values as unsigned. That is, the result is the operand closer
     * to the value of the expression {@code 0xFF} numerically
     * treating it as unsigned. If the operands have the same value,
     * the result is that same value.
     *
     * @param a the first operand.
     * @param b the second operand.
     * @return the larger of {@code a} and {@code b}.
     * @see VectorOperators#UMAX
     */
    public static byte maxUnsigned(byte a, byte b) {
        return Byte.compareUnsigned(a, b) > 0 ? a : b;
    }

    /**
     * Adds two {@code byte} values using saturation
     * arithemetic. The lower and upper (inclusive) bounds are
     * {@code Byte.MIN_VALUE} and {@code Byte.MAX_VALUE}, respectively.
     * <p>
     * If the result of the addition would otherwise overflow from
     * a positive value to a negative value then the result is clamped
     * to the upper bound {@code Byte.MAX_VALUE}.
     * If the result of the addition would otherwise underflow from
     * a negative value to a positive value then the result is clamped
     * to lower bound {@code Byte.MIN_VALUE}.
     *
     * @param a the first operand.
     * @param b the second operand.
     * @return the saturating addition of the operands.
     * @see VectorOperators#SADD
     */
    public static byte addSaturating(byte a, byte b) {
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
     * Subtracts two {@code byte} values using saturation
     * arithemetic. The lower and upper (inclusive) bounds are
     * {@code Byte.MIN_VALUE} and {@code Byte.MAX_VALUE}, respectively.
     * <p>
     * If the result of the subtraction would otherwise overflow from
     * a positive value to a negative value then the result is clamped
     * to the upper bound {@code Byte.MAX_VALUE}.
     * If the result of the subtraction would otherwise underflow from
     * a negative value to a positive value then the result is clamped
     * to lower bound {@code Byte.MIN_VALUE}.
     *
     * @param a the first operand.
     * @param b the second operand.
     * @return the saturating difference of the operands.
     * @see VectorOperators#SSUB
     */
    public static byte subSaturating(byte a, byte b) {
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
     * Adds two {@code byte} values using saturation
     * arithemetic and numerically treating the values
     * as unsigned. The lower and upper (inclusive) bounds
     * are {@code 0} and {@code 0xFF}, respectively,
     * numerically treating them as unsigned.
     * <p>
     * If the result of the unsigned addition would otherwise overflow
     * from the greater of the two operands to a lesser value then the
     * result is clamped to the upper bound {@code 0xFF}.
     *
     * @param a the first operand.
     * @param b the second operand.
     * @return the saturating addition of the operands.
     * @see VectorOperators#SUADD
     */
    public static byte addSaturatingUnsigned(byte a, byte b) {
        byte res = (byte)(a + b);
        boolean overflow = Byte.compareUnsigned(res, (byte)(a | b)) < 0;
        if (overflow) {
           return (byte)(-1);
        } else {
           return res;
        }
    }

    /**
     * Subtracts two {@code byte} values using saturation
     * arithemetic and numerically treating the values
     * as unsigned. The lower and upper (inclusive) bounds
     * are {@code 0} and {@code 0xFF}, respectively,
     * numerically treating them as unsigned.
     * <p>
     * If the result of the unsigned subtraction would otherwise underflow
     * from the lesser of the two operands to a greater value then the
     * result is clamped to the lower bound {@code 0}.
     *
     * @param a the first operand.
     * @param b the second operand.
     * @return the saturating difference of the operands.
     * @see VectorOperators#SUSUB
     */
   public static byte subSaturatingUnsigned(byte a, byte b) {
        if (Byte.compareUnsigned(b, a) < 0) {
            return (byte)(a - b);
        } else {
            return 0;
        }
    }
}
