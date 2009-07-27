/*
 * Copyright 2004-2005 Sun Microsystems, Inc.  All Rights Reserved.
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


import java.util.Collection;
import java.util.ArrayList;

import com.sun.mirror.declaration.*;
import com.sun.mirror.type.ReferenceType;
import com.sun.tools.apt.mirror.AptEnv;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.code.Symbol.*;


/**
 * Implementation of ExecutableDeclaration
 */
@SuppressWarnings("deprecation")
public abstract class ExecutableDeclarationImpl extends MemberDeclarationImpl
                                             implements ExecutableDeclaration {
    public MethodSymbol sym;

    protected ExecutableDeclarationImpl(AptEnv env, MethodSymbol sym) {
        super(env, sym);
        this.sym = sym;
    }


    /**
     * Returns type parameters (if any), method name, and signature
     * (value parameter types).
     */
    public String toString() {
        return sym.toString();
    }

    /**
     * {@inheritDoc}
     */
    public boolean isVarArgs() {
        return AptEnv.hasFlag(sym, Flags.VARARGS);
    }

    /**
     * {@inheritDoc}
     */
    public Collection<ParameterDeclaration> getParameters() {
        Collection<ParameterDeclaration> res =
            new ArrayList<ParameterDeclaration>();
        for (VarSymbol param : sym.params())
            res.add(env.declMaker.getParameterDeclaration(param));
        return res;
    }

    /**
     * {@inheritDoc}
     */
    public Collection<ReferenceType> getThrownTypes() {
        ArrayList<ReferenceType> res = new ArrayList<ReferenceType>();
        for (Type t : sym.type.getThrownTypes()) {
            res.add((ReferenceType) env.typeMaker.getType(t));
        }
        return res;
    }
}
