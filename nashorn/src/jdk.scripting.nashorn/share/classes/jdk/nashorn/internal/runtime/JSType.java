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

package jdk.nashorn.internal.runtime;

import static jdk.nashorn.internal.codegen.CompilerConstants.staticCall;
import static jdk.nashorn.internal.lookup.Lookup.MH;
import static jdk.nashorn.internal.runtime.ECMAErrors.typeError;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import jdk.internal.dynalink.beans.StaticClass;
import jdk.nashorn.api.scripting.JSObject;
import jdk.nashorn.internal.codegen.CompilerConstants.Call;
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.objects.Global;
import jdk.nashorn.internal.parser.Lexer;
import jdk.nashorn.internal.runtime.arrays.ArrayLikeIterator;
import jdk.nashorn.internal.runtime.linker.Bootstrap;

/**
 * Representation for ECMAScript types - this maps directly to the ECMA script standard
 */
public enum JSType {
    /** The undefined type */
    UNDEFINED("undefined"),

    /** The null type */
    NULL("object"),

    /** The boolean type */
    BOOLEAN("boolean"),

    /** The number type */
    NUMBER("number"),

    /** The string type */
    STRING("string"),

    /** The object type */
    OBJECT("object"),

    /** The function type */
    FUNCTION("function");

    /** The type name as returned by ECMAScript "typeof" operator*/
    private final String typeName;

    /** Max value for an uint32 in JavaScript */
    public static final long MAX_UINT = 0xFFFF_FFFFL;

    private static final MethodHandles.Lookup JSTYPE_LOOKUP = MethodHandles.lookup();

    /** JavaScript compliant conversion function from Object to boolean */
    public static final Call TO_BOOLEAN = staticCall(JSTYPE_LOOKUP, JSType.class, "toBoolean", boolean.class, Object.class);

    /** JavaScript compliant conversion function from number to boolean */
    public static final Call TO_BOOLEAN_D = staticCall(JSTYPE_LOOKUP, JSType.class, "toBoolean", boolean.class, double.class);

    /** JavaScript compliant conversion function from Object to integer */
    public static final Call TO_INTEGER = staticCall(JSTYPE_LOOKUP, JSType.class, "toInteger", int.class, Object.class);

    /** JavaScript compliant conversion function from Object to long */
    public static final Call TO_LONG = staticCall(JSTYPE_LOOKUP, JSType.class, "toLong", long.class, Object.class);

    /** JavaScript compliant conversion function from double to long */
    public static final Call TO_LONG_D = staticCall(JSTYPE_LOOKUP, JSType.class, "toLong", long.class, double.class);

    /** JavaScript compliant conversion function from Object to number */
    public static final Call TO_NUMBER = staticCall(JSTYPE_LOOKUP, JSType.class, "toNumber", double.class, Object.class);

    /** JavaScript compliant conversion function from Object to number with type check */
    public static final Call TO_NUMBER_OPTIMISTIC = staticCall(JSTYPE_LOOKUP, JSType.class, "toNumberOptimistic", double.class, Object.class, int.class);

    /** JavaScript compliant conversion function from Object to String */
    public static final Call TO_STRING = staticCall(JSTYPE_LOOKUP, JSType.class, "toString", String.class, Object.class);

    /** JavaScript compliant conversion function from Object to int32 */
    public static final Call TO_INT32 = staticCall(JSTYPE_LOOKUP, JSType.class, "toInt32", int.class, Object.class);

    /** JavaScript compliant conversion function from Object to int32 */
    public static final Call TO_INT32_L = staticCall(JSTYPE_LOOKUP, JSType.class, "toInt32", int.class, long.class);

    /** JavaScript compliant conversion function from Object to int32 with type check */
    public static final Call TO_INT32_OPTIMISTIC = staticCall(JSTYPE_LOOKUP, JSType.class, "toInt32Optimistic", int.class, Object.class, int.class);

    /** JavaScript compliant conversion function from double to int32 */
    public static final Call TO_INT32_D = staticCall(JSTYPE_LOOKUP, JSType.class, "toInt32", int.class, double.class);

    /** JavaScript compliant conversion function from int to uint32 */
    public static final Call TO_UINT32_I = staticCall(JSTYPE_LOOKUP, JSType.class, "toUint32", long.class, int.class);

    /** JavaScript compliant conversion function from Object to uint32 */
    public static final Call TO_UINT32 = staticCall(JSTYPE_LOOKUP, JSType.class, "toUint32", long.class, Object.class);

    /** JavaScript compliant conversion function from Object to long with type check */
    public static final Call TO_LONG_OPTIMISTIC = staticCall(JSTYPE_LOOKUP, JSType.class, "toLongOptimistic", long.class, Object.class, int.class);

    /** JavaScript compliant conversion function from number to uint32 */
    public static final Call TO_UINT32_D = staticCall(JSTYPE_LOOKUP, JSType.class, "toUint32", long.class, double.class);

    /** JavaScript compliant conversion function from number to String */
    public static final Call TO_STRING_D = staticCall(JSTYPE_LOOKUP, JSType.class, "toString", String.class, double.class);

    /** Combined call to toPrimitive followed by toString. */
    public static final Call TO_PRIMITIVE_TO_STRING = staticCall(JSTYPE_LOOKUP, JSType.class, "toPrimitiveToString", String.class, Object.class);

    /** Combined call to toPrimitive followed by toCharSequence. */
    public static final Call TO_PRIMITIVE_TO_CHARSEQUENCE = staticCall(JSTYPE_LOOKUP, JSType.class, "toPrimitiveToCharSequence", CharSequence.class, Object.class);

    /** Throw an unwarranted optimism exception */
    public static final Call THROW_UNWARRANTED = staticCall(JSTYPE_LOOKUP, JSType.class, "throwUnwarrantedOptimismException", Object.class, Object.class, int.class);

    /** Add exact wrapper for potentially overflowing integer operations */
    public static final Call ADD_EXACT       = staticCall(JSTYPE_LOOKUP, JSType.class, "addExact", int.class, int.class, int.class, int.class);

    /** Sub exact wrapper for potentially overflowing integer operations */
    public static final Call SUB_EXACT       = staticCall(JSTYPE_LOOKUP, JSType.class, "subExact", int.class, int.class, int.class, int.class);

    /** Multiply exact wrapper for potentially overflowing integer operations */
    public static final Call MUL_EXACT       = staticCall(JSTYPE_LOOKUP, JSType.class, "mulExact", int.class, int.class, int.class, int.class);

    /** Div exact wrapper for potentially integer division that turns into float point */
    public static final Call DIV_EXACT       = staticCall(JSTYPE_LOOKUP, JSType.class, "divExact", int.class, int.class, int.class, int.class);

    /** Div zero wrapper for integer division that handles (0/0)|0 == 0 */
    public static final Call DIV_ZERO        = staticCall(JSTYPE_LOOKUP, JSType.class, "divZero", int.class, int.class, int.class);

    /** Mod zero wrapper for integer division that handles (0%0)|0 == 0 */
    public static final Call REM_ZERO        = staticCall(JSTYPE_LOOKUP, JSType.class, "remZero", int.class, int.class, int.class);

    /** Mod exact wrapper for potentially integer remainders that turns into float point */
    public static final Call REM_EXACT       = staticCall(JSTYPE_LOOKUP, JSType.class, "remExact", int.class, int.class, int.class, int.class);

    /** Decrement exact wrapper for potentially overflowing integer operations */
    public static final Call DECREMENT_EXACT = staticCall(JSTYPE_LOOKUP, JSType.class, "decrementExact",   int.class, int.class, int.class);

