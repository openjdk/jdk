/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.objects;

import jdk.nashorn.internal.objects.annotations.Attribute;
import jdk.nashorn.internal.objects.annotations.Function;
import jdk.nashorn.internal.objects.annotations.Property;
import jdk.nashorn.internal.objects.annotations.ScriptClass;
import jdk.nashorn.internal.objects.annotations.SpecializedFunction;
import jdk.nashorn.internal.objects.annotations.Where;
import jdk.nashorn.internal.runtime.JSType;
import jdk.nashorn.internal.runtime.PropertyMap;
import jdk.nashorn.internal.runtime.ScriptObject;

/**
 * ECMA 15.8 The Math Object
 *
 */
@ScriptClass("Math")
public final class NativeMath extends ScriptObject {

    // initialized by nasgen
    private static PropertyMap $nasgenmap$;

    private NativeMath() {
        // don't create me!
        throw new UnsupportedOperationException();
    }

    /** ECMA 15.8.1.1 - E, always a double constant. Not writable or configurable */
    @Property(attributes = Attribute.NON_ENUMERABLE_CONSTANT, where = Where.CONSTRUCTOR)
    public static final double E = Math.E;

    /** ECMA 15.8.1.2 - LN10, always a double constant. Not writable or configurable */
    @Property(attributes = Attribute.NON_ENUMERABLE_CONSTANT, where = Where.CONSTRUCTOR)
    public static final double LN10 = 2.302585092994046;

    /** ECMA 15.8.1.3 - LN2, always a double constant. Not writable or configurable */
    @Property(attributes = Attribute.NON_ENUMERABLE_CONSTANT, where = Where.CONSTRUCTOR)
    public static final double LN2 = 0.6931471805599453;

    /** ECMA 15.8.1.4 - LOG2E, always a double constant. Not writable or configurable */
    @Property(attributes = Attribute.NON_ENUMERABLE_CONSTANT, where = Where.CONSTRUCTOR)
    public static final double LOG2E = 1.4426950408889634;

    /** ECMA 15.8.1.5 - LOG10E, always a double constant. Not writable or configurable */
    @Property(attributes = Attribute.NON_ENUMERABLE_CONSTANT, where = Where.CONSTRUCTOR)
    public static final double LOG10E = 0.4342944819032518;

    /** ECMA 15.8.1.6 - PI, always a double constant. Not writable or configurable */
    @Property(attributes = Attribute.NON_ENUMERABLE_CONSTANT, where = Where.CONSTRUCTOR)
    public static final double PI = Math.PI;

    /** ECMA 15.8.1.7 - SQRT1_2, always a double constant. Not writable or configurable */
    @Property(attributes = Attribute.NON_ENUMERABLE_CONSTANT, where = Where.CONSTRUCTOR)
    public static final double SQRT1_2 = 0.7071067811865476;

    /** ECMA 15.8.1.8 - SQRT2, always a double constant. Not writable or configurable */
    @Property(attributes = Attribute.NON_ENUMERABLE_CONSTANT, where = Where.CONSTRUCTOR)
    public static final double SQRT2 = 1.4142135623730951;

    /**
     * ECMA 15.8.2.1 abs(x)
     *
     * @param self  self reference
     * @param x     argument
     *
     * @return abs of value
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static Object abs(final Object self, final Object x) {
        return Math.abs(JSType.toNumber(x));
    }

    /**
     * ECMA 15.8.2.1 abs(x) - specialization for int values
     *
     * @param self  self reference
     * @param x     argument
     *
     * @return abs of argument
     */
    @SpecializedFunction
    public static int abs(final Object self, final int x) {
        return Math.abs(x);
    }

    /**
     * ECMA 15.8.2.1 abs(x) - specialization for long values
     *
     * @param self  self reference
     * @param x     argument
     *
     * @return abs of argument
     */
    @SpecializedFunction
    public static long abs(final Object self, final long x) {
        return Math.abs(x);
    }

    /**
     * ECMA 15.8.2.1 abs(x) - specialization for double values
     *
     * @param self  self reference
     * @param x     argument
     *
     * @return abs of argument
     */
    @SpecializedFunction
    public static double abs(final Object self, final double x) {
        return Math.abs(x);
    }

