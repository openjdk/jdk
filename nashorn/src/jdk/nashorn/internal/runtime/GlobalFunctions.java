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

import static jdk.nashorn.internal.runtime.JSType.digit;
import static jdk.nashorn.internal.lookup.Lookup.MH;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.Locale;

/**
 * Utilities used by Global class.
 *
 * These are actual implementation methods for functions exposed by global
 * scope. The code lives here to share the code across the contexts.
 */
public final class GlobalFunctions {

    /** Methodhandle to implementation of ECMA 15.1.2.2, parseInt */
    public static final MethodHandle PARSEINT = findOwnMH("parseInt",   double.class, Object.class, Object.class, Object.class);

    /** Methodhandle to implementation of ECMA 15.1.2.3, parseFloat */
    public static final MethodHandle PARSEFLOAT = findOwnMH("parseFloat", double.class, Object.class, Object.class);

    /** Methodhandle to implementation of ECMA 15.1.2.4, isNaN */
    public static final MethodHandle IS_NAN = findOwnMH("isNaN",      boolean.class, Object.class, Object.class);

    /** Methodhandle to implementation of ECMA 15.1.2.5, isFinite */
    public static final MethodHandle IS_FINITE = findOwnMH("isFinite",   boolean.class, Object.class, Object.class);

    /** Methodhandle to implementation of ECMA 15.1.3.3, encodeURI */
    public static final MethodHandle ENCODE_URI = findOwnMH("encodeURI",  Object.class, Object.class, Object.class);

    /** Methodhandle to implementation of ECMA 15.1.3.4, encodeURIComponent */
    public static final MethodHandle ENCODE_URICOMPONENT = findOwnMH("encodeURIComponent", Object.class, Object.class, Object.class);

    /** Methodhandle to implementation of ECMA 15.1.3.1, decodeURI */
    public static final MethodHandle DECODE_URI = findOwnMH("decodeURI", Object.class, Object.class, Object.class);

    /** Methodhandle to implementation of ECMA 15.1.3.2, decodeURIComponent */
    public static final MethodHandle DECODE_URICOMPONENT = findOwnMH("decodeURIComponent", Object.class, Object.class, Object.class);

    /** Methodhandle to implementation of ECMA B.2.1, escape */
    public static final MethodHandle ESCAPE = findOwnMH("escape",    String.class, Object.class, Object.class);

    /** Methodhandle to implementation of ECMA B.2.2, unescape */
    public static final MethodHandle UNESCAPE = findOwnMH("unescape",  String.class, Object.class, Object.class);

    /** Methodhandle to implementation of ECMA 15.3.4, "anonymous" - Properties of the Function Prototype Object. */
    public static final MethodHandle ANONYMOUS = findOwnMH("anonymous", Object.class, Object.class);

    private static final String UNESCAPED = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789@*_+-./";

    private GlobalFunctions() {
    }

    /**
     * ECMA 15.1.2.2 parseInt implementation
     *
     * TODO: specialize
     *
     * @param self   self reference
     * @param string string to parse
     * @param rad    radix
     *
     * @return numeric type representing string contents as an int (TODO: specialize for int case)
     */
    //TODO specialize
    public static double parseInt(final Object self, final Object string, final Object rad) {
        final String str    = JSType.trimLeft(JSType.toString(string));
        final int    length = str.length();

        // empty string is not valid
        if (length == 0) {
            return Double.NaN;
        }

        boolean negative = false;
        int idx = 0;

        // checking for the sign character
        final char firstChar = str.charAt(idx);
        if (firstChar < '0') {
            // Possible leading "+" or "-"
            if (firstChar == '-') {
                negative = true;
            } else if (firstChar != '+') {
                return Double.NaN;
            }
            // skip the sign character
            idx++;
        }

        boolean stripPrefix = true;
        int     radix = JSType.toInt32(rad);

        if (radix != 0) {
            if (radix < 2 || radix > 36) {
                return Double.NaN;
            }
            if (radix != 16) {
                stripPrefix = false;
            }
        } else {
            // default radix
            radix = 10;
        }
        // strip "0x" or "0X" and treat radix as 16
        if (stripPrefix && ((idx + 1) < length)) {
            final char c1 = str.charAt(idx);
            final char c2 = str.charAt(idx + 1);
            if (c1 == '0' && (c2 == 'x' || c2 == 'X')) {
                radix = 16;
                // skip "0x" or "0X"
                idx += 2;
            }
        }

        double result = 0.0;
        int digit;
        // we should see atleast one valid digit
        boolean entered = false;
        while (idx < length) {
            digit = digit(str.charAt(idx++), radix, true);
            if (digit < 0) {
                break;
            }
            // we have seen atleast one valid digit in the specified radix
            entered = true;
            result *= radix;
            result += digit;
        }

        return entered ? (negative ? -result : result) : Double.NaN;
    }