    /** Increment exact wrapper for potentially overflowing integer operations */
    public static final Call INCREMENT_EXACT = staticCall(JSTYPE_LOOKUP, JSType.class, "incrementExact",   int.class, int.class, int.class);

    /** Negate exact exact wrapper for potentially overflowing integer operations */
    public static final Call NEGATE_EXACT         = staticCall(JSTYPE_LOOKUP, JSType.class, "negateExact", int.class, int.class, int.class);

    /** Add exact wrapper for potentially overflowing long operations */
    public static final Call ADD_EXACT_LONG       = staticCall(JSTYPE_LOOKUP, JSType.class, "addExact", long.class, long.class, long.class, int.class);

    /** Sub exact wrapper for potentially overflowing long operations */
    public static final Call SUB_EXACT_LONG       = staticCall(JSTYPE_LOOKUP, JSType.class, "subExact", long.class, long.class, long.class, int.class);

    /** Multiply exact wrapper for potentially overflowing long operations */
    public static final Call MUL_EXACT_LONG       = staticCall(JSTYPE_LOOKUP, JSType.class, "mulExact", long.class, long.class, long.class, int.class);

    /** Div exact wrapper for potentially integer division that turns into float point */
    public static final Call DIV_EXACT_LONG       = staticCall(JSTYPE_LOOKUP, JSType.class, "divExact", long.class, long.class, long.class, int.class);

    /** Div zero wrapper for long division that handles (0/0) &gt;&gt;&gt; 0 == 0 */
    public static final Call DIV_ZERO_LONG        = staticCall(JSTYPE_LOOKUP, JSType.class, "divZero", long.class, long.class, long.class);

    /** Mod zero wrapper for long division that handles (0%0) &gt;&gt;&gt; 0 == 0 */
    public static final Call REM_ZERO_LONG       = staticCall(JSTYPE_LOOKUP, JSType.class, "remZero", long.class, long.class, long.class);

    /** Mod exact wrapper for potentially integer remainders that turns into float point */
    public static final Call REM_EXACT_LONG       = staticCall(JSTYPE_LOOKUP, JSType.class, "remExact", long.class, long.class, long.class, int.class);

    /** Decrement exact wrapper for potentially overflowing long operations */
    public static final Call DECREMENT_EXACT_LONG = staticCall(JSTYPE_LOOKUP, JSType.class, "decrementExact",  long.class, long.class, int.class);

    /** Increment exact wrapper for potentially overflowing long operations */
    public static final Call INCREMENT_EXACT_LONG = staticCall(JSTYPE_LOOKUP, JSType.class, "incrementExact",  long.class, long.class, int.class);

    /** Negate exact exact wrapper for potentially overflowing long operations */
    public static final Call NEGATE_EXACT_LONG    = staticCall(JSTYPE_LOOKUP, JSType.class, "negateExact",     long.class, long.class, int.class);

    /** Method handle to convert a JS Object to a Java array. */
    public static final Call TO_JAVA_ARRAY = staticCall(JSTYPE_LOOKUP, JSType.class, "toJavaArray", Object.class, Object.class, Class.class);

    /** Method handle for void returns. */
    public static final Call VOID_RETURN = staticCall(JSTYPE_LOOKUP, JSType.class, "voidReturn", void.class);

    /**
     * The list of available accessor types in width order. This order is used for type guesses narrow{@literal ->} wide
     *  in the dual--fields world
     */
    private static final List<Type> ACCESSOR_TYPES = Collections.unmodifiableList(
            Arrays.asList(
                Type.INT,
                Type.LONG,
                Type.NUMBER,
                Type.OBJECT));

    /** table index for undefined type - hard coded so it can be used in switches at compile time */
    public static final int TYPE_UNDEFINED_INDEX = -1;
    /** table index for integer type - hard coded so it can be used in switches at compile time */
    public static final int TYPE_INT_INDEX    = 0; //getAccessorTypeIndex(int.class);
    /** table index for long type - hard coded so it can be used in switches at compile time */
    public static final int TYPE_LONG_INDEX   = 1; //getAccessorTypeIndex(long.class);
    /** table index for double type - hard coded so it can be used in switches at compile time */
    public static final int TYPE_DOUBLE_INDEX = 2; //getAccessorTypeIndex(double.class);
    /** table index for object type - hard coded so it can be used in switches at compile time */
    public static final int TYPE_OBJECT_INDEX = 3; //getAccessorTypeIndex(Object.class);

    /** object conversion quickies with JS semantics - used for return value and parameter filter */
    public static final List<MethodHandle> CONVERT_OBJECT = toUnmodifiableList(
        JSType.TO_INT32.methodHandle(),
        JSType.TO_UINT32.methodHandle(),
        JSType.TO_NUMBER.methodHandle(),
        null
    );

    /**
     * object conversion quickies with JS semantics - used for return value and parameter filter, optimistic
     * throws exception upon incompatible type (asking for a narrower one than the storage)
     */
    public static final List<MethodHandle> CONVERT_OBJECT_OPTIMISTIC = toUnmodifiableList(
        JSType.TO_INT32_OPTIMISTIC.methodHandle(),
        JSType.TO_LONG_OPTIMISTIC.methodHandle(),
        JSType.TO_NUMBER_OPTIMISTIC.methodHandle(),
        null
    );

    /** The value of Undefined cast to an int32 */
    public static final int    UNDEFINED_INT    = 0;
    /** The value of Undefined cast to a long */
    public static final long   UNDEFINED_LONG   = 0L;
    /** The value of Undefined cast to a double */
    public static final double UNDEFINED_DOUBLE = Double.NaN;

    /**
     * Method handles for getters that return undefined coerced
     * to the appropriate type
     */
    public static final List<MethodHandle> GET_UNDEFINED = toUnmodifiableList(
        MH.constant(int.class, UNDEFINED_INT),
        MH.constant(long.class, UNDEFINED_LONG),
        MH.constant(double.class, UNDEFINED_DOUBLE),
        MH.constant(Object.class, Undefined.getUndefined())
    );

    private static final double INT32_LIMIT = 4294967296.0;

    /**
     * Constructor
     *
     * @param typeName the type name
     */
    private JSType(final String typeName) {
        this.typeName = typeName;
    }

    /**
     * The external type name as returned by ECMAScript "typeof" operator
     *
     * @return type name for this type
     */
    public final String typeName() {
        return this.typeName;
    }

    /**
     * Return the JSType for a given object
     *
     * @param obj an object
     *
     * @return the JSType for the object
     */
    public static JSType of(final Object obj) {
        // Order of these statements is tuned for performance (see JDK-8024476)
        if (obj == null) {
            return JSType.NULL;
        }

        if (obj instanceof ScriptObject) {
            return obj instanceof ScriptFunction ? JSType.FUNCTION : JSType.OBJECT;
        }

        if (obj instanceof Boolean) {
            return JSType.BOOLEAN;
        }

        if (isString(obj)) {
            return JSType.STRING;
        }

        if (obj instanceof Number) {
            return JSType.NUMBER;
        }

        if (obj == ScriptRuntime.UNDEFINED) {
            return JSType.UNDEFINED;
        }

        return Bootstrap.isCallable(obj) ? JSType.FUNCTION : JSType.OBJECT;
    }

