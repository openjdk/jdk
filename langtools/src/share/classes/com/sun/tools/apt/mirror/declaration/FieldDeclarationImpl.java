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
import com.sun.mirror.type.TypeMirror;
import com.sun.mirror.util.DeclarationVisitor;
import com.sun.tools.apt.mirror.AptEnv;
import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.code.TypeTags;


/**
 * Implementation of FieldDeclaration
 */
@SuppressWarnings("deprecation")
class FieldDeclarationImpl extends MemberDeclarationImpl
                                  implements FieldDeclaration {

    protected VarSymbol sym;

    FieldDeclarationImpl(AptEnv env, VarSymbol sym) {
        super(env, sym);
        this.sym = sym;
    }


    /**
     * Returns the field's name.
     */
    public String toString() {
        return getSimpleName();
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
    public Object getConstantValue() {
        Object val = sym.getConstValue();
        // val may be null, indicating that this is not a constant.

        return Constants.decodeConstant(val, sym.type);
    }

    /**
     * {@inheritDoc}
     */
    public String getConstantExpression() {
        Object val = getConstantValue();
        if (val == null) {
            return null;
        }
        Constants.Formatter fmtr = Constants.getFormatter();
        fmtr.append(val);
        return fmtr.toString();
    }

    /**
     * {@inheritDoc}
     */
    public void accept(DeclarationVisitor v) {
        v.visitFieldDeclaration(this);
    }
}