    /**
     * ECMA 15.8.2.2 acos(x)
     *
     * @param self  self reference
     * @param x     argument
     *
     * @return acos of argument
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static Object acos(final Object self, final Object x) {
        return Math.acos(JSType.toNumber(x));
    }

    /**
     * ECMA 15.8.2.2 acos(x) - specialization for double values
     *
     * @param self  self reference
     * @param x     argument
     *
     * @return acos of argument
     */
    @SpecializedFunction
    public static double acos(final Object self, final double x) {
        return Math.acos(x);
    }

    /**
     * ECMA 15.8.2.3 asin(x)
     *
     * @param self  self reference
     * @param x     argument
     *
     * @return asin of argument
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static Object asin(final Object self, final Object x) {
        return Math.asin(JSType.toNumber(x));
    }

    /**
     * ECMA 15.8.2.3 asin(x) - specialization for double values
     *
     * @param self  self reference
     * @param x     argument
     *
     * @return asin of argument
     */
    @SpecializedFunction
    public static double asin(final Object self, final double x) {
        return Math.asin(x);
    }

    /**
     * ECMA 15.8.2.4 atan(x)
     *
     * @param self  self reference
     * @param x     argument
     *
     * @return atan of argument
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static Object atan(final Object self, final Object x) {
        return Math.atan(JSType.toNumber(x));
    }

    /**
     * ECMA 15.8.2.4 atan(x) - specialization for double values
     *
     * @param self  self reference
     * @param x     argument
     *
     * @return atan of argument
     */
    @SpecializedFunction
    public static double atan(final Object self, final double x) {
        return Math.atan(x);
    }

    /**
     * ECMA 15.8.2.5 atan2(x,y)
     *
     * @param self  self reference
     * @param x     first argument
     * @param y     second argument
     *
     * @return atan2 of x and y
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static Object atan2(final Object self, final Object y, final Object x) {
        return Math.atan2(JSType.toNumber(y), JSType.toNumber(x));
    }

    /**
     * ECMA 15.8.2.5 atan2(x,y) - specialization for double values
     *
     * @param self  self reference
     * @param x     first argument
     * @param y     second argument
     *
     * @return atan2 of x and y
     */
    @SpecializedFunction
    public static double atan2(final Object self, final double y, final double x) {
        return Math.atan2(y,x);
    }

    /**
     * ECMA 15.8.2.6 ceil(x)
     *
     * @param self  self reference
     * @param x     argument
     *
     * @return ceil of argument
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static Object ceil(final Object self, final Object x) {
        return Math.ceil(JSType.toNumber(x));
    }

    /**
     * ECMA 15.8.2.6 ceil(x) - specialized version for ints
     *
     * @param self  self reference
     * @param x     argument
     *
     * @return ceil of argument
     */
    @SpecializedFunction
    public static int ceil(final Object self, final int x) {
        return x;
    }

    /**
     * ECMA 15.8.2.6 ceil(x) - specialized version for longs
     *
     * @param self  self reference
     * @param x     argument
     *
     * @return ceil of argument
     */
    @SpecializedFunction
    public static long ceil(final Object self, final long x) {
        return x;
    }

    /**
     * ECMA 15.8.2.6 ceil(x) - specialized version for doubles
     *
     * @param self  self reference
     * @param x     argument
     *
     * @return ceil of argument
     */
    @SpecializedFunction
    public static double ceil(final Object self, final double x) {
        return Math.ceil(x);
    }

    /**
     * ECMA 15.8.2.7 cos(x)
     *
     * @param self  self reference
     * @param x     argument
     *
     * @return cos of argument
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static Object cos(final Object self, final Object x) {
        return Math.cos(JSType.toNumber(x));
    }

    /**
     * ECMA 15.8.2.7 cos(x) - specialized version for doubles
     *
     * @param self  self reference
     * @param x     argument
     *
     * @return cos of argument
     */
    @SpecializedFunction
    public static double cos(final Object self, final double x) {
        return Math.cos(x);
    }

