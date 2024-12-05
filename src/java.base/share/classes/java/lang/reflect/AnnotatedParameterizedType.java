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

/**
 * {@code AnnotatedParameterizedType} represents the potentially annotated use
 * of a parameterized type, whose type arguments may themselves represent
 * annotated uses of type arguments.
 * <p>
 * For example, an annotated use {@code Outer<@TC Long>.@TA Inner<@TB String>}
 * has an annotation {@code @TA} and represents the parameterized type {@code
 * Outer<Long>.Inner<String>}, a class.  It has exactly one type argument, which
 * is the annotated use {@code @TB String}, with an annotation {@code @TB},
 * representing the {@code String} class.  The use of its immediately enclosing
 * class is {@code Outer<@TC Long>}, with no annotation, representing the
 * parameterized type {@code Outer<Long>}.
 * <p>
 * Two {@code AnnotatedParameterizedType} objects should be compared using the
 * {@link Object#equals equals} method.
 *
 * @see ParameterizedType
 * @jls 4.5 Parameterized Types
 * @since 1.8
 */
public interface AnnotatedParameterizedType extends AnnotatedType {

    /**
     * {@return the potentially annotated use, as in the source code, of type
     * arguments of the parameterized type}
     * <p>
     * This method does not return the potentially annotated use of type
     * arguments of the {@linkplain #getAnnotatedOwnerType() enclosing classes}
     * of the parameterized type, if the parameterized type is nested.  For
     * example, if this use is {@code @TB O<@TC T>.@TA I<@TB S>}, this method
     * returns an array containing exactly the use of {@code @TB S}.  In
     * particular, if this nested type is a non-generic class in a generic
     * enclosing class, such as in the use {@code @TB O<@TC T>.@TA I}, this
     * method returns an empty array.
     *
     * @see ParameterizedType#getActualTypeArguments()
     */
    AnnotatedType[] getAnnotatedActualTypeArguments();

    /**
     * {@inheritDoc}
     *
     * @throws TypeNotPresentException {@inheritDoc}
     * @throws MalformedParameterizedTypeException {@inheritDoc}
     * @see ParameterizedType#getOwnerType()
     * @since 9
     */
    @Override
    AnnotatedType getAnnotatedOwnerType();

    /**
     * {@return the parameterized type that this potentially annotated use
     * represents}  Returns a {@link ParameterizedType}.
     */
    @Override
    Type getType();
}
