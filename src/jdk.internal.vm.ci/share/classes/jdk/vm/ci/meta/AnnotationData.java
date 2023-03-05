/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package jdk.vm.ci.meta;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.util.Arrays;

/**
 * Represents an annotation where {@link Class} values are represented with {@link JavaType}, enum
 * values are represented with {@link EnumData} and error values are represented with
 * {@link StringBuilder}.
 *
 * This is in contrast to the standard annotation API based on {@link Annotation}. Use of
 * {@link AnnotationData} allows annotations to be queried without the JVMCI runtime having to
 * support dynamic loading of arbitrary {@link Annotation} subclasses. Such support is impossible in
 * a closed world, ahead-of-time compiled environment such as libgraal.
 */
public final class AnnotationData {

    // Implementation note: The functionality for equals, hashCode and toString
    // is largely copied from sun.reflect.annotation.AnnotationInvocationHandler
    // so that AnnotationData behaves mostly like java.lang.annotation.Annotation.

    private final JavaType type;
    private final String[] names;
    private final Object[] values;

    /**
     * Gets the entry in {@code annotations} whose {@linkplain #getType() type} equals
     * {@code annotationType}.
     *
     * @return {@code null} if there is no match
     */
    public static AnnotationData getAnnotation(AnnotationData[] annotations, JavaType annotationType) {
        for (AnnotationData a : annotations) {
            if (a.getType().equals(annotationType)) {
                return a;
            }
        }
        return null;
    }

    /**
     * Creates an annotation.
     *
     * @param type the annotation interface of this annotation, represented as a {@link JavaType}
     * @param values the values of this annotation's element values. There is no distinction between
     *            values explicitly present in the annotation and those derived from an element's
     *            default value.
     */
    public AnnotationData(JavaType type, String[] names, Object[] values) {
        assert names.length == values.length;
        this.type = type;
        this.names = names;
        this.values = values;
    }

    /**
     * @return the annotation interface of this annotation, represented as a {@link JavaType}
     */
    public JavaType getType() {
        return type;
    }

    private Object lookup(String name) {
        for (int i = 0; i < names.length; i++) {
            if (names[i].equals(name)) {
                return values[i];
            }
        }
        return null;
    }

    /**
     * Determines if this annotation has an element named {@code name}.
     */
    public boolean has(String name) {
        return lookup(name) != null;
    }

    /**
     * Gets the annotation element denoted by {@code name}. If {@code name} denotes an enum
     * constant, the name of the enumm constant is returned as a string. To get an {@link EnumData}
     * value for an enum constant, call {@link #getEnum(String)} instead.
     *
     * If the returned value is an array, the caller of this method is free to modify it; it will
     * have no effect on arrays returned to other callers.
     *
     * @param <V> the type of the element
     * @return the annotation element denoted by {@code name}
     * @throws ClassCastException if the element is not of type {@code V}
     * @throws IllegalArgumentException if this annotation {@link #has} no element named
     *             {@code name} or if there was an error parsing or creating the element value
     */
    @SuppressWarnings("unchecked")
    public <V> V get(String name) {
        Object val = lookup(name);
        if (val == null) {
            throw new IllegalArgumentException("no element named " + name);
        }
        Class<? extends Object> valClass = val.getClass();
        if (valClass == StringBuilder.class) {
            throw new IllegalArgumentException(val.toString());
        }
        if (valClass.isArray() && Array.getLength(val) != 0) {
            val = cloneArray(val);
        }
        return (V) val;
    }

    private static Object cloneArray(Object array) {
        Class<?> type = array.getClass();

        if (type == byte[].class) {
            byte[] byteArray = (byte[]) array;
            return byteArray.clone();
        }
        if (type == char[].class) {
            char[] charArray = (char[]) array;
            return charArray.clone();
        }
        if (type == double[].class) {
            double[] doubleArray = (double[]) array;
            return doubleArray.clone();
        }
        if (type == float[].class) {
            float[] floatArray = (float[]) array;
            return floatArray.clone();
        }
        if (type == int[].class) {
            int[] intArray = (int[]) array;
            return intArray.clone();
        }
        if (type == long[].class) {
            long[] longArray = (long[]) array;
            return longArray.clone();
        }
        if (type == short[].class) {
            short[] shortArray = (short[]) array;
            return shortArray.clone();
        }
        if (type == boolean[].class) {
            boolean[] booleanArray = (boolean[]) array;
            return booleanArray.clone();
        }

        Object[] objectArray = (Object[]) array;
        return objectArray.clone();
    }