    /**
     * ECMA 15.8.2.8 exp(x)
     *
     * @param self  self reference
     * @param x     argument
     *
     * @return exp of argument
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static Object exp(final Object self, final Object x) {
        return Math.exp(JSType.toNumber(x));
    }

    /**
     * ECMA 15.8.2.9 floor(x)
     *
     * @param self  self reference
     * @param x     argument
     *
     * @return floor of argument
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static Object floor(final Object self, final Object x) {
        return Math.floor(JSType.toNumber(x));
    }

    /**
     * ECMA 15.8.2.9 floor(x) - specialized version for ints
     *
     * @param self  self reference
     * @param x     argument
     *
     * @return floor of argument
     */
    @SpecializedFunction
    public static int floor(final Object self, final int x) {
        return x;
    }

    /**
     * ECMA 15.8.2.9 floor(x) - specialized version for longs
     *
     * @param self  self reference
     * @param x     argument
     *
     * @return floor of argument
     */
    @SpecializedFunction
    public static long floor(final Object self, final long x) {
        return x;
    }

    /**
     * ECMA 15.8.2.9 floor(x) - specialized version for doubles
     *
     * @param self  self reference
     * @param x     argument
     *
     * @return floor of argument
     */
    @SpecializedFunction
    public static double floor(final Object self, final double x) {
        return Math.floor(x);
    }

    /**
     * ECMA 15.8.2.10 log(x)
     *
     * @param self  self reference
     * @param x     argument
     *
     * @return log of argument
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static Object log(final Object self, final Object x) {
        return Math.log(JSType.toNumber(x));
    }

    /**
     * ECMA 15.8.2.10 log(x) - specialized version for doubles
     *
     * @param self  self reference
     * @param x     argument
     *
     * @return log of argument
     */
    @SpecializedFunction
    public static double log(final Object self, final double x) {
        return Math.log(x);
    }

