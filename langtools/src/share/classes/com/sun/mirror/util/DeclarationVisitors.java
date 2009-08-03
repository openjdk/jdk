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

/**
 * Utilities to create specialized <tt>DeclarationVisitor</tt> instances.
 *
 * @deprecated All components of this API have been superseded by the
 * standardized annotation processing API.  There is no direct
 * replacement for the functionality of this class in the standardized
 * API due to that API's different visitor structure.
 *
 * @author Joseph D. Darcy
 * @author Scott Seligman
 * @since 1.5
 */
@Deprecated
@SuppressWarnings("deprecation")
public class DeclarationVisitors {
    private DeclarationVisitors(){} // do not instantiate.

    /**
     * A visitor that has no side effects and keeps no state.
     */
    public static final DeclarationVisitor NO_OP = new SimpleDeclarationVisitor();

    /**
     * Return a <tt>DeclarationVisitor</tt> that will scan the
     * declaration structure, visiting declarations contained in
     * another declaration.  For example, when visiting a class, the
     * fields, methods, constructors, etc. of the class are also
     * visited.  The order in which the contained declarations are scanned is
     * not specified.
     *
     * <p>The <tt>pre</tt> and <tt>post</tt>
     * <tt>DeclarationVisitor</tt> parameters specify,
     * respectively, the processing the scanner will do before or
     * after visiting the contained declarations.  If only one of pre
     * and post processing is needed, use {@link
     * DeclarationVisitors#NO_OP DeclarationVisitors.NO_OP} for the
     * other parameter.
     *
     * @param pre visitor representing processing to do before
     * visiting contained declarations.
     *
     * @param post visitor representing processing to do after
     * visiting contained declarations.
     */
    public static DeclarationVisitor getDeclarationScanner(DeclarationVisitor pre,
                                                           DeclarationVisitor post) {
        return new DeclarationScanner(pre, post);
    }

    /**
     * Return a <tt>DeclarationVisitor</tt> that will scan the
     * declaration structure, visiting declarations contained in
     * another declaration in source code order.  For example, when
     * visiting a class, the fields, methods, constructors, etc. of
     * the class are also visited.  The order in which the contained
     * declarations are visited is as close to source code order as
     * possible; declaration mirrors created from class files instead
     * of source code will not have source position information.
     *
     * <p>The <tt>pre</tt> and <tt>post</tt>
     * <tt>DeclarationVisitor</tt> parameters specify,
     * respectively, the processing the scanner will do before or
     * after visiting the contained declarations.  If only one of pre
     * and post processing is needed, use {@link
     * DeclarationVisitors#NO_OP DeclarationVisitors.NO_OP} for the other parameter.
     *
     * @param pre visitor representing processing to do before
     * visiting contained declarations.
     *
     * @param post visitor representing processing to do after
     * visiting contained declarations.
     */
    public static DeclarationVisitor getSourceOrderDeclarationScanner(DeclarationVisitor pre,
                                                                      DeclarationVisitor post) {
        return new SourceOrderDeclScanner(pre, post);
    }
}
