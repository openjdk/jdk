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
 * {@code AnnotatedType} represents the potentially annotated (JLS {@jls 9.7.4})
 * use of a type or type argument in the current runtime. See {@link Type} for
 * the complete list of types and type arguments.
 * <p>
 * Here is a mapping from types and type arguments of the use, with examples,
 * to the modeling interfaces. "{@code AnnotatedType} alone" means the modeling
 * class does not implement any of the subinterfaces of {@code AnnotatedType}.
 * <ul>
 * <li>Primitive types (such as {@code @TA int}):
 *     {@code AnnotatedType} alone
 * <li>Reference types: <ul>
 *     <li>Class types and interface types:<ul>
 *         <li>Parameterized types (such as {@code @TA List<@TB ? extends @TC
 *             String>}): {@link AnnotatedParameterizedType}
 *         <li>Non-generic classes and interfaces (such as {@code @TC String})
 *             and raw types (such as {@code @TA List}):
 *             {@code AnnotatedType} alone
 *     </ul>
 *     <li>Type variables (such as {@code @TA T}):
 *         {@link AnnotatedTypeVariable}
 *     <li>Array types (such as {@code @TB int @TA []}):
 *         {@link AnnotatedArrayType}
 * </ul>
 * <li>Wildcard type arguments (such as {@code @TB ? extends @TC String}):
 *     {@link AnnotatedWildcardType}
 * </ul>
 * <p>
 * For example, an annotated use {@code @TB Outer.@TA Inner}, represented by
 * {@code AnnotatedType} alone, has an annotation {@code @TA} and represents the
 * non-generic {@code Outer.Inner} class. The use of its immediately enclosing
 * class is {@code @TB Outer}, with an annotation {@code @TB}, representing the
 * non-generic {@code Outer} class.
 * <p>
 * Note that any annotations returned by methods on this interface are
 * <em>type annotations</em> (JLS {@jls 9.7.4}) as the entity being
 * potentially annotated is a type.
 * <p>
 * Two {@code AnnotatedType} objects should be compared using the {@link
 * Object#equals equals} method.
 *
 * @see Type
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
     * of the type, or {@code null} if and only if the type is not an inner
     * member class}  For example, if this use is {@code @TB Outer.@TA Inner},
     * this method returns a representation of {@code @TB Outer}.
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
     * {@return the type that this potentially annotated use represents}
     * <p>
     * If this object does not implement any of the subinterfaces of {@code
     * AnnotatedType}, this use represents a primitive type, a non-generic class
     * or interface, or a raw type, and this method returns a {@link Class}.
     */
    Type getType();

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
