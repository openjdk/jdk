/*
 * Copyright (c) 2009, 2023, Oracle and/or its affiliates. All rights reserved.
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

import jdk.vm.ci.meta.annotation.Annotated;
import jdk.vm.ci.meta.annotation.TypeAnnotationValue;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;

/**
 * Represents a reference to a resolved Java field. Fields, like methods and types, are resolved
 * through {@link ConstantPool constant pools}.
 */
public interface ResolvedJavaField extends JavaField, ModifiersProvider, AnnotatedElement, Annotated {

    /**
     * {@inheritDoc}
     * <p>
     * Only the {@linkplain Modifier#fieldModifiers() field flags} specified in the JVM
     * specification will be included in the returned mask.
     */
    @Override
    int getModifiers();

    /**
     * Returns the offset of the field relative to the base of its storage container (e.g.,
     * {@code instanceOop} for an instance field or {@code Klass*} for a static field on HotSpot).
     */
    int getOffset();

    default boolean isFinal() {
        return ModifiersProvider.super.isFinalFlagSet();
    }

    /**
     * Determines if this field was injected by the VM. Such a field, for example, is not derived
     * from a class file.
     */
    boolean isInternal();

    /**
     * Determines if this field is a synthetic field as defined by the Java Language Specification.
     */
    boolean isSynthetic();

    /**
     * Returns the {@link ResolvedJavaType} object representing the class or interface that declares
     * this field.
     */
    @Override
    ResolvedJavaType getDeclaringClass();

    /**
     * Gets the value of the {@code ConstantValue} attribute ({@jvms 4.7.2}) associated with this
     * field.
     *
     * @return {@code null} if this field has no {@code ConstantValue} attribute
     * @throws UnsupportedOperationException if this operation is not supported
     */
    default JavaConstant getConstantValue() {
        throw new UnsupportedOperationException();
    }

    /**
     * Gets the type annotations for this field that backs the implementation
     * of {@link Field#getAnnotatedType()}. This method returns an empty
     * list if there are no type annotations.
     *
     * @throws UnsupportedOperationException if this operation is not supported
     */
    default List<TypeAnnotationValue> getTypeAnnotationValues() {
        throw new UnsupportedOperationException(getClass().getName());
    }
}
