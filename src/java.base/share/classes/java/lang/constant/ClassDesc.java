/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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
package java.lang.constant;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.TypeDescriptor;
import java.util.stream.Stream;

import jdk.internal.constant.PrimitiveClassDescImpl;
import jdk.internal.constant.ReferenceClassDescImpl;
import sun.invoke.util.Wrapper;

import static java.util.stream.Collectors.joining;
import static jdk.internal.constant.ConstantUtils.MAX_ARRAY_TYPE_DESC_DIMENSIONS;
import static jdk.internal.constant.ConstantUtils.arrayDepth;
import static jdk.internal.constant.ConstantUtils.binaryToInternal;
import static jdk.internal.constant.ConstantUtils.dropFirstAndLastChar;
import static jdk.internal.constant.ConstantUtils.internalToBinary;
import static jdk.internal.constant.ConstantUtils.validateBinaryClassName;
import static jdk.internal.constant.ConstantUtils.validateInternalClassName;
import static jdk.internal.constant.ConstantUtils.validateMemberName;

/**
 * A <a href="package-summary.html#nominal">nominal descriptor</a> for a
 * {@link Class} constant.
 *
 * <p>For common system types, including all the primitive types, there are
 * predefined {@linkplain ClassDesc} constants in {@link ConstantDescs}.
 * (The {@code java.lang.constant} APIs consider {@code void} to be a primitive type.)
 * To create a {@linkplain ClassDesc} for a class or interface type, use {@link #of} or
 * {@link #ofDescriptor(String)}; to create a {@linkplain ClassDesc} for an array
 * type, use {@link #ofDescriptor(String)}, or first obtain a
 * {@linkplain ClassDesc} for the component type and then call the {@link #arrayType()}
 * or {@link #arrayType(int)} methods.
 *
 * @see ConstantDescs
 *
 * @since 12
 */