    /**
     * Gets the boolean element denoted by {@code name}.
     *
     * @see #get(String)
     */
    public boolean getBoolean(String name) {
        return (Boolean) get(name);
    }

    /**
     * Gets the boolean element denoted by {@code name}.
     *
     * @see #get(String)
     */
    public byte getByte(String name) {
        return (Byte) get(name);
    }

    /**
     * Gets the char element denoted by {@code name}.
     *
     * @see #get(String)
     */
    public char getChar(String name) {
        return (Character) get(name);
    }

    /**
     * Gets the short element denoted by {@code name}.
     *
     * @see #get(String)
     */
    public short getShort(String name) {
        return (Short) get(name);
    }

    /**
     * Gets the int element denoted by {@code name}.
     *
     * @see #get(String)
     */
    public int getInt(String name) {
        return (Integer) get(name);
    }

    /**
     * Gets the float element denoted by {@code name}.
     *
     * @see #get(String)
     */
    public float getFloat(String name) {
        return (Float) get(name);
    }

    /**
     * Gets the long element denoted by {@code name}.
     *
     * @see #get(String)
     */
    public long getLong(String name) {
        return (Long) get(name);
    }

    /**
     * Gets the double element denoted by {@code name}.
     *
     * @see #get(String)
     */
    public double getDouble(String name) {
        return (Double) get(name);
    }

    /**
     * Gets the String element denoted by {@code name}.
     *
     * @see #get(String)
     */
    public String getString(String name) {
        return (String) get(name);
    }

    /**
     * Gets the Class element denoted by {@code name}.
     *
     * @see #get(String)
     */
    public JavaType getClass(String name) {
        return (JavaType) get(name);
    }

    /**
     * Gets an {@link EnumData} value for the enum annotation element denoted by {@code name}.
     *
     * @see #get(String)
     */
    public EnumData getEnum(String name) {
        return (EnumData) get(name);
    }

    /**
     * Gets an {@link AnnotationData} value for the sub-annotation element denoted by {@code name}.
     *
     * @see #get(String)
     */
    public AnnotationData getAnnotation(String name) {
        return (AnnotationData) get(name);
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder(128);
        result.append('@');
        result.append(type.toClassName().replace('$', '.'));
        result.append('(');
        boolean firstMember = true;
        boolean loneValue = names.length == 1;
        for (int i = 0; i < names.length; i++) {
            if (firstMember) {
                firstMember = false;
            } else {
                result.append(", ");
            }

            String key = names[i];
            if (!loneValue || !"value".equals(key)) {
                result.append(key);
                result.append('=');
            }
            loneValue = false;
            Object value = values[i];
            if (!value.getClass().isArray()) {
                result.append(valueToString(value));
            } else {
                int len = Array.getLength(value);
                result.append('{');
                for (int j = 0; j < len; j++) {
                    if (j != 0) {
                        result.append(", ");
                    }
                    result.append(valueToString(Array.get(value, j)));
                }
                result.append('}');
            }

        }
        result.append(')');
        return result.toString();
    }

    private static String toSourceString(byte b) {
        return String.format("(byte)0x%02x", b);
    }

    private static String toSourceString(long ell) {
        return String.valueOf(ell) + "L";
    }

    private static String toSourceString(float f) {
        if (Float.isFinite(f)) {
            return Float.toString(f) + "f";
        } else {
            if (Float.isInfinite(f)) {
                return (f < 0.0f) ? "-1.0f/0.0f" : "1.0f/0.0f";
            } else {
                return "0.0f/0.0f";
            }
        }
    }

    private static String toSourceString(double d) {
        if (Double.isFinite(d)) {
            return Double.toString(d);
        } else {
            if (Double.isInfinite(d)) {
                return (d < 0.0f) ? "-1.0/0.0" : "1.0/0.0";
            } else {
                return "0.0/0.0";
            }
        }
    }

