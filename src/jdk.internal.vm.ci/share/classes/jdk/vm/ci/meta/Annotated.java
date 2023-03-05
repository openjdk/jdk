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

/**
 * Represents a program element such as a method, constructor, field or class for which annotations
 * may be present.
 */
public interface Annotated {

    /**
     * Gets the annotations of this element whose types are in {@code filter}.
     * The search for annotations of this element includes inherited annotations
     * if this element is a class.
     *
     * All enum types referenced by the returned annotation are initialized.
     * Class initialization is not performed for enum types referenced by other
     * annotations of this element.
     *
     * The caller of this method is free to modify the returned array; it will
     * have no effect on the arrays returned to other callers.
     *
     * @param filter an array of types
     * @return the annotations of this type whose types are in {@code filter}
     * @throws UnsupportedOperationException if this operation is not supported
     */
    default AnnotationData[] getAnnotationData(ResolvedJavaType... filter) {
        throw new UnsupportedOperationException();
    }

    /**
     * Gets this element's annotation of type {@code type}.
     *
     * @param type the type object corresponding to the annotation type
     * @return this element's annotation for the specified annotation type if present on this
     *         element, else null
     * @throws UnsupportedOperationException if this operation is not supported
     */
    default AnnotationData getAnnotationDataFor(ResolvedJavaType type) {
        AnnotationData[] a = getAnnotationData(type);
        return a.length == 1 ? a[0] : null;
    }
}
