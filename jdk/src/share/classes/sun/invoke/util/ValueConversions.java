/*
 * Copyright (c) 2008, 2011, Oracle and/or its affiliates. All rights reserved.
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

package sun.invoke.util;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;

public class ValueConversions {
    private static final Class<?> THIS_CLASS = ValueConversions.class;
    // Do not adjust this except for special platforms:
    private static final int MAX_ARITY;
    static {
        final Object[] values = { 255 };
        AccessController.doPrivileged(new PrivilegedAction<Void>() {
                public Void run() {
                    values[0] = Integer.getInteger(THIS_CLASS.getName()+".MAX_ARITY", 255);
                    return null;
                }
            });
        MAX_ARITY = (Integer) values[0];
    }

    private static final Lookup IMPL_LOOKUP = MethodHandles.lookup();

    private static EnumMap<Wrapper, MethodHandle>[] newWrapperCaches(int n) {
        @SuppressWarnings("unchecked")  // generic array creation
        EnumMap<Wrapper, MethodHandle>[] caches
                = (EnumMap<Wrapper, MethodHandle>[]) new EnumMap<?,?>[n];
        for (int i = 0; i < n; i++)
            caches[i] = new EnumMap<>(Wrapper.class);
        return caches;
    }

    /// Converting references to values.

    // There are several levels of this unboxing conversions:
    //   no conversions:  exactly Integer.valueOf, etc.
    //   implicit conversions sanctioned by JLS 5.1.2, etc.
    //   explicit conversions as allowed by explicitCastArguments

    static int unboxInteger(Object x, boolean cast) {
        if (x instanceof Integer)
            return ((Integer) x).intValue();
        return primitiveConversion(Wrapper.INT, x, cast).intValue();
    }

    static byte unboxByte(Object x, boolean cast) {
        if (x instanceof Byte)
            return ((Byte) x).byteValue();
        return primitiveConversion(Wrapper.BYTE, x, cast).byteValue();
    }

    static short unboxShort(Object x, boolean cast) {
        if (x instanceof Short)
            return ((Short) x).shortValue();
        return primitiveConversion(Wrapper.SHORT, x, cast).shortValue();
    }

    static boolean unboxBoolean(Object x, boolean cast) {
        if (x instanceof Boolean)
            return ((Boolean) x).booleanValue();
        return (primitiveConversion(Wrapper.BOOLEAN, x, cast).intValue() & 1) != 0;
    }

    static char unboxCharacter(Object x, boolean cast) {
        if (x instanceof Character)
            return ((Character) x).charValue();
        return (char) primitiveConversion(Wrapper.CHAR, x, cast).intValue();
    }

    static long unboxLong(Object x, boolean cast) {
        if (x instanceof Long)
            return ((Long) x).longValue();
        return primitiveConversion(Wrapper.LONG, x, cast).longValue();
    }

    static float unboxFloat(Object x, boolean cast) {
        if (x instanceof Float)
            return ((Float) x).floatValue();
        return primitiveConversion(Wrapper.FLOAT, x, cast).floatValue();
    }

    static double unboxDouble(Object x, boolean cast) {
        if (x instanceof Double)
            return ((Double) x).doubleValue();
        return primitiveConversion(Wrapper.DOUBLE, x, cast).doubleValue();
    }

    private static MethodType unboxType(Wrapper wrap) {
        return MethodType.methodType(wrap.primitiveType(), Object.class, boolean.class);
    }

    private static final EnumMap<Wrapper, MethodHandle>[]
            UNBOX_CONVERSIONS = newWrapperCaches(2);

    private static MethodHandle unbox(Wrapper wrap, boolean cast) {
        EnumMap<Wrapper, MethodHandle> cache = UNBOX_CONVERSIONS[(cast?1:0)];
        MethodHandle mh = cache.get(wrap);
        if (mh != null) {
            return mh;
        }
        // slow path
        switch (wrap) {
            case OBJECT:
                mh = IDENTITY; break;
            case VOID:
                mh = IGNORE; break;
        }
        if (mh != null) {
            cache.put(wrap, mh);
            return mh;
        }
        // look up the method
        String name = "unbox" + wrap.wrapperSimpleName();
        MethodType type = unboxType(wrap);
        try {
            mh = IMPL_LOOKUP.findStatic(THIS_CLASS, name, type);
        } catch (ReflectiveOperationException ex) {
            mh = null;
        }
        if (mh != null) {
            mh = MethodHandles.insertArguments(mh, 1, cast);
            cache.put(wrap, mh);
            return mh;
        }
        throw new IllegalArgumentException("cannot find unbox adapter for " + wrap
                + (cast ? " (cast)" : ""));
    }

    public static MethodHandle unboxCast(Wrapper type) {
        return unbox(type, true);
    }

    public static MethodHandle unbox(Class<?> type) {
        return unbox(Wrapper.forPrimitiveType(type), false);
    }

    public static MethodHandle unboxCast(Class<?> type) {
        return unbox(Wrapper.forPrimitiveType(type), true);
    }

    static private final Integer ZERO_INT = 0, ONE_INT = 1;

    /// Primitive conversions
    /**
     * Produce a Number which represents the given value {@code x}
     * according to the primitive type of the given wrapper {@code wrap}.
     * Caller must invoke intValue, byteValue, longValue (etc.) on the result
     * to retrieve the desired primitive value.
     */
    public static Number primitiveConversion(Wrapper wrap, Object x, boolean cast) {
        // Maybe merge this code with Wrapper.convert/cast.
        Number res = null;
        if (x == null) {
            if (!cast)  return null;
            return ZERO_INT;
        }
        if (x instanceof Number) {
            res = (Number) x;
        } else if (x instanceof Boolean) {
            res = ((boolean)x ? ONE_INT : ZERO_INT);
        } else if (x instanceof Character) {
            res = (int)(char)x;
        } else {
            // this will fail with the required ClassCastException:
            res = (Number) x;
        }
        Wrapper xwrap = Wrapper.findWrapperType(x.getClass());
        if (xwrap == null || !cast && !wrap.isConvertibleFrom(xwrap))
            // this will fail with the required ClassCastException:
            return (Number) wrap.wrapperType().cast(x);
        return res;
    }

    /**
     * The JVM verifier allows boolean, byte, short, or char to widen to int.
     * Support exactly this conversion, from a boxed value type Boolean,
     * Byte, Short, Character, or Integer.
     */
    public static int widenSubword(Object x) {
        if (x instanceof Integer)
            return (int) x;
        else if (x instanceof Boolean)
            return fromBoolean((boolean) x);
        else if (x instanceof Character)
            return (char) x;
        else if (x instanceof Short)
            return (short) x;
        else if (x instanceof Byte)
            return (byte) x;
        else
            // Fail with a ClassCastException.
            return (int) x;
    }

    /// Converting primitives to references

    static Integer boxInteger(int x) {
        return x;
    }

    static Byte boxByte(byte x) {
        return x;
    }

    static Short boxShort(short x) {
        return x;
    }

    static Boolean boxBoolean(boolean x) {
        return x;
    }

    static Character boxCharacter(char x) {
        return x;
    }

    static Long boxLong(long x) {
        return x;
    }

    static Float boxFloat(float x) {
        return x;
    }

    static Double boxDouble(double x) {
        return x;
    }

    private static MethodType boxType(Wrapper wrap) {
        // be exact, since return casts are hard to compose
        Class<?> boxType = wrap.wrapperType();
        return MethodType.methodType(boxType, wrap.primitiveType());
    }

    private static final EnumMap<Wrapper, MethodHandle>[]
            BOX_CONVERSIONS = newWrapperCaches(2);

    private static MethodHandle box(Wrapper wrap, boolean exact) {
        EnumMap<Wrapper, MethodHandle> cache = BOX_CONVERSIONS[(exact?1:0)];
        MethodHandle mh = cache.get(wrap);
        if (mh != null) {
            return mh;
        }
        // slow path
        switch (wrap) {
            case OBJECT:
                mh = IDENTITY; break;
            case VOID:
                mh = ZERO_OBJECT;
                break;
        }
        if (mh != null) {
            cache.put(wrap, mh);
            return mh;
        }
        // look up the method
        String name = "box" + wrap.wrapperSimpleName();
        MethodType type = boxType(wrap);
        if (exact) {
            try {
                mh = IMPL_LOOKUP.findStatic(THIS_CLASS, name, type);
            } catch (ReflectiveOperationException ex) {
                mh = null;
            }
        } else {
            mh = box(wrap, !exact).asType(type.erase());
        }
        if (mh != null) {
            cache.put(wrap, mh);
            return mh;
        }
        throw new IllegalArgumentException("cannot find box adapter for "
                + wrap + (exact ? " (exact)" : ""));
    }

    public static MethodHandle box(Class<?> type) {
        boolean exact = false;
        // e.g., boxShort(short)Short if exact,
        // e.g., boxShort(short)Object if !exact
        return box(Wrapper.forPrimitiveType(type), exact);
    }

    public static MethodHandle box(Wrapper type) {
        boolean exact = false;
        return box(type, exact);
    }

    /// Constant functions

    static void ignore(Object x) {
        // no value to return; this is an unbox of null
        return;
    }

    static void empty() {
        return;
    }

    static Object zeroObject() {
        return null;
    }

    static int zeroInteger() {
        return 0;
    }

    static long zeroLong() {
        return 0;
    }

    static float zeroFloat() {
        return 0;
    }

    static double zeroDouble() {
        return 0;
    }

    private static final EnumMap<Wrapper, MethodHandle>[]
            CONSTANT_FUNCTIONS = newWrapperCaches(2);

    public static MethodHandle zeroConstantFunction(Wrapper wrap) {
        EnumMap<Wrapper, MethodHandle> cache = CONSTANT_FUNCTIONS[0];
        MethodHandle mh = cache.get(wrap);
        if (mh != null) {
            return mh;
        }
        // slow path
        MethodType type = MethodType.methodType(wrap.primitiveType());
        switch (wrap) {
            case VOID:
                mh = EMPTY;
                break;
            case OBJECT:
            case INT: case LONG: case FLOAT: case DOUBLE:
                try {
                    mh = IMPL_LOOKUP.findStatic(THIS_CLASS, "zero"+wrap.wrapperSimpleName(), type);
                } catch (ReflectiveOperationException ex) {
                    mh = null;
                }
                break;
        }
        if (mh != null) {
            cache.put(wrap, mh);
            return mh;
        }

        // use zeroInt and cast the result
        if (wrap.isSubwordOrInt() && wrap != Wrapper.INT) {
            mh = MethodHandles.explicitCastArguments(zeroConstantFunction(Wrapper.INT), type);
            cache.put(wrap, mh);
            return mh;
        }
        throw new IllegalArgumentException("cannot find zero constant for " + wrap);
    }

    /// Converting references to references.

    /**
     * Value-killing function.
     * @param x an arbitrary reference value
     * @return a null
     */
    static Object alwaysNull(Object x) {
        return null;
    }

    /**
     * Value-killing function.
     * @param x an arbitrary reference value
     * @return a zero
     */
    static int alwaysZero(Object x) {
        return 0;
    }

    /**
     * Identity function.
     * @param x an arbitrary reference value
     * @return the same value x
     */
    static <T> T identity(T x) {
        return x;
    }

    /**
     * Identity function on ints.
     * @param x an arbitrary int value
     * @return the same value x
     */
    static int identity(int x) {
        return x;
    }

    static byte identity(byte x) {
        return x;
    }

    static short identity(short x) {
        return x;
    }

    static boolean identity(boolean x) {
        return x;
    }

    static char identity(char x) {
        return x;
    }

    /**
     * Identity function on longs.
     * @param x an arbitrary long value
     * @return the same value x
     */
    static long identity(long x) {
        return x;
    }

    static float identity(float x) {
        return x;
    }

    static double identity(double x) {
        return x;
    }

    /**
     * Identity function, with reference cast.
     * @param t an arbitrary reference type
     * @param x an arbitrary reference value
     * @return the same value x
     */
    static <T,U> T castReference(Class<? extends T> t, U x) {
        return t.cast(x);
    }

    private static final MethodHandle IDENTITY, CAST_REFERENCE, ALWAYS_NULL, ALWAYS_ZERO, ZERO_OBJECT, IGNORE, EMPTY, NEW_ARRAY;
    static {
        try {
            MethodType idType = MethodType.genericMethodType(1);
            MethodType castType = idType.insertParameterTypes(0, Class.class);
            MethodType alwaysZeroType = idType.changeReturnType(int.class);
            MethodType ignoreType = idType.changeReturnType(void.class);
            MethodType zeroObjectType = MethodType.genericMethodType(0);
            IDENTITY = IMPL_LOOKUP.findStatic(THIS_CLASS, "identity", idType);
            //CAST_REFERENCE = IMPL_LOOKUP.findVirtual(Class.class, "cast", idType);
            CAST_REFERENCE = IMPL_LOOKUP.findStatic(THIS_CLASS, "castReference", castType);
            ALWAYS_NULL = IMPL_LOOKUP.findStatic(THIS_CLASS, "alwaysNull", idType);
            ALWAYS_ZERO = IMPL_LOOKUP.findStatic(THIS_CLASS, "alwaysZero", alwaysZeroType);
            ZERO_OBJECT = IMPL_LOOKUP.findStatic(THIS_CLASS, "zeroObject", zeroObjectType);
            IGNORE = IMPL_LOOKUP.findStatic(THIS_CLASS, "ignore", ignoreType);
            EMPTY = IMPL_LOOKUP.findStatic(THIS_CLASS, "empty", ignoreType.dropParameterTypes(0, 1));
            NEW_ARRAY = IMPL_LOOKUP.findStatic(THIS_CLASS, "newArray", MethodType.methodType(Object[].class, int.class));
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            throw new InternalError("uncaught exception", ex);
        }
    }

    // Varargs methods need to be in a separately initialized class, to bootstrapping problems.
    static class LazyStatics {
        private static final MethodHandle COPY_AS_REFERENCE_ARRAY, COPY_AS_PRIMITIVE_ARRAY, MAKE_LIST;
        static {
            try {
                //MAKE_ARRAY = IMPL_LOOKUP.findStatic(THIS_CLASS, "makeArray", MethodType.methodType(Object[].class, Object[].class));
                COPY_AS_REFERENCE_ARRAY = IMPL_LOOKUP.findStatic(THIS_CLASS, "copyAsReferenceArray", MethodType.methodType(Object[].class, Class.class, Object[].class));
                COPY_AS_PRIMITIVE_ARRAY = IMPL_LOOKUP.findStatic(THIS_CLASS, "copyAsPrimitiveArray", MethodType.methodType(Object.class, Wrapper.class, Object[].class));
                MAKE_LIST = IMPL_LOOKUP.findStatic(THIS_CLASS, "makeList", MethodType.methodType(List.class, Object[].class));
            } catch (ReflectiveOperationException ex) {
                throw new InternalError("uncaught exception", ex);
            }
        }
    }

    private static final EnumMap<Wrapper, MethodHandle>[] WRAPPER_CASTS
            = newWrapperCaches(2);

    /** Return a method that casts its sole argument (an Object) to the given type
     *  and returns it as the given type (if exact is true), or as plain Object (if erase is true).
     */
    public static MethodHandle cast(Class<?> type) {
        boolean exact = false;
        if (type.isPrimitive())  throw new IllegalArgumentException("cannot cast primitive type "+type);
        MethodHandle mh = null;
        Wrapper wrap = null;
        EnumMap<Wrapper, MethodHandle> cache = null;
        if (Wrapper.isWrapperType(type)) {
            wrap = Wrapper.forWrapperType(type);
            cache = WRAPPER_CASTS[exact?1:0];
            mh = cache.get(wrap);
            if (mh != null)  return mh;
        }
        if (VerifyType.isNullReferenceConversion(Object.class, type))
            mh = IDENTITY;
        else if (VerifyType.isNullType(type))
            mh = ALWAYS_NULL;
        else
            mh = MethodHandles.insertArguments(CAST_REFERENCE, 0, type);
        if (exact) {
            MethodType xmt = MethodType.methodType(type, Object.class);
            mh = MethodHandles.explicitCastArguments(mh, xmt);
        }
        if (cache != null)
            cache.put(wrap, mh);
        return mh;
    }

    public static MethodHandle identity() {
        return IDENTITY;
    }

    public static MethodHandle identity(Class<?> type) {
        if (!type.isPrimitive())
            // Reference identity has been moved into MethodHandles:
            return MethodHandles.identity(type);
        return identity(Wrapper.findPrimitiveType(type));
    }

    public static MethodHandle identity(Wrapper wrap) {
        EnumMap<Wrapper, MethodHandle> cache = CONSTANT_FUNCTIONS[1];
        MethodHandle mh = cache.get(wrap);
        if (mh != null) {
            return mh;
        }
        // slow path
        MethodType type = MethodType.methodType(wrap.primitiveType());
        if (wrap != Wrapper.VOID)
            type = type.appendParameterTypes(wrap.primitiveType());
        try {
            mh = IMPL_LOOKUP.findStatic(THIS_CLASS, "identity", type);
        } catch (ReflectiveOperationException ex) {
            mh = null;
        }
        if (mh == null && wrap == Wrapper.VOID) {
            mh = EMPTY;  // #(){} : #()void
        }
        if (mh != null) {
            cache.put(wrap, mh);
            return mh;
        }

        if (mh != null) {
            cache.put(wrap, mh);
            return mh;
        }
        throw new IllegalArgumentException("cannot find identity for " + wrap);
    }

    /// Primitive conversions.
    // These are supported directly by the JVM, usually by a single instruction.
    // In the case of narrowing to a subword, there may be a pair of instructions.
    // In the case of booleans, there may be a helper routine to manage a 1-bit value.
    // This is the full 8x8 matrix (minus the diagonal).

    // narrow double to all other types:
    static float doubleToFloat(double x) {  // bytecode: d2f
        return (float) x;
    }
    static long doubleToLong(double x) {  // bytecode: d2l
        return (long) x;
    }
    static int doubleToInt(double x) {  // bytecode: d2i
        return (int) x;
    }
    static short doubleToShort(double x) {  // bytecodes: d2i, i2s
        return (short) x;
    }
    static char doubleToChar(double x) {  // bytecodes: d2i, i2c
        return (char) x;
    }
    static byte doubleToByte(double x) {  // bytecodes: d2i, i2b
        return (byte) x;
    }
    static boolean doubleToBoolean(double x) {
        return toBoolean((byte) x);
    }

    // widen float:
    static double floatToDouble(float x) {  // bytecode: f2d
        return x;
    }
    // narrow float:
    static long floatToLong(float x) {  // bytecode: f2l
        return (long) x;
    }
    static int floatToInt(float x) {  // bytecode: f2i
        return (int) x;
    }
    static short floatToShort(float x) {  // bytecodes: f2i, i2s
        return (short) x;
    }
    static char floatToChar(float x) {  // bytecodes: f2i, i2c
        return (char) x;
    }
    static byte floatToByte(float x) {  // bytecodes: f2i, i2b
        return (byte) x;
    }
    static boolean floatToBoolean(float x) {
        return toBoolean((byte) x);
    }

    // widen long:
    static double longToDouble(long x) {  // bytecode: l2d
        return x;
    }
    static float longToFloat(long x) {  // bytecode: l2f
        return x;
    }
    // narrow long:
    static int longToInt(long x) {  // bytecode: l2i
        return (int) x;
    }
    static short longToShort(long x) {  // bytecodes: f2i, i2s
        return (short) x;
    }
    static char longToChar(long x) {  // bytecodes: f2i, i2c
        return (char) x;
    }
    static byte longToByte(long x) {  // bytecodes: f2i, i2b
        return (byte) x;
    }
    static boolean longToBoolean(long x) {
        return toBoolean((byte) x);
    }

    // widen int:
    static double intToDouble(int x) {  // bytecode: i2d
        return x;
    }
    static float intToFloat(int x) {  // bytecode: i2f
        return x;
    }
    static long intToLong(int x) {  // bytecode: i2l
        return x;
    }
    // narrow int:
    static short intToShort(int x) {  // bytecode: i2s
        return (short) x;
    }
    static char intToChar(int x) {  // bytecode: i2c
        return (char) x;
    }
    static byte intToByte(int x) {  // bytecode: i2b
        return (byte) x;
    }
    static boolean intToBoolean(int x) {
        return toBoolean((byte) x);
    }

    // widen short:
    static double shortToDouble(short x) {  // bytecode: i2d (implicit 's2i')
        return x;
    }
    static float shortToFloat(short x) {  // bytecode: i2f (implicit 's2i')
        return x;
    }
    static long shortToLong(short x) {  // bytecode: i2l (implicit 's2i')
        return x;
    }
    static int shortToInt(short x) {  // (implicit 's2i')
        return x;
    }
    // narrow short:
    static char shortToChar(short x) {  // bytecode: i2c (implicit 's2i')
        return (char)x;
    }
    static byte shortToByte(short x) {  // bytecode: i2b (implicit 's2i')
        return (byte)x;
    }
    static boolean shortToBoolean(short x) {
        return toBoolean((byte) x);
    }

    // widen char:
    static double charToDouble(char x) {  // bytecode: i2d (implicit 'c2i')
        return x;
    }
    static float charToFloat(char x) {  // bytecode: i2f (implicit 'c2i')
        return x;
    }
    static long charToLong(char x) {  // bytecode: i2l (implicit 'c2i')
        return x;
    }
    static int charToInt(char x) {  // (implicit 'c2i')
        return x;
    }
    // narrow char:
    static short charToShort(char x) {  // bytecode: i2s (implicit 'c2i')
        return (short)x;
    }
    static byte charToByte(char x) {  // bytecode: i2b (implicit 'c2i')
        return (byte)x;
    }
    static boolean charToBoolean(char x) {
        return toBoolean((byte) x);
    }

    // widen byte:
    static double byteToDouble(byte x) {  // bytecode: i2d (implicit 'b2i')
        return x;
    }
    static float byteToFloat(byte x) {  // bytecode: i2f (implicit 'b2i')
        return x;
    }
    static long byteToLong(byte x) {  // bytecode: i2l (implicit 'b2i')
        return x;
    }
    static int byteToInt(byte x) {  // (implicit 'b2i')
        return x;
    }
    static short byteToShort(byte x) {  // bytecode: i2s (implicit 'b2i')
        return (short)x;
    }
    static char byteToChar(byte x) {  // bytecode: i2b (implicit 'b2i')
        return (char)x;
    }
    // narrow byte to boolean:
    static boolean byteToBoolean(byte x) {
        return toBoolean(x);
    }

    // widen boolean to all types:
    static double booleanToDouble(boolean x) {
        return fromBoolean(x);
    }
    static float booleanToFloat(boolean x) {
        return fromBoolean(x);
    }
    static long booleanToLong(boolean x) {
        return fromBoolean(x);
    }
    static int booleanToInt(boolean x) {
        return fromBoolean(x);
    }
    static short booleanToShort(boolean x) {
        return fromBoolean(x);
    }
    static char booleanToChar(boolean x) {
        return (char)fromBoolean(x);
    }
    static byte booleanToByte(boolean x) {
        return fromBoolean(x);
    }

    // helpers to force boolean into the conversion scheme:
    static boolean toBoolean(byte x) {
        // see javadoc for MethodHandles.explicitCastArguments
        return ((x & 1) != 0);
    }
    static byte fromBoolean(boolean x) {
        // see javadoc for MethodHandles.explicitCastArguments
        return (x ? (byte)1 : (byte)0);
    }

    private static final EnumMap<Wrapper, MethodHandle>[]
            CONVERT_PRIMITIVE_FUNCTIONS = newWrapperCaches(Wrapper.values().length);

    public static MethodHandle convertPrimitive(Wrapper wsrc, Wrapper wdst) {
        EnumMap<Wrapper, MethodHandle> cache = CONVERT_PRIMITIVE_FUNCTIONS[wsrc.ordinal()];
        MethodHandle mh = cache.get(wdst);
        if (mh != null) {
            return mh;
        }
        // slow path
        Class<?> src = wsrc.primitiveType();
        Class<?> dst = wdst.primitiveType();
        MethodType type = src == void.class ? MethodType.methodType(dst) : MethodType.methodType(dst, src);
        if (wsrc == wdst) {
            mh = identity(src);
        } else if (wsrc == Wrapper.VOID) {
            mh = zeroConstantFunction(wdst);
        } else if (wdst == Wrapper.VOID) {
            mh = MethodHandles.dropArguments(EMPTY, 0, src);  // Defer back to MethodHandles.
        } else if (wsrc == Wrapper.OBJECT) {
            mh = unboxCast(dst);
        } else if (wdst == Wrapper.OBJECT) {
            mh = box(src);
        } else {
            assert(src.isPrimitive() && dst.isPrimitive());
            try {
                mh = IMPL_LOOKUP.findStatic(THIS_CLASS, src.getSimpleName()+"To"+capitalize(dst.getSimpleName()), type);
            } catch (ReflectiveOperationException ex) {
                mh = null;
            }
        }
        if (mh != null) {
            assert(mh.type() == type) : mh;
            cache.put(wdst, mh);
            return mh;
        }

        throw new IllegalArgumentException("cannot find primitive conversion function for " +
                                           src.getSimpleName()+" -> "+dst.getSimpleName());
    }

    public static MethodHandle convertPrimitive(Class<?> src, Class<?> dst) {
        return convertPrimitive(Wrapper.forPrimitiveType(src), Wrapper.forPrimitiveType(dst));
    }

    private static String capitalize(String x) {
        return Character.toUpperCase(x.charAt(0))+x.substring(1);
    }

    /// Collection of multiple arguments.

    public static Object convertArrayElements(Class<?> arrayType, Object array) {
        Class<?> src = array.getClass().getComponentType();
        Class<?> dst = arrayType.getComponentType();
        if (src == null || dst == null)  throw new IllegalArgumentException("not array type");
        Wrapper sw = (src.isPrimitive() ? Wrapper.forPrimitiveType(src) : null);
        Wrapper dw = (dst.isPrimitive() ? Wrapper.forPrimitiveType(dst) : null);
        int length;
        if (sw == null) {
            Object[] a = (Object[]) array;
            length = a.length;
            if (dw == null)
                return Arrays.copyOf(a, length, arrayType.asSubclass(Object[].class));
            Object res = dw.makeArray(length);
            dw.copyArrayUnboxing(a, 0, res, 0, length);
            return res;
        }
        length = java.lang.reflect.Array.getLength(array);
        Object[] res;
        if (dw == null) {
            res = Arrays.copyOf(NO_ARGS_ARRAY, length, arrayType.asSubclass(Object[].class));
        } else {
            res = new Object[length];
        }
        sw.copyArrayBoxing(array, 0, res, 0, length);
        if (dw == null)  return res;
        Object a = dw.makeArray(length);
        dw.copyArrayUnboxing(res, 0, a, 0, length);
        return a;
    }

    private static MethodHandle findCollector(String name, int nargs, Class<?> rtype, Class<?>... ptypes) {
        MethodType type = MethodType.genericMethodType(nargs)
                .changeReturnType(rtype)
                .insertParameterTypes(0, ptypes);
        try {
            return IMPL_LOOKUP.findStatic(THIS_CLASS, name, type);
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }

    private static final Object[] NO_ARGS_ARRAY = {};
    private static Object[] makeArray(Object... args) { return args; }
    private static Object[] array() { return NO_ARGS_ARRAY; }
    private static Object[] array(Object a0)
                { return makeArray(a0); }
    private static Object[] array(Object a0, Object a1)
                { return makeArray(a0, a1); }
    private static Object[] array(Object a0, Object a1, Object a2)
                { return makeArray(a0, a1, a2); }
    private static Object[] array(Object a0, Object a1, Object a2, Object a3)
                { return makeArray(a0, a1, a2, a3); }
    private static Object[] array(Object a0, Object a1, Object a2, Object a3,
                                  Object a4)
                { return makeArray(a0, a1, a2, a3, a4); }
    private static Object[] array(Object a0, Object a1, Object a2, Object a3,
                                  Object a4, Object a5)
                { return makeArray(a0, a1, a2, a3, a4, a5); }
    private static Object[] array(Object a0, Object a1, Object a2, Object a3,
                                  Object a4, Object a5, Object a6)
                { return makeArray(a0, a1, a2, a3, a4, a5, a6); }
    private static Object[] array(Object a0, Object a1, Object a2, Object a3,
                                  Object a4, Object a5, Object a6, Object a7)
                { return makeArray(a0, a1, a2, a3, a4, a5, a6, a7); }
    private static Object[] array(Object a0, Object a1, Object a2, Object a3,
                                  Object a4, Object a5, Object a6, Object a7,
                                  Object a8)
                { return makeArray(a0, a1, a2, a3, a4, a5, a6, a7, a8); }
    private static Object[] array(Object a0, Object a1, Object a2, Object a3,
                                  Object a4, Object a5, Object a6, Object a7,
                                  Object a8, Object a9)
                { return makeArray(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9); }
    private static MethodHandle[] makeArrays() {
        ArrayList<MethodHandle> mhs = new ArrayList<>();
        for (;;) {
            MethodHandle mh = findCollector("array", mhs.size(), Object[].class);
            if (mh == null)  break;
            mhs.add(mh);
        }
        assert(mhs.size() == 11);  // current number of methods
        return mhs.toArray(new MethodHandle[MAX_ARITY+1]);
    }
    private static final MethodHandle[] ARRAYS = makeArrays();

    // mh-fill versions of the above:
    private static Object[] newArray(int len) { return new Object[len]; }
    private static void fillWithArguments(Object[] a, int pos, Object... args) {
        System.arraycopy(args, 0, a, pos, args.length);
    }
    // using Integer pos instead of int pos to avoid bootstrapping problems
    private static Object[] fillArray(Object[] a, Integer pos, Object a0)
                { fillWithArguments(a, pos, a0); return a; }
    private static Object[] fillArray(Object[] a, Integer pos, Object a0, Object a1)
                { fillWithArguments(a, pos, a0, a1); return a; }
    private static Object[] fillArray(Object[] a, Integer pos, Object a0, Object a1, Object a2)
                { fillWithArguments(a, pos, a0, a1, a2); return a; }
    private static Object[] fillArray(Object[] a, Integer pos, Object a0, Object a1, Object a2, Object a3)
                { fillWithArguments(a, pos, a0, a1, a2, a3); return a; }
    private static Object[] fillArray(Object[] a, Integer pos, Object a0, Object a1, Object a2, Object a3,
                                  Object a4)
                { fillWithArguments(a, pos, a0, a1, a2, a3, a4); return a; }
    private static Object[] fillArray(Object[] a, Integer pos, Object a0, Object a1, Object a2, Object a3,
                                  Object a4, Object a5)
                { fillWithArguments(a, pos, a0, a1, a2, a3, a4, a5); return a; }
    private static Object[] fillArray(Object[] a, Integer pos, Object a0, Object a1, Object a2, Object a3,
                                  Object a4, Object a5, Object a6)
                { fillWithArguments(a, pos, a0, a1, a2, a3, a4, a5, a6); return a; }
    private static Object[] fillArray(Object[] a, Integer pos, Object a0, Object a1, Object a2, Object a3,
                                  Object a4, Object a5, Object a6, Object a7)
                { fillWithArguments(a, pos, a0, a1, a2, a3, a4, a5, a6, a7); return a; }
    private static Object[] fillArray(Object[] a, Integer pos, Object a0, Object a1, Object a2, Object a3,
                                  Object a4, Object a5, Object a6, Object a7,
                                  Object a8)
                { fillWithArguments(a, pos, a0, a1, a2, a3, a4, a5, a6, a7, a8); return a; }
    private static Object[] fillArray(Object[] a, Integer pos, Object a0, Object a1, Object a2, Object a3,
                                  Object a4, Object a5, Object a6, Object a7,
                                  Object a8, Object a9)
                { fillWithArguments(a, pos, a0, a1, a2, a3, a4, a5, a6, a7, a8, a9); return a; }
    private static MethodHandle[] makeFillArrays() {
        ArrayList<MethodHandle> mhs = new ArrayList<>();
        mhs.add(null);  // there is no empty fill; at least a0 is required
        for (;;) {
            MethodHandle mh = findCollector("fillArray", mhs.size(), Object[].class, Object[].class, Integer.class);
            if (mh == null)  break;
            mhs.add(mh);
        }
        assert(mhs.size() == 11);  // current number of methods
        return mhs.toArray(new MethodHandle[0]);
    }
    private static final MethodHandle[] FILL_ARRAYS = makeFillArrays();

    private static Object[] copyAsReferenceArray(Class<? extends Object[]> arrayType, Object... a) {
        return Arrays.copyOf(a, a.length, arrayType);
    }
    private static Object copyAsPrimitiveArray(Wrapper w, Object... boxes) {
        Object a = w.makeArray(boxes.length);
        w.copyArrayUnboxing(boxes, 0, a, 0, boxes.length);
        return a;
    }

    /** Return a method handle that takes the indicated number of Object
     *  arguments and returns an Object array of them, as if for varargs.
     */
    public static MethodHandle varargsArray(int nargs) {
        MethodHandle mh = ARRAYS[nargs];
        if (mh != null)  return mh;
        mh = findCollector("array", nargs, Object[].class);
        if (mh != null)  return ARRAYS[nargs] = mh;
        MethodHandle producer = filler(0);  // identity function produces result
        return ARRAYS[nargs] = buildVarargsArray(producer, nargs);
    }

    private static MethodHandle buildVarargsArray(MethodHandle producer, int nargs) {
        // Build up the result mh as a sequence of fills like this:
        //   producer(fill(fill(fill(newArray(23),0,x1..x10),10,x11..x20),20,x21..x23))
        // The various fill(_,10*I,___*[J]) are reusable.
        MethodHandle filler = filler(nargs);
        MethodHandle mh = producer;
        mh = MethodHandles.dropArguments(mh, 1, filler.type().parameterList());
        mh = MethodHandles.foldArguments(mh, filler);
        mh = MethodHandles.foldArguments(mh, buildNewArray(nargs));
        return mh;
    }

    private static MethodHandle buildNewArray(int nargs) {
        return MethodHandles.insertArguments(NEW_ARRAY, 0, nargs);
    }

    private static final MethodHandle[] FILLERS = new MethodHandle[MAX_ARITY+1];
    // filler(N).invoke(a, arg0..arg[N-1]) fills a[0]..a[N-1]
    private static MethodHandle filler(int nargs) {
        MethodHandle filler = FILLERS[nargs];
        if (filler != null)  return filler;
        return FILLERS[nargs] = buildFiller(nargs);
    }
    private static MethodHandle buildFiller(int nargs) {
        if (nargs == 0)
            return MethodHandles.identity(Object[].class);
        final int CHUNK = (FILL_ARRAYS.length - 1);
        int rightLen = nargs % CHUNK;
        int leftLen = nargs - rightLen;
        if (rightLen == 0) {
            leftLen = nargs - (rightLen = CHUNK);
            if (FILLERS[leftLen] == null) {
                // build some precursors from left to right
                for (int j = 0; j < leftLen; j += CHUNK)  filler(j);
            }
        }
        MethodHandle leftFill = filler(leftLen);  // recursive fill
        MethodHandle rightFill = FILL_ARRAYS[rightLen];
        rightFill = MethodHandles.insertArguments(rightFill, 1, leftLen);  // [leftLen..nargs-1]

        // Combine the two fills: right(left(newArray(nargs), x1..x20), x21..x23)
        MethodHandle mh = filler(0);  // identity function produces result
        mh = MethodHandles.dropArguments(mh, 1, rightFill.type().parameterList());
        mh = MethodHandles.foldArguments(mh, rightFill);
        if (leftLen > 0) {
            mh = MethodHandles.dropArguments(mh, 1, leftFill.type().parameterList());
            mh = MethodHandles.foldArguments(mh, leftFill);
        }
        return mh;
    }

    // Type-polymorphic version of varargs maker.
    private static final ClassValue<MethodHandle[]> TYPED_COLLECTORS
        = new ClassValue<MethodHandle[]>() {
            protected MethodHandle[] computeValue(Class<?> type) {
                return new MethodHandle[256];
            }
    };

    /** Return a method handle that takes the indicated number of
     *  typed arguments and returns an array of them.
     *  The type argument is the array type.
     */
    public static MethodHandle varargsArray(Class<?> arrayType, int nargs) {
        Class<?> elemType = arrayType.getComponentType();
        if (elemType == null)  throw new IllegalArgumentException("not an array: "+arrayType);
        // FIXME: Need more special casing and caching here.
        if (elemType == Object.class)
            return varargsArray(nargs);
        // other cases:  primitive arrays, subtypes of Object[]
        MethodHandle cache[] = TYPED_COLLECTORS.get(elemType);
        MethodHandle mh = nargs < cache.length ? cache[nargs] : null;
        if (mh != null)  return mh;
        MethodHandle producer = buildArrayProducer(arrayType);
        mh = buildVarargsArray(producer, nargs);
        mh = mh.asType(MethodType.methodType(arrayType, Collections.<Class<?>>nCopies(nargs, elemType)));
        cache[nargs] = mh;
        return mh;
    }

    private static MethodHandle buildArrayProducer(Class<?> arrayType) {
        Class<?> elemType = arrayType.getComponentType();
        if (elemType.isPrimitive())
            return LazyStatics.COPY_AS_PRIMITIVE_ARRAY.bindTo(Wrapper.forPrimitiveType(elemType));
        else
            return LazyStatics.COPY_AS_REFERENCE_ARRAY.bindTo(arrayType);
    }

    // List version of varargs maker.

    private static final List<Object> NO_ARGS_LIST = Arrays.asList(NO_ARGS_ARRAY);
    private static List<Object> makeList(Object... args) { return Arrays.asList(args); }
    private static List<Object> list() { return NO_ARGS_LIST; }
    private static List<Object> list(Object a0)
                { return makeList(a0); }
    private static List<Object> list(Object a0, Object a1)
                { return makeList(a0, a1); }
    private static List<Object> list(Object a0, Object a1, Object a2)
                { return makeList(a0, a1, a2); }
    private static List<Object> list(Object a0, Object a1, Object a2, Object a3)
                { return makeList(a0, a1, a2, a3); }
    private static List<Object> list(Object a0, Object a1, Object a2, Object a3,
                                     Object a4)
                { return makeList(a0, a1, a2, a3, a4); }
    private static List<Object> list(Object a0, Object a1, Object a2, Object a3,
                                     Object a4, Object a5)
                { return makeList(a0, a1, a2, a3, a4, a5); }
    private static List<Object> list(Object a0, Object a1, Object a2, Object a3,
                                     Object a4, Object a5, Object a6)
                { return makeList(a0, a1, a2, a3, a4, a5, a6); }
    private static List<Object> list(Object a0, Object a1, Object a2, Object a3,
                                     Object a4, Object a5, Object a6, Object a7)
                { return makeList(a0, a1, a2, a3, a4, a5, a6, a7); }
    private static List<Object> list(Object a0, Object a1, Object a2, Object a3,
                                     Object a4, Object a5, Object a6, Object a7,
                                     Object a8)
                { return makeList(a0, a1, a2, a3, a4, a5, a6, a7, a8); }
    private static List<Object> list(Object a0, Object a1, Object a2, Object a3,
                                     Object a4, Object a5, Object a6, Object a7,
                                     Object a8, Object a9)
                { return makeList(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9); }
    private static MethodHandle[] makeLists() {
        ArrayList<MethodHandle> mhs = new ArrayList<>();
        for (;;) {
            MethodHandle mh = findCollector("list", mhs.size(), List.class);
            if (mh == null)  break;
            mhs.add(mh);
        }
        assert(mhs.size() == 11);  // current number of methods
        return mhs.toArray(new MethodHandle[MAX_ARITY+1]);
    }
    private static final MethodHandle[] LISTS = makeLists();

    /** Return a method handle that takes the indicated number of Object
     *  arguments and returns a List.
     */
    public static MethodHandle varargsList(int nargs) {
        MethodHandle mh = LISTS[nargs];
        if (mh != null)  return mh;
        mh = findCollector("list", nargs, List.class);
        if (mh != null)  return LISTS[nargs] = mh;
        return LISTS[nargs] = buildVarargsList(nargs);
    }
    private static MethodHandle buildVarargsList(int nargs) {
        return MethodHandles.filterReturnValue(varargsArray(nargs), LazyStatics.MAKE_LIST);
    }
}
