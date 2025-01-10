/*
 * Copyright (c) 2012, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * {@code AnnotatedArrayType} represents the potentially annotated use of an
 * array type, whose component type may itself represent the annotated use of a
 * type.
 * <p>
 * For example, an annotated use {@code @TC int @TA [] @TB []} has an annotation
 * {@code @TA} and represents the array type {@code int[][]}.  Its component
 * type is the use {@code @TC int @TB []}, with an annotation {@code @TB},
 * representing the array type {@code int[]}.  Its element type is the use
 * {@code @TC int}, with an annotation {@code @TC}, representing the primitive
 * type {@code int}.
 * <p>
 * Two {@code AnnotatedArrayType} objects should be compared using the {@link
 * Object#equals equals} method.
 *
 * @jls 10.1 Array Types
 * @since 1.8
 */
public interface AnnotatedArrayType extends AnnotatedType {

    /**
     * {@return the potentially annotated use of the component type of the array
     * type}
     *
     * @see Class#componentType() Class::componentType
     * @see GenericArrayType#getGenericComponentType()
     *      GenericArrayType::getGenericComponentType
     */
    AnnotatedType getAnnotatedGenericComponentType();

    /**
     * {@return {@code null}}  An array type is not a member class or interface.
     *
     * @since 9
     */
    @Override
    AnnotatedType getAnnotatedOwnerType();

    /**
     * {@return the array type that this potentially annotated use represents}
     * Returns a {@link Class} representing an array type or a {@link
     * GenericArrayType}.
     */
    @Override
    Type getType();
}
