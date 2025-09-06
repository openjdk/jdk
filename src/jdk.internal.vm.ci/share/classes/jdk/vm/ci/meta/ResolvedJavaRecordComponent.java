/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.RecordComponent;
import java.util.List;

/**
 * A reference to a {@link java.lang.reflect.RecordComponent}.
 */
public interface ResolvedJavaRecordComponent extends Annotated {

    /**
     * Gets the {@link ResolvedJavaType} object representing the class which declares this record component.
     */
     ResolvedJavaType getDeclaringRecord();

    /**
     * Gets a {@code ResolvedJavaMethod} that represents the accessor for this record
     * component.
     */
     default ResolvedJavaMethod getAccessor() {
         for (ResolvedJavaMethod method : getDeclaringRecord().getDeclaredMethods(false)) {
             if (method.getName().equals(getName()) &&
                     method.getSignature().getParameterCount(false) == 0 &&
                     method.getSignature().getReturnType(null).getName().equals(getType().getName())) {
                 return method;
             }
         }
         return null;
     }

    /**
     * Returns the name of this record component.
     */
    String getName();

    /**
     * Returns a {@link JavaType} object that identifies the declared type for this record component.
     */
    JavaType getType();

    /**
     * Gets the type annotations for this record component that backs the implementation
     * of {@link RecordComponent#getAnnotatedType()}. This method returns an empty
     * list if there are no type annotations.
     *
     * @throws UnsupportedOperationException if this operation is not supported
     */
    default List<TypeAnnotationValue> getTypeAnnotationValues() {
        throw new UnsupportedOperationException(getClass().getName());
    }
}
