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
package jdk.vm.ci.meta.annotation;

import jdk.vm.ci.meta.ResolvedJavaType;

import java.lang.reflect.AnnotatedElement;
import java.util.Map;

/**
 * Represents a program element such as a method, constructor, field or class for which annotations
 * may be directly present. This API is analogous to {@link java.lang.reflect.AnnotatedElement}
 * except that it only supports {@linkplain AnnotatedElement#getDeclaredAnnotations() declared annotations}.
 */
public interface Annotated {

    /**
     * Gets the annotations directly present on this element.
     * Class initialization is not triggered for enum types referenced by the returned
     * annotation. This method ignores inherited annotations.
     *
     * @return an immutable map from annotation type to annotation of the annotations directly present
     *         on this element
     * @throws UnsupportedOperationException if this operation is not supported
     */
    default Map<ResolvedJavaType, AnnotationValue> getDeclaredAnnotationValues() {
        throw new UnsupportedOperationException(this.getClass().getName());
    }

    /**
     * Gets the annotation directly present on this element whose type is {@code type}.
     * Class initialization is not triggered for enum types referenced by the returned
     * annotation. This method ignores inherited annotations.
     *
     * @param type the type object corresponding to the annotation interface type
     * @return this element's annotation for the specified annotation type if directly present on this
     * element, else null
     * @throws IllegalArgumentException      if {@code type} is not an annotation interface type
     * @throws UnsupportedOperationException if this operation is not supported
     */
    default AnnotationValue getDeclaredAnnotationValue(ResolvedJavaType type) {
        throw new UnsupportedOperationException(this.getClass().getName());
    }
}
