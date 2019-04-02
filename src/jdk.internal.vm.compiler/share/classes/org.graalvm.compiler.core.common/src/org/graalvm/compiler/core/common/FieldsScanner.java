/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.core.common;

import static org.graalvm.compiler.serviceprovider.GraalUnsafeAccess.getUnsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;

import sun.misc.Unsafe;

/**
 * Scans the fields in a class hierarchy.
 */
public class FieldsScanner {

    /**
     * Determines the offset (in bytes) of a field.
     */
    public interface CalcOffset {

        long getOffset(Field field);
    }

    /**
     * Determines the offset (in bytes) of a field using {@link Unsafe#objectFieldOffset(Field)}.
     */
    public static class DefaultCalcOffset implements CalcOffset {

        private static final Unsafe UNSAFE = getUnsafe();

        @Override
        public long getOffset(Field field) {
            return UNSAFE.objectFieldOffset(field);
        }
    }

    /**
     * Describes a field in a class during {@linkplain FieldsScanner scanning}.
     */
    public static class FieldInfo implements Comparable<FieldInfo> {
        public final long offset;
        public final String name;
        public final Class<?> type;
        public final Class<?> declaringClass;

        public FieldInfo(long offset, String name, Class<?> type, Class<?> declaringClass) {
            this.offset = offset;
            this.name = name;
            this.type = type;
            this.declaringClass = declaringClass;
        }

        /**
         * Sorts fields in ascending order by their {@link #offset}s.
         */
        @Override
        public int compareTo(FieldInfo o) {
            return offset < o.offset ? -1 : (offset > o.offset ? 1 : 0);
        }

        @Override
        public String toString() {
            return "[" + offset + "]" + name + ":" + type.getSimpleName();
        }
    }

    private final FieldsScanner.CalcOffset calc;

    /**
     * Fields not belonging to a more specific category defined by scanner subclasses are added to
     * this list.
     */
    public final ArrayList<FieldsScanner.FieldInfo> data = new ArrayList<>();

    public FieldsScanner(FieldsScanner.CalcOffset calc) {
        this.calc = calc;
    }

    /**
     * Scans the fields in a class hierarchy.
     *
     * @param clazz the class at which to start scanning
     * @param endClazz scanning stops when this class is encountered (i.e. {@code endClazz} is not
     *            scanned)
     */
    public void scan(Class<?> clazz, Class<?> endClazz, boolean includeTransient) {
        Class<?> currentClazz = clazz;
        while (currentClazz != endClazz) {
            for (Field field : currentClazz.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                if (!includeTransient && Modifier.isTransient(field.getModifiers())) {
                    continue;
                }
                long offset = calc.getOffset(field);
                scanField(field, offset);
            }
            currentClazz = currentClazz.getSuperclass();
        }
    }

    protected void scanField(Field field, long offset) {
        data.add(new FieldsScanner.FieldInfo(offset, field.getName(), field.getType(), field.getDeclaringClass()));
    }
}
