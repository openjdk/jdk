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

package jdk.nashorn.internal.codegen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jdk.nashorn.internal.runtime.PropertyMap;

/**
 * Manages constants needed by code generation.  Objects are maintained in an
 * interning maps to remove duplicates.
 */
final class ConstantData {
    /** Constant table. */
    final List<Object> constants;

    /** Constant table string interning map. */
    final Map<String, Integer> stringMap;

    /** Constant table object interning map. */
    final Map<Object, Integer> objectMap;

    private static class ArrayWrapper {
        private final Object array;
        private final int    hashCode;

        public ArrayWrapper(final Object array) {
            this.array    = array;
            this.hashCode = calcHashCode();
        }

        /**
         * Calculate a shallow hashcode for the array.
         * @return Hashcode with elements factored in.
         */
        private int calcHashCode() {
            final Class<?> cls = array.getClass();

            if (!cls.getComponentType().isPrimitive()) {
                return Arrays.hashCode((Object[])array);
            } else if (cls == double[].class) {
                return Arrays.hashCode((double[])array);
            } if (cls == long[].class) {
                return Arrays.hashCode((long[])array);
            } if (cls == int[].class) {
                return Arrays.hashCode((int[])array);
            }

            throw new AssertionError("ConstantData doesn't support " + cls);
        }

        @Override
        public boolean equals(final Object other) {
            if (!(other instanceof ArrayWrapper)) {
                return false;
            }

            final Object otherArray = ((ArrayWrapper)other).array;

            if (array == otherArray) {
                return true;
            }

            final Class<?> cls = array.getClass();

            if (cls == otherArray.getClass()) {
                if (!cls.getComponentType().isPrimitive()) {
                    return Arrays.equals((Object[])array, (Object[])otherArray);
                } else if (cls == double[].class) {
                    return Arrays.equals((double[])array, (double[])otherArray);
                } else if (cls == long[].class) {
                    return Arrays.equals((long[])array, (long[])otherArray);
                } else if (cls == int[].class) {
                    return Arrays.equals((int[])array, (int[])otherArray);
                }
            }

            return false;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }

    /**
     * {@link PropertyMap} wrapper class that provides implementations for the {@code hashCode} and {@code equals}
     * methods that are based on the map layout. {@code PropertyMap} itself inherits the identity based implementations
     * from {@code java.lang.Object}.
     */
    private static class PropertyMapWrapper {
        private final PropertyMap propertyMap;
        private final int hashCode;

        public PropertyMapWrapper(final PropertyMap map) {
            this.hashCode = Arrays.hashCode(map.getProperties());
            this.propertyMap = map;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public boolean equals(final Object other) {
            return other instanceof PropertyMapWrapper &&
                    Arrays.equals(propertyMap.getProperties(), ((PropertyMapWrapper) other).propertyMap.getProperties());
        }
    }

    /**
     * Constructor
     */
    ConstantData() {
        this.constants = new ArrayList<>();
        this.stringMap = new HashMap<>();
        this.objectMap = new HashMap<>();
    }

    /**
     * Add a string to the constant data
     *
     * @param string the string to add
     * @return the index in the constant pool that the string was given
     */
    public int add(final String string) {
        final Integer value = stringMap.get(string);

        if (value != null) {
            return value.intValue();
        }

        constants.add(string);
        final int index = constants.size() - 1;
        stringMap.put(string, index);

        return index;
    }

    /**
     * Add an object to the constant data
     *
     * @param object the string to add
     * @return the index in the constant pool that the object was given
     */
    public int add(final Object object) {
        assert object != null;
        final Object  entry;
        if (object.getClass().isArray()) {
            entry = new ArrayWrapper(object);
        } else if (object instanceof PropertyMap) {
            entry = new PropertyMapWrapper((PropertyMap) object);
        } else {
            entry = object;
        }
        final Integer value = objectMap.get(entry);

        if (value != null) {
            return value.intValue();
        }

        constants.add(object);
        final int index = constants.size() - 1;
        objectMap.put(entry, index);

        return index;
    }

    Object[] toArray() {
        return constants.toArray();
    }
}
