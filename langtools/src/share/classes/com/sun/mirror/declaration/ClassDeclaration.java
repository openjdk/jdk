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

package com.sun.mirror.declaration;


import java.util.Collection;

import com.sun.mirror.type.ClassType;


/**
 * Represents the declaration of a class.
 * For the declaration of an interface, see {@link InterfaceDeclaration}.
 * Provides access to information about the class, its members, and
 * its constructors.
 * Note that an {@linkplain EnumDeclaration enum} is a kind of class.
 *
 * <p> While a <tt>ClassDeclaration</tt> represents the <i>declaration</i>
 * of a class, a {@link ClassType} represents a class <i>type</i>.
 * See {@link TypeDeclaration} for more on this distinction.
 *
 * <p> {@link com.sun.mirror.util.DeclarationFilter}
 * provides a simple way to select just the items of interest
 * when a method returns a collection of declarations.
 *
 * @deprecated All components of this API have been superseded by the
 * standardized annotation processing API.  The replacement for the
 * functionality of this interface is included in {@link
 * javax.lang.model.element.TypeElement}.
 *
 * @author Joseph D. Darcy
 * @author Scott Seligman
 *
 * @see ClassType
 * @since 1.5
 */
@Deprecated
@SuppressWarnings("deprecation")
public interface ClassDeclaration extends TypeDeclaration {

    /**
     * Returns the class type directly extended by this class.
     * The only class with no superclass is <tt>java.lang.Object</tt>,
     * for which this method returns null.
     *
     * @return the class type directly extended by this class, or null
     * if there is none
     */
    ClassType getSuperclass();

    /**
     * Returns the constructors of this class.
     * This includes the default constructor if this class has
     * no constructors explicitly declared.
     *
     * @return the constructors of this class
     *
     * @see com.sun.mirror.util.DeclarationFilter
     */
    Collection<ConstructorDeclaration> getConstructors();

    /**
     * {@inheritDoc}
     */
    Collection<MethodDeclaration> getMethods();
}
