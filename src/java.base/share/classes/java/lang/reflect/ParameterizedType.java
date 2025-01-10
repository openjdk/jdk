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
 * {@code ParameterizedType} represents a parameterized type, such as {@code
 * Collection<String>}.  A parameterized type is a class or interface.
 * <p>
 * A parameterized type is created the first time it is needed by a reflective
 * method, as specified in this package. When a parameterized type {@code p} is
 * created, the generic class or interface declaration that {@linkplain
 * #getRawType() defines} {@code p} is resolved, and all type arguments of
 * {@code p} are created recursively.  See {@link TypeVariable} for details on
 * the creation process for type variables. Repeated creation of a parameterized
 * type has no effect.
 * <p>
 * Two {@code ParameterizedType} objects should be compared using the {@link
 * #equals equals} method.
 *
 * @jls 4.5 Parameterized Types
 * @since 1.5
 */
public interface ParameterizedType extends Type {
    /**
     * {@return the type arguments of this type, as used in the source code}
     * <p>
     * This method does not return the type arguments of the {@linkplain
     * #getOwnerType() enclosing classes} of this type, if this type is nested.
     * For example, if this type is {@code O<T>.I<S>}, this method returns an
     * array containing exactly {@code S}.  In particular, if this is a
     * non-generic class in a generic enclosing class, such as the type {@code
     * O<T>.I}, this method returns an empty array.
     *
     * @throws TypeNotPresentException if any of the actual type arguments
     *     refers to a non-existent class or interface declaration
     * @throws MalformedParameterizedTypeException if any of the
     *     actual type arguments refer to a parameterized type that cannot
     *     be instantiated for any reason
     */
    Type[] getActualTypeArguments();

    /**
     * {@return the raw type of this type}  This is the generic class or
     * interface that defines this parameterized type, and applies recursively
     * to the {@linkplain #getOwnerType() type that this type is a member of} if
     * such a type exists.  For example, if this type is {@code O<T>.I<S>}, this
     * method returns a representation of {@code O.I}.
     * <p>
     * The returned object implements {@link Type##alone Type} without any other
     * subinterface.
     * <p>
     * This method performs type erasure (JLS {@jls 4.6}) for parameterized
     * types.
     *
     * @see Type##alone The {@code Type} interface alone
     * @jls 4.8 Raw Types
     */
    Type getRawType();

    /**
     * {@return the type that this type is a member of, or {@code null} if this
     * type is not a nested type}  The returned type is the immediately enclosing
     * class or interface of this type.
     * <p>
     * Top-level classes and interfaces, local classes and interfaces, and
     * anonymous classes are not members of other classes or interfaces.  For
     * example, if this type is {@code Map<K, V>}, this method returns {@code
     * null}.
     * <p>
     * If this type is explicitly or implicitly {@code static}, the class or
     * interface that declared this type is always represented by a {@link
     * Type##alone Type} without any other subinterface.  For example, if this
     * type is {@code Map.Entry<K, V>}, this method returns the raw type {@code
     * Map}.
     * <p>
     * If this type is not {@code static}, the class or interface that declared
     * this type may be a {@code ParameterizedType} that has more type arguments
     * in one of the enclosing classes and interfaces, or a {@link Type##alone
     * Type} without any other subinterface if there is no more type argument.
     * For example, if this type is {@code O<T>.I<S>}, this method returns the
     * parameterized type {@code O<T>}.
     *
     * @throws TypeNotPresentException if the immediately enclosing class or
     *     interface refers to a non-existent class or interface declaration
     * @throws MalformedParameterizedTypeException if the immediately enclosing
     *     class or interface refers to a parameterized type that cannot be
     *     instantiated for any reason
     * @see Class#getDeclaringClass() Class::getDeclaringClass
     * @jls 8.5 Member Class and Interface Declarations
     * @jls 9.5 Member Class and Interface Declarations
     */
    Type getOwnerType();

    /**
     * {@return whether some other object is equal to this {@code
     * ParameterizedType}}  Two instances of {@code ParameterizedType} are equal
     * if and only if they share the same {@linkplain #getRawType() generic
     * class or interface declaration} and have equal {@linkplain
     * #getActualTypeArguments() type arguments}, including those from the
     * {@linkplain #getOwnerType() enclosing classes}.
     *
     * @param o {@inheritDoc}
     */
    @Override
    boolean equals(Object o);
}
