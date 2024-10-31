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
 * {@code AnnotatedType} represents the potentially annotated use of a type or
 * type argument in the current runtime.  The use of a type (JLS {@jls 4.1}) is
 * the use of a primitive type or a reference type.  The use of a type argument
 * (JLS {@jls 4.5.1}) is the use of a reference type or a wildcard type
 * argument.
 * <table class="striped">
 * <caption style="display:none">
 * Types and Type Arguments Used to Modeling Interfaces
 * </caption>
 * <thead>
 * <tr><th colspan="3">Type or Type Argument Used
 *     <th>Example
 *     <th>Modeling interface
 * </thead>
 * <tbody>
 * <tr><td colspan="3">Primitive Types (JLS {@jls 4.2})
 *     <td>{@code @TA int}
 *     <td rowspan="3">{@link ##alone AnnotatedType}
 * <tr><td rowspan="5">Reference<br>Types<br>(JLS {@jls 4.3})
 *     <td rowspan="3">Classes<br>and<br>Interfaces
 *     <td>Non-generic Classes and<br>Interfaces
 *         (JLS {@jls 8.1.3}, {@jls 9.1.3})
 *     <td>{@code @TC String}
 * <tr><td>Raw Types (JLS {@jls 4.8})
 *     <td>{@code @TA List}
 * <tr><td>Parameterized Types (JLS {@jls 4.5})
 *     <td>{@code @TA List<@TB ? extends @TC String>}
 *     <td>{@link AnnotatedParameterizedType}
 * <tr><td colspan="2">Type Variables (JLS {@jls 4.4})
 *     <td>{@code @TA T}
 *     <td>{@link AnnotatedTypeVariable}
 * <tr><td colspan="2">Array Types (JLS {@jls 10.1})
 *     <td>{@code @TB int @TA []}
 *     <td>{@link AnnotatedArrayType}
 * <tr><td colspan="3">Wildcard Type Arguments (JLS {@jls 4.5.1})
 *     <td>{@code @TB ? extends @TC String}
 *     <td>{@link AnnotatedWildcardType}
 * </tbody>
 * </table>
 * <p>
 * Note that any annotations returned by methods on this interface are
 * <em>type annotations</em> (JLS {@jls 9.7.4}) as the entity being
 * potentially annotated is a type.
 * <p>
 * Two {@code AnnotatedType} objects should be compared using the {@link
 * Object#equals equals} method.
 *
 * <h2 id="alone">The {@code AnnotatedType} interface alone</h2>
 * Some {@code AnnotatedType} objects are not instances of the {@link
 * AnnotatedArrayType}, {@link AnnotatedParameterizedType}, {@link
 * AnnotatedTypeVariable}, or {@link AnnotatedWildcardType} subinterfaces.
 * Such a potentially annotated use represents a primitive type, a non-generic
 * class or interface, or a raw type, and the {@link #getType() getType()}
 * method returns a {@link Class}.
 * <p>
 * For example, an annotated use {@code @TB Outer.@TA Inner} has an annotation
 * {@code @TA} and represents the non-generic {@code Outer.Inner} class. The use
 * of its immediately enclosing class is {@code @TB Outer}, with an annotation
 * {@code @TB}, representing the non-generic {@code Outer} class.
 *
 * @see Type
 * @jls 4.11 Where Types Are Used
 * @jls 9.7.4 Where Annotations May Appear
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
     * If this object is not an instance of {@link AnnotatedArrayType}, {@link
     * AnnotatedParameterizedType}, {@link AnnotatedTypeVariable}, or {@link
     * AnnotatedWildcardType}, this method returns a {@link Class}.
     *
     * @see ##alone The {@code AnnotatedType} interface alone
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
