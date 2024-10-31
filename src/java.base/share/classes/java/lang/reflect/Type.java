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
 * {@code Type} represents types in the Java programming language and type
 * arguments.  Types (JLS {@jls 4.1}) are primitive types and reference types.
 * Type arguments (JLS {@jls 4.5.1}) are reference types and wildcard type
 * arguments.
 * <table class="striped">
 * <caption style="display:none">
 * Types and Type Arguments to Modeling Interfaces
 * </caption>
 * <thead>
 * <tr><th colspan="3">Type or Type Argument
 *     <th>Example
 *     <th>Modeling interface
 * </thead>
 * <tbody>
 * <tr><td colspan="3">Primitive Types (JLS {@jls 4.2})
 *     <td>{@code int}
 *     <td rowspan="3">{@link ##alone Type}
 * <tr><td rowspan="9">Reference<br>Types<br>(JLS {@jls 4.3})
 *     <td rowspan="3">Classes<br>and<br>Interfaces
 *     <td>Non-generic Classes and<br>Interfaces
 *         (JLS {@jls 8.1.3}, {@jls 9.1.3})
 *     <td>{@code String}
 * <tr><td>Raw Types (JLS {@jls 4.8})
 *     <td>{@code List}
 * <tr><td>Parameterized Types (JLS {@jls 4.5})
 *     <td>{@code List<String>}
 *     <td>{@link ParameterizedType}
 * <tr><td colspan="2">Type Variables (JLS {@jls 4.4})
 *     <td>{@code T}
 *     <td>{@link TypeVariable}
 * <tr><td rowspan="5">Array<br>Types<br>(JLS {@jls 10.1})
 *     <td>Primitive Type Elements
 *     <td>{@code int[]}
 *     <td rowspan="3">{@link ##alone Type}
 * <tr><td>Non-generic Class or<br>Interface Elements
 *     <td>{@code String[]}
 * <tr><td>Raw Type Elements
 *     <td>{@code List[]}
 * <tr><td>Parameterized Type Elements
 *     <td>{@code List<String>[][]}
 *     <td rowspan="2">{@link GenericArrayType}
 * <tr><td>Types Variable Elements
 *     <td>{@code T[][][]}
 * <tr><td colspan="3">Wildcard Type Arguments (JLS {@jls 4.5.1})
 *     <td>{@code ? extends String}
 *     <td>{@link WildcardType}
 * </tbody>
 * </table>
 * <p>
 * Two {@code Type} objects should be compared using the {@link Object#equals
 * equals} method.
 *
 * <h2 id="alone">The {@code Type} interface alone</h2>
 * Some {@code Type} objects are not instances of the {@link GenericArrayType},
 * {@link ParameterizedType}, {@link TypeVariable}, or {@link WildcardType}
 * subinterfaces.  Such a type is a primitive type, a non-generic class or
 * interface, a raw type, or an array type with any of these types as its
 * element type.  In core reflection, they are all represented by {@link Class}.
 * <p>
 * Examples include the primitive type {@code int}, the non-generic {@link
 * Object} class, the raw type {@code List}, and the array type {@code int[]}.
 *
 * @jls 4.1 The Kinds of Types and Values
 * @jls 4.11 Where Types Are Used
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
