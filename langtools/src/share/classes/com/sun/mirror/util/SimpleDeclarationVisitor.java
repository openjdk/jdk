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
 * A simple visitor for declarations.
 *
 * <p> The implementations of the methods of this class do nothing but
 * delegate up the declaration hierarchy.  A subclass should override the
 * methods that correspond to the kinds of declarations on which it
 * will operate.
 *
 * @deprecated All components of this API have been superseded by the
 * standardized annotation processing API.  The replacement for the
 * functionality of this class is {@link
 * javax.lang.model.util.SimpleElementVisitor6}.
 *
 * @author Joseph D. Darcy
 * @author Scott Seligman
 * @since 1.5
 */
@Deprecated
@SuppressWarnings("deprecation")
public class SimpleDeclarationVisitor implements DeclarationVisitor {

    /**
     * Creates a new <tt>SimpleDeclarationVisitor</tt>.
     */
    public SimpleDeclarationVisitor(){}

    /**
     * Visits a declaration.
     * The implementation does nothing.
     * @param d the declaration to visit
     */
    public void visitDeclaration(Declaration d) {
    }

    /**
     * Visits a package declaration.
     * The implementation simply invokes
     * {@link #visitDeclaration visitDeclaration}.
     * @param d the declaration to visit
     */
    public void visitPackageDeclaration(PackageDeclaration d) {
        visitDeclaration(d);
    }

    /**
     * Visits a member or constructor declaration.
     * The implementation simply invokes
     * {@link #visitDeclaration visitDeclaration}.
     * @param d the declaration to visit
     */
    public void visitMemberDeclaration(MemberDeclaration d) {
        visitDeclaration(d);
    }

    /**
     * Visits a type declaration.
     * The implementation simply invokes
     * {@link #visitMemberDeclaration visitMemberDeclaration}.
     * @param d the declaration to visit
     */
    public void visitTypeDeclaration(TypeDeclaration d) {
        visitMemberDeclaration(d);
    }

    /**
     * Visits a class declaration.
     * The implementation simply invokes
     * {@link #visitTypeDeclaration visitTypeDeclaration}.
     * @param d the declaration to visit
     */
    public void visitClassDeclaration(ClassDeclaration d) {
        visitTypeDeclaration(d);
    }

    /**
     * Visits an enum declaration.
     * The implementation simply invokes
     * {@link #visitClassDeclaration visitClassDeclaration}.
     * @param d the declaration to visit
     */
    public void visitEnumDeclaration(EnumDeclaration d) {
        visitClassDeclaration(d);
    }

    /**
     * Visits an interface declaration.
     * The implementation simply invokes
     * {@link #visitTypeDeclaration visitTypeDeclaration}.
     * @param d the declaration to visit
     */
    public void visitInterfaceDeclaration(InterfaceDeclaration d) {
        visitTypeDeclaration(d);
    }

    /**
     * Visits an annotation type declaration.
     * The implementation simply invokes
     * {@link #visitInterfaceDeclaration visitInterfaceDeclaration}.
     * @param d the declaration to visit
     */
    public void visitAnnotationTypeDeclaration(AnnotationTypeDeclaration d) {
        visitInterfaceDeclaration(d);
    }

    /**
     * Visits a field declaration.
     * The implementation simply invokes
     * {@link #visitMemberDeclaration visitMemberDeclaration}.
     * @param d the declaration to visit
     */
    public void visitFieldDeclaration(FieldDeclaration d) {
        visitMemberDeclaration(d);
    }

    /**
     * Visits an enum constant declaration.
     * The implementation simply invokes
     * {@link #visitFieldDeclaration visitFieldDeclaration}.
     * @param d the declaration to visit
     */
    public void visitEnumConstantDeclaration(EnumConstantDeclaration d) {
        visitFieldDeclaration(d);
    }

    /**
     * Visits a method or constructor declaration.
     * The implementation simply invokes
     * {@link #visitMemberDeclaration visitMemberDeclaration}.
     * @param d the declaration to visit
     */
    public void visitExecutableDeclaration(ExecutableDeclaration d) {
        visitMemberDeclaration(d);
    }

    /**
     * Visits a constructor declaration.
     * The implementation simply invokes
     * {@link #visitExecutableDeclaration visitExecutableDeclaration}.
     * @param d the declaration to visit
     */
    public void visitConstructorDeclaration(ConstructorDeclaration d) {
        visitExecutableDeclaration(d);
    }

    /**
     * Visits a method declaration.
     * The implementation simply invokes
     * {@link #visitExecutableDeclaration visitExecutableDeclaration}.
     * @param d the declaration to visit
     */
    public void visitMethodDeclaration(MethodDeclaration d) {
        visitExecutableDeclaration(d);
    }

    /**
     * Visits an annotation type element declaration.
     * The implementation simply invokes
     * {@link #visitMethodDeclaration visitMethodDeclaration}.
     * @param d the declaration to visit
     */
    public void visitAnnotationTypeElementDeclaration(
            AnnotationTypeElementDeclaration d) {
        visitMethodDeclaration(d);
    }

    /**
     * Visits a parameter declaration.
     * The implementation simply invokes
     * {@link #visitDeclaration visitDeclaration}.
     * @param d the declaration to visit
     */
    public void visitParameterDeclaration(ParameterDeclaration d) {
        visitDeclaration(d);
    }

    /**
     * Visits a type parameter declaration.
     * The implementation simply invokes
     * {@link #visitDeclaration visitDeclaration}.
     * @param d the declaration to visit
     */
    public void visitTypeParameterDeclaration(TypeParameterDeclaration d) {
        visitDeclaration(d);
    }
}
