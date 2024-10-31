/*
 * Copyright (c) 2003, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * {@code Type} represents types in the Java programming language (JLS {@jls
 * 4.1}) and type arguments (JLS {@jls 4.5.1}).  Types are primitive types (JLS
 * {@jls 4.2}) and reference types (JLS {@jls 4.3}).  Reference types are
 * non-generic classes (JLS {@jls 8.1.2}) (which must not be an {@linkplain
 * ParameterizedType##inner-member-class inner member class} of a generic class)
 * and interfaces (JLS {@jls 9.1.2}), raw types (JLS {@jls 4.8}) and
 * parameterized types (JLS {@jls 4.5}) of generic classes and interfaces,
 * type variables (JLS {@jls 4.4}), and array types (JLS {@jls 10.1}).  Type
 * arguments are reference types and wildcard type arguments.
 * <p>
 * Here is a mapping from types and type arguments to the modeling interfaces.
 * "{@code Type} alone" means the modeling class does not implement any other
 * {@code Type} subinterface.  The modeling class is {@link Class} in core
 * reflection representation of types in the current runtime.  Other
 * implementations may use different modeling classes to represent types not
 * in the current runtime.
 * <ul>
 * <li>Primitive types (such as {@code int}): {@code Type} alone
 * <li>Reference types: <ul>
 *     <li>Class types and interface types:<ul>
 *         <li>Parameterized types (such as {@code List<String>}):
 *             {@link ParameterizedType}
 *         <li>Non-generic classes and interfaces (such as {@code String}) and
 *             raw types (such as {@code List}): {@code Type} alone
 *     </ul>
 *     <li>Type variables (such as {@code T}): {@link TypeVariable}
 *     <li>Array types: Depends on its element type. <ul>
 *         <li>If the element type is modeled by {@code Type} alone, such as
 *             {@code int} for the array type {@code int[]}, the array type is
 *             modeled by {@code Type} alone.
 *         <li>Otherwise, the element type must be modeled by {@link
 *             ParameterizedType}, such as {@code Comparable<?>} for the array
 *             type {@code Comparable<?>[]}, or {@link TypeVariable}, such as
 *             {@code T} for the array type {@code T[]}, and the array type is
 *             modeled by {@link GenericArrayType}.
 *     </ul>
 * </ul>
 * <li>Wildcard type arguments (such as {@code ? extends String}):
 *     {@link WildcardType}
 * </ul>
 * <p>
 * Two {@code Type} objects should be compared using the {@link Object#equals
 * equals} method.
 *
 * @jls 4.1 The Kinds of Types and Values
 * @jls 4.2 Primitive Types and Values
 * @jls 4.3 Reference Types and Values
 * @jls 4.4 Type Variables
 * @jls 4.5 Parameterized Types
 * @jls 4.8 Raw Types
 * @jls 4.9 Intersection Types
 * @jls 10.1 Array Types
 * @since 1.5
 */
public interface Type {
    /**
     * Returns a string describing this type, including information
     * about any type parameters.
     *
     * @implSpec The default implementation calls {@code toString}.
     *
     * @return a string describing this type
     * @since 1.8
     */
    default String getTypeName() {
        return toString();
    }
}
