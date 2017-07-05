/*
 * Copyright 2003 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.reflect.generics.visitor;

import sun.reflect.generics.tree.*;

/**
 * Visit a TypeTree and produce a result of type T.
 */
public interface TypeTreeVisitor<T> {

    /**
     * Returns the result of the visit.
     * @return the result of the visit
     */
    T getResult();

    // Visitor methods, per node type

    void visitFormalTypeParameter(FormalTypeParameter ftp);

    void visitClassTypeSignature(ClassTypeSignature ct);
    void visitArrayTypeSignature(ArrayTypeSignature a);
    void visitTypeVariableSignature(TypeVariableSignature tv);
    void visitWildcard(Wildcard w);

    void visitSimpleClassTypeSignature(SimpleClassTypeSignature sct);
    void visitBottomSignature(BottomSignature b);

    //  Primitives and Void
    void visitByteSignature(ByteSignature b);
    void visitBooleanSignature(BooleanSignature b);
    void visitShortSignature(ShortSignature s);
    void visitCharSignature(CharSignature c);
    void visitIntSignature(IntSignature i);
    void visitLongSignature(LongSignature l);
    void visitFloatSignature(FloatSignature f);
    void visitDoubleSignature(DoubleSignature d);

    void visitVoidDescriptor(VoidDescriptor v);
}
