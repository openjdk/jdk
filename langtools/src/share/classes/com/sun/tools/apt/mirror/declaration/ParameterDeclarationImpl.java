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


import java.util.Collection;

import com.sun.mirror.declaration.*;
import com.sun.mirror.type.TypeMirror;
import com.sun.mirror.util.DeclarationVisitor;
import com.sun.tools.apt.mirror.AptEnv;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.code.Symbol.VarSymbol;


/**
 * Implementation of ParameterDeclaration
 */
@SuppressWarnings("deprecation")
public class ParameterDeclarationImpl extends DeclarationImpl
                                      implements ParameterDeclaration
{
    protected VarSymbol sym;


    ParameterDeclarationImpl(AptEnv env, VarSymbol sym) {
        super(env, sym);
        this.sym = sym;
    }


    /**
     * Returns the simple name of the parameter.
     */
    public String toString() {
        return getType() + " " + sym.name;
    }

    /**
     * {@inheritDoc}
     */
    public boolean equals(Object obj) {
        // Neither ParameterDeclarationImpl objects nor their symbols
        // are cached by the current implementation, so check symbol
        // owners and names.

        if (obj instanceof ParameterDeclarationImpl) {
            ParameterDeclarationImpl that = (ParameterDeclarationImpl) obj;
            return sym.owner == that.sym.owner &&
                   sym.name == that.sym.name &&
                   env == that.env;
        } else {
            return false;
        }
    }

    /**
     * {@inheritDoc}
     */
    public int hashCode() {
        return sym.owner.hashCode() + sym.name.hashCode() + env.hashCode();
    }

    /**
     * {@inheritDoc}
     */
    public TypeMirror getType() {
        return env.typeMaker.getType(sym.type);
    }

    /**
     * {@inheritDoc}
     */
    public void accept(DeclarationVisitor v) {
        v.visitParameterDeclaration(this);
    }
}