public sealed interface ClassDesc
        extends ConstantDesc,
                TypeDescriptor.OfField<ClassDesc>
        permits PrimitiveClassDescImpl,
                ReferenceClassDescImpl {

    /**
     * Returns a {@linkplain ClassDesc} for a class or interface type,
     * given the name of the class or interface, such as {@code "java.lang.String"}.
     * (To create a descriptor for an array type, either use {@link #ofDescriptor(String)}
     * or {@link #arrayType()}; to create a descriptor for a primitive type, use
     * {@link #ofDescriptor(String)} or use the predefined constants in
     * {@link ConstantDescs}).
     *
     * @param name the fully qualified (dot-separated) binary class name
     * @return a {@linkplain ClassDesc} describing the desired class
     * @throws NullPointerException if the argument is {@code null}
     * @throws IllegalArgumentException if the name string is not in the
     * correct format
     * @see ClassDesc#ofDescriptor(String)
     * @see ClassDesc#ofInternalName(String)
     */
    static ClassDesc of(String name) {
        validateBinaryClassName(name);
        return ClassDesc.ofDescriptor("L" + binaryToInternal(name) + ";");
    }

    /**
     * Returns a {@linkplain ClassDesc} for a class or interface type,
     * given the name of the class or interface in internal form,
     * such as {@code "java/lang/String"}.
     *
     * @apiNote
     * To create a descriptor for an array type, either use {@link #ofDescriptor(String)}
     * or {@link #arrayType()}; to create a descriptor for a primitive type, use
     * {@link #ofDescriptor(String)} or use the predefined constants in
     * {@link ConstantDescs}.
     *
     * @param name the fully qualified class name, in internal (slash-separated) form
     * @return a {@linkplain ClassDesc} describing the desired class
     * @throws NullPointerException if the argument is {@code null}
     * @throws IllegalArgumentException if the name string is not in the
     * correct format
     * @jvms 4.2.1 Binary Class and Interface Names
     * @see ClassDesc#of(String)
     * @see ClassDesc#ofDescriptor(String)
     * @since 20
     */
    static ClassDesc ofInternalName(String name) {
        validateInternalClassName(name);
        return ClassDesc.ofDescriptor("L" + name + ";");
    }

    /**
     * Returns a {@linkplain ClassDesc} for a class or interface type,
     * given a package name and the unqualified (simple) name for the
     * class or interface.
     *
     * @param packageName the package name (dot-separated); if the package
     *                    name is the empty string, the class is considered to
     *                    be in the unnamed package
     * @param className the unqualified (simple) class name
     * @return a {@linkplain ClassDesc} describing the desired class
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if the package name or class name are
     * not in the correct format
     */
    static ClassDesc of(String packageName, String className) {
        validateBinaryClassName(packageName);
        if (packageName.isEmpty()) {
            return of(className);
        }
        validateMemberName(className, false);
        return ofDescriptor("L" + binaryToInternal(packageName) +
                "/" + className + ";");
    }

    /**
     * Returns a {@linkplain ClassDesc} given a descriptor string for a class,
     * interface, array, or primitive type.
     *
     * @apiNote
     *
     * A field type descriptor string for a non-array type is either
     * a one-letter code corresponding to a primitive type
     * ({@code "J", "I", "C", "S", "B", "D", "F", "Z", "V"}), or the letter {@code "L"}, followed
     * by the fully qualified binary name of a class, followed by {@code ";"}.
     * A field type descriptor for an array type is the character {@code "["}
     * followed by the field descriptor for the component type.  Examples of
     * valid type descriptor strings include {@code "Ljava/lang/String;"}, {@code "I"},
     * {@code "[I"}, {@code "V"}, {@code "[Ljava/lang/String;"}, etc.
     * See JVMS {@jvms 4.3.2 }("Field Descriptors") for more detail.
     *
     * @param descriptor a field descriptor string
     * @return a {@linkplain ClassDesc} describing the desired class
     * @throws NullPointerException if the argument is {@code null}
     * @throws IllegalArgumentException if the descriptor string is not in the
     * correct format
     * @jvms 4.3.2 Field Descriptors
     * @jvms 4.4.1 The CONSTANT_Class_info Structure
     * @see ClassDesc#of(String)
     * @see ClassDesc#ofInternalName(String)
     */
    static ClassDesc ofDescriptor(String descriptor) {
        // implicit null-check
        return (descriptor.length() == 1)
               ? Wrapper.forPrimitiveType(descriptor.charAt(0)).classDescriptor()
               // will throw IAE on descriptor.length == 0 or if array dimensions too long
               : ReferenceClassDescImpl.of(descriptor);
    }

    /**
     * Returns a {@linkplain ClassDesc} for an array type whose component type
     * is described by this {@linkplain ClassDesc}.
     *
     * @return a {@linkplain ClassDesc} describing the array type
     * @throws IllegalStateException if the resulting {@linkplain
     * ClassDesc} would have an array rank of greater than 255
     * @jvms 4.4.1 The CONSTANT_Class_info Structure
     */
    default ClassDesc arrayType() {
        String desc = descriptorString();
        int depth = arrayDepth(desc);
        if (depth >= MAX_ARRAY_TYPE_DESC_DIMENSIONS) {
            throw new IllegalStateException(
                    "Cannot create an array type descriptor with more than " +
                    MAX_ARRAY_TYPE_DESC_DIMENSIONS + " dimensions");
        }
        String newDesc = "[".concat(desc);
        if (desc.length() == 1 && desc.charAt(0) == 'V') {
            throw new IllegalArgumentException("not a valid reference type descriptor: " + newDesc);
        }
        return ReferenceClassDescImpl.ofValidated(newDesc);
    }

    /**
     * Returns a {@linkplain ClassDesc} for an array type of the specified rank,
     * whose component type is described by this {@linkplain ClassDesc}.
     *
     * @param rank the rank of the array
     * @return a {@linkplain ClassDesc} describing the array type
     * @throws IllegalArgumentException if the rank is less than or
     * equal to zero or if the rank of the resulting array type is
     * greater than 255
     * @jvms 4.4.1 The CONSTANT_Class_info Structure
     */
    default ClassDesc arrayType(int rank) {
        if (rank <= 0) {
            throw new IllegalArgumentException("rank " + rank + " is not a positive value");
        }
        String desc = descriptorString();
        long currentDepth = arrayDepth(desc);
        long netRank = currentDepth + rank;
        if (netRank > MAX_ARRAY_TYPE_DESC_DIMENSIONS) {
            throw new IllegalArgumentException("rank: " + netRank +
                    " exceeds maximum supported dimension of " +
                    MAX_ARRAY_TYPE_DESC_DIMENSIONS);
        }
        String newDesc = new StringBuilder(desc.length() + rank).repeat('[', rank).append(desc).toString();
        if (desc.length() == 1 && desc.charAt(0) == 'V') {
            throw new IllegalArgumentException("not a valid reference type descriptor: " + newDesc);
        }
        return ReferenceClassDescImpl.ofValidated(newDesc);
    }

    /**
     * Returns a {@linkplain ClassDesc} for a nested class of the class or
     * interface type described by this {@linkplain ClassDesc}.
     *
     * @apiNote
     *
     * Example: If descriptor {@code d} describes the class {@code java.util.Map}, a
     * descriptor for the class {@code java.util.Map.Entry} could be obtained
     * by {@code d.nested("Entry")}.
     *
     * @param nestedName the unqualified name of the nested class
     * @return a {@linkplain ClassDesc} describing the nested class
     * @throws NullPointerException if the argument is {@code null}
     * @throws IllegalStateException if this {@linkplain ClassDesc} does not
     * describe a class or interface type
     * @throws IllegalArgumentException if the nested class name is invalid
     */
    default ClassDesc nested(String nestedName) {
        validateMemberName(nestedName, false);
        if (!isClassOrInterface())
            throw new IllegalStateException("Outer class is not a class or interface type");
        String desc = descriptorString();
        StringBuilder sb = new StringBuilder(desc.length() + nestedName.length() + 1);
        sb.append(desc, 0, desc.length() - 1).append('$').append(nestedName).append(';');
        return ReferenceClassDescImpl.ofValidated(sb.toString());
    }

    /**
     * Returns a {@linkplain ClassDesc} for a nested class of the class or
     * interface type described by this {@linkplain ClassDesc}.
     *
     * @param firstNestedName the unqualified name of the first level of nested class
     * @param moreNestedNames the unqualified name(s) of the remaining levels of
     *                       nested class
     * @return a {@linkplain ClassDesc} describing the nested class
     * @throws NullPointerException if any argument or its contents is {@code null}
     * @throws IllegalStateException if this {@linkplain ClassDesc} does not
     * describe a class or interface type
     * @throws IllegalArgumentException if the nested class name is invalid
     */
    default ClassDesc nested(String firstNestedName, String... moreNestedNames) {
        if (!isClassOrInterface())
            throw new IllegalStateException("Outer class is not a class or interface type");
        validateMemberName(firstNestedName, false);
        // implicit null-check
        for (String addNestedNames : moreNestedNames) {
            validateMemberName(addNestedNames, false);
        }
        return moreNestedNames.length == 0
               ? nested(firstNestedName)
               : nested(firstNestedName + Stream.of(moreNestedNames).collect(joining("$", "$", "")));
    }

    /**
     * Returns whether this {@linkplain ClassDesc} describes an array type.
     *
     * @return whether this {@linkplain ClassDesc} describes an array type
     */
    default boolean isArray() {
        return descriptorString().charAt(0) == '[';
    }

    /**
     * Returns whether this {@linkplain ClassDesc} describes a primitive type.
     *
     * @return whether this {@linkplain ClassDesc} describes a primitive type
     */
    default boolean isPrimitive() {
        return descriptorString().length() == 1;
    }

    /**
     * Returns whether this {@linkplain ClassDesc} describes a class or interface type.
     *
     * @return whether this {@linkplain ClassDesc} describes a class or interface type
     */
    default boolean isClassOrInterface() {
        return descriptorString().charAt(0) == 'L';
    }

    /**
     * Returns the component type of this {@linkplain ClassDesc}, if it describes
     * an array type, or {@code null} otherwise.
     *
     * @return a {@linkplain ClassDesc} describing the component type, or {@code null}
     * if this descriptor does not describe an array type
     */
    default ClassDesc componentType() {
        if (isArray()) {
            String desc = descriptorString();
            if (desc.length() == 2) {
                return Wrapper.forBasicType(desc.charAt(1)).classDescriptor();
            } else {
                return ReferenceClassDescImpl.ofValidated(desc.substring(1));
            }
        }
        return null;
    }

    /**
     * Returns the package name of this {@linkplain ClassDesc}, if it describes
     * a class or interface type.
     *
     * @return the package name, or the empty string if the class is in the
     * default package, or this {@linkplain ClassDesc} does not describe a class or interface type
     */
    default String packageName() {
        if (!isClassOrInterface())
            return "";
        String desc = descriptorString();
        int index = desc.lastIndexOf('/');
        return (index == -1) ? "" : internalToBinary(desc.substring(1, index));
    }

    /**
     * Returns a human-readable name for the type described by this descriptor.
     *
     * @implSpec
     * <p>The default implementation returns the simple name
     * (e.g., {@code int}) for primitive types, the unqualified class name
     * for class or interface types, or the display name of the component type
     * suffixed with the appropriate number of {@code []} pairs for array types.
     *
     * @return the human-readable name
     */
    default String displayName() {
        if (isPrimitive())
            return Wrapper.forBasicType(descriptorString().charAt(0)).primitiveSimpleName();
        else if (isClassOrInterface()) {
            String desc = descriptorString();
            return desc.substring(Math.max(1, desc.lastIndexOf('/') + 1), desc.length() - 1);
        }
        else if (isArray()) {
            int depth = arrayDepth(descriptorString());
            ClassDesc c = this;
            for (int i=0; i<depth; i++)
                c = c.componentType();
            return c.displayName() + "[]".repeat(depth);
        }
        else
            throw new IllegalStateException(descriptorString());
    }

    /**
     * Returns a field type descriptor string for this type
     *
     * @return the descriptor string
     * @jvms 4.3.2 Field Descriptors
     */
    String descriptorString();

    @Override
    Class<?> resolveConstantDesc(MethodHandles.Lookup lookup) throws ReflectiveOperationException;

    /**
     * Compare the specified object with this descriptor for equality.  Returns
     * {@code true} if and only if the specified object is also a
     * {@linkplain ClassDesc} and both describe the same type.
     *
     * @param o the other object
     * @return whether this descriptor is equal to the other object
     */
    boolean equals(Object o);
}