    /**
     * Similar to {@link #of(Object)}, but does not distinguish between {@link #FUNCTION} and {@link #OBJECT}, returning
     * {@link #OBJECT} in both cases. The distinction is costly, and the EQ and STRICT_EQ predicates don't care about it
     * so we maintain this version for their use.
     *
     * @param obj an object
     *
     * @return the JSType for the object; returns {@link #OBJECT} instead of {@link #FUNCTION} for functions.
     */
    public static JSType ofNoFunction(final Object obj) {
        // Order of these statements is tuned for performance (see JDK-8024476)
        if (obj == null) {
            return JSType.NULL;
        }

        if (obj instanceof ScriptObject) {
            return JSType.OBJECT;
        }

        if (obj instanceof Boolean) {
            return JSType.BOOLEAN;
        }

        if (isString(obj)) {
            return JSType.STRING;
        }

        if (obj instanceof Number) {
            return JSType.NUMBER;
        }

        if (obj == ScriptRuntime.UNDEFINED) {
            return JSType.UNDEFINED;
        }

        return JSType.OBJECT;
    }

    /**
     * Void return method handle glue
     */
    public static void voidReturn() {
        //empty
        //TODO: fix up SetMethodCreator better so we don't need this stupid thing
    }

    /**
     * Returns true if double number can be represented as an int
     *
     * @param number a long to inspect
     *
     * @return true for int representable longs
     */
    public static boolean isRepresentableAsInt(final long number) {
        return (int)number == number;
    }

    /**
     * Returns true if double number can be represented as an int. Note that it returns true for negative
     * zero. If you need to exclude negative zero, use {@link #isStrictlyRepresentableAsInt(double)}.
     *
     * @param number a double to inspect
     *
     * @return true for int representable doubles
     */
    public static boolean isRepresentableAsInt(final double number) {
        return (int)number == number;
    }

    /**
     * Returns true if double number can be represented as an int. Note that it returns false for negative
     * zero. If you don't need to distinguish negative zero, use {@link #isRepresentableAsInt(double)}.
     *
     * @param number a double to inspect
     *
     * @return true for int representable doubles
     */
    public static boolean isStrictlyRepresentableAsInt(final double number) {
        return isRepresentableAsInt(number) && isNotNegativeZero(number);
    }

    /**
     * Returns true if Object can be represented as an int
     *
     * @param obj an object to inspect
     *
     * @return true for int representable objects
     */
    public static boolean isRepresentableAsInt(final Object obj) {
        if (obj instanceof Number) {
            return isRepresentableAsInt(((Number)obj).doubleValue());
        }
        return false;
    }

    /**
     * Returns true if double number can be represented as a long. Note that it returns true for negative
     * zero. If you need to exclude negative zero, use {@link #isStrictlyRepresentableAsLong(double)}.
     *
     * @param number a double to inspect
     * @return true for long representable doubles
     */
    public static boolean isRepresentableAsLong(final double number) {
        return (long)number == number;
    }

    /**
     * Returns true if double number can be represented as a long. Note that it returns false for negative
     * zero. If you don't need to distinguish negative zero, use {@link #isRepresentableAsLong(double)}.
     *
     * @param number a double to inspect
     *
     * @return true for long representable doubles
     */
    public static boolean isStrictlyRepresentableAsLong(final double number) {
        return isRepresentableAsLong(number) && isNotNegativeZero(number);
    }

    /**
     * Returns true if Object can be represented as a long
     *
     * @param obj an object to inspect
     *
     * @return true for long representable objects
     */
    public static boolean isRepresentableAsLong(final Object obj) {
        if (obj instanceof Number) {
            return isRepresentableAsLong(((Number)obj).doubleValue());
        }
        return false;
    }

    /**
     * Returns true if the number is not the negative zero ({@code -0.0d}).
     * @param number the number to test
     * @return true if it is not the negative zero, false otherwise.
     */
    private static boolean isNotNegativeZero(final double number) {
        return Double.doubleToRawLongBits(number) != 0x8000000000000000L;
    }

    /**
     * Check whether an object is primitive
     *
     * @param obj an object
     *
     * @return true if object is primitive (includes null and undefined)
     */
    public static boolean isPrimitive(final Object obj) {
        return obj == null ||
               obj == ScriptRuntime.UNDEFINED ||
               obj instanceof Boolean ||
               obj instanceof Number ||
               isString(obj);
    }

   /**
    * Primitive converter for an object
    *
    * @param obj an object
    *
    * @return primitive form of the object
    */
    public static Object toPrimitive(final Object obj) {
        return toPrimitive(obj, null);
    }

    /**
     * Primitive converter for an object including type hint
     * See ECMA 9.1 ToPrimitive
     *
     * @param obj  an object
     * @param hint a type hint
     *
     * @return the primitive form of the object
     */
    public static Object toPrimitive(final Object obj, final Class<?> hint) {
        if (obj instanceof ScriptObject) {
            return toPrimitive((ScriptObject)obj, hint);
        } else if (isPrimitive(obj)) {
            return obj;
        } else if (obj instanceof JSObject) {
            return toPrimitive((JSObject)obj, hint);
        } else if (obj instanceof StaticClass) {
            final String name = ((StaticClass)obj).getRepresentedClass().getName();
            return new StringBuilder(12 + name.length()).append("[JavaClass ").append(name).append(']').toString();
        }
        return obj.toString();
    }

    private static Object toPrimitive(final ScriptObject sobj, final Class<?> hint) {
        return requirePrimitive(sobj.getDefaultValue(hint));
    }

    private static Object requirePrimitive(final Object result) {
        if (!isPrimitive(result)) {
            throw typeError("bad.default.value", result.toString());
        }
        return result;
    }

    /**
     * Primitive converter for a {@link JSObject} including type hint. Invokes
     * {@link JSObject#getDefaultValue(Class)} and translates any thrown {@link UnsupportedOperationException}
     * to a ECMAScript {@code TypeError}.
     * See ECMA 9.1 ToPrimitive
     *
     * @param jsobj  a JSObject
     * @param hint a type hint
     *
     * @return the primitive form of the JSObject
     */
    public static Object toPrimitive(final JSObject jsobj, final Class<?> hint) {
        try {
            return requirePrimitive(jsobj.getDefaultValue(hint));
        } catch (final UnsupportedOperationException e) {
            throw new ECMAException(Context.getGlobal().newTypeError(e.getMessage()), e);
        }
    }

    /**
     * Combines a hintless toPrimitive and a toString call.
     *
     * @param obj  an object
     *
     * @return the string form of the primitive form of the object
     */
    public static String toPrimitiveToString(final Object obj) {
        return toString(toPrimitive(obj));
    }

    /**
     * Like {@link #toPrimitiveToString(Object)}, but avoids conversion of ConsString to String.
     *
     * @param obj  an object
     * @return the CharSequence form of the primitive form of the object
     */
    public static CharSequence toPrimitiveToCharSequence(final Object obj) {
        return toCharSequence(toPrimitive(obj));
    }

    /**
     * JavaScript compliant conversion of number to boolean
     *
     * @param num a number
     *
     * @return a boolean
     */
    public static boolean toBoolean(final double num) {
        return num != 0 && !Double.isNaN(num);
    }

    /**
     * JavaScript compliant conversion of Object to boolean
     * See ECMA 9.2 ToBoolean
     *
     * @param obj an object
     *
     * @return a boolean
     */
    public static boolean toBoolean(final Object obj) {
        if (obj instanceof Boolean) {
            return (Boolean)obj;
        }

        if (nullOrUndefined(obj)) {
            return false;
        }

        if (obj instanceof Number) {
            final double num = ((Number)obj).doubleValue();
            return num != 0 && !Double.isNaN(num);
        }

        if (isString(obj)) {
            return ((CharSequence)obj).length() > 0;
        }

        return true;
    }


    /**
     * JavaScript compliant converter of Object to String
     * See ECMA 9.8 ToString
     *
     * @param obj an object
     *
     * @return a string
     */
    public static String toString(final Object obj) {
        return toStringImpl(obj, false);
    }

