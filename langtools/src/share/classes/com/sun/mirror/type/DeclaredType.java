/*
 * Copyright 2004 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.mirror.type;


import java.util.Collection;

import com.sun.mirror.declaration.TypeDeclaration;


/**
 * Represents a declared type, either a class type or an interface type.
 * This includes parameterized types such as {@code java.util.Set<String>}
 * as well as raw types.
 *
 * <p> While a <tt>TypeDeclaration</tt> represents the <i>declaration</i>
 * of a class or interface, a <tt>DeclaredType</tt> represents a class
 * or interface <i>type</i>, the latter being a use of the former.
 * See {@link TypeDeclaration} for more on this distinction.
 *
 * <p> A <tt>DeclaredType</tt> may represent a type
 * for which details (declaration, supertypes, <i>etc.</i>) are unknown.
 * This may be the result of a processing error, such as a missing class file,
 * and is indicated by {@link #getDeclaration()} returning <tt>null</tt>.
 * Other method invocations on such an unknown type will not, in general,
 * return meaningful results.
 *
 * @deprecated All components of this API have been superseded by the
 * standardized annotation processing API.  The replacement for the
 * functionality of this interface is included in {@link
 * javax.lang.model.type.DeclaredType}.
 *
 * @author Joseph D. Darcy
 * @author Scott Seligman
 * @since 1.5
 */
@Deprecated
@SuppressWarnings("deprecation")
public interface DeclaredType extends ReferenceType {

    /**
     * Returns the declaration of this type.
     *
     * <p> Returns null if this type's declaration is unknown.  This may
     * be the result of a processing error, such as a missing class file.
     *
     * @return the declaration of this type, or null if unknown
     */
    TypeDeclaration getDeclaration();

    /**
     * Returns the type that contains this type as a member.
     * Returns <tt>null</tt> if this is a top-level type.
     *
     * <p> For example, the containing type of {@code O.I<S>}
     * is the type {@code O}, and the containing type of
     * {@code O<T>.I<S>} is the type {@code O<T>}.
     *
     * @return the type that contains this type,
     * or <tt>null</tt> if this is a top-level type
     */
    DeclaredType getContainingType();

    /**
     * Returns (in order) the actual type arguments of this type.
     * For a generic type nested within another generic type
     * (such as {@code Outer<String>.Inner<Number>}), only the type
     * arguments of the innermost type are included.
     *
     * @return the actual type arguments of this type, or an empty collection
     * if there are none
     */
    Collection<TypeMirror> getActualTypeArguments();

    /**
     * Returns the interface types that are direct supertypes of this type.
     * These are the interface types implemented or extended
     * by this type's declaration, with any type arguments
     * substituted in.
     *
     * <p> For example, the interface type extended by
     * {@code java.util.Set<String>} is {@code java.util.Collection<String>}.
     *
     * @return the interface types that are direct supertypes of this type,
     * or an empty collection if there are none
     */
    Collection<InterfaceType> getSuperinterfaces();
}
