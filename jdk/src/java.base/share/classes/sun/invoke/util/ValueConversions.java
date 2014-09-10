/*
 * Copyright (c) 2008, 2013, Oracle and/or its affiliates. All rights reserved.
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
import java.util.EnumMap;

public class ValueConversions {
    private static final Class<?> THIS_CLASS = ValueConversions.class;
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
        Number res;
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
    }

    static void empty() {
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
     * Identity function.
     * @param x an arbitrary reference value
     * @return the same value x
     */
    static <T> T identity(T x) {
        return x;
    }

    static <T> T[] identity(T[] x) {
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

    private static final MethodHandle IDENTITY, CAST_REFERENCE, ZERO_OBJECT, IGNORE, EMPTY;
    static {
        try {
            MethodType idType = MethodType.genericMethodType(1);
            MethodType ignoreType = idType.changeReturnType(void.class);
            MethodType zeroObjectType = MethodType.genericMethodType(0);
            IDENTITY = IMPL_LOOKUP.findStatic(THIS_CLASS, "identity", idType);
            CAST_REFERENCE = IMPL_LOOKUP.findVirtual(Class.class, "cast", idType);
            ZERO_OBJECT = IMPL_LOOKUP.findStatic(THIS_CLASS, "zeroObject", zeroObjectType);
            IGNORE = IMPL_LOOKUP.findStatic(THIS_CLASS, "ignore", ignoreType);
            EMPTY = IMPL_LOOKUP.findStatic(THIS_CLASS, "empty", ignoreType.dropParameterTypes(0, 1));
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            throw newInternalError("uncaught exception", ex);
        }
    }

    private static final EnumMap<Wrapper, MethodHandle>[] WRAPPER_CASTS
            = newWrapperCaches(1);

    /** Return a method that casts its sole argument (an Object) to the given type
     *  and returns it as the given type.
     */
    public static MethodHandle cast(Class<?> type) {
        return cast(type, CAST_REFERENCE);
    }
    public static MethodHandle cast(Class<?> type, MethodHandle castReference) {
        if (type.isPrimitive())  throw new IllegalArgumentException("cannot cast primitive type "+type);
        MethodHandle mh;
        Wrapper wrap = null;
        EnumMap<Wrapper, MethodHandle> cache = null;
        if (Wrapper.isWrapperType(type)) {
            wrap = Wrapper.forWrapperType(type);
            cache = WRAPPER_CASTS[0];
            mh = cache.get(wrap);
            if (mh != null)  return mh;
        }
        mh = MethodHandles.insertArguments(castReference, 0, type);
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

    // handy shared exception makers (they simplify the common case code)
    private static InternalError newInternalError(String message, Throwable cause) {
        return new InternalError(message, cause);
    }
    private static InternalError newInternalError(Throwable cause) {
        return new InternalError(cause);
    }
}
