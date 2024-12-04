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
 * {@code AnnotatedTypeVariable} represents the potentially annotated use of a
 * type variable.
 * <p>
 * For example, an annotated use {@code @TA T} has an annotation {@code @TA}
 * and represents the type variable {@code T}.
 * <p>
 * Two {@code AnnotatedTypeVariable} objects should be compared using the {@link
 * Object#equals equals} method.
 *
 * @see TypeVariable
 * @jls 4.4 Type Variables
 * @since 1.8
 */
public interface AnnotatedTypeVariable extends AnnotatedType {

    /**
     * {@return the potentially annotated use of upper bounds of the type
     * variable}
     * <p>
     * Given an {@code AnnotatedTypeVariable tv}, the call {@code
     * tv.getAnnotatedBounds()} is equivalent to:
     * {@snippet lang=java :
     * // @link substring="getAnnotatedBounds" target="TypeVariable#getAnnotatedBounds()" :
     * ((TypeVariable<?>) tv.getType()).getAnnotatedBounds() // @link substring="getType" target="#getType()"
     * }
     *
     * @throws TypeNotPresentException if any of the bounds refers to a
     *     non-existent type declaration
     * @throws MalformedParameterizedTypeException if any of the bounds refer to
     *     a parameterized type that cannot be instantiated for any reason
     * @jls 4.9 Intersection Types
     * @see TypeVariable#getAnnotatedBounds()
     * @see TypeVariable#getBounds()
     */
    AnnotatedType[] getAnnotatedBounds();

    /**
     * {@return {@code null}}  A type variable is not an inner member class.
     *
     * @since 9
     */
    @Override
    AnnotatedType getAnnotatedOwnerType();

    /**
     * {@return the type variable that this potentially annotated use
     * represents}  Returns a {@link TypeVariable}.
     */
    @Override
    Type getType();
}