    /**
     * If obj is an instance of {@link ConsString} cast to CharSequence, else return
     * result of {@link #toString(Object)}.
     *
     * @param obj an object
     * @return an instance of String or ConsString
     */
    public static CharSequence toCharSequence(final Object obj) {
        if (obj instanceof ConsString) {
            return (CharSequence) obj;
        }
        return toString(obj);
    }

    /**
     * Check whether a string is representable as a JavaScript number
     *
     * @param str  a string
     *
     * @return     true if string can be represented as a number
     */
    public static boolean isNumber(final String str) {
        try {
            Double.parseDouble(str);
            return true;
        } catch (final NumberFormatException e) {
            return false;
        }
    }

    /**
     * Returns true if object represents a primitive JavaScript string value.
     * @param obj the object
     * @return true if the object represents a primitive JavaScript string value.
     */
    public static boolean isString(final Object obj) {
        return obj instanceof String || obj instanceof ConsString;
    }

    /**
     * JavaScript compliant conversion of integer to String
     *
     * @param num an integer
     *
     * @return a string
     */
    public static String toString(final int num) {
        return Integer.toString(num);
    }

    /**
     * JavaScript compliant conversion of number to String
     * See ECMA 9.8.1
     *
     * @param num a number
     *
     * @return a string
     */
    public static String toString(final double num) {
        if (isRepresentableAsInt(num)) {
            return Integer.toString((int)num);
        }

        if (num == Double.POSITIVE_INFINITY) {
            return "Infinity";
        }

        if (num == Double.NEGATIVE_INFINITY) {
            return "-Infinity";
        }

        if (Double.isNaN(num)) {
            return "NaN";
        }

        return NumberToString.stringFor(num);
    }

    /**
     * JavaScript compliant conversion of number to String
     *
     * @param num   a number
     * @param radix a radix for the conversion
     *
     * @return a string
     */
    public static String toString(final double num, final int radix) {
        assert radix >= 2 && radix <= 36 : "invalid radix";

        if (isRepresentableAsInt(num)) {
            return Integer.toString((int)num, radix);
        }

        if (num == Double.POSITIVE_INFINITY) {
            return "Infinity";
        }

        if (num == Double.NEGATIVE_INFINITY) {
            return "-Infinity";
        }

        if (Double.isNaN(num)) {
            return "NaN";
        }

        if (num == 0.0) {
            return "0";
        }

        final String chars     = "0123456789abcdefghijklmnopqrstuvwxyz";
        final StringBuilder sb = new StringBuilder();

        final boolean negative  = num < 0.0;
        final double  signedNum = negative ? -num : num;

        double intPart = Math.floor(signedNum);
        double decPart = signedNum - intPart;

        // encode integer part from least significant digit, then reverse
        do {
            final double remainder = intPart % radix;
            sb.append(chars.charAt((int) remainder));
            intPart -= remainder;
            intPart /= radix;
        } while (intPart >= 1.0);

        if (negative) {
            sb.append('-');
        }
        sb.reverse();

        // encode decimal part
        if (decPart > 0.0) {
            final int dot = sb.length();
            sb.append('.');
            do {
                decPart *= radix;
                final double d = Math.floor(decPart);
                sb.append(chars.charAt((int)d));
                decPart -= d;
            } while (decPart > 0.0 && sb.length() - dot < 1100);
            // somewhat arbitrarily use same limit as V8
        }

        return sb.toString();
    }

    /**
     * JavaScript compliant conversion of Object to number
     * See ECMA 9.3 ToNumber
     *
     * @param obj  an object
     *
     * @return a number
     */
    public static double toNumber(final Object obj) {
        if (obj instanceof Double) {
            return (Double)obj;
        }
        if (obj instanceof Number) {
            return ((Number)obj).doubleValue();
        }
        return toNumberGeneric(obj);
    }

    /**
     * Converts an object for a comparison with a number. Almost identical to {@link #toNumber(Object)} but
     * converts {@code null} to {@code NaN} instead of zero, so it won't compare equal to zero.
     *
     * @param obj  an object
     *
     * @return a number
     */
    public static double toNumberForEq(final Object obj) {
        return obj == null ? Double.NaN : toNumber(obj);
    }

    /**
     * Converts an object for strict comparison with a number. Returns {@code NaN} for any object that is not
     * a {@link Number}, so only boxed numerics can compare strictly equal to numbers.
     *
     * @param obj  an object
     *
     * @return a number
     */
    public static double toNumberForStrictEq(final Object obj) {
        if (obj instanceof Double) {
            return (Double)obj;
        }
        if (obj instanceof Number) {
            return ((Number)obj).doubleValue();
        }
        return Double.NaN;
    }


    /**
     * JavaScript compliant conversion of Boolean to number
     * See ECMA 9.3 ToNumber
     *
     * @param b a boolean
     *
     * @return JS numeric value of the boolean: 1.0 or 0.0
     */
    public static double toNumber(final Boolean b) {
        return b ? 1d : +0d;
    }

    /**
     * JavaScript compliant conversion of Object to number
     * See ECMA 9.3 ToNumber
     *
     * @param obj  an object
     *
     * @return a number
     */
    public static double toNumber(final ScriptObject obj) {
        return toNumber(toPrimitive(obj, Number.class));
    }

    /**
     * Optimistic number conversion - throws UnwarrantedOptimismException if Object
     *
     * @param obj           object to convert
     * @param programPoint  program point
     * @return double
     */
    public static double toNumberOptimistic(final Object obj, final int programPoint) {
        if (obj != null) {
            final Class<?> clz = obj.getClass();
            if (clz == Double.class || clz == Integer.class || clz == Long.class) {
                return ((Number)obj).doubleValue();
            }
        }
        throw new UnwarrantedOptimismException(obj, programPoint);
    }

    /**
     * Object to number conversion that delegates to either {@link #toNumber(Object)} or to
     * {@link #toNumberOptimistic(Object, int)} depending on whether the program point is valid or not.
     * @param obj the object to convert
     * @param programPoint the program point; can be invalid.
     * @return the value converted to a number
     * @throws UnwarrantedOptimismException if the value can't be represented as a number and the program point is valid.
     */
    public static double toNumberMaybeOptimistic(final Object obj, final int programPoint) {
        return UnwarrantedOptimismException.isValid(programPoint) ? toNumberOptimistic(obj, programPoint) : toNumber(obj);
    }

    /**
     * Digit representation for a character
     *
     * @param ch     a character
     * @param radix  radix
     *
     * @return the digit for this character
     */
    public static int digit(final char ch, final int radix) {
        return digit(ch, radix, false);
    }

    /**
     * Digit representation for a character
     *
     * @param ch             a character
     * @param radix          radix
     * @param onlyIsoLatin1  iso latin conversion only
     *
     * @return the digit for this character
     */
    public static int digit(final char ch, final int radix, final boolean onlyIsoLatin1) {
        final char maxInRadix = (char)('a' + (radix - 1) - 10);
        final char c          = Character.toLowerCase(ch);

        if (c >= 'a' && c <= maxInRadix) {
            return Character.digit(ch, radix);
        }

        if (Character.isDigit(ch)) {
            if (!onlyIsoLatin1 || ch >= '0' && ch <= '9') {
                return Character.digit(ch, radix);
            }
        }

        return -1;
    }

