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

package com.sun.mirror.util;


import com.sun.mirror.type.*;


/**
 * A visitor for types, in the style of the standard visitor design pattern.
 * This is used to operate on a type when the kind
 * of type is unknown at compile time.
 * When a visitor is passed to a type's
 * {@link TypeMirror#accept accept} method,
 * the most specific <tt>visit<i>Xxx</i></tt> method applicable to
 * that type is invoked.
 *
 * @deprecated All components of this API have been superseded by the
 * standardized annotation processing API.  The replacement for the
 * functionality of this interface is {@link
 * javax.lang.model.element.TypeVisitor}.
 *
 * @author Joseph D. Darcy
 * @author Scott Seligman
 * @since 1.5
 */
@Deprecated
@SuppressWarnings("deprecation")
public interface TypeVisitor {

    /**
     * Visits a type mirror.
     *
     * @param t the type to visit
     */
    public void visitTypeMirror(TypeMirror t);

    /**
     * Visits a primitive type.

     * @param t the type to visit
     */
    public void visitPrimitiveType(PrimitiveType t);

    /**
     * Visits a void type.
     *
     * @param t the type to visit
     */
    public void visitVoidType(VoidType t);

    /**
     * Visits a reference type.
     *
     * @param t the type to visit
     */
    public void visitReferenceType(ReferenceType t);

    /**
     * Visits a declared type.
     *
     * @param t the type to visit
     */
    public void visitDeclaredType(DeclaredType t);

    /**
     * Visits a class type.
     *
     * @param t the type to visit
     */
    public void visitClassType(ClassType t);

    /**
     * Visits an enum type.
     *
     * @param t the type to visit
     */
    public void visitEnumType(EnumType t);

    /**
     * Visits an interface type.
     *
     * @param t the type to visit
     */
    public void visitInterfaceType(InterfaceType t);

    /**
     * Visits an annotation type.
     *
     * @param t the type to visit
     */
    public void visitAnnotationType(AnnotationType t);

    /**
     * Visits an array type.
     *
     * @param t the type to visit
     */
    public void visitArrayType(ArrayType t);

    /**
     * Visits a type variable.
     *
     * @param t the type to visit
     */
    public void visitTypeVariable(TypeVariable t);

    /**
     * Visits a wildcard.
     *
     * @param t the type to visit
     */
    public void visitWildcardType(WildcardType t);
}