    /**
     * ECMA 15.1.2.3 parseFloat implementation
     *
     * @param self   self reference
     * @param string string to parse
     *
     * @return numeric type representing string contents
     */
    public static double parseFloat(final Object self, final Object string) {
        final String str    = JSType.trimLeft(JSType.toString(string));
        final int    length = str.length();

        // empty string is not valid
        if (length == 0) {
            return Double.NaN;
        }

        int     start    = 0;
        boolean negative = false;
        char    ch       = str.charAt(0);

        if (ch == '-') {
            start++;
            negative = true;
        } else if (ch == '+') {
            start++;
        } else if (ch == 'N') {
            if (str.startsWith("NaN")) {
                return Double.NaN;
            }
        }

        if (start == length) {
            // just the sign character
            return Double.NaN;
        }

        ch = str.charAt(start);
        if (ch == 'I') {
            if (str.substring(start).startsWith("Infinity")) {
                return negative? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
            }
        }

        boolean dotSeen    = false;
        boolean exponentOk = false;
        int exponentOffset = -1;
        int end;

loop:
        for (end = start; end < length; end++) {
            ch = str.charAt(end);

            switch (ch) {
            case '.':
                // dot allowed only once
                if (dotSeen) {
                    break loop;
                }
                dotSeen = true;
                break;

            case 'e':
            case 'E':
                // 'e'/'E' allow only once
                if (exponentOffset != -1) {
                    break loop;
                }
                exponentOffset = end;
                break;

            case '+':
            case '-':
                // Sign of the exponent. But allowed only if the
                // previous char in the string was 'e' or 'E'.
                if (exponentOffset != end - 1) {
                    break loop;
                }
                break;

            case '0':
            case '1':
            case '2':
            case '3':
            case '4':
            case '5':
            case '6':
            case '7':
            case '8':
            case '9':
                if (exponentOffset != -1) {
                    // seeing digit after 'e' or 'E'
                    exponentOk = true;
                }
                break;

            default: // ignore garbage at the end
                break loop;
            }
        }

        // ignore 'e'/'E' followed by '+/-' if not real exponent found
        if (exponentOffset != -1 && !exponentOk) {
            end = exponentOffset;
        }

        if (start == end) {
            return Double.NaN;
        }

        try {
            final double result = Double.valueOf(str.substring(start, end));
            return negative ? -result : result;
        } catch (final NumberFormatException e) {
            return Double.NaN;
        }
    }

    /**
     * ECMA 15.1.2.4, isNaN implementation
     *
     * @param self    self reference
     * @param number  number to check
     *
     * @return true if number is NaN
     */
    public static boolean isNaN(final Object self, final Object number) {
        return Double.isNaN(JSType.toNumber(number));
    }

    /**
     * ECMA 15.1.2.5, isFinite implementation
     *
     * @param self   self reference
     * @param number number to check
     *
     * @return true if number is infinite
     */
    public static boolean isFinite(final Object self, final Object number) {
        final double value = JSType.toNumber(number);
        return ! (Double.isInfinite(value) || Double.isNaN(value));
    }