    /**
     * JavaScript compliant String to number conversion
     *
     * @param str  a string
     *
     * @return a number
     */
    public static double toNumber(final String str) {
        int end = str.length();
        if (end == 0) {
            return 0.0; // Empty string
        }

        int  start = 0;
        char f     = str.charAt(0);

        while (Lexer.isJSWhitespace(f)) {
            if (++start == end) {
                return 0.0d; // All whitespace string
            }
            f = str.charAt(start);
        }

        // Guaranteed to terminate even without start >= end check, as the previous loop found at least one
        // non-whitespace character.
        while (Lexer.isJSWhitespace(str.charAt(end - 1))) {
            end--;
        }

        final boolean negative;
        if (f == '-') {
            if(++start == end) {
                return Double.NaN; // Single-char "-" string
            }
            f = str.charAt(start);
            negative = true;
        } else {
            if (f == '+') {
                if (++start == end) {
                    return Double.NaN; // Single-char "+" string
                }
                f = str.charAt(start);
            }
            negative = false;
        }

        final double value;
        if (start + 1 < end && f == '0' && Character.toLowerCase(str.charAt(start + 1)) == 'x') {
            //decode hex string
            value = parseRadix(str.toCharArray(), start + 2, end, 16);
        } else if (f == 'I' && end - start == 8 && str.regionMatches(start, "Infinity", 0, 8)) {
            return negative ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        } else {
            // Fast (no NumberFormatException) path to NaN for non-numeric strings.
            for (int i = start; i < end; i++) {
                f = str.charAt(i);
                if ((f < '0' || f > '9') && f != '.' && f != 'e' && f != 'E' && f != '+' && f != '-') {
                    return Double.NaN;
                }
            }
            try {
                value = Double.parseDouble(str.substring(start, end));
            } catch (final NumberFormatException e) {
                return Double.NaN;
            }
        }

        return negative ? -value : value;
    }

    /**
     * JavaScript compliant Object to integer conversion. See ECMA 9.4 ToInteger
     *
     * <p>Note that this returns {@link java.lang.Integer#MAX_VALUE} or {@link java.lang.Integer#MIN_VALUE}
     * for double values that exceed the int range, including positive and negative Infinity. It is the
     * caller's responsibility to handle such values correctly.</p>
     *
     * @param obj  an object
     * @return an integer
     */
    public static int toInteger(final Object obj) {
        return (int)toNumber(obj);
    }

    /**
     * Converts an Object to long.
     *
     * <p>Note that this returns {@link java.lang.Long#MAX_VALUE} or {@link java.lang.Long#MIN_VALUE}
     * for double values that exceed the long range, including positive and negative Infinity. It is the
     * caller's responsibility to handle such values correctly.</p>
     *
     * @param obj  an object
     * @return a long
     */
    public static long toLong(final Object obj) {
        return obj instanceof Long ? ((Long)obj) : toLong(toNumber(obj));
    }

    /**
     * Converts a double to long.
     *
     * @param num the double to convert
     * @return the converted long value
     */
    public static long toLong(final double num) {
        return (long)num;
    }

    /**
     * Optimistic long conversion - throws UnwarrantedOptimismException if double or Object
     *
     * @param obj           object to convert
     * @param programPoint  program point
     * @return long
     */
    public static long toLongOptimistic(final Object obj, final int programPoint) {
        if (obj != null) {
            final Class<?> clz = obj.getClass();
            if (clz == Long.class || clz == Integer.class) {
                return ((Number)obj).longValue();
            }
        }
        throw new UnwarrantedOptimismException(obj, programPoint);
    }

    /**
     * Object to int conversion that delegates to either {@link #toLong(Object)} or to
     * {@link #toLongOptimistic(Object, int)} depending on whether the program point is valid or not.
     * @param obj the object to convert
     * @param programPoint the program point; can be invalid.
     * @return the value converted to long
     * @throws UnwarrantedOptimismException if the value can't be represented as long and the program point is valid.
     */
    public static long toLongMaybeOptimistic(final Object obj, final int programPoint) {
        return UnwarrantedOptimismException.isValid(programPoint) ? toLongOptimistic(obj, programPoint) : toLong(obj);
    }

    /**
     * JavaScript compliant Object to int32 conversion
     * See ECMA 9.5 ToInt32
     *
     * @param obj an object
     * @return an int32
     */
    public static int toInt32(final Object obj) {
        return toInt32(toNumber(obj));
    }

    /**
     * Optimistic int conversion - throws UnwarrantedOptimismException if double, long or Object
     *
     * @param obj           object to convert
     * @param programPoint  program point
     * @return double
     */
    public static int toInt32Optimistic(final Object obj, final int programPoint) {
        if (obj != null && obj.getClass() == Integer.class) {
            return ((Integer)obj);
        }
        throw new UnwarrantedOptimismException(obj, programPoint);
    }

    /**
     * Object to int conversion that delegates to either {@link #toInt32(Object)} or to
     * {@link #toInt32Optimistic(Object, int)} depending on whether the program point is valid or not.
     * @param obj the object to convert
     * @param programPoint the program point; can be invalid.
     * @return the value converted to int
     * @throws UnwarrantedOptimismException if the value can't be represented as int and the program point is valid.
     */
    public static int toInt32MaybeOptimistic(final Object obj, final int programPoint) {
        return UnwarrantedOptimismException.isValid(programPoint) ? toInt32Optimistic(obj, programPoint) : toInt32(obj);
    }

    // Minimum and maximum range between which every long value can be precisely represented as a double.
    private static final long MAX_PRECISE_DOUBLE = 1L << 53;
    private static final long MIN_PRECISE_DOUBLE = -MAX_PRECISE_DOUBLE;

    /**
     * JavaScript compliant long to int32 conversion
     *
     * @param num a long
     * @return an int32
     */
    public static int toInt32(final long num) {
        return (int)(num >= MIN_PRECISE_DOUBLE && num <= MAX_PRECISE_DOUBLE ? num : (long)(num % INT32_LIMIT));
    }


    /**
     * JavaScript compliant number to int32 conversion
     *
     * @param num a number
     * @return an int32
     */
    public static int toInt32(final double num) {
        return (int)doubleToInt32(num);
    }

    /**
     * JavaScript compliant Object to uint32 conversion
     *
     * @param obj an object
     * @return a uint32
     */
    public static long toUint32(final Object obj) {
        return toUint32(toNumber(obj));
    }

    /**
     * JavaScript compliant number to uint32 conversion
     *
     * @param num a number
     * @return a uint32
     */
    public static long toUint32(final double num) {
        return doubleToInt32(num) & MAX_UINT;
    }

    /**
     * JavaScript compliant int to uint32 conversion
     *
     * @param num an int
     * @return a uint32
     */
    public static long toUint32(final int num) {
        return num & MAX_UINT;
    }

    /**
     * JavaScript compliant Object to uint16 conversion
     * ECMA 9.7 ToUint16: (Unsigned 16 Bit Integer)
     *
     * @param obj an object
     * @return a uint16
     */
    public static int toUint16(final Object obj) {
        return toUint16(toNumber(obj));
    }

    /**
     * JavaScript compliant number to uint16 conversion
     *
     * @param num a number
     * @return a uint16
     */
    public static int toUint16(final int num) {
        return num & 0xffff;
    }

    /**
     * JavaScript compliant number to uint16 conversion
     *
     * @param num a number
     * @return a uint16
     */
    public static int toUint16(final long num) {
        return (int)num & 0xffff;
    }

    /**
     * JavaScript compliant number to uint16 conversion
     *
     * @param num a number
     * @return a uint16
     */
    public static int toUint16(final double num) {
        return (int)doubleToInt32(num) & 0xffff;
    }

