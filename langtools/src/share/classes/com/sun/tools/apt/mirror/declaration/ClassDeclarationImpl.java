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

package com.sun.tools.apt.mirror.declaration;


import java.lang.annotation.Annotation;
import java.lang.annotation.Inherited;
import java.util.ArrayList;
import java.util.Collection;

import com.sun.mirror.declaration.*;
import com.sun.mirror.type.ClassType;
import com.sun.mirror.util.DeclarationVisitor;
import com.sun.tools.apt.mirror.AptEnv;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.code.Symbol.*;


/**
 * Implementation of ClassDeclaration
 */
@SuppressWarnings("deprecation")
public class ClassDeclarationImpl extends TypeDeclarationImpl
                                  implements ClassDeclaration {

    ClassDeclarationImpl(AptEnv env, ClassSymbol sym) {
        super(env, sym);
    }


    /**
     * {@inheritDoc}
     * Overridden here to handle @Inherited.
     */
    public <A extends Annotation> A getAnnotation(Class<A> annoType) {

        boolean inherited = annoType.isAnnotationPresent(Inherited.class);
        for (Type t = sym.type;
             t.tsym != env.symtab.objectType.tsym && !t.isErroneous();
             t = env.jctypes.supertype(t)) {

            A result = getAnnotation(annoType, t.tsym);
            if (result != null || !inherited) {
                return result;
            }
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    public ClassType getSuperclass() {
        //  java.lang.Object has no superclass
        if (sym == env.symtab.objectType.tsym) {
            return null;
        }
        Type t = env.jctypes.supertype(sym.type);
        return (ClassType) env.typeMaker.getType(t);
    }

    /**
     * {@inheritDoc}
     */
    public Collection<ConstructorDeclaration> getConstructors() {
        ArrayList<ConstructorDeclaration> res =
            new ArrayList<ConstructorDeclaration>();
        for (Symbol s : getMembers(true)) {
            if (s.isConstructor()) {
                MethodSymbol m = (MethodSymbol) s;
                res.add((ConstructorDeclaration)
                        env.declMaker.getExecutableDeclaration(m));
            }
        }
        return res;
    }

    /**
     * {@inheritDoc}
     */
    public Collection<MethodDeclaration> getMethods() {
        return identityFilter.filter(super.getMethods(),
                                     MethodDeclaration.class);
    }

    /**
     * {@inheritDoc}
     */
    public void accept(DeclarationVisitor v) {
        v.visitClassDeclaration(this);
    }
}
