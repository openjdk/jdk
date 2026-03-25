/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.value;

import jdk.internal.access.JavaLangReflectAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.misc.PreviewFeatures;
import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.IntrinsicCandidate;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Utilities to access package private methods of java.lang.Class and related reflection classes.
 */
public final class ValueClass {
    private static final JavaLangReflectAccess JLRA = SharedSecrets.getJavaLangReflectAccess();

    /// {@return whether this field type may store value objects}
    /// This excludes primitives and includes Object.
    public static boolean isValueObjectCompatible(Class<?> fieldType) {
        return PreviewFeatures.isEnabled()
                && !fieldType.isPrimitive() // non-primitive
                && (!fieldType.isIdentity() || fieldType == Object.class); // AVC or Object
    }

    /// {@return whether an object of this exact class is a value object}
    /// This excludes abstract value classes and primitives.
    public static boolean isConcreteValueClass(Class<?> clazz) {
        return clazz.isValue() && !Modifier.isAbstract(clazz.getModifiers());
    }

    /// {@return whether a field of type `c` can be represented with a payload
    /// without oops}  For example, primitive type fields and value classes with
    /// all primitive fields recursively may be represented by a payload of a
    /// layout without oops.  Returns false if there is no flat layout for a
    /// field of type `c`.
    public static boolean hasBinaryPayload(Class<?> c) {
        // non-concrete value class type field always a reference
        if (!ValueClass.isConcreteValueClass(c))
            return c.isPrimitive();
        // Check the flat layout
        return Unsafe.getUnsafe().isFlatPayloadBinary(c);
    }

    /**
     * {@return {@code true} if the field is NullRestricted}
     */
    public static boolean isNullRestrictedField(Field f) {
        return JLRA.isNullRestrictedField(f);
    }

    /**
     * Allocate an array of a value class type with components that behave in
     * the same way as a {@link jdk.internal.vm.annotation.NullRestricted}
     * field.
     * <p>
     * Because these behaviors are not specified by Java SE, arrays created with
     * this method should only be used by internal JDK code for experimental
     * purposes and should not affect user-observable outcomes.
     *
     * @throws IllegalArgumentException if {@code componentType} is not a
     *                                  value class type.
     */
    @IntrinsicCandidate
    public static native Object[] newNullRestrictedAtomicArray(Class<?> componentType,
                                                               int length, Object initVal);

    @IntrinsicCandidate
    public static native Object[] newNullRestrictedNonAtomicArray(Class<?> componentType,
                                                                  int length, Object initVal);

    @IntrinsicCandidate
    public static native Object[] newNullableAtomicArray(Class<?> componentType,
                                                         int length);

    public static native Object[] newReferenceArray(Class<?> componentType,
                                                    int length);

    /**
     * {@return true if the given array is a flat array}
     */
    @IntrinsicCandidate
    public static native boolean isFlatArray(Object array);

    public static Object[] copyOfSpecialArray(Object[] array, int newLength) {
        if (newLength < 0) {
            throw new NegativeArraySizeException("" + newLength);
        }
        return copyOfSpecialArray0(array, 0, newLength);
    }

    public static Object[] copyOfRangeSpecialArray(Object[] array, int from, int to) {
        int length = array.length;
        if (from < 0 || from > length) {
            throw new ArrayIndexOutOfBoundsException("source index " + from + " out of bounds for object array[" + length + "]");
        }
        if (from > to) {
            throw new IllegalArgumentException(from + " > " + to);
        }
        return copyOfSpecialArray0(array, from, to);
    }

    private static native Object[] copyOfSpecialArray0(Object[] array, int from, int to);

    /**
     * {@return true if the given array is a null-restricted array}
     */
    @IntrinsicCandidate
    public static native boolean isNullRestrictedArray(Object array);

    /**
     * {@return true if the given array uses a layout designed for atomic accesses }
     */
    @IntrinsicCandidate
    public static native boolean isAtomicArray(Object array);
}