    private static long doubleToInt32(final double num) {
        final int exponent = Math.getExponent(num);
        if (exponent < 31) {
            return (long) num;  // Fits into 32 bits
        }
        if (exponent >= 84) {
            // Either infinite or NaN or so large that shift / modulo will produce 0
            // (52 bit mantissa + 32 bit target width).
            return 0;
        }
        // This is rather slow and could probably be sped up using bit-fiddling.
        final double d = num >= 0 ? Math.floor(num) : Math.ceil(num);
        return (long)(d % INT32_LIMIT);
    }

    /**
     * Check whether a number is finite
     *
     * @param num a number
     * @return true if finite
     */
    public static boolean isFinite(final double num) {
        return !Double.isInfinite(num) && !Double.isNaN(num);
    }

    /**
     * Convert a primitive to a double
     *
     * @param num a double
     * @return a boxed double
     */
    public static Double toDouble(final double num) {
        return num;
    }

    /**
     * Convert a primitive to a double
     *
     * @param num a long
     * @return a boxed double
     */
    public static Double toDouble(final long num) {
        return (double)num;
    }

    /**
     * Convert a primitive to a double
     *
     * @param num an int
     * @return a boxed double
     */
    public static Double toDouble(final int num) {
        return (double)num;
    }

    /**
     * Convert a boolean to an Object
     *
     * @param bool a boolean
     * @return a boxed boolean, its Object representation
     */
    public static Object toObject(final boolean bool) {
        return bool;
    }

    /**
     * Convert a number to an Object
     *
     * @param num an integer
     * @return the boxed number
     */
    public static Object toObject(final int num) {
        return num;
    }

    /**
     * Convert a number to an Object
     *
     * @param num a long
     * @return the boxed number
     */
    public static Object toObject(final long num) {
        return num;
    }

    /**
     * Convert a number to an Object
     *
     * @param num a double
     * @return the boxed number
     */
    public static Object toObject(final double num) {
        return num;
    }

    /**
     * Identity converter for objects.
     *
     * @param obj an object
     * @return the boxed number
     */
    public static Object toObject(final Object obj) {
        return obj;
    }

    /**
     * Object conversion. This is used to convert objects and numbers to their corresponding
     * NativeObject type
     * See ECMA 9.9 ToObject
     *
     * @param obj     the object to convert
     *
     * @return the wrapped object
     */
    public static Object toScriptObject(final Object obj) {
        return toScriptObject(Context.getGlobal(), obj);
    }

    /**
     * Object conversion. This is used to convert objects and numbers to their corresponding
     * NativeObject type
     * See ECMA 9.9 ToObject
     *
     * @param global  the global object
     * @param obj     the object to convert
     *
     * @return the wrapped object
     */
    public static Object toScriptObject(final Global global, final Object obj) {
        if (nullOrUndefined(obj)) {
            throw typeError(global, "not.an.object", ScriptRuntime.safeToString(obj));
        }

        if (obj instanceof ScriptObject) {
            return obj;
        }

        return global.wrapAsObject(obj);
    }

    /**
     * Script object to Java array conversion.
     *
     * @param obj script object to be converted to Java array
     * @param componentType component type of the destination array required
     * @return converted Java array
     */
    public static Object toJavaArray(final Object obj, final Class<?> componentType) {
        if (obj instanceof ScriptObject) {
            return ((ScriptObject)obj).getArray().asArrayOfType(componentType);
        } else if (obj instanceof JSObject) {
            final ArrayLikeIterator<?> itr = ArrayLikeIterator.arrayLikeIterator(obj);
            final int len = (int) itr.getLength();
            final Object[] res = new Object[len];
            int idx = 0;
            while (itr.hasNext()) {
                res[idx++] = itr.next();
            }
            return convertArray(res, componentType);
        } else if(obj == null) {
            return null;
        } else {
            throw new IllegalArgumentException("not a script object");
        }
    }

    /**
     * Java array to java array conversion - but using type conversions implemented by linker.
     *
     * @param src source array
     * @param componentType component type of the destination array required
     * @return converted Java array
     */
    public static Object convertArray(final Object[] src, final Class<?> componentType) {
        if(componentType == Object.class) {
            for(int i = 0; i < src.length; ++i) {
                final Object e = src[i];
                if(e instanceof ConsString) {
                    src[i] = e.toString();
                }
            }
        }

        final int l = src.length;
        final Object dst = Array.newInstance(componentType, l);
        final MethodHandle converter = Bootstrap.getLinkerServices().getTypeConverter(Object.class, componentType);
        try {
            for (int i = 0; i < src.length; i++) {
                Array.set(dst, i, invoke(converter, src[i]));
            }
        } catch (final RuntimeException | Error e) {
            throw e;
        } catch (final Throwable t) {
            throw new RuntimeException(t);
        }
        return dst;
    }

    /**
     * Check if an object is null or undefined
     *
     * @param obj object to check
     *
     * @return true if null or undefined
     */
    public static boolean nullOrUndefined(final Object obj) {
        return obj == null || obj == ScriptRuntime.UNDEFINED;
    }

    static String toStringImpl(final Object obj, final boolean safe) {
        if (obj instanceof String) {
            return (String)obj;
        }

        if (obj instanceof ConsString) {
            return obj.toString();
        }

        if (obj instanceof Number) {
            return toString(((Number)obj).doubleValue());
        }

        if (obj == ScriptRuntime.UNDEFINED) {
            return "undefined";
        }

        if (obj == null) {
            return "null";
        }

        if (obj instanceof Boolean) {
            return obj.toString();
        }

        if (safe && obj instanceof ScriptObject) {
            final ScriptObject sobj = (ScriptObject)obj;
            final Global gobj = Context.getGlobal();
            return gobj.isError(sobj) ?
                ECMAException.safeToString(sobj) :
                sobj.safeToString();
        }

        return toString(toPrimitive(obj, String.class));
    }

    // trim from left for JS whitespaces.
    static String trimLeft(final String str) {
        int start = 0;

        while (start < str.length() && Lexer.isJSWhitespace(str.charAt(start))) {
            start++;
        }

        return str.substring(start);
    }

    /**
     * Throw an unwarranted optimism exception for a program point
     * @param value         real return value
     * @param programPoint  program point
     * @return
     */
    @SuppressWarnings("unused")
    private static Object throwUnwarrantedOptimismException(final Object value, final int programPoint) {
        throw new UnwarrantedOptimismException(value, programPoint);
    }

    /**
     * Wrapper for addExact
     *
     * Catches ArithmeticException and rethrows as UnwarrantedOptimismException
     * containing the result and the program point of the failure
     *
     * @param x first term
     * @param y second term
     * @param programPoint program point id
     * @return the result
     * @throws UnwarrantedOptimismException if overflow occurs
     */
    public static int addExact(final int x, final int y, final int programPoint) throws UnwarrantedOptimismException {
        try {
            return Math.addExact(x, y);
        } catch (final ArithmeticException e) {
            throw new UnwarrantedOptimismException((long)x + (long)y, programPoint);
        }
    }

    /**
     * Wrapper for addExact
     *
     * Catches ArithmeticException and rethrows as UnwarrantedOptimismException
     * containing the result and the program point of the failure
     *
     * @param x first term
     * @param y second term
     * @param programPoint program point id
     * @return the result
     * @throws UnwarrantedOptimismException if overflow occurs
     */
    public static long addExact(final long x, final long y, final int programPoint) throws UnwarrantedOptimismException {
        try {
            return Math.addExact(x, y);
        } catch (final ArithmeticException e) {
            throw new UnwarrantedOptimismException((double)x + (double)y, programPoint);
        }
    }

