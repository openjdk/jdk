/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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
package java.lang.runtime;

/**
 * A testing conversion of a value is exact if it yields a result without loss
 * of information or throwing an exception. Otherwise, it is inexact. Some
 * conversions are always exact regardless of the value. These conversions are
 * said to be unconditionally exact.
 * <p>
 * For example, a conversion from {@code int} to {@code byte} for the value 10
 * is exact because the result, 10, is the same as the original value. In
 * contrast, if the {@code int} variable {@code i} stores the value 1000 then a
 * narrowing primitive conversion to {@code byte} will yield the result -24.
 * Loss of information has occurred: both the magnitude and the sign of the
 * result are different than those of the original value. As such, a conversion
 * from {@code int} to {@code byte} for the value 1000 is inexact. Finally a
 * widening primitive conversion from {@code byte} to {@code int} is
 * unconditionally exact because it will always succeed with no loss of
 * information about the magnitude of the numeric value.
 * <p>
 * The methods in this class provide the run-time support for the exactness
 * checks of testing conversions from a primitive type to primitive type. These
 * methods may be used, for example, by Java compiler implementations to
 * implement checks for {@code instanceof} and pattern matching runtime
 * implementations. Unconditionally exact testing conversions do not require a
 * corresponding action at run time and, for this reason, methods corresponding
 * to these exactness checks are omitted here.
 * <p>
 * The run time conversion checks examine whether loss of information would
 * occur if a testing conversion would be to be applied. In those cases where a
 * floating-point primitive type is involved, and the value of the testing
 * conversion is either signed zero, signed infinity or {@code NaN}, these
 * methods comply with the following:
 *
 * <ul>
 * <li>Converting a floating-point negative zero to an integer type is considered
 *   inexact.</li>
 * <li>Converting a floating-point {@code NaN} or infinity to an integer type is
 *   considered inexact.</li>
 * <li>Converting a floating-point {@code NaN} or infinity or signed zero to another
 *   floating-point type is considered exact.</li>
 * </ul>
 *
 * @jls primitive-types-in-patterns-instanceof-switch-5.7.1 Exact Testing Conversions
 * @jls primitive-types-in-patterns-instanceof-switch-5.7.2 Unconditionally Exact Testing Conversions
 * @jls primitive-types-in-patterns-instanceof-switch-15.20.2 The {@code instanceof} Operator
 *
 * @implNote Some exactness checks describe a test which can be redirected
 * safely through one of the existing methods. Those are omitted too (i.e.,
 * {@code byte} to {@code char} can be redirected  to
 * {@link ExactConversionsSupport#isIntToCharExact(int)}, {@code short} to
 * {@code byte} can be redirected to
 * {@link ExactConversionsSupport#isIntToByteExact(int)} and similarly for
 * {@code short} to {@code char}, {@code char} to {@code byte} and {@code char}
 * to {@code short} to the corresponding methods that take an {@code int}).
 *
 * @since 23
 */
public final class ExactConversionsSupport {

    private ExactConversionsSupport() { }

    /**
     * Exactness method from int to byte
     * @param n value
     * @return true if and only if the passed value can be converted exactly to the target type
     */
    public static boolean isIntToByteExact(int n)      {return n == (int)(byte)n;}

    /**
     * Exactness method from int to short
     * @param n value
     * @return true if and only if the passed value can be converted exactly to the target type
     */
    public static boolean isIntToShortExact(int n)     {return n == (int)(short)n;}

    /**
     * Exactness method from int to char
     * @param n value
     * @return true if and only if the passed value can be converted exactly to the target type
     */
    public static boolean isIntToCharExact(int n)      {return n == (int)(char)n;}

    /**
     * Exactness method from int to float
     * @param n value
     * @return true if and only if the passed value can be converted exactly to the target type
     *
     * @implSpec relies on the notion of representation equivalence defined in the
     * specification of the {@linkplain Double} class.
     */
    public static boolean isIntToFloatExact(int n) {
        return n == (int)(float)n && n != Integer.MAX_VALUE;
    }
    /**
     * Exactness method from long to byte
     * @param n value
     * @return true if and only if the passed value can be converted exactly to the target type
     */
    public static boolean isLongToByteExact(long n)    {return n == (long)(byte)n;}

    /**
     * Exactness method from long to short
     * @param n value
     * @return true if and only if the passed value can be converted exactly to the target type
     */
    public static boolean isLongToShortExact(long n)   {return n == (long)(short)n;}

    /**
     * Exactness method from long to char
     * @param n value
     * @return true if and only if the passed value can be converted exactly to the target type
     */
    public static boolean isLongToCharExact(long n)    {return n == (long)(char)n;}

    /**
     * Exactness method from long to int
     * @param n value
     * @return true if and only if the passed value can be converted exactly to the target type
     */
    public static boolean isLongToIntExact(long n)     {return n == (long)(int)n;}

