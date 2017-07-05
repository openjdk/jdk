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

package jdk.nashorn.internal.runtime.linker;

import static jdk.nashorn.internal.lookup.Lookup.MH;
import static jdk.nashorn.internal.runtime.ECMAErrors.typeError;
import static jdk.nashorn.internal.runtime.JSType.isString;
import static jdk.nashorn.internal.runtime.ScriptRuntime.UNDEFINED;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Map;
import jdk.internal.dynalink.support.TypeUtilities;
import jdk.nashorn.internal.runtime.ConsString;
import jdk.nashorn.internal.runtime.JSType;
import jdk.nashorn.internal.runtime.ScriptObject;

/**
 * Utility class shared by {@code NashornLinker} and {@code NashornPrimitiveLinker} for converting JS values to Java
 * types.
 */
final class JavaArgumentConverters {

    private static final MethodHandle TO_BOOLEAN        = findOwnMH("toBoolean", Boolean.class, Object.class);
    private static final MethodHandle TO_STRING         = findOwnMH("toString", String.class, Object.class);
    private static final MethodHandle TO_DOUBLE         = findOwnMH("toDouble", Double.class, Object.class);
    private static final MethodHandle TO_NUMBER         = findOwnMH("toNumber", Number.class, Object.class);
    private static final MethodHandle TO_LONG           = findOwnMH("toLong", Long.class, Object.class);
    private static final MethodHandle TO_LONG_PRIMITIVE = findOwnMH("toLongPrimitive", long.class, Object.class);
    private static final MethodHandle TO_CHAR           = findOwnMH("toChar", Character.class, Object.class);
    private static final MethodHandle TO_CHAR_PRIMITIVE = findOwnMH("toCharPrimitive", char.class, Object.class);

    private JavaArgumentConverters() {
    }

    static MethodHandle getConverter(final Class<?> targetType) {
        return CONVERTERS.get(targetType);
    }

    @SuppressWarnings("unused")
    private static Boolean toBoolean(final Object obj) {
        if (obj instanceof Boolean) {
            return (Boolean) obj;
        }

        if (obj == null) {
            // NOTE: FindBugs complains here about the NP_BOOLEAN_RETURN_NULL pattern: we're returning null from a
            // method that has a return type of Boolean, as it is worried about a NullPointerException if there's a
            // conversion to a primitive boolean. We know what we're doing, though. We're using a separate method when
            // we're converting Object to a primitive boolean - see how the CONVERTERS map is populated. We specifically
            // want to have null and Undefined to be converted to a (Boolean)null when being passed to a Java method
            // that expects a Boolean argument.
            // TODO: if/when we're allowed to use FindBugs at build time, we can use annotations to disable this warning
            return null;
        }

        if (obj == UNDEFINED) {
            // NOTE: same reasoning for FindBugs NP_BOOLEAN_RETURN_NULL warning as in the preceding comment.
            return null;
        }

        if (obj instanceof Number) {
            final double num = ((Number) obj).doubleValue();
            return num != 0 && !Double.isNaN(num);
        }

        if (isString(obj)) {
            return ((CharSequence) obj).length() > 0;
        }

        if (obj instanceof ScriptObject) {
            return true;
        }

        throw assertUnexpectedType(obj);
    }

    private static Character toChar(final Object o) {
        if (o == null) {
            return null;
        }

        if (o instanceof Number) {
            final int ival = ((Number)o).intValue();
            if (ival >= Character.MIN_VALUE && ival <= Character.MAX_VALUE) {
                return Character.valueOf((char) ival);
            }

            throw typeError("cant.convert.number.to.char");
        }

        final String s = toString(o);
        if (s == null) {
            return null;
        }

        if (s.length() != 1) {
            throw typeError("cant.convert.string.to.char");
        }

        return s.charAt(0);
    }

    static char toCharPrimitive(final Object obj0) {
        final Character c = toChar(obj0);
        return c == null ? (char)0 : c;
    }

    // Almost identical to ScriptRuntime.toString, but returns null for null instead of the string "null".
    static String toString(final Object obj) {
        return obj == null ? null : JSType.toString(obj);
    }

    @SuppressWarnings("unused")
    private static Double toDouble(final Object obj0) {
        // TODO - Order tests for performance.
        for (Object obj = obj0; ;) {
            if (obj == null) {
                return null;
            } else if (obj instanceof Double) {
                return (Double) obj;
            } else if (obj instanceof Number) {
                return ((Number)obj).doubleValue();
            } else if (obj instanceof String) {
                return JSType.toNumber((String) obj);
            } else if (obj instanceof ConsString) {
                return JSType.toNumber(obj.toString());
            } else if (obj instanceof Boolean) {
                return (Boolean) obj ? 1 : +0.0;
            } else if (obj instanceof ScriptObject) {
                obj = JSType.toPrimitive(obj, Number.class);
                continue;
            } else if (obj == UNDEFINED) {
                return Double.NaN;
            }
            throw assertUnexpectedType(obj);
        }
    }