    /**
     * Wrapper for subExact
     *
     * Catches ArithmeticException and rethrows as UnwarrantedOptimismException
     * containing the result and the program point of the failure
     *
     * @param x first term
     * @param y second term
     * @param programPoint program point id
     * @return the result
     * @throws UnwarrantedOptimismException if overflow occurs
     */
    public static int subExact(final int x, final int y, final int programPoint) throws UnwarrantedOptimismException {
        try {
            return Math.subtractExact(x, y);
        } catch (final ArithmeticException e) {
            throw new UnwarrantedOptimismException((long)x - (long)y, programPoint);
        }
    }

    /**
     * Wrapper for subExact
     *
     * Catches ArithmeticException and rethrows as UnwarrantedOptimismException
     * containing the result and the program point of the failure
     *
     * @param x first term
     * @param y second term
     * @param programPoint program point id
     * @return the result
     * @throws UnwarrantedOptimismException if overflow occurs
     */
    public static long subExact(final long x, final long y, final int programPoint) throws UnwarrantedOptimismException {
        try {
            return Math.subtractExact(x, y);
        } catch (final ArithmeticException e) {
            throw new UnwarrantedOptimismException((double)x - (double)y, programPoint);
        }
    }

    /**
     * Wrapper for mulExact
     *
     * Catches ArithmeticException and rethrows as UnwarrantedOptimismException
     * containing the result and the program point of the failure
     *
     * @param x first term
     * @param y second term
     * @param programPoint program point id
     * @return the result
     * @throws UnwarrantedOptimismException if overflow occurs
     */
    public static int mulExact(final int x, final int y, final int programPoint) throws UnwarrantedOptimismException {
        try {
            return Math.multiplyExact(x, y);
        } catch (final ArithmeticException e) {
            throw new UnwarrantedOptimismException((long)x * (long)y, programPoint);
        }
    }

    /**
     * Wrapper for mulExact
     *
     * Catches ArithmeticException and rethrows as UnwarrantedOptimismException
     * containing the result and the program point of the failure
     *
     * @param x first term
     * @param y second term
     * @param programPoint program point id
     * @return the result
     * @throws UnwarrantedOptimismException if overflow occurs
     */
    public static long mulExact(final long x, final long y, final int programPoint) throws UnwarrantedOptimismException {
        try {
            return Math.multiplyExact(x, y);
        } catch (final ArithmeticException e) {
            throw new UnwarrantedOptimismException((double)x * (double)y, programPoint);
        }
    }

    /**
     * Wrapper for divExact. Throws UnwarrantedOptimismException if the result of the division can't be represented as
     * int.
     *
     * @param x first term
     * @param y second term
     * @param programPoint program point id
     * @return the result
     * @throws UnwarrantedOptimismException if the result of the division can't be represented as int.
     */
    public static int divExact(final int x, final int y, final int programPoint) throws UnwarrantedOptimismException {
        final int res;
        try {
            res = x / y;
        } catch (final ArithmeticException e) {
            assert y == 0; // Only div by zero anticipated
            throw new UnwarrantedOptimismException(x > 0 ? Double.POSITIVE_INFINITY : x < 0 ? Double.NEGATIVE_INFINITY : Double.NaN, programPoint);
        }
        final int rem = x % y;
        if (rem == 0) {
            return res;
        }
        // go directly to double here, as anything with non zero remainder is a floating point number in JavaScript
        throw new UnwarrantedOptimismException((double)x / (double)y, programPoint);
    }

    /**
     * Implements int division but allows {@code x / 0} to be represented as 0. Basically equivalent to
     * {@code (x / y)|0} JavaScript expression (division of two ints coerced to int).
     * @param x the dividend
     * @param y the divisor
     * @return the result
     */
    public static int divZero(final int x, final int y) {
        return y == 0 ? 0 : x / y;
    }

    /**
     * Implements int remainder but allows {@code x % 0} to be represented as 0. Basically equivalent to
     * {@code (x % y)|0} JavaScript expression (remainder of two ints coerced to int).
     * @param x the dividend
     * @param y the divisor
     * @return the remainder
     */
    public static int remZero(final int x, final int y) {
        return y == 0 ? 0 : x % y;
    }

    /**
     * Wrapper for modExact. Throws UnwarrantedOptimismException if the modulo can't be represented as int.
     *
     * @param x first term
     * @param y second term
     * @param programPoint program point id
     * @return the result
     * @throws UnwarrantedOptimismException if the modulo can't be represented as int.
     */
    public static int remExact(final int x, final int y, final int programPoint) throws UnwarrantedOptimismException {
        try {
            return x % y;
        } catch (final ArithmeticException e) {
            assert y == 0; // Only mod by zero anticipated
            throw new UnwarrantedOptimismException(Double.NaN, programPoint);
        }
    }

    /**
     * Wrapper for divExact. Throws UnwarrantedOptimismException if the result of the division can't be represented as
     * long.
     *
     * @param x first term
     * @param y second term
     * @param programPoint program point id
     * @return the result
     * @throws UnwarrantedOptimismException if the result of the division can't be represented as long.
     */
    public static long divExact(final long x, final long y, final int programPoint) throws UnwarrantedOptimismException {
        final long res;
        try {
            res = x / y;
        } catch (final ArithmeticException e) {
            assert y == 0L; // Only div by zero anticipated
            throw new UnwarrantedOptimismException(x > 0L ? Double.POSITIVE_INFINITY : x < 0L ? Double.NEGATIVE_INFINITY : Double.NaN, programPoint);
        }
        final long rem = x % y;
        if (rem == 0L) {
            return res;
        }
        throw new UnwarrantedOptimismException((double)x / (double)y, programPoint);
    }

    /**
     * Implements long division but allows {@code x / 0} to be represented as 0. Useful when division of two longs
     * is coerced to long.
     * @param x the dividend
     * @param y the divisor
     * @return the result
     */
    public static long divZero(final long x, final long y) {
        return y == 0L ? 0L : x / y;
    }

    /**
     * Implements long remainder but allows {@code x % 0} to be represented as 0. Useful when remainder of two longs
     * is coerced to long.
     * @param x the dividend
     * @param y the divisor
     * @return the remainder
     */
    public static long remZero(final long x, final long y) {
        return y == 0L ? 0L : x % y;
    }

    /**
     * Wrapper for modExact. Throws UnwarrantedOptimismException if the modulo can't be represented as int.
     *
     * @param x first term
     * @param y second term
     * @param programPoint program point id
     * @return the result
     * @throws UnwarrantedOptimismException if the modulo can't be represented as int.
     */
    public static long remExact(final long x, final long y, final int programPoint) throws UnwarrantedOptimismException {
        try {
            return x % y;
        } catch (final ArithmeticException e) {
            assert y == 0L; // Only mod by zero anticipated
            throw new UnwarrantedOptimismException(Double.NaN, programPoint);
        }
    }

    /**
     * Wrapper for decrementExact
     *
     * Catches ArithmeticException and rethrows as UnwarrantedOptimismException
     * containing the result and the program point of the failure
     *
     * @param x number to negate
     * @param programPoint program point id
     * @return the result
     * @throws UnwarrantedOptimismException if overflow occurs
     */
    public static int decrementExact(final int x, final int programPoint) throws UnwarrantedOptimismException {
        try {
            return Math.decrementExact(x);
        } catch (final ArithmeticException e) {
            throw new UnwarrantedOptimismException((long)x - 1, programPoint);
        }
    }

    /**
     * Wrapper for decrementExact
     *
     * Catches ArithmeticException and rethrows as UnwarrantedOptimismException
     * containing the result and the program point of the failure
     *
     * @param x number to negate
     * @param programPoint program point id
     * @return the result
     * @throws UnwarrantedOptimismException if overflow occurs
     */
    public static long decrementExact(final long x, final int programPoint) throws UnwarrantedOptimismException {
        try {
            return Math.decrementExact(x);
        } catch (final ArithmeticException e) {
            throw new UnwarrantedOptimismException((double)x - 1L, programPoint);
        }
    }

