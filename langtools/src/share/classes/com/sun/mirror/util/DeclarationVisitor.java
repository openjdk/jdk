/*
 * Copyright (c) 2004, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.mirror.util;

import com.sun.mirror.declaration.*;


/**
 * A visitor for declarations, in the style of the standard visitor
 * design pattern.  Classes implementing this interface are used to
 * operate on a declaration when the kind of declaration is unknown at
 * compile time.  When a visitor is passed to a declaration's {@link
 * Declaration#accept accept} method, the most specific
 * <tt>visit<i>Xxx</i></tt> method applicable to that declaration is
 * invoked.
 *
 * @deprecated All components of this API have been superseded by the
 * standardized annotation processing API.  The replacement for the
 * functionality of this interface is {@link
 * javax.lang.model.element.ElementVisitor}.
 *
 * @author Joseph D. Darcy
 * @author Scott Seligman
 * @since 1.5
 */
@Deprecated
@SuppressWarnings("deprecation")
public interface DeclarationVisitor {

    /**
     * Visits a declaration.
     * @param d the declaration to visit
     */
    public void visitDeclaration(Declaration d);

    /**
     * Visits a package declaration.
     * @param d the declaration to visit
     */
    public void visitPackageDeclaration(PackageDeclaration d);

    /**
     * Visits a member or constructor declaration.
     * @param d the declaration to visit
     */
    public void visitMemberDeclaration(MemberDeclaration d);

    /**
     * Visits a type declaration.
     * @param d the declaration to visit
     */
    public void visitTypeDeclaration(TypeDeclaration d);

    /**
     * Visits a class declaration.
     * @param d the declaration to visit
     */
    public void visitClassDeclaration(ClassDeclaration d);

    /**
     * Visits an enum declaration.
     * @param d the declaration to visit
     */
    public void visitEnumDeclaration(EnumDeclaration d);

    /**
     * Visits an interface declaration.
     * @param d the declaration to visit
     */
    public void visitInterfaceDeclaration(InterfaceDeclaration d);

    /**
     * Visits an annotation type declaration.
     * @param d the declaration to visit
     */
    public void visitAnnotationTypeDeclaration(AnnotationTypeDeclaration d);

    /**
     * Visits a field declaration.
     * @param d the declaration to visit
     */
    public void visitFieldDeclaration(FieldDeclaration d);

    /**
     * Visits an enum constant declaration.
     * @param d the declaration to visit
     */
    public void visitEnumConstantDeclaration(EnumConstantDeclaration d);

    /**
     * Visits a method or constructor declaration.
     * @param d the declaration to visit
     */
    public void visitExecutableDeclaration(ExecutableDeclaration d);

    /**
     * Visits a constructor declaration.
     * @param d the declaration to visit
     */
    public void visitConstructorDeclaration(ConstructorDeclaration d);

    /**
     * Visits a method declaration.
     * @param d the declaration to visit
     */
    public void visitMethodDeclaration(MethodDeclaration d);

    /**
     * Visits an annotation type element declaration.
     * @param d the declaration to visit
     */
    public void visitAnnotationTypeElementDeclaration(
                                     AnnotationTypeElementDeclaration d);

    /**
     * Visits a parameter declaration.
     * @param d the declaration to visit
     */
    public void visitParameterDeclaration(ParameterDeclaration d);

    /**
     * Visits a type parameter declaration.
     * @param d the declaration to visit
     */
    public void visitTypeParameterDeclaration(TypeParameterDeclaration d);
}
