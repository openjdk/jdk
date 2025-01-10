/*
 * Copyright (c) 2003, 2025, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.reflect;

/**
 * {@code GenericArrayType} represents an array type whose element
 * type is either a parameterized type, such as {@code Comparable<?>} for the
 * array type {@code Comparable<?>[]}, or a type variable, such as {@code T}
 * for the array type {@code T[][]}.
 * <p>
 * Two {@code GenericArrayType} objects should be compared using the {@link
 * Object#equals equals} method.
 *
 * @jls 10.1 Array Types
 * @since 1.5
 */
public interface GenericArrayType extends Type {
    /**
     * {@return the component type of this array type}  The component type must
     * be one of {@link GenericArrayType}, {@link ParameterizedType}, or {@link
     * TypeVariable}.
     * <p>
     * This method creates the component type of the array.  See {@link
     * ParameterizedType} for the semantics of the creation process for
     * parameterized types and see {@link TypeVariable} for the creation process
     * for type variables.
     *
     * @throws TypeNotPresentException if the component type refers to a
     *     non-existent class or interface declaration
     * @throws MalformedParameterizedTypeException if the component type refers
     *     to a parameterized type that cannot be instantiated for any reason
     */
    Type getGenericComponentType();
}