    /**
     * ECMA 15.8.2.11 max(x)
     *
     * @param self  self reference
     * @param args  arguments
     *
     * @return the largest of the arguments, {@link Double#NEGATIVE_INFINITY} if no args given, or identity if one arg is given
     */
    @Function(arity = 2, attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static Object max(final Object self, final Object... args) {
        switch (args.length) {
        case 0:
            return Double.NEGATIVE_INFINITY;
        case 1:
            return JSType.toNumber(args[0]);
        default:
            double res = JSType.toNumber(args[0]);
            for (int i = 1; i < args.length; i++) {
                res = Math.max(res, JSType.toNumber(args[i]));
            }
            return res;
        }
    }

    /**
     * ECMA 15.8.2.11 max(x) - specialized no args version
     *
     * @param self  self reference
     *
     * @return {@link Double#NEGATIVE_INFINITY}
     */
    @SpecializedFunction
    public static double max(final Object self) {
        return Double.NEGATIVE_INFINITY;
    }

    /**
     * ECMA 15.8.2.11 max(x) - specialized version for ints
     *
     * @param self  self reference
     * @param x     first argument
     * @param y     second argument
     *
     * @return largest value of x and y
     */
    @SpecializedFunction
    public static int max(final Object self, final int x, final int y) {
        return Math.max(x, y);
    }

    /**
     * ECMA 15.8.2.11 max(x) - specialized version for longs
     *
     * @param self  self reference
     * @param x     first argument
     * @param y     second argument
     *
     * @return largest value of x and y
     */
    @SpecializedFunction
    public static long max(final Object self, final long x, final long y) {
        return Math.max(x, y);
    }

    /**
     * ECMA 15.8.2.11 max(x) - specialized version for doubles
     *
     * @param self  self reference
     * @param x     first argument
     * @param y     second argument
     *
     * @return largest value of x and y
     */
    @SpecializedFunction
    public static double max(final Object self, final double x, final double y) {
        return Math.max(x, y);
    }

    /**
     * ECMA 15.8.2.12 min(x)
     *
     * @param self  self reference
     * @param args  arguments
     *
     * @return the smallest of the arguments, {@link Double#NEGATIVE_INFINITY} if no args given, or identity if one arg is given
     */
    @Function(arity = 2, attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static Object min(final Object self, final Object... args) {
        switch (args.length) {
        case 0:
            return Double.POSITIVE_INFINITY;
        case 1:
            return JSType.toNumber(args[0]);
        default:
            double res = JSType.toNumber(args[0]);
            for (int i = 1; i < args.length; i++) {
                res = Math.min(res, JSType.toNumber(args[i]));
            }
            return res;
        }
    }

    /**
     * ECMA 15.8.2.11 min(x) - specialized no args version
     *
     * @param self  self reference
     *
     * @return {@link Double#POSITIVE_INFINITY}
     */
    @SpecializedFunction
    public static double min(final Object self) {
        return Double.POSITIVE_INFINITY;
    }

    /**
     * ECMA 15.8.2.12 min(x) - specialized version for ints
     *
     * @param self  self reference
     * @param x     first argument
     * @param y     second argument
     *
     * @return smallest value of x and y
     */
    @SpecializedFunction
    public static int min(final Object self, final int x, final int y) {
        return Math.min(x, y);
    }

    /**
     * ECMA 15.8.2.12 min(x) - specialized version for longs
     *
     * @param self  self reference
     * @param x     first argument
     * @param y     second argument
     *
     * @return smallest value of x and y
     */
    @SpecializedFunction
    public static long min(final Object self, final long x, final long y) {
        return Math.min(x, y);
    }

    /**
     * ECMA 15.8.2.12 min(x) - specialized version for doubles
     *
     * @param self  self reference
     * @param x     first argument
     * @param y     second argument
     *
     * @return smallest value of x and y
     */
    @SpecializedFunction
    public static double min(final Object self, final double x, final double y) {
        return Math.min(x, y);
    }

    /**
     * ECMA 15.8.2.13 pow(x,y)
     *
     * @param self  self reference
     * @param x     first argument
     * @param y     second argument
     *
     * @return x raised to the power of y
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static Object pow(final Object self, final Object x, final Object y) {
        return Math.pow(JSType.toNumber(x), JSType.toNumber(y));
    }

    /**
     * ECMA 15.8.2.13 pow(x,y) - specialized version for doubles
     *
     * @param self  self reference
     * @param x     first argument
     * @param y     second argument
     *
     * @return x raised to the power of y
     */
    @SpecializedFunction
    public static double pow(final Object self, final double x, final double y) {
        return Math.pow(x, y);
    }

    /**
     * ECMA 15.8.2.14 random()
     *
     * @param self  self reference
     *
     * @return random number in the range [0..1)
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static Object random(final Object self) {
        return Math.random();
    }

    /**
     * ECMA 15.8.2.15 round(x)
     *
     * @param self  self reference
     * @param x     argument
     *
     * @return x rounded
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static Object round(final Object self, final Object x) {
        final double d = JSType.toNumber(x);
        if (Math.getExponent(d) >= 52) {
            return d;
        }
        return Math.copySign(Math.floor(d + 0.5), d);
    }

    /**
     * ECMA 15.8.2.16 sin(x)
     *
     * @param self  self reference
     * @param x     argument
     *
     * @return sin of x
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static Object sin(final Object self, final Object x) {
        return Math.sin(JSType.toNumber(x));
    }

    /**
     * ECMA 15.8.2.16 sin(x) - specialized version for doubles
     *
     * @param self  self reference
     * @param x     argument
     *
     * @return sin of x
     */
    @SpecializedFunction
    public static double sin(final Object self, final double x) {
        return Math.sin(x);
    }

    /**
     * ECMA 15.8.2.17 sqrt(x)
     *
     * @param self  self reference
     * @param x     argument
     *
     * @return sqrt of x
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static Object sqrt(final Object self, final Object x) {
        return Math.sqrt(JSType.toNumber(x));
    }

    /**
     * ECMA 15.8.2.17 sqrt(x) - specialized version for doubles
     *
     * @param self  self reference
     * @param x     argument
     *
     * @return sqrt of x
     */
    @SpecializedFunction
    public static double sqrt(final Object self, final double x) {
        return Math.sqrt(x);
    }

    /**
     * ECMA 15.8.2.18 tan(x)
     *
     * @param self  self reference
     * @param x     argument
     *
     * @return tan of x
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where=Where.CONSTRUCTOR)
    public static Object tan(final Object self, final Object x) {
        return Math.tan(JSType.toNumber(x));
    }

    /**
     * ECMA 15.8.2.18 tan(x) - specialized version for doubles
     *
     * @param self  self reference
     * @param x     argument
     *
     * @return tan of x
     */
    @SpecializedFunction
    public static double tan(final Object self, final double x) {
        return Math.tan(x);
    }
}
