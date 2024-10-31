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
     * #getOwnerType() enclosing classes} of this type, if this is an
     * {@linkplain ##inner-member-class inner member class}.  For example, if
     * this type is {@code O<T>.I<S>}, this method returns an array containing
     * exactly {@code S}.  In particular, if this inner member class is
     * non-generic but an enclosing class is, this method returns an empty
     * array.
     *
     * @throws TypeNotPresentException if any of the actual type arguments
     *     refers to a non-existent class or interface declaration
     * @throws MalformedParameterizedTypeException if any of the
     *     actual type parameters refer to a parameterized type that cannot
     *     be instantiated for any reason
     */
    Type[] getActualTypeArguments();

    /**
     * {@return the raw type of this type}  This is the generic class or
     * interface that defines this parameterized type, and applies recursively
     * to the {@linkplain #getOwnerType() immediately enclosing class} of this
     * type if there is one.  For example, if this type is {@code O<T>.I<S>},
     * this method returns a representation of {@code O.I}.
     * <p>
     * The returned object is not an instance of {@link GenericArrayType},
     * {@link ParameterizedType}, {@link TypeVariable}, or {@link WildcardType}.
     * <p>
     * This method performs type erasure (JLS {@jls 4.6}) for parameterized
     * types.
     *
     * @see Type##alone The {@code Type} interface alone
     * @jls 4.8 Raw Types
     */
    Type getRawType();

    /**
     * {@return the immediately enclosing class of this type, or {@code null} if
     * and only if this type is not an inner member class}  For example, if this
     * type is {@code O<T>.I<S>}, this method returns a representation of {@code
     * O<T>}.
     *
     * <h4 id="inner-member-class">Inner member classes</h4>
     * An inner member class is both an inner class (JLS {@jls 8.1.3}) and a
     * member class (JLS {@jls 8.5}).  Any object of an inner member class
     * {@code C} has an immediately enclosing instance (JLS {@jls 15.9.2}) of
     * the {@linkplain Class#getDeclaringClass() immediately enclosing class} of
     * {@code C}.
     * <p>
     * A type is not an inner member class if it is not an inner class, such as
     * any interface, top-level class, or static nested class, or is not a
     * member class, such as any local or anonymous class.
     * <p>
     * Nested interfaces (JLS {@jls 9.1.1.3}) and interface members (JLS {@jls
     * 9.5}) are all implicitly {@code static}, so there is no inner member
     * interface, and the immediately enclosing class or interface for an inner
     * member class must be a class.
     * <p>
     * To check if a {@link Class} is an inner member class:
     * {@snippet lang=java :
     * // @replace substring="int.class" replacement=... :
     * Class<?> clazz = int.class;
     * // @link substring="getDeclaringClass" target="Class#getDeclaringClass()" :
     * return clazz.getDeclaringClass() != null &&
     * // @link region substring="isStatic" target="Modifier#isStatic(int)"
     * // @link substring="getModifiers" target="Class#getModifiers()":
     *         !Modifier.isStatic(clazz.getModifiers());
     * // @end
     * }
     *
     * @throws TypeNotPresentException if the immediately enclosing class refers
     *     to a non-existent class or interface declaration
     * @throws MalformedParameterizedTypeException if the immediately enclosing
     *     class refers to a parameterized type that cannot be instantiated for
     *     any reason
     * @jls 8.1.3 Inner Classes and Enclosing Instances
     * @jls 8.5 Member Class and Interface Declarations
     * @jls 15.9.2 Determining Enclosing Instances
     */
    Type getOwnerType();

    /**
     * {@return whether some other object is equal to this {@code
     * ParameterizedType}}  Two instances of {@code ParameterizedType} are equal
     * if and only if they share the same {@linkplain #getRawType() generic
     * class or interface declaration} and have equal {@linkplain
     * #getActualTypeArguments() type parameters}, including those from the
     * {@linkplain #getOwnerType() enclosing classes}.
     *
     * @param o {@inheritDoc}
     */
    @Override
    boolean equals(Object o);
}
