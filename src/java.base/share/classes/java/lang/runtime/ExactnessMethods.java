/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * Exactness methods to test whether a conversion between types would be
 * exact when not enough static information is present. These methods may
 * be used, for example, by Java compiler implementations to implement checks
 * for instanceof and pattern matching runtime implementations.
 *
 * @since 22
 */
public class ExactnessMethods {

    private ExactnessMethods() { }

     /** Exactness method from int to byte
     *
     * @param n value
     * @return  true if the passed value can be converted exactly to the target type
     *
     * */
    public static boolean int_byte(int n)      {return n == (int)(byte)n;}

    /** Exactness method from int to short
     *
     * @param n value
     * @return  true if the passed value can be converted exactly to the target type
     *
     * */
    public static boolean int_short(int n)     {return n == (int)(short)n;}

    /** Exactness method from int to char
     *
     * @param n value
     * @return  true if the passed value can be converted exactly to the target type
     *
     * */
    public static boolean int_char(int n)      {return n == (int)(char)n;}

    /** Exactness method from int to float
     *
     * @param n value
     * @return  true if the passed value can be converted exactly to the target type
     *
     * */
    public static boolean int_float(int n) { return n == (int)(float)n && n != Integer.MAX_VALUE; }

    /** Exactness method from long to byte
     *
     * @param n value
     * @return  true if the passed value can be converted exactly to the target type
     *
     * */
    public static boolean long_byte(long n)    {return n == (long)(byte)n;}

    /** Exactness method from long to short
     *
     * @param n value
     * @return  true if the passed value can be converted exactly to the target type
     *
     * */
    public static boolean long_short(long n)   {return n == (long)(short)n;}

    /** Exactness method from long to char
     *
     * @param n value
     * @return  true if the passed value can be converted exactly to the target type
     *
     * */
    public static boolean long_char(long n)    {return n == (long)(char)n;}

    /** Exactness method from long to int
     *
     * @param n value
     * @return  true if the passed value can be converted exactly to the target type
     *
     * */
    public static boolean long_int(long n)     {return n == (long)(int)n;}

    /** Exactness method from long to float
     *
     * @param n value
     * @return  true if the passed value can be converted exactly to the target type
     *
     * */
    public static boolean long_float(long n) {
        return n == (long)(float)n && n != Long.MAX_VALUE;
    }

    /** Exactness method from long to double
     *
     * @param n value
     * @return  true if the passed value can be converted exactly to the target type
     *
     * */
    public static boolean long_double(long n) {
        return n == (long)(double)n && n != Long.MAX_VALUE;
    }

    /** Exactness method from float to byte
     *
     * @param n value
     * @return  true if the passed value can be converted exactly to the target type
     *
     * */
    public static boolean float_byte(float n)  {
        return n == (float)(byte)n && !isNegativeZero(n);
    }

    /** Exactness method from float to short
     *
     * @param n value
     * @return  true if the passed value can be converted exactly to the target type
     *
     * */
    public static boolean float_short(float n) {
        return n == (float)(short)n && !isNegativeZero(n);
    }

    /** Exactness method from float to char
     *
     * @param n value
     * @return  true if the passed value can be converted exactly to the target type
     *
     * */
    public static boolean float_char(float n)  {
        return n == (float)(char)n && !isNegativeZero(n);
    }

    /** Exactness method from float to int
     *
     * @param n value
     * @return  true if the passed value can be converted exactly to the target type
     *
     * */
    public static boolean float_int(float n) {
        return n == (float)(int)n && n != 0x1p31f && !isNegativeZero(n);
    }

    /** Exactness method from float to long
     *
     * @param n value
     * @return  true if the passed value can be converted exactly to the target type
     *
     * */
    public static boolean float_long(float n) {
        return n == (float)(long)n && n != 0x1p63f && !isNegativeZero(n);
    }

    /** Exactness method from double to byte
     *
     * @param n value
     * @return  true if the passed value can be converted exactly to the target type
     *
     * */
    public static boolean double_byte(double n) {
        return n == (double)(byte)n && !isNegativeZero(n);
    }

    /** Exactness method from double to short
     *
     * @param n value
     * @return  true if the passed value can be converted exactly to the target type
     *
     * */
    public static boolean double_short(double n){
        return n == (double)(short)n && !isNegativeZero(n);
    }

    /** Exactness method from double to char
     *
     * @param n value
     * @return  true if the passed value can be converted exactly to the target type
     *
     * */
    public static boolean double_char(double n) {
        return n == (double)(char)n && !isNegativeZero(n);
    }

    /** Exactness method from double to int
     *
     * @param n value
     * @return  true if the passed value can be converted exactly to the target type
     *
     * */
    public static boolean double_int(double n)  {
        return n == (double)(int)n && !isNegativeZero(n);
    }

    /** Exactness method from double to long
     *
     * @param n value
     * @return  true if the passed value can be converted exactly to the target type
     *
     * */
    public static boolean double_long(double n) {
        return n == (double)(long)n && n != 0x1p63 && !isNegativeZero(n);
    }

    /** Exactness method from double to float
     *
     * @param n value
     * @return  true if the passed value can be converted exactly to the target type
     *
     * */
    public static boolean double_float(double n) {
        return n == (double)(float)n || n != n;
    }

    private static boolean isNegativeZero(float n) {
        return Float.floatToRawIntBits(n) == Integer.MIN_VALUE;
    }

    private static boolean isNegativeZero(double n) {
        return Double.doubleToRawLongBits(n) == Long.MIN_VALUE;
    }
}