    private static String toSourceString(char c) {
        StringBuilder sb = new StringBuilder(4);
        sb.append('\'');
        sb.append(quote(c));
        return sb.append('\'').toString();
    }

    /**
     * Escapes a character if it has an escape sequence or is non-printable ASCII. Leaves non-ASCII
     * characters alone.
     */
    private static String quote(char ch) {
        // @formatter:off
        switch (ch) {
        case '\b':  return "\\b";
        case '\f':  return "\\f";
        case '\n':  return "\\n";
        case '\r':  return "\\r";
        case '\t':  return "\\t";
        case '\'':  return "\\'";
        case '\"':  return "\\\"";
        case '\\':  return "\\\\";
        default:
            return (isPrintableAscii(ch))
                ? String.valueOf(ch)
                : String.format("\\u%04x", (int) ch);
        }
        // @formatter:on
    }

    /**
     * Is a character printable ASCII?
     */
    private static boolean isPrintableAscii(char ch) {
        return ch >= ' ' && ch <= '~';
    }

    private static String valueToString(Object value) {
        if (value instanceof JavaType) {
            return ((JavaType) value).toClassName().replace('$', '.') + ".class";
        } else if (value instanceof Byte) {
            return toSourceString((byte) value);
        } else if (value instanceof Long) {
            return toSourceString((long) value);
        } else if (value instanceof Float) {
            return toSourceString((float) value);
        } else if (value instanceof Double) {
            return toSourceString((double) value);
        } else if (value instanceof String) {
            return "\"" + value + "\"";
        } else if (value instanceof Character) {
            return toSourceString((char) value);
        }
        return value.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof AnnotationData) {
            AnnotationData that = (AnnotationData) obj;
            if (this.type.equals(that.type) &&
                            Arrays.equals(this.names, that.names) && this.values.length == that.values.length) {
                for (int i = 0; i < values.length; i++) {
                    if (!memberValueEquals(this.values[i], that.values[i])) {
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }

    private static boolean memberValueEquals(Object v1, Object v2) {
        Class<?> type = v1.getClass();

        if (!type.isArray()) {
            return v1.equals(v2);
        }

        if (v1 instanceof Object[] && v2 instanceof Object[]) {
            return Arrays.equals((Object[]) v1, (Object[]) v2);
        }

        // Deal with array of primitives
        if (type == byte[].class) {
            return Arrays.equals((byte[]) v1, (byte[]) v2);
        }
        if (type == char[].class) {
            return Arrays.equals((char[]) v1, (char[]) v2);
        }
        if (type == double[].class) {
            return Arrays.equals((double[]) v1, (double[]) v2);
        }
        if (type == float[].class) {
            return Arrays.equals((float[]) v1, (float[]) v2);
        }
        if (type == int[].class) {
            return Arrays.equals((int[]) v1, (int[]) v2);
        }
        if (type == long[].class) {
            return Arrays.equals((long[]) v1, (long[]) v2);
        }
        if (type == short[].class) {
            return Arrays.equals((short[]) v1, (short[]) v2);
        }
        assert type == boolean[].class;
        return Arrays.equals((boolean[]) v1, (boolean[]) v2);
    }

    @Override
    public int hashCode() {
        int result = type.hashCode();
        for (int i = 0; i < names.length; i++) {
            result += (127 * names[i].hashCode()) ^
                            valueHashCode(values[i]);
        }
        return result;
    }

    private static int valueHashCode(Object value) {
        Class<?> type = value.getClass();
        if (!type.isArray()) {
            return value.hashCode();
        }
        if (type == byte[].class) {
            return Arrays.hashCode((byte[]) value);
        }
        if (type == char[].class) {
            return Arrays.hashCode((char[]) value);
        }
        if (type == double[].class) {
            return Arrays.hashCode((double[]) value);
        }
        if (type == float[].class) {
            return Arrays.hashCode((float[]) value);
        }
        if (type == int[].class) {
            return Arrays.hashCode((int[]) value);
        }
        if (type == long[].class) {
            return Arrays.hashCode((long[]) value);
        }
        if (type == short[].class) {
            return Arrays.hashCode((short[]) value);
        }
        if (type == boolean[].class) {
            return Arrays.hashCode((boolean[]) value);
        }
        return Arrays.hashCode((Object[]) value);
    }
}