    /**
     * Exactness method from long to float
     * @param n value
     * @return true if and only if the passed value can be converted exactly to the target type
     * @implSpec relies on the notion of representation equivalence defined in the
     * specification of the {@linkplain Double} class.
     */
    public static boolean isLongToFloatExact(long n) {
        return n == (long)(float)n && n != Long.MAX_VALUE;
    }

    /**
     * Exactness method from long to double
     * @param n value
     * @return true if and only if the passed value can be converted exactly to the target type
     * @implSpec relies on the notion of representation equivalence defined in the
     * specification of the {@linkplain Double} class.
     */
    public static boolean isLongToDoubleExact(long n) {
        return n == (long)(double)n && n != Long.MAX_VALUE;
    }

    /**
     * Exactness method from float to byte
     * @param n value
     * @return true if and only if the passed value can be converted exactly to the target type
     * @implSpec relies on the notion of representation equivalence defined in the
     * specification of the {@linkplain Double} class.
     */
    public static boolean isFloatToByteExact(float n)  {
        return n == (float)(byte)n && !isNegativeZero(n);
    }

    /**
     * Exactness method from float to short
     * @param n value
     * @return true if and only if the passed value can be converted exactly to the target type
     * @implSpec relies on the notion of representation equivalence defined in the
     * specification of the {@linkplain Double} class.
     */
    public static boolean isFloatToShortExact(float n) {
        return n == (float)(short)n && !isNegativeZero(n);
    }

    /**
     * Exactness method from float to char
     * @param n value
     * @return true if and only if the passed value can be converted exactly to the target type
     * @implSpec relies on the notion of representation equivalence defined in the
     * specification of the {@linkplain Double} class.
     */
    public static boolean isFloatToCharExact(float n)  {
        return n == (float)(char)n && !isNegativeZero(n);
    }

    /**
     * Exactness method from float to int
     * @param n value
     * @return true if and only if the passed value can be converted exactly to the target type
     * @implSpec relies on the notion of representation equivalence defined in the
     * specification of the {@linkplain Double} class.
     */
    public static boolean isFloatToIntExact(float n) {
        return n == (float)(int)n && n != 0x1p31f && !isNegativeZero(n);
    }

    /**
     * Exactness method from float to long
     * @param n value
     * @return true if and only if the passed value can be converted exactly to the target type
     * @implSpec relies on the notion of representation equivalence defined in the
     * specification of the {@linkplain Double} class.
     */
    public static boolean isFloatToLongExact(float n) {
        return n == (float)(long)n && n != 0x1p63f && !isNegativeZero(n);
    }

    /**
     * Exactness method from double to byte
     * @param n value
     * @return true if and only if the passed value can be converted exactly to the target type
     * @implSpec relies on the notion of representation equivalence defined in the
     * specification of the {@linkplain Double} class.
     */
    public static boolean isDoubleToByteExact(double n) {
        return n == (double)(byte)n && !isNegativeZero(n);
    }

    /**
     * Exactness method from double to short
     * @param n value
     * @return true if and only if the passed value can be converted exactly to the target type
     * @implSpec relies on the notion of representation equivalence defined in the
     * specification of the {@linkplain Double} class.
     */
    public static boolean isDoubleToShortExact(double n){
        return n == (double)(short)n && !isNegativeZero(n);
    }

    /**
     * Exactness method from double to char
     * @param n value
     * @return true if and only if the passed value can be converted exactly to the target type
     * @implSpec relies on the notion of representation equivalence defined in the
     * specification of the {@linkplain Double} class.
     */
    public static boolean isDoubleToCharExact(double n) {
        return n == (double)(char)n && !isNegativeZero(n);
    }

    /**
     * Exactness method from double to int
     * @param n value
     * @return true if and only if the passed value can be converted exactly to the target type
     * @implSpec relies on the notion of representation equivalence defined in the
     * specification of the {@linkplain Double} class.
     */
    public static boolean isDoubleToIntExact(double n)  {
        return n == (double)(int)n && !isNegativeZero(n);
    }

    /**
     * Exactness method from double to long
     * @param n value
     * @return true if and only if the passed value can be converted exactly to the target type
     * @implSpec relies on the notion of representation equivalence defined in the
     * specification of the {@linkplain Double} class.
     */
    public static boolean isDoubleToLongExact(double n) {
        return n == (double)(long)n && n != 0x1p63 && !isNegativeZero(n);
    }

    /**
     * Exactness method from double to float
     * @param n value
     * @return true if and only if the passed value can be converted exactly to the target type
     * @implSpec relies on the notion of representation equivalence defined in the
     * specification of the {@linkplain Double} class.
     */
    public static boolean isDoubleToFloatExact(double n) {
        return n == (double)(float)n || n != n;
    }

    private static boolean isNegativeZero(float n) {
        return Float.floatToRawIntBits(n) == Integer.MIN_VALUE;
    }

    private static boolean isNegativeZero(double n) {
        return Double.doubleToRawLongBits(n) == Long.MIN_VALUE;
    }
}
