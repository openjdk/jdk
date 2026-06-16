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
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.IntrinsicCandidate;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Objects;

import static java.lang.classfile.ClassFile.ACC_STATIC;
import static java.lang.classfile.ClassFile.ACC_STRICT_INIT;

/**
 * Utilities to access package private methods of java.lang.Class and related reflection classes.
 */
public final class ValueClass {
    private static final JavaLangReflectAccess JLRA = SharedSecrets.getJavaLangReflectAccess();

    /// {@return whether this field type may store value objects}
    /// This excludes primitives and includes Object.
    public static boolean isValueObjectCompatible(Class<?> fieldType) {
        return PreviewFeatures.isEnabled() &&
                (fieldType.isValue() ||
                 fieldType.isInterface() ||
                 fieldType == Object.class);
    }

    /// {@return whether an object of this exact class is a value object}
    /// This excludes abstract value classes.
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

    @ForceInline
    private static void validateArrayArguments(Class<?> componentType,
                                               int length) {
        if (componentType == null) {
            throw new NullPointerException("Component type is null");
        }
        if (!isConcreteValueClass(componentType)) {
            throw new IllegalArgumentException("Component type is not a concrete value class");
        }
        if (length < 0) {
            throw new NegativeArraySizeException(Integer.toString(length));
        }
    }

    @ForceInline
    private static void validateArrayArguments(Class<?> componentType,
                                               int length, Object initVal) {
        validateArrayArguments(componentType, length);
        if (initVal == null) {
            throw new NullPointerException("Initial value is null");
        }
        // Special arrays are monomorphic, Java mirror can be compared directly
        if (initVal.getClass() != componentType) {
            throw new IllegalArgumentException("Type mismatch between array and initial value");
        }
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
    @ForceInline
    public static Object[] newNullRestrictedAtomicArray(Class<?> componentType,
                                                         int length, Object initVal) {
        validateArrayArguments(componentType, length, initVal);
        return newNullRestrictedAtomicArray0(componentType, length, initVal);
    }

    @IntrinsicCandidate
    private static native Object[] newNullRestrictedAtomicArray0(Class<?> componentType,
                                                                int length, Object initVal);

    @ForceInline
    public static Object[] newNullRestrictedNonAtomicArray(Class<?> componentType,
                                                           int length, Object initVal) {
        validateArrayArguments(componentType, length, initVal);
        return newNullRestrictedNonAtomicArray0(componentType, length, initVal);
    }

    @IntrinsicCandidate
    private static native Object[] newNullRestrictedNonAtomicArray0(Class<?> componentType,
                                                                    int length, Object initVal);

    @ForceInline
    public static Object[] newNullableAtomicArray(Class<?> componentType,
                                                  int length) {
        validateArrayArguments(componentType, length);
        return newNullableAtomicArray0(componentType, length);
    }

    @IntrinsicCandidate
    private static native Object[] newNullableAtomicArray0(Class<?> componentType,
                                                          int length);

    public static Object[] newReferenceArray(Class<?> componentType,
                                             int length) {
        validateArrayArguments(componentType, length);
        return newReferenceArray0(componentType, length);
    }

    private static native Object[] newReferenceArray0(Class<?> componentType,
                                                      int length);

    /**
     * {@return true if the given array is a flat array}
     */
    @ForceInline
    public static boolean isFlatArray(Object[] array) {
        return isFlatArray0(Objects.requireNonNull(array));
    }

    @IntrinsicCandidate
    private static native boolean isFlatArray0(Object[] array);

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
    @ForceInline
    public static boolean isNullRestrictedArray(Object[] array) {
        return isNullRestrictedArray0(Objects.requireNonNull(array));
    }

    @IntrinsicCandidate
    private static native boolean isNullRestrictedArray0(Object[] array);

    /**
     * {@return true if the given array uses a layout designed for atomic accesses }
     */
    @ForceInline
    public static boolean isAtomicArray(Object[] array) {
        return isAtomicArray0(Objects.requireNonNull(array));
    }

    @IntrinsicCandidate
    private static native boolean isAtomicArray0(Object[] array);

    // This class also serves as a lazy holder of its singleton instance
    private static final class StrictInstanceFieldClassValue extends ClassValue<Boolean> {
        private static final StrictInstanceFieldClassValue INSTANCE = new StrictInstanceFieldClassValue();

        private StrictInstanceFieldClassValue() {}

        @Override
        protected Boolean computeValue(Class<?> type) {
            if (!isClassOrInterface(type)) {
                return false;
            }
            for (var field : type.getDeclaredFields()) {
                // Reflection filters fields, hope the filtered classes don't declare strict fields
                if ((field.getModifiers() & (ACC_STATIC | ACC_STRICT_INIT)) == ACC_STRICT_INIT) {
                    return true;
                }
            }
            return false;
        }
    }

    /// Returns whether a class or interface declares strict instance fields.
    /// This does not include inherited instance fields.
    public static boolean hasStrictInstanceField(Class<?> cl) {
        if (!isClassOrInterface(cl)) {
            // Not class or interface
            return false;
        }
        return StrictInstanceFieldClassValue.INSTANCE.get(cl);
    }

    // Only primitive and array types have both ABSTRACT and FINAL flags set
    private static final int NON_CLASS_COMMON_MODIFIERS = Modifier.ABSTRACT | Modifier.FINAL;

    /// Returns if a Class object represents a class or interface instead of a
    /// primitive type or an array type.
    private static boolean isClassOrInterface(Class<?> cl) {
        return (cl.getModifiers() & NON_CLASS_COMMON_MODIFIERS) != NON_CLASS_COMMON_MODIFIERS;
    }
}
