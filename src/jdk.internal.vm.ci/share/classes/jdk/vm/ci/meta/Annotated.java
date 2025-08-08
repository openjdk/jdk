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

import java.lang.annotation.Inherited;
import java.util.List;

/**
 * Represents a program element such as a method, constructor, field or class for which annotations
 * may be present.
 */
public interface Annotated {

    /**
     * Gets the annotations present on this element whose types are in {@code types}.
     * Class initialization is not triggered for enum types referenced by the returned
     * annotations or any other annotations of this element.
     *
     * If this element is a class, then {@link Inherited} annotations are included in the set of
     * annotations considered.
     *
     * See {@link java.lang.reflect.AnnotatedElement} for the definition of <em>present</em>.
     *
     * @param types annotation types to select
     * @return an immutable list of the annotations present on this element that match one {@code types}
     * @throws IllegalArgumentException if any type in {@code types} is not an annotation interface type
     * @throws UnsupportedOperationException if this operation is not supported
     */
    default List<AnnotationValue> getAnnotationValues(ResolvedJavaType... types) {
        throw new UnsupportedOperationException(this.getClass().getName());
    }

    /**
     * Gets the annotation present on this element of type {@code type}.
     * Class initialization is not triggered for enum types referenced by the returned
     * annotation.
     *
     * See {@link java.lang.reflect.AnnotatedElement} for the definition of <em>present</em>.
     *
     * @param type the type object corresponding to the annotation interface type
     * @return this element's annotation for the specified annotation type if present on this
     *         element, else null
     * @throws IllegalArgumentException if {@code type} is not an annotation interface type
     * @throws UnsupportedOperationException if this operation is not supported
     */
    default AnnotationValue getAnnotationValue(ResolvedJavaType type) {
        throw new UnsupportedOperationException(this.getClass().getName());
    }
}
