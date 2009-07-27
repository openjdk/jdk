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
 * A simple visitor for types.
 *
 * <p> The implementations of the methods of this class do nothing but
 * delegate up the type hierarchy.  A subclass should override the
 * methods that correspond to the kinds of types on which it will
 * operate.
 *
 * @deprecated All components of this API have been superseded by the
 * standardized annotation processing API.  The replacement for the
 * functionality of this class is {@link
 * javax.lang.model.util.SimpleTypeVisitor6}.
 *
 * @author Joseph D. Darcy
 * @author Scott Seligman
 * @since 1.5
 */
@Deprecated
@SuppressWarnings("deprecation")
public class SimpleTypeVisitor implements TypeVisitor {

    /**
     * Creates a new <tt>SimpleTypeVisitor</tt>.
     */
    public SimpleTypeVisitor() {}

    /**
     * Visits a type mirror.
     * The implementation does nothing.
     * @param t the type to visit
     */
    public void visitTypeMirror(TypeMirror t) {
    }

    /**
     * Visits a primitive type.
     * The implementation simply invokes
     * {@link #visitTypeMirror visitTypeMirror}.
     * @param t the type to visit
     */
    public void visitPrimitiveType(PrimitiveType t) {
        visitTypeMirror(t);
    }

    /**
     * Visits a void type.
     * The implementation simply invokes
     * {@link #visitTypeMirror visitTypeMirror}.
     * @param t the type to visit
     */
    public void visitVoidType(VoidType t) {
        visitTypeMirror(t);
    }

    /**
     * Visits a reference type.
     * The implementation simply invokes
     * {@link #visitTypeMirror visitTypeMirror}.
     * @param t the type to visit
     */
    public void visitReferenceType(ReferenceType t) {
        visitTypeMirror(t);
    }

    /**
     * Visits a declared type.
     * The implementation simply invokes
     * {@link #visitReferenceType visitReferenceType}.
     * @param t the type to visit
     */
    public void visitDeclaredType(DeclaredType t) {
        visitReferenceType(t);
    }

    /**
     * Visits a class type.
     * The implementation simply invokes
     * {@link #visitDeclaredType visitDeclaredType}.
     * @param t the type to visit
     */
    public void visitClassType(ClassType t) {
        visitDeclaredType(t);
    }

    /**
     * Visits an enum type.
     * The implementation simply invokes
     * {@link #visitClassType visitClassType}.
     * @param t the type to visit
     */
    public void visitEnumType(EnumType t) {
        visitClassType(t);
    }

    /**
     * Visits an interface type.
     * The implementation simply invokes
     * {@link #visitDeclaredType visitDeclaredType}.
     * @param t the type to visit
     */
    public void visitInterfaceType(InterfaceType t) {
        visitDeclaredType(t);
    }

    /**
     * Visits an annotation type.
     * The implementation simply invokes
     * {@link #visitInterfaceType visitInterfaceType}.
     * @param t the type to visit
     */
    public void visitAnnotationType(AnnotationType t) {
        visitInterfaceType(t);
    }

    /**
     * Visits an array type.
     * The implementation simply invokes
     * {@link #visitReferenceType visitReferenceType}.
     * @param t the type to visit
     */
    public void visitArrayType(ArrayType t) {
        visitReferenceType(t);
    }

    /**
     * Visits a type variable.
     * The implementation simply invokes
     * {@link #visitReferenceType visitReferenceType}.
     * @param t the type to visit
     */
    public void visitTypeVariable(TypeVariable t) {
        visitReferenceType(t);
    }

    /**
     * Visits a wildcard.
     * The implementation simply invokes
     * {@link #visitTypeMirror visitTypeMirror}.
     * @param t the type to visit
     */
    public void visitWildcardType(WildcardType t) {
        visitTypeMirror(t);
    }
}