    @SuppressWarnings("unused")
    private static Number toNumber(final Object obj0) {
        // TODO - Order tests for performance.
        for (Object obj = obj0; ;) {
            if (obj == null) {
                return null;
            } else if (obj instanceof Number) {
                return (Number) obj;
            } else if (obj instanceof String) {
                return JSType.toNumber((String) obj);
            } else if (obj instanceof ConsString) {
                return JSType.toNumber(obj.toString());
            } else if (obj instanceof Boolean) {
                return (Boolean) obj ? 1 : +0.0;
            } else if (obj instanceof ScriptObject) {
                obj = JSType.toPrimitive(obj, Number.class);
                continue;
            } else if (obj == UNDEFINED) {
                return Double.NaN;
            }
            throw assertUnexpectedType(obj);
        }
    }

    private static Long toLong(final Object obj0) {
        // TODO - Order tests for performance.
        for (Object obj = obj0; ;) {
            if (obj == null) {
                return null;
            } else if (obj instanceof Long) {
                return (Long) obj;
            } else if (obj instanceof Integer) {
                return ((Integer)obj).longValue();
            } else if (obj instanceof Double) {
                final Double d = (Double)obj;
                if(Double.isInfinite(d.doubleValue())) {
                    return 0L;
                }
                return d.longValue();
            } else if (obj instanceof Float) {
                final Float f = (Float)obj;
                if(Float.isInfinite(f.floatValue())) {
                    return 0L;
                }
                return f.longValue();
            } else if (obj instanceof Number) {
                return ((Number)obj).longValue();
            } else if (isString(obj)) {
                return JSType.toLong(obj);
            } else if (obj instanceof Boolean) {
                return (Boolean)obj ? 1L : 0L;
            } else if (obj instanceof ScriptObject) {
                obj = JSType.toPrimitive(obj, Number.class);
                continue;
            } else if (obj == UNDEFINED) {
                return null; // null or 0L?
            }
            throw assertUnexpectedType(obj);
        }
    }

    private static AssertionError assertUnexpectedType(final Object obj) {
        return new AssertionError("Unexpected type" + obj.getClass().getName() + ". Guards should have prevented this");
    }

    @SuppressWarnings("unused")
    private static long toLongPrimitive(final Object obj0) {
        final Long l = toLong(obj0);
        return l == null ? 0L : l;
    }

    private static MethodHandle findOwnMH(final String name, final Class<?> rtype, final Class<?>... types) {
        return MH.findStatic(MethodHandles.lookup(), JavaArgumentConverters.class, name, MH.type(rtype, types));
    }

    private static final Map<Class<?>, MethodHandle> CONVERTERS = new HashMap<>();

    static {
        CONVERTERS.put(Number.class, TO_NUMBER);
        CONVERTERS.put(String.class, TO_STRING);

        CONVERTERS.put(boolean.class, JSType.TO_BOOLEAN.methodHandle());
        CONVERTERS.put(Boolean.class, TO_BOOLEAN);

        CONVERTERS.put(char.class, TO_CHAR_PRIMITIVE);
        CONVERTERS.put(Character.class, TO_CHAR);

        CONVERTERS.put(double.class, JSType.TO_NUMBER.methodHandle());
        CONVERTERS.put(Double.class, TO_DOUBLE);

        CONVERTERS.put(long.class, TO_LONG_PRIMITIVE);
        CONVERTERS.put(Long.class, TO_LONG);

        putLongConverter(Byte.class);
        putLongConverter(Short.class);
        putLongConverter(Integer.class);
        putDoubleConverter(Float.class);

    }

    private static void putDoubleConverter(final Class<?> targetType) {
        final Class<?> primitive = TypeUtilities.getPrimitiveType(targetType);
        CONVERTERS.put(primitive,  MH.explicitCastArguments(JSType.TO_NUMBER.methodHandle(), JSType.TO_NUMBER.methodHandle().type().changeReturnType(primitive)));
        CONVERTERS.put(targetType, MH.filterReturnValue(TO_DOUBLE, findOwnMH(primitive.getName() + "Value", targetType, Double.class)));
    }

    private static void putLongConverter(final Class<?> targetType) {
        final Class<?> primitive = TypeUtilities.getPrimitiveType(targetType);
        CONVERTERS.put(primitive,  MH.explicitCastArguments(TO_LONG_PRIMITIVE, TO_LONG_PRIMITIVE.type().changeReturnType(primitive)));
        CONVERTERS.put(targetType, MH.filterReturnValue(TO_LONG, findOwnMH(primitive.getName() + "Value", targetType, Long.class)));
    }

    @SuppressWarnings("unused")
    private static Byte byteValue(final Long l) {
        return l == null ? null : l.byteValue();
    }

    @SuppressWarnings("unused")
    private static Short shortValue(final Long l) {
        return l == null ? null : l.shortValue();
    }

    @SuppressWarnings("unused")
    private static Integer intValue(final Long l) {
        return l == null ? null : l.intValue();
    }

    @SuppressWarnings("unused")
    private static Float floatValue(final Double d) {
        return d == null ? null : d.floatValue();
    }

}