    /**
     * Wrapper for incrementExact
     *
     * Catches ArithmeticException and rethrows as UnwarrantedOptimismException
     * containing the result and the program point of the failure
     *
     * @param x the number to increment
     * @param programPoint program point id
     * @return the result
     * @throws UnwarrantedOptimismException if overflow occurs
     */
    public static int incrementExact(final int x, final int programPoint) throws UnwarrantedOptimismException {
        try {
            return Math.incrementExact(x);
        } catch (final ArithmeticException e) {
            throw new UnwarrantedOptimismException((long)x + 1, programPoint);
        }
    }

    /**
     * Wrapper for incrementExact
     *
     * Catches ArithmeticException and rethrows as UnwarrantedOptimismException
     * containing the result and the program point of the failure
     *
     * @param x the number to increment
     * @param programPoint program point id
     * @return the result
     * @throws UnwarrantedOptimismException if overflow occurs
     */
    public static long incrementExact(final long x, final int programPoint) throws UnwarrantedOptimismException {
        try {
            return Math.incrementExact(x);
        } catch (final ArithmeticException e) {
            throw new UnwarrantedOptimismException((double)x + 1L, programPoint);
        }
    }

    /**
     * Wrapper for negateExact
     *
     * Catches ArithmeticException and rethrows as UnwarrantedOptimismException
     * containing the result and the program point of the failure
     *
     * @param x the number to negate
     * @param programPoint program point id
     * @return the result
     * @throws UnwarrantedOptimismException if overflow occurs
     */
    public static int negateExact(final int x, final int programPoint) throws UnwarrantedOptimismException {
        try {
            if (x == 0) {
                throw new UnwarrantedOptimismException(-0.0, programPoint);
            }
            return Math.negateExact(x);
        } catch (final ArithmeticException e) {
            throw new UnwarrantedOptimismException(-(long)x, programPoint);
        }
    }

    /**
     * Wrapper for negateExact
     *
     * Catches ArithmeticException and rethrows as UnwarrantedOptimismException
     * containing the result and the program point of the failure
     *
     * @param x the number to negate
     * @param programPoint program point id
     * @return the result
     * @throws UnwarrantedOptimismException if overflow occurs
     */
    public static long negateExact(final long x, final int programPoint) throws UnwarrantedOptimismException {
        try {
            if (x == 0L) {
                throw new UnwarrantedOptimismException(-0.0, programPoint);
            }
            return Math.negateExact(x);
        } catch (final ArithmeticException e) {
            throw new UnwarrantedOptimismException(-(double)x, programPoint);
        }
    }

    /**
     * Given a type of an accessor, return its index in [0..getNumberOfAccessorTypes())
     *
     * @param type the type
     *
     * @return the accessor index, or -1 if no accessor of this type exists
     */
    public static int getAccessorTypeIndex(final Type type) {
        return getAccessorTypeIndex(type.getTypeClass());
    }

    /**
     * Given a class of an accessor, return its index in [0..getNumberOfAccessorTypes())
     *
     * Note that this is hardcoded with respect to the dynamic contents of the accessor
     * types array for speed. Hotspot got stuck with this as 5% of the runtime in
     * a benchmark when it looped over values and increased an index counter. :-(
     *
     * @param type the type
     *
     * @return the accessor index, or -1 if no accessor of this type exists
     */
    public static int getAccessorTypeIndex(final Class<?> type) {
        if (type == null) {
            return TYPE_UNDEFINED_INDEX;
        } else if (type == int.class) {
            return TYPE_INT_INDEX;
        } else if (type == long.class) {
            return TYPE_LONG_INDEX;
        } else if (type == double.class) {
            return TYPE_DOUBLE_INDEX;
        } else if (!type.isPrimitive()) {
            return TYPE_OBJECT_INDEX;
        }
        return -1;
    }

    /**
     * Return the accessor type based on its index in [0..getNumberOfAccessorTypes())
     * Indexes are ordered narrower{@literal ->}wider / optimistic{@literal ->}pessimistic. Invalidations always
     * go to a type of higher index
     *
     * @param index accessor type index
     *
     * @return a type corresponding to the index.
     */

    public static Type getAccessorType(final int index) {
        return ACCESSOR_TYPES.get(index);
    }

    /**
     * Return the number of accessor types available.
     *
     * @return number of accessor types in system
     */
    public static int getNumberOfAccessorTypes() {
        return ACCESSOR_TYPES.size();
    }

    private static double parseRadix(final char chars[], final int start, final int length, final int radix) {
        int pos = 0;

        for (int i = start; i < length ; i++) {
            if (digit(chars[i], radix) == -1) {
                return Double.NaN;
            }
            pos++;
        }

        if (pos == 0) {
            return Double.NaN;
        }

        double value = 0.0;
        for (int i = start; i < start + pos; i++) {
            value *= radix;
            value += digit(chars[i], radix);
        }

        return value;
    }

    private static double toNumberGeneric(final Object obj) {
        if (obj == null) {
            return +0.0;
        }

        if (obj instanceof String) {
            return toNumber((String)obj);
        }

        if (obj instanceof ConsString) {
            return toNumber(obj.toString());
        }

        if (obj instanceof Boolean) {
            return toNumber((Boolean)obj);
        }

        if (obj instanceof ScriptObject) {
            return toNumber((ScriptObject)obj);
        }

        if (obj instanceof Undefined) {
            return Double.NaN;
        }

        return toNumber(toPrimitive(obj, Number.class));
    }

    private static Object invoke(final MethodHandle mh, final Object arg) {
        try {
            return mh.invoke(arg);
        } catch (final RuntimeException | Error e) {
            throw e;
        } catch (final Throwable t) {
            throw new RuntimeException(t);
        }
    }

    /**
     * Returns the boxed version of a primitive class
     * @param clazz the class
     * @return the boxed type of clazz, or unchanged if not primitive
     */
    public static Class<?> getBoxedClass(final Class<?> clazz) {
        if (clazz == int.class) {
            return Integer.class;
        } else if (clazz == long.class) {
            return Long.class;
        } else if (clazz == double.class) {
            return Double.class;
        }
        assert !clazz.isPrimitive();
        return clazz;
    }

    /**
     * Create a method handle constant of the correct primitive type
     * for a constant object
     * @param o object
     * @return constant function that returns object
     */
    public static MethodHandle unboxConstant(final Object o) {
        if (o != null) {
            if (o.getClass() == Integer.class) {
                return MH.constant(int.class, ((Integer)o));
            } else if (o.getClass() == Long.class) {
                return MH.constant(long.class, ((Long)o));
            } else if (o.getClass() == Double.class) {
                return MH.constant(double.class, ((Double)o));
            }
        }
        return MH.constant(Object.class, o);
    }

    /**
     * Get the unboxed (primitive) type for an object
     * @param o object
     * @return primitive type or Object.class if not primitive
     */
    public static Class<?> unboxedFieldType(final Object o) {
        if (o == null) {
            return Object.class;
        } else if (o.getClass() == Integer.class) {
            return int.class;
        } else if (o.getClass() == Long.class) {
            return long.class;
        } else if (o.getClass() == Double.class) {
            return double.class;
        } else {
            return Object.class;
        }
    }

    private static List<MethodHandle> toUnmodifiableList(final MethodHandle... methodHandles) {
        return Collections.unmodifiableList(Arrays.asList(methodHandles));
    }
}
