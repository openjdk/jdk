/*
 * Copyright (c) 2012, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.annotation.Annotation;

/**
 * {@code AnnotatedType} represents the potentially annotated use of a type in
 * the program currently running in this VM. The use may be of any type in the
 * Java programming language, including an array type, a parameterized type, a
 * type variable, or a wildcard type.
 * <p>
 * Note that any annotations returned by methods on this interface are
 * <em>type annotations</em> (JLS {@jls 9.7.4}) as the entity being
 * potentially annotated is a type.
 *
 * <h2 id="hierarchy">Interface Hierarchy of {@code AnnotatedType}</h2>
 * Annotated use of types in the Java programming language is modeled with these
 * subinterfaces, and {@link #getType() getType()} can identify their underlying
 * {@linkplain Type##hierarchy types}:
 * <ul>
 * <li>No particular subinterface models primitive types (JLS {@jls 4.2}) and
 *     non-generic (JLS {@jls 4.5}) and raw types (JLS {@jls 4.8}) of classes
 *     and interfaces (JLS {@jls 4.3}).  Their underlying type is {@link Class}.
 * <li>{@link AnnotatedArrayType} models array types (JLS {@jls 10.1}). Their
 *     underlying type is {@link Class} or {@link GenericArrayType}.
 * <li>{@link AnnotatedParameterizedType} models parameterized types (JLS {@jls
 *     4.4}), including non-generic {@linkplain #getAnnotatedOwnerType() inner
 *     member classes} of generic classes.  Their underlying type is {@link
 *     ParameterizedType}.
 * <li>{@link AnnotatedTypeVariable} models type variable (JLS {@jls 4.4})
 *     usages.  Their underlying type is {@link TypeVariable}.
 * <li>{@link AnnotatedWildcardType} models wildcard {@linkplain
 *     AnnotatedParameterizedType#getAnnotatedActualTypeArguments() type
 *     arguments} (JLS {@jls 4.5.1}).  Their underlying type is {@link
 *     AnnotatedWildcardType}.
 * </ul>
 *
 * @jls 4.1 The Kinds of Types and Values
 * @jls 4.2 Primitive Types and Values
 * @jls 4.3 Reference Types and Values
 * @jls 4.4 Type Variables
 * @jls 4.5 Parameterized Types
 * @jls 4.8 Raw Types
 * @jls 4.9 Intersection Types
 * @jls 10.1 Array Types
 * @since 1.8
 */
public interface AnnotatedType extends AnnotatedElement {

    /**
     * {@return the potentially annotated use of the immediately enclosing class
     * of this type, or {@code null} if and only if this type is not an inner
     * member class}  For example, if this type is {@code @TA O<T>.I<S>}, this
     * method returns a representation of {@code @TA O<T>}.
     *
     * @implSpec
     * This default implementation returns {@code null} and performs no other
     * action.
     *
     * @throws TypeNotPresentException if the immediate enclosing class refers
     *     to a non-existent class declaration
     * @throws MalformedParameterizedTypeException if the immediate enclosing
     *     class refers to a parameterized type that cannot be instantiated for
     *     any reason
     * @see ParameterizedType##inner-member-class Inner member classes
     * @since 9
     */
    default AnnotatedType getAnnotatedOwnerType() {
        return null;
    }

    /**
     * {@return the underlying type that this annotated type represents}
     *
     * @see ##hierarchy Interface Hierarchy of {@code AnnotatedType}
     * @see Type##hierarchy Interface Hierarchy of {@code Type}
     */
    public Type getType();

    /**
     * {@inheritDoc}
     * <p>Note that any annotation returned by this method is a type
     * annotation.
     *
     * @throws NullPointerException {@inheritDoc}
     */
    @Override
    <T extends Annotation> T getAnnotation(Class<T> annotationClass);

    /**
     * {@inheritDoc}
     * <p>Note that any annotations returned by this method are type
     * annotations.
     */
    @Override
    Annotation[] getAnnotations();

    /**
     * {@inheritDoc}
     * <p>Note that any annotations returned by this method are type
     * annotations.
     */
    @Override
    Annotation[] getDeclaredAnnotations();
}