    /**
     * ECMA 15.1.3.3, encodeURI implementation
     *
     * @param self  self reference
     * @param uri   URI to encode
     *
     * @return encoded URI
     */
    public static Object encodeURI(final Object self, final Object uri) {
        return URIUtils.encodeURI(self, JSType.toString(uri));
    }

    /**
     * ECMA 15.1.3.4, encodeURIComponent implementation
     *
     * @param self  self reference
     * @param uri   URI component to encode
     *
     * @return encoded URIComponent
     */
    public static Object encodeURIComponent(final Object self, final Object uri) {
        return URIUtils.encodeURIComponent(self, JSType.toString(uri));
    }

    /**
     * ECMA 15.1.3.1, decodeURI implementation
     *
     * @param self  self reference
     * @param uri   URI to decode
     *
     * @return decoded URI
     */
    public static Object decodeURI(final Object self, final Object uri) {
        return URIUtils.decodeURI(self, JSType.toString(uri));
    }

    /**
     * ECMA 15.1.3.2, decodeURIComponent implementation
     *
     * @param self  self reference
     * @param uri   URI component to encode
     *
     * @return decoded URI
     */
    public static Object decodeURIComponent(final Object self, final Object uri) {
        return URIUtils.decodeURIComponent(self, JSType.toString(uri));
    }

    /**
     * ECMA B.2.1, escape implementation
     *
     * @param self    self reference
     * @param string  string to escape
     *
     * @return escaped string
     */
    public static String escape(final Object self, final Object string) {
        final String str = JSType.toString(string);
        final int length = str.length();

        if (length == 0) {
            return str;
        }

        final StringBuilder sb = new StringBuilder();
        for (int k = 0; k < length; k++) {
            final char ch = str.charAt(k);
            if (UNESCAPED.indexOf(ch) != -1) {
                sb.append(ch);
            } else if (ch < 256) {
                sb.append('%');
                if (ch < 16) {
                    sb.append('0');
                }
                sb.append(Integer.toHexString(ch).toUpperCase(Locale.ENGLISH));
            } else {
                sb.append("%u");
                if (ch < 4096) {
                    sb.append('0');
                }
                sb.append(Integer.toHexString(ch).toUpperCase(Locale.ENGLISH));
            }
        }

        return sb.toString();
    }

    /**
     * ECMA B.2.2, unescape implementation
     *
     * @param self    self reference
     * @param string  string to unescape
     *
     * @return unescaped string
     */
    public static String unescape(final Object self, final Object string) {
        final String str    = JSType.toString(string);
        final int    length = str.length();

        if (length == 0) {
            return str;
        }

        final StringBuilder sb = new StringBuilder();
        for (int k = 0; k < length; k++) {
            char ch = str.charAt(k);
            if (ch != '%') {
                sb.append(ch);
            } else {
                if (k < (length - 5)) {
                   if (str.charAt(k + 1) == 'u') {
                       try {
                           ch = (char) Integer.parseInt(str.substring(k + 2, k + 6), 16);
                           sb.append(ch);
                           k += 5;
                           continue;
                       } catch (final NumberFormatException e) {
                           //ignored
                       }
                   }
                }

                if (k < (length - 2)) {
                    try {
                        ch = (char) Integer.parseInt(str.substring(k + 1, k + 3), 16);
                        sb.append(ch);
                        k += 2;
                        continue;
                    } catch (final NumberFormatException e) {
                        //ignored
                    }
                }

                // everything fails
                sb.append(ch);
            }
        }

        return sb.toString();
    }


    /**
     * ECMA 15.3.4 Properties of the Function Prototype Object.
     * The Function prototype object is itself a Function object
     * (its [[Class]] is "Function") that, when invoked, accepts
     * any arguments and returns undefined. This method is used to
     * implement that anonymous function.
     *
     * @param self  self reference
     *
     * @return undefined
     */
    public static Object anonymous(final Object self) {
        return ScriptRuntime.UNDEFINED;
    }

    private static MethodHandle findOwnMH(final String name, final Class<?> rtype, final Class<?>... types) {
        return MH.findStatic(MethodHandles.lookup(), GlobalFunctions.class, name, MH.type(rtype, types));
    }

}
