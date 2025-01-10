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
 * {@code WildcardType} represents a wildcard type argument, such as {@code ?},
 * {@code ? extends Number}, or {@code ? super Integer}.  Since a wildcard type
 * argument is not a type, it will only be returned by APIs where type arguments
 * may appear, such as {@link ParameterizedType#getActualTypeArguments()
 * ParameterizedType::getActualTypeArguments}.
 * <p>
 * Two {@code WildcardType} objects should be compared using the {@link
 * Object#equals equals} method.
 *
 * @jls 4.5.1 Type Arguments of Parameterized Types
 * @since 1.5
 */
public interface WildcardType extends Type {
    /**
     * {@return the upper bounds of this wildcard type argument}  An upper bound
     * has the syntax {@code ? extends B} in Java source code, where {@code B}
     * is the bound.  If no upper bound is explicitly declared, the upper bound
     * is the {@link Object} class.
     *
     * <p>For each upper bound B:
     * <ul>
     *  <li>if B is a parameterized type or a type variable, it is created.
     *  (see {@link ParameterizedType} and {@link TypeVariable} for the details
     *  of the creation process for parameterized types and type variables)
     *  <li>Otherwise, B is resolved.
     * </ul>
     *
     * @apiNote
     * While to date a wildcard type argument may have at most one upper bound,
     * callers of this method should be written to accommodate multiple bounds.
     *
     * @throws TypeNotPresentException if any of the bounds refers to a
     *     non-existent type declaration
     * @throws MalformedParameterizedTypeException if any of the bounds refer to
     *     a parameterized type that cannot be instantiated for any reason
     */
    Type[] getUpperBounds();

    /**
     * {@return the lower bounds of this wildcard type argument}  A lower bound
     * has the syntax {@code ? super B} in Java source code, where {@code B} is
     * the bound.  If no lower bound is explicitly declared, the lower bound is
     * the null type (JLS {@jls 4.1}).  In this case, a zero length array is
     * returned.
     *
     * <p>For each lower bound B:
     * <ul>
     *  <li>if B is a parameterized type or a type variable, it is created.
     *  (see {@link ParameterizedType} and {@link TypeVariable} for the details
     *  of the creation process for parameterized types and type variables)
     *  <li>Otherwise, B is resolved.
     * </ul>
     *
     * @apiNote
     * While to date a wildcard type argument may have at most one lower bound,
     * callers of this method should be written to accommodate multiple bounds.
     *
     * @throws TypeNotPresentException if any of the bounds refers to a
     *     non-existent type declaration
     * @throws MalformedParameterizedTypeException if any of the bounds refer to
     *     a parameterized type that cannot be instantiated for any reason
     */
    Type[] getLowerBounds();
}
