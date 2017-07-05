/*
 * Copyright 2008-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.dyn.util;

import java.dyn.*;
import java.dyn.MethodHandles.Lookup;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import sun.dyn.Access;
import sun.dyn.AdapterMethodHandle;
import sun.dyn.MethodHandleImpl;

public class ValueConversions {
    private static final Access IMPL_TOKEN = Access.getToken();
    private static final Lookup IMPL_LOOKUP = MethodHandleImpl.getLookup(IMPL_TOKEN);

    private static EnumMap<Wrapper, MethodHandle>[] newWrapperCaches(int n) {
        @SuppressWarnings("unchecked")
        EnumMap<Wrapper, MethodHandle>[] caches
                = (EnumMap<Wrapper, MethodHandle>[]) new EnumMap[n];  // unchecked warning expected here
        for (int i = 0; i < n; i++)
            caches[i] = new EnumMap<Wrapper, MethodHandle>(Wrapper.class);
        return caches;
    }

    /// Converting references to values.

    static int unboxInteger(Object x) {
        if (x == null)  return 0;  // never NPE
        return ((Integer) x).intValue();
    }

    static byte unboxByte(Object x) {
        if (x == null)  return 0;  // never NPE
        return ((Byte) x).byteValue();
    }

    static short unboxShort(Object x) {
        if (x == null)  return 0;  // never NPE
        return ((Short) x).shortValue();
    }

    static boolean unboxBoolean(Object x) {
        if (x == null)  return false;  // never NPE
        return ((Boolean) x).booleanValue();
    }

    static char unboxCharacter(Object x) {
        if (x == null)  return 0;  // never NPE
        return ((Character) x).charValue();
    }

    static long unboxLong(Object x) {
        if (x == null)  return 0;  // never NPE
        return ((Long) x).longValue();
    }

    static float unboxFloat(Object x) {
        if (x == null)  return 0;  // never NPE
        return ((Float) x).floatValue();
    }

    static double unboxDouble(Object x) {
        if (x == null)  return 0;  // never NPE
        return ((Double) x).doubleValue();
    }

    /// Converting references to "raw" values.
    /// A raw primitive value is always an int or long.

    static int unboxByteRaw(Object x) {
        return unboxByte(x);
    }

    static int unboxShortRaw(Object x) {
        return unboxShort(x);
    }

    static int unboxBooleanRaw(Object x) {
        return unboxBoolean(x) ? 1 : 0;
    }

    static int unboxCharacterRaw(Object x) {
        return unboxCharacter(x);
    }

    static int unboxFloatRaw(Object x) {
        return Float.floatToIntBits(unboxFloat(x));
    }

    static long unboxDoubleRaw(Object x) {
        return Double.doubleToRawLongBits(unboxDouble(x));
    }

    private static MethodType unboxType(Wrapper wrap, boolean raw) {
        return MethodType.methodType(rawWrapper(wrap, raw).primitiveType(), wrap.wrapperType());
    }

    private static final EnumMap<Wrapper, MethodHandle>[]
            UNBOX_CONVERSIONS = newWrapperCaches(4);

    private static MethodHandle unbox(Wrapper wrap, boolean exact, boolean raw) {
        EnumMap<Wrapper, MethodHandle> cache = UNBOX_CONVERSIONS[(exact?1:0)+(raw?2:0)];
        MethodHandle mh = cache.get(wrap);
        if (mh != null) {
            return mh;
        }
        // slow path
        switch (wrap) {
            case OBJECT:
                mh = IDENTITY; break;
            case VOID:
                mh = raw ? ALWAYS_ZERO : IGNORE; break;
            case INT: case LONG:
                // these guys don't need separate raw channels
                if (raw)  mh = unbox(wrap, exact, false);
                break;
        }
        if (mh != null) {
            cache.put(wrap, mh);
            return mh;
        }
        // look up the method
        String name = "unbox" + wrap.simpleName() + (raw ? "Raw" : "");
        MethodType type = unboxType(wrap, raw);
        if (!exact)
            // actually, type is wrong; the Java method takes Object
            mh = IMPL_LOOKUP.findStatic(ValueConversions.class, name, type.erase());
        else
            mh = retype(type, unbox(wrap, !exact, raw));
        if (mh != null) {
            cache.put(wrap, mh);
            return mh;
        }
        throw new IllegalArgumentException("cannot find unbox adapter for " + wrap + (raw ? " (raw)" : ""));
    }

    public static MethodHandle unbox(Wrapper type, boolean exact) {
        return unbox(type, exact, false);
    }

    public static MethodHandle unboxRaw(Wrapper type, boolean exact) {
        return unbox(type, exact, true);
    }

    public static MethodHandle unbox(Class<?> type, boolean exact) {
        return unbox(Wrapper.forPrimitiveType(type), exact, false);
    }

    public static MethodHandle unboxRaw(Class<?> type, boolean exact) {
        return unbox(Wrapper.forPrimitiveType(type), exact, true);
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

    /// Converting raw primitives to references

    static Byte boxByteRaw(int x) {
        return boxByte((byte)x);
    }

    static Short boxShortRaw(int x) {
        return boxShort((short)x);
    }

    static Boolean boxBooleanRaw(int x) {
        return boxBoolean(x != 0);
    }

    static Character boxCharacterRaw(int x) {
        return boxCharacter((char)x);
    }

    static Float boxFloatRaw(int x) {
        return boxFloat(Float.intBitsToFloat(x));
    }

    static Double boxDoubleRaw(long x) {
        return boxDouble(Double.longBitsToDouble(x));
    }

    // a raw void value is (arbitrarily) a garbage int
    static Void boxVoidRaw(int x) {
        return null;
    }

    private static MethodType boxType(Wrapper wrap, boolean raw) {
        // be exact, since return casts are hard to compose
        Class<?> boxType = wrap.wrapperType();
        return MethodType.methodType(boxType, rawWrapper(wrap, raw).primitiveType());
    }

    private static Wrapper rawWrapper(Wrapper wrap, boolean raw) {
        if (raw)  return wrap.isDoubleWord() ? Wrapper.LONG : Wrapper.INT;
        return wrap;
    }

    private static final EnumMap<Wrapper, MethodHandle>[]
            BOX_CONVERSIONS = newWrapperCaches(4);

    private static MethodHandle box(Wrapper wrap, boolean exact, boolean raw) {
        EnumMap<Wrapper, MethodHandle> cache = BOX_CONVERSIONS[(exact?1:0)+(raw?2:0)];
        MethodHandle mh = cache.get(wrap);
        if (mh != null) {
            return mh;
        }
        // slow path
        switch (wrap) {
            case OBJECT:
                mh = IDENTITY; break;
            case VOID:
                if (!raw)  mh = ZERO_OBJECT;
                break;
            case INT: case LONG:
                // these guys don't need separate raw channels
                if (raw)  mh = box(wrap, exact, false);
                break;
        }
        if (mh != null) {
            cache.put(wrap, mh);
            return mh;
        }
        // look up the method
        String name = "box" + wrap.simpleName() + (raw ? "Raw" : "");
        MethodType type = boxType(wrap, raw);
        if (exact)
            mh = IMPL_LOOKUP.findStatic(ValueConversions.class, name, type);
        else
            mh = retype(type.erase(), box(wrap, !exact, raw));
        if (mh != null) {
            cache.put(wrap, mh);
            return mh;
        }
        throw new IllegalArgumentException("cannot find box adapter for " + wrap + (raw ? " (raw)" : ""));
    }

    public static MethodHandle box(Class<?> type, boolean exact) {
        return box(Wrapper.forPrimitiveType(type), exact, false);
    }

    public static MethodHandle boxRaw(Class<?> type, boolean exact) {
        return box(Wrapper.forPrimitiveType(type), exact, true);
    }

    public static MethodHandle box(Wrapper type, boolean exact) {
        return box(type, exact, false);
    }

    public static MethodHandle boxRaw(Wrapper type, boolean exact) {
        return box(type, exact, true);
    }

    /// Kludges for when raw values get accidentally boxed.

    static int unboxRawInteger(Object x) {
        if (x instanceof Integer)
            return unboxInteger(x);
        else
            return (int) unboxLong(x);
    }

    static Integer reboxRawInteger(Object x) {
        if (x instanceof Integer)
            return (Integer) x;
        else
            return (int) unboxLong(x);
    }

    static Byte reboxRawByte(Object x) {
        if (x instanceof Byte)  return (Byte) x;
        return boxByteRaw(unboxRawInteger(x));
    }

    static Short reboxRawShort(Object x) {
        if (x instanceof Short)  return (Short) x;
        return boxShortRaw(unboxRawInteger(x));
    }

    static Boolean reboxRawBoolean(Object x) {
        if (x instanceof Boolean)  return (Boolean) x;
        return boxBooleanRaw(unboxRawInteger(x));
    }

    static Character reboxRawCharacter(Object x) {
        if (x instanceof Character)  return (Character) x;
        return boxCharacterRaw(unboxRawInteger(x));
    }

    static Float reboxRawFloat(Object x) {
        if (x instanceof Float)  return (Float) x;
        return boxFloatRaw(unboxRawInteger(x));
    }

    static Long reboxRawLong(Object x) {
        return (Long) x;  //never a rebox
    }

    static Double reboxRawDouble(Object x) {
        if (x instanceof Double)  return (Double) x;
        return boxDoubleRaw(unboxLong(x));
    }

    private static MethodType reboxType(Wrapper wrap) {
        Class<?> boxType = wrap.wrapperType();
        return MethodType.methodType(boxType, Object.class);
    }

    private static final EnumMap<Wrapper, MethodHandle>[]
            REBOX_CONVERSIONS = newWrapperCaches(2);

    /**
     * Becase we normalize primitive types to reduce the number of signatures,
     * primitives are sometimes manipulated under an "erased" type,
     * either int (for types other than long/double) or long (for all types).
     * When the erased primitive value is then boxed into an Integer or Long,
     * the final boxed primitive is sometimes required.  This transformation
     * is called a "rebox".  It takes an Integer or Long and produces some
     * other boxed value.
     */
    public static MethodHandle rebox(Wrapper wrap, boolean exact) {
        EnumMap<Wrapper, MethodHandle> cache = REBOX_CONVERSIONS[exact?1:0];
        MethodHandle mh = cache.get(wrap);
        if (mh != null) {
            return mh;
        }
        // slow path
        switch (wrap) {
            case OBJECT:
                mh = IDENTITY; break;
            case VOID:
                throw new IllegalArgumentException("cannot rebox a void");
        }
        if (mh != null) {
            cache.put(wrap, mh);
            return mh;
        }
        // look up the method
        String name = "reboxRaw" + wrap.simpleName();
        MethodType type = reboxType(wrap);
        if (exact)
            mh = IMPL_LOOKUP.findStatic(ValueConversions.class, name, type);
        else
            mh = retype(IDENTITY.type(), rebox(wrap, !exact));
        if (mh != null) {
            cache.put(wrap, mh);
            return mh;
        }
        throw new IllegalArgumentException("cannot find rebox adapter for " + wrap);
    }

    public static MethodHandle rebox(Class<?> type, boolean exact) {
        return rebox(Wrapper.forPrimitiveType(type), exact);
    }

    /// Width-changing conversions between int and long.

    static long widenInt(int x) {
        return (long) x;
    }

    static Long widenBoxedInt(Integer x) {
        return (long)(int)x;
    }

    static int narrowLong(long x) {
        return (int) x;
    }

    static Integer narrowBoxedLong(Long x) {
        return (int)(long) x;
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
            ZERO_CONSTANT_FUNCTIONS = newWrapperCaches(1);

    public static MethodHandle zeroConstantFunction(Wrapper wrap) {
        EnumMap<Wrapper, MethodHandle> cache = ZERO_CONSTANT_FUNCTIONS[0];
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
            case INT: case LONG: case FLOAT: case DOUBLE:
                mh = IMPL_LOOKUP.findStatic(ValueConversions.class, "zero"+wrap.simpleName(), type);
                break;
        }
        if (mh != null) {
            cache.put(wrap, mh);
            return mh;
        }

        // use the raw method
        Wrapper rawWrap = wrap.rawPrimitive();
        if (rawWrap != wrap) {
            mh = retype(type, zeroConstantFunction(rawWrap));
        }
        if (mh != null) {
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
     * Identity function, with reference cast.
     * @param t an arbitrary reference type
     * @param x an arbitrary reference value
     * @return the same value x
     */
    static <T,U> T castReference(Class<? extends T> t, U x) {
        return t.cast(x);
    }

    private static final MethodHandle IDENTITY, CAST_REFERENCE, ALWAYS_NULL, ALWAYS_ZERO, ZERO_OBJECT, IGNORE, EMPTY;
    static {
        try {
            MethodType idType = MethodType.genericMethodType(1);
            MethodType castType = idType.insertParameterTypes(0, Class.class);
            MethodType alwaysZeroType = idType.changeReturnType(int.class);
            MethodType ignoreType = idType.changeReturnType(void.class);
            MethodType zeroObjectType = MethodType.genericMethodType(0);
            IDENTITY = IMPL_LOOKUP.findStatic(ValueConversions.class, "identity", idType);
            //CAST_REFERENCE = IMPL_LOOKUP.findVirtual(Class.class, "cast", idType);
            CAST_REFERENCE = IMPL_LOOKUP.findStatic(ValueConversions.class, "castReference", castType);
            ALWAYS_NULL = IMPL_LOOKUP.findStatic(ValueConversions.class, "alwaysNull", idType);
            ALWAYS_ZERO = IMPL_LOOKUP.findStatic(ValueConversions.class, "alwaysZero", alwaysZeroType);
            ZERO_OBJECT = IMPL_LOOKUP.findStatic(ValueConversions.class, "zeroObject", zeroObjectType);
            IGNORE = IMPL_LOOKUP.findStatic(ValueConversions.class, "ignore", ignoreType);
            EMPTY = IMPL_LOOKUP.findStatic(ValueConversions.class, "empty", ignoreType.dropParameterTypes(0, 1));
        } catch (RuntimeException ex) {
            throw ex;
        }
    }

    private static final EnumMap<Wrapper, MethodHandle> WRAPPER_CASTS
            = new EnumMap<Wrapper, MethodHandle>(Wrapper.class);

    private static final EnumMap<Wrapper, MethodHandle> EXACT_WRAPPER_CASTS
            = new EnumMap<Wrapper, MethodHandle>(Wrapper.class);

    /** Return a method that casts its sole argument (an Object) to the given type
     *  and returns it as the given type (if exact is true), or as plain Object (if erase is true).
     */
    public static MethodHandle cast(Class<?> type, boolean exact) {
        if (type.isPrimitive())  throw new IllegalArgumentException("cannot cast primitive type "+type);
        MethodHandle mh = null;
        Wrapper wrap = null;
        EnumMap<Wrapper, MethodHandle> cache = null;
        if (Wrapper.isWrapperType(type)) {
            wrap = Wrapper.forWrapperType(type);
            cache = (exact ? EXACT_WRAPPER_CASTS : WRAPPER_CASTS);
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
            mh = AdapterMethodHandle.makeRetypeRaw(IMPL_TOKEN, xmt, mh);
        }
        if (cache != null)
            cache.put(wrap, mh);
        return mh;
    }

    public static MethodHandle identity() {
        return IDENTITY;
    }

    private static MethodHandle retype(MethodType type, MethodHandle mh) {
        return AdapterMethodHandle.makeRetypeOnly(IMPL_TOKEN, type, mh);
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
    static MethodHandle[] makeArrays() {
        ArrayList<MethodHandle> arrays = new ArrayList<MethodHandle>();
        MethodHandles.Lookup lookup = IMPL_LOOKUP;
        for (;;) {
            int nargs = arrays.size();
            MethodType type = MethodType.genericMethodType(nargs).changeReturnType(Object[].class);
            String name = "array";
            MethodHandle array = null;
            try {
                array = lookup.findStatic(ValueConversions.class, name, type);
            } catch (NoAccessException ex) {
            }
            if (array == null)  break;
            arrays.add(array);
        }
        assert(arrays.size() == 11);  // current number of methods
        return arrays.toArray(new MethodHandle[0]);
    }
    static final MethodHandle[] ARRAYS = makeArrays();

    /** Return a method handle that takes the indicated number of Object
     *  arguments and returns an Object array of them, as if for varargs.
     */
    public static MethodHandle varargsArray(int nargs) {
        if (nargs < ARRAYS.length)
            return ARRAYS[nargs];
        // else need to spin bytecode or do something else fancy
        throw new UnsupportedOperationException("NYI");
    }

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
    static MethodHandle[] makeLists() {
        ArrayList<MethodHandle> arrays = new ArrayList<MethodHandle>();
        MethodHandles.Lookup lookup = IMPL_LOOKUP;
        for (;;) {
            int nargs = arrays.size();
            MethodType type = MethodType.genericMethodType(nargs).changeReturnType(List.class);
            String name = "list";
            MethodHandle array = null;
            try {
                array = lookup.findStatic(ValueConversions.class, name, type);
            } catch (NoAccessException ex) {
            }
            if (array == null)  break;
            arrays.add(array);
        }
        assert(arrays.size() == 11);  // current number of methods
        return arrays.toArray(new MethodHandle[0]);
    }
    static final MethodHandle[] LISTS = makeLists();

    /** Return a method handle that takes the indicated number of Object
     *  arguments and returns List.
     */
    public static MethodHandle varargsList(int nargs) {
        if (nargs < LISTS.length)
            return LISTS[nargs];
        // else need to spin bytecode or do something else fancy
        throw new UnsupportedOperationException("NYI");
    }
}

