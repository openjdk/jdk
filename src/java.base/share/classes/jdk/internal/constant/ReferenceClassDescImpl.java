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
package jdk.internal.constant;

import java.lang.constant.ClassDesc;
import java.lang.invoke.MethodHandles;

import static jdk.internal.constant.ConstantUtils.*;

/**
 * A <a href="package-summary.html#nominal">nominal descriptor</a> for a class,
 * interface, or array type.  A {@linkplain ReferenceClassDescImpl} corresponds to a
 * {@code Constant_Class_info} entry in the constant pool of a classfile.
 */
public final class ReferenceClassDescImpl implements ClassDesc {
    private final String descriptor;

    private ReferenceClassDescImpl(String descriptor) {
        this.descriptor = descriptor;
    }

    /**
     * Creates a {@linkplain ClassDesc} from a descriptor string for a class or
     * interface type or an array type.
     *
     * @param descriptor a field descriptor string for a class or interface type
     * @throws IllegalArgumentException if the descriptor string is not a valid
     * field descriptor string, or does not describe a class or interface type
     * @jvms 4.3.2 Field Descriptors
     */
    public static ReferenceClassDescImpl of(String descriptor) {
        int dLen = descriptor.length();
        int len = ConstantUtils.skipOverFieldSignature(descriptor, 0, dLen, false);
        if (len <= 1 || len != dLen)
            throw new IllegalArgumentException(String.format("not a valid reference type descriptor: %s", descriptor));
        return new ReferenceClassDescImpl(descriptor);
    }

    /**
     * Creates a {@linkplain ClassDesc} from a pre-validated descriptor string
     * for a class or interface type or an array type.
     *
     * @param descriptor a field descriptor string for a class or interface type
     * @jvms 4.3.2 Field Descriptors
     */
    public static ReferenceClassDescImpl ofValidated(String descriptor) {
        assert ConstantUtils.skipOverFieldSignature(descriptor, 0, descriptor.length(), false)
                == descriptor.length() : descriptor;
        return new ReferenceClassDescImpl(descriptor);
    }

    @Override
    public String descriptorString() {
        return descriptor;
    }

    @Override
    public Class<?> resolveConstantDesc(MethodHandles.Lookup lookup)
            throws ReflectiveOperationException {
        if (isArray()) {
            if (isPrimitiveArray()) {
                return lookup.findClass(descriptor);
            }
            // Class.forName is slow on class or interface arrays
            int depth = ConstantUtils.arrayDepth(descriptor);
            Class<?> clazz = lookup.findClass(internalToBinary(descriptor.substring(depth + 1, descriptor.length() - 1)));
            for (int i = 0; i < depth; i++)
                clazz = clazz.arrayType();
            return clazz;
        }
        return lookup.findClass(internalToBinary(dropFirstAndLastChar(descriptor)));
    }

    /**
     * Whether the descriptor is one of a primitive array, given this is
     * already a valid reference type descriptor.
     */
    private boolean isPrimitiveArray() {
        // All L-type descriptors must end with a semicolon; same for reference
        // arrays, leaving primitive arrays the only ones without a final semicolon
        return descriptor.charAt(descriptor.length() - 1) != ';';
    }

    /**
     * Returns {@code true} if this {@linkplain ReferenceClassDescImpl} is
     * equal to another {@linkplain ReferenceClassDescImpl}.  Equality is
     * determined by the two class descriptors having equal class descriptor
     * strings.
     *
     * @param o the {@code ClassDesc} to compare to this
     *       {@code ClassDesc}
     * @return {@code true} if the specified {@code ClassDesc}
     *      is equal to this {@code ClassDesc}.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o instanceof ReferenceClassDescImpl constant) {
            return descriptor.equals(constant.descriptor);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return descriptor.hashCode();
    }

    @Override
    public String toString() {
        return String.format("ClassDesc[%s]", displayName());
    }
}
