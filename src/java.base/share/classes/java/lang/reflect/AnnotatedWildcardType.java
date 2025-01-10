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
 * {@code AnnotatedWildcardType} represents the potentially annotated use of a
 * wildcard type argument, whose upper or lower bounds may themselves represent
 * annotated uses of types.  Since the use of a wildcard type argument is not
 * the use of a type, it will only be returned by APIs where uses of type
 * arguments may appear, such as {@link
 * AnnotatedParameterizedType#getAnnotatedActualTypeArguments()
 * AnnotatedParameterizedType::getAnnotatedActualTypeArguments}.
 * <p>
 * For example, an annotated use {@code @TA ? extends @TB Number} has an
 * annotation {@code @TA} and represents the wildcard type argument {@code ?
 * extends Number}.  Its upper bound is the annotated use {@code @TB Number}
 * with an annotation {@code @TB}, representing the {@code Number} class.  It
 * has no lower bound.
 * <p>
 * Two {@code AnnotatedWildcardType} objects should be compared using the {@link
 * Object#equals equals} method.
 *
 * @see WildcardType
 * @jls 4.5.1 Type Arguments of Parameterized Types
 * @since 1.8
 */
public interface AnnotatedWildcardType extends AnnotatedType {

    /**
     * {@return the potentially annotated use of lower bounds of the wildcard
     * type argument}  A lower bound has the syntax {@code ? super B} in Java
     * source code, where {@code B} is the bound.  If no lower bound is
     * explicitly declared, the lower bound is the null type (JLS {@jls 4.1})
     * and the use is unannotated.  In this case, a zero length array is
     * returned.
     *
     * @apiNote
     * While to date a wildcard type argument may have at most one lower bound,
     * callers of this method should be written to accommodate multiple bounds.
     *
     * @see WildcardType#getLowerBounds()
     */
    AnnotatedType[] getAnnotatedLowerBounds();

    /**
     * {@return the potentially annotated use of upper bounds of the wildcard
     * type argument}  An upper bound has the syntax {@code ? extends B} in Java
     * source code, where {@code B} is the bound.  If no upper bound is
     * explicitly declared, the upper bound is the {@code Object} class and the
     * use is unannotated.
     *
     * @apiNote
     * While to date a wildcard type argument may have at most one upper bound,
     * callers of this method should be written to accommodate multiple bounds.
     *
     * @see WildcardType#getUpperBounds()
     */
    AnnotatedType[] getAnnotatedUpperBounds();

    /**
     * {@return {@code null}}  A wildcard type argument is not a member class or
     * interface.
     *
     * @since 9
     */
    @Override
    AnnotatedType getAnnotatedOwnerType();

    /**
     * {@return the wildcard type argument that this potentially annotated use
     * represents}  Returns a {@link WildcardType}.
     */
    @Override
    Type getType();
}
