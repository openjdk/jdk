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
import static jdk.nashorn.internal.runtime.ECMAErrors.typeError;

import java.lang.invoke.MethodHandles;
import jdk.internal.dynalink.beans.StaticClass;
import jdk.nashorn.internal.codegen.CompilerConstants.Call;
import jdk.nashorn.internal.parser.Lexer;
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

    private static final MethodHandles.Lookup myLookup = MethodHandles.lookup();

    /** JavaScript compliant conversion function from Object to boolean */
    public static final Call TO_BOOLEAN = staticCall(myLookup, JSType.class, "toBoolean", boolean.class, Object.class);

    /** JavaScript compliant conversion function from number to boolean */
    public static final Call TO_BOOLEAN_D = staticCall(myLookup, JSType.class, "toBoolean", boolean.class, double.class);

    /** JavaScript compliant conversion function from Object to integer */
    public static final Call TO_INTEGER = staticCall(myLookup, JSType.class, "toInteger", int.class, Object.class);

    /** JavaScript compliant conversion function from Object to long */
    public static final Call TO_LONG = staticCall(myLookup, JSType.class, "toLong", long.class, Object.class);

    /** JavaScript compliant conversion function from Object to number */
    public static final Call TO_NUMBER = staticCall(myLookup, JSType.class, "toNumber", double.class, Object.class);

    /** JavaScript compliant conversion function from Object to int32 */
    public static final Call TO_INT32 = staticCall(myLookup, JSType.class, "toInt32", int.class, Object.class);

    /** JavaScript compliant conversion function from double to int32 */
    public static final Call TO_INT32_D = staticCall(myLookup, JSType.class, "toInt32", int.class, double.class);

    /** JavaScript compliant conversion function from Object to uint32 */
    public static final Call TO_UINT32 = staticCall(myLookup, JSType.class, "toUint32", long.class, Object.class);

    /** JavaScript compliant conversion function from number to uint32 */
    public static final Call TO_UINT32_D = staticCall(myLookup, JSType.class, "toUint32", long.class, double.class);

    /** JavaScript compliant conversion function from Object to int64 */
    public static final Call TO_INT64 = staticCall(myLookup, JSType.class, "toInt64", long.class, Object.class);

    /** JavaScript compliant conversion function from number to int64 */
    public static final Call TO_INT64_D = staticCall(myLookup, JSType.class, "toInt64", long.class, double.class);

    /** JavaScript compliant conversion function from Object to String */
    public static final Call TO_STRING = staticCall(myLookup, JSType.class, "toString", String.class, Object.class);

    /** JavaScript compliant conversion function from number to String */
    public static final Call TO_STRING_D = staticCall(myLookup, JSType.class, "toString", String.class, double.class);

    /** JavaScript compliant conversion function from Object to primitive */
    public static final Call TO_PRIMITIVE = staticCall(myLookup, JSType.class, "toPrimitive", Object.class,  Object.class);

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
            return (obj instanceof ScriptFunction) ? JSType.FUNCTION : JSType.OBJECT;
        }

        if (obj instanceof Boolean) {
            return JSType.BOOLEAN;
        }

        if (obj instanceof String || obj instanceof ConsString) {
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
     * Returns true if double number can be represented as an int
     *
     * @param number a double to inspect
     *
     * @return true for int representable doubles
     */
    public static boolean isRepresentableAsInt(final double number) {
        return (int)number == number;
    }

    /**
     * Returns true if double number can be represented as a long
     *
     * @param number a double to inspect
     * @return true for long representable doubles
     */
    public static boolean isRepresentableAsLong(final double number) {
        return (long)number == number;
    }

    /**
     * Get the smallest integer representation of a number. Returns an Integer
     * for something that is int representable, and Long for something that
     * is long representable. If the number needs to be a double, this is an
     * identity function
     *
     * @param number number to check
     *
     * @return Number instanceof the narrowest possible integer representation for number
     */
    public static Number narrowestIntegerRepresentation(final double number) {
        if (isRepresentableAsInt(number)) {
            return (int)number;
        } else if (isRepresentableAsLong(number)) {
            return (long)number;
        } else {
            return number;
        }
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
               obj instanceof String ||
               obj instanceof ConsString;
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
        if (!(obj instanceof ScriptObject)) {
            return obj;
        }

        final ScriptObject sobj   = (ScriptObject)obj;
        final Object       result = sobj.getDefaultValue(hint);

        if (!isPrimitive(result)) {
            throw typeError("bad.default.value", result.toString());
        }

        return result;
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

        if (obj instanceof String || obj instanceof ConsString) {
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
            sb.append(chars.charAt((int) (intPart % radix)));
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
        if (obj instanceof Number) {
            return ((Number)obj).doubleValue();
        }
        return toNumberGeneric(obj);
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
        } else {
            // Fast (no NumberFormatException) path to NaN for non-numeric strings. We allow those starting with "I" or
            // "N" to allow for parsing "NaN" and "Infinity" correctly.
            if ((f < '0' || f > '9') && f != '.' && f != 'I' && f != 'N') {
                return Double.NaN;
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
     * JavaScript compliant Object to long conversion. See ECMA 9.4 ToInteger
     *
     * <p>Note that this returns {@link java.lang.Long#MAX_VALUE} or {@link java.lang.Long#MIN_VALUE}
     * for double values that exceed the long range, including positive and negative Infinity. It is the
     * caller's responsibility to handle such values correctly.</p>
     *
     * @param obj  an object
     * @return a long
     */
    public static long toLong(final Object obj) {
        return (long)toNumber(obj);
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
     * JavaScript compliant long to int32 conversion
     *
     * @param num a long
     * @return an int32
     */
    public static int toInt32(final long num) {
        return (int)num;
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
     * JavaScript compliant Object to int64 conversion
     *
     * @param obj an object
     * @return an int64
     */
    public static long toInt64(final Object obj) {
        return toInt64(toNumber(obj));
    }

    /**
     * JavaScript compliant number to int64 conversion
     *
     * @param num a number
     * @return an int64
     */
    public static long toInt64(final double num) {
        if (Double.isInfinite(num)) {
            return 0L;
        }
        return (long)num;
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
        return ((int)num) & 0xffff;
    }

    /**
     * JavaScript compliant number to uint16 conversion
     *
     * @param num a number
     * @return a uint16
     */
    public static int toUint16(final double num) {
        return ((int)doubleToInt32(num)) & 0xffff;
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
        final double d = (num >= 0) ? Math.floor(num) : Math.ceil(num);
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
        return toScriptObject(Context.getGlobalTrusted(), obj);
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
    public static Object toScriptObject(final ScriptObject global, final Object obj) {
        if (nullOrUndefined(obj)) {
            throw typeError(global, "not.an.object", ScriptRuntime.safeToString(obj));
        }

        if (obj instanceof ScriptObject) {
            return obj;
        }

        return ((GlobalObject)global).wrapAsObject(obj);
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

        if (obj instanceof Number) {
            return toString(((Number)obj).doubleValue());
        }

        if (obj == ScriptRuntime.UNDEFINED) {
            return "undefined";
        }

        if (obj == null) {
            return "null";
        }

        if (obj instanceof ScriptObject) {
            if (safe) {
                final ScriptObject sobj = (ScriptObject)obj;
                final GlobalObject gobj = (GlobalObject)Context.getGlobalTrusted();
                return gobj.isError(sobj) ?
                    ECMAException.safeToString(sobj) :
                    sobj.safeToString();
            }

            return toString(toPrimitive(obj, String.class));
        }

        if (obj instanceof StaticClass) {
            return "[JavaClass " + ((StaticClass)obj).getRepresentedClass().getName() + "]";
        }

        return obj.toString();
    }

    // trim from left for JS whitespaces.
    static String trimLeft(final String str) {
        int start = 0;

        while (start < str.length() && Lexer.isJSWhitespace(str.charAt(start))) {
            start++;
        }

        return str.substring(start);
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
            return (Boolean)obj ? 1 : +0.0;
        }

        if (obj instanceof ScriptObject) {
            return toNumber(toPrimitive(obj, Number.class));
        }

        return Double.NaN;
    }

}
